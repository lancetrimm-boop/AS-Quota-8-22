package com.example.data

import android.util.Log
import com.example.data.db.*
import com.example.data.blueprint.*
import com.example.data.intelligence.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/**
 * Repository for managing the Aura Intelligence lifecycle.
 * Handles persistence of Findings, Suggested Improvements, and Lifecycle Events.
 */
class IntelligenceRepository(
    private val dao: com.example.data.db.IntelligenceDao,
    val mediaRepository: MediaRepository = MediaRepository(),
    private val moshi: Moshi = Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val database: AuraDatabase? = null
) {
    private val reportingEngine = database?.let { IntelligenceReportingEngine(it) }
    private val _snapshotReport = MutableStateFlow<IntelligenceSnapshotReport?>(null)
    val snapshotReport: StateFlow<IntelligenceSnapshotReport?> = _snapshotReport.asStateFlow()
    private val strategyBlueprintAdapter = moshi.adapter(StrategyBlueprint::class.java)
    private val proposedChangesAdapter = moshi.adapter(ProposedChanges::class.java)
    private val implementationPlanAdapter = moshi.adapter(ImplementationPlan::class.java)
    private val verificationPlanAdapter = moshi.adapter(VerificationPlan::class.java)
    private val rollbackPlanAdapter = moshi.adapter(RollbackPlan::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )
    private val stringMapAdapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )
    private val doubleMapAdapter = moshi.adapter<Map<String, Double>>(
        Types.newParameterizedType(Map::class.java, String::class.java, java.lang.Double::class.java)
    )
    private val metadataAdapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

    private fun normalizePath(path: String): String {
        return path.replace("\\", "/").lowercase().trim()
    }

    init {
        startEventProcessor()
        observeDataChanges()
    }

    private fun observeDataChanges() {
        scope.launch {
            mediaRepository.tasteDNA
                .debounce(5000) // Don't churn on every slider move
                .collect { dna ->
                    refreshIntelligenceSnapshot(dna)
                }
        }

        // Also trigger on significant interaction changes
        database?.let { db ->
            scope.launch {
                db.pairwiseDao().getAllOutcomes()
                    .debounce(10000)
                    .collect { _ -> refreshIntelligenceSnapshot() }
            }
            scope.launch {
                db.aiSkipDao().observeAllEvents()
                    .debounce(10000)
                    .collect { _ -> refreshIntelligenceSnapshot() }
            }
        }
    }

    suspend fun refreshIntelligenceSnapshot(dna: TasteDNA? = null) {
        val currentDna = dna ?: mediaRepository.tasteDNA.value
        reportingEngine?.let { engine ->
            try {
                val report = engine.generateSnapshotReport(currentDna)
                _snapshotReport.value = report
            } catch (e: Exception) {
                Log.e("IntelligenceRepository", "Failed to refresh intelligence snapshot", e)
            }
        }
    }

    /**
     * Retrieves an insight explaining why a specific item was recommended.
     */
    suspend fun getRecommendationInsight(item: com.example.data.MediaItem): RecommendationInsightSnapshot? {
        return reportingEngine?.generateRecommendationInsight(item, mediaRepository.tasteDNA.value)
    }

    private fun startEventProcessor() {
        scope.launch {
            dao.getPendingEvents().collect { events ->
                events.forEach { event ->
                    // Attempt to lock event processing
                    try {
                        val current = dao.getAllIntelligenceEvents().first().find { it.id == event.id }
                        if (current?.status == "PENDING") {
                            dao.updateEvent(current.copy(status = "PROCESSING"))
                            processEvent(current)
                        }
                    } catch (e: Exception) {
                        Log.e("IntelligenceRepository", "Event processor conflict", e)
                    }
                }
            }
        }
    }

    private suspend fun processEvent(event: IntelligenceEventEntity) {
        try {
            when (event.type) {
                "EVIDENCE_AVAILABLE" -> {
                    val report = generateClosedLoopReport()
                    createFindingFromReport(report, "Personalization Update")
                }
                "MONITORING_EVIDENCE_AVAILABLE" -> {
                }
                "FINDING_CREATED" -> {
                }
            }
            dao.updateEvent(event.copy(status = "COMPLETED"))
        } catch (e: Exception) {
            val retry = event.retryCount + 1
            if (retry < 3) {
                dao.updateEvent(event.copy(status = "PENDING", retryCount = retry))
            } else {
                dao.updateEvent(event.copy(status = "FAILED", failureReason = e.message))
            }
        }
    }

    suspend fun onEvidenceAvailable(evidenceId: String) {
        val event = IntelligenceEventEntity(
            id = UUID.randomUUID().toString(),
            type = "EVIDENCE_AVAILABLE",
            sourceId = evidenceId
        )
        dao.insertEvent(event)
    }

    suspend fun generateClosedLoopReport(): ClosedLoopReport {
        val evidenceEntities = dao.getAllStoredEvidence().first()
        val evidenceList = evidenceEntities.map { entity ->
            EvidenceRecord(
                id = entity.id,
                tier = EvidenceTier.valueOf(entity.tier),
                sampleCount = entity.sampleCount,
                score = entity.score,
                quality = entity.quality,
                source = entity.source,
                timestamp = entity.timestamp,
                associatedManifestId = entity.associatedManifestId
            )
        }

        val baseline = 50.0 
        val productionEvidence = evidenceList.filter { it.tier == EvidenceTier.PRODUCTION }
        val measured = if (productionEvidence.isNotEmpty()) {
            productionEvidence.sumOf { it.score * it.sampleCount } / productionEvidence.sumOf { it.sampleCount }.toDouble()
        } else baseline

        return ClosedLoopEngine.evaluate(
            baselineScore = baseline,
            measuredScore = measured,
            targetScore = measured + 5.0, 
            evidenceList = evidenceList
        )
    }

    fun getAllFindings(): Flow<List<Finding>> = dao.getAllFindings().map { entities ->
        entities.map { it.toDomainFinding() }
    }

    fun getAllImprovements(): Flow<List<SuggestedImprovement>> = dao.getAllImprovements().map { entities ->
        entities.map { it.toDomainImprovement() }
    }

    fun getAllActions(): Flow<List<IntelligenceAction>> = dao.getAllActions().map { entities ->
        entities.map { entity ->
             IntelligenceAction(
                id = entity.id,
                improvementId = entity.improvementId,
                type = entity.type,
                status = entity.status,
                plan = implementationPlanAdapter.fromJson(entity.planJson)!!,
                result = entity.resultJson,
                updatedAt = entity.updatedAt
            )
        }
    }

    fun getLifecycleHistory(targetId: String): Flow<List<LifecycleEvent>> = dao.getLifecycleHistory(targetId).map { entities ->
        entities.map { it.toDomainLifecycleEvent() }
    }

    suspend fun createFindingFromReport(report: ClosedLoopReport, title: String): Finding {
        val blueprint = StrategyBlueprintGenerator.generateBlueprint(title, report.summaryMessage, report)
        val finding = Finding(
            id = "FINDING-${UUID.randomUUID().toString().take(8).uppercase()}",
            title = title,
            summary = report.summaryMessage,
            classification = report.toClassification(),
            confidence = report.toConfidenceLevel(),
            dateDiscovered = System.currentTimeMillis(),
            technicalDetails = blueprint,
            lifecycleState = if (report.outcomeClassification == OutcomeClassification.REGRESSION_DETECTED) {
                IntelligenceLifecycleState.REGRESSION_DETECTED
            } else {
                IntelligenceLifecycleState.FINDING_DETECTED
            }
        )
        
        dao.insertFinding(finding.toEntityFinding())
        recordLifecycleEvent(finding.id, null, finding.lifecycleState, "Initial detection from ClosedLoopReport")
        
        createAttentionItem(
            sourceType = "Finding",
            sourceId = finding.id,
            type = if (finding.classification == FindingClassification.REGRESSION) AttentionType.REGRESSION_DETECTED else AttentionType.REVIEW_RECOMMENDED,
            priority = if (finding.classification == FindingClassification.REGRESSION) DecisionPriority.HIGH else DecisionPriority.MEDIUM,
            title = finding.title,
            summary = finding.summary,
            whyItMatters = "New intelligence requires human review.",
            requiresAction = finding.classification == FindingClassification.REGRESSION,
            deepLink = "finding/${finding.id}"
        )
        
        if (evaluateActionability(finding)) {
            val existing = findEquivalentImprovement(finding)
            if (existing != null) {
                recordLifecycleEvent(existing.id, existing.status, existing.status, "Recurring finding ${finding.id} linked.")
                val updated = existing.copy(
                    evidenceCount = existing.evidenceCount + finding.technicalDetails.evidence.productionCount,
                )
                dao.updateImprovement(updated.toEntityImprovement())
                transitionFindingState(finding.id, IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT, "Finding linked to existing improvement ${existing.id}")
            } else {
                generateImprovementFromFinding(finding)
            }
            return finding.copy(lifecycleState = IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT)
        } else if (finding.classification == FindingClassification.REGRESSION) {
            val alertId = createRegressionAlertFromFinding(finding)
            recordLifecycleEvent(finding.id, finding.lifecycleState, IntelligenceLifecycleState.REGRESSION_DETECTED, "Regression alert created: $alertId")
        }

        return finding
    }

    private fun evaluateActionability(finding: Finding): Boolean {
        if (finding.classification != FindingClassification.IMPROVEMENT_OPPORTUNITY && 
            finding.classification != FindingClassification.ACTION_REQUIRED) {
            return false
        }
        val bp = finding.technicalDetails
        val hasEvidence = bp.evidence.productionCount >= 5 || bp.evidence.experimentalCount >= 10
        val hasChanges = bp.proposedModifications.isNotEmpty()
        return hasEvidence && hasChanges
    }

    private suspend fun findEquivalentImprovement(finding: Finding): SuggestedImprovement? {
        val activeImprovements = dao.getAllImprovements().first()
            .map { it.toDomainImprovement() }
            .filter { it.status != IntelligenceLifecycleState.VALIDATED && 
                     it.status != IntelligenceLifecycleState.REJECTED &&
                     it.status != IntelligenceLifecycleState.ROLLED_BACK }
        
        val targetMetric = finding.technicalDetails.targetState.targetMetric
        val affectedComponents = finding.technicalDetails.executionPlan.affectedComponents.toSet()

        return activeImprovements.find { imp ->
            val impBp = imp.technicalDetails
            impBp != null && 
            impBp.targetState.targetMetric == targetMetric &&
            impBp.executionPlan.affectedComponents.toSet() == affectedComponents
        }
    }

    private suspend fun generateImprovementFromFinding(finding: Finding): SuggestedImprovement {
        val blueprint = finding.technicalDetails
        val improvement = SuggestedImprovement(
            id = "IMP-${UUID.randomUUID().toString().take(8).uppercase()}",
            findingId = finding.id,
            title = blueprint.title,
            summary = blueprint.description,
            priority = if (blueprint.expectedOutcome.riskLevel == "HIGH") "HIGH" else "MEDIUM",
            expectedImpact = blueprint.expectedOutcome.expectedImprovement,
            risk = blueprint.expectedOutcome.riskLevel,
            confidence = blueprint.diagnosis.diagnosticConfidence.toConfidenceLevel(),
            evidenceCount = blueprint.evidence.productionCount + blueprint.evidence.experimentalCount + blueprint.evidence.simulationCount,
            source = "Automated Pipeline",
            classification = finding.classification,
            rationale = "Aura identified ${finding.classification.name.replace("_", " ")} based on production evidence.",
            whatWillChange = blueprint.proposedModifications.joinToString(", ") { "${it.component}.${it.parameter}" },
            whatWillNotChange = "Core system architecture.",
            proposedChanges = ProposedChanges(blueprint.proposedModifications),
            implementationPlan = ImplementationPlan(
                steps = blueprint.executionPlan.intendedActions,
                targetComponents = blueprint.executionPlan.affectedComponents
            ),
            verificationPlan = VerificationPlan(
                criteria = blueprint.targetState.successCriteria,
                testCases = blueprint.productionValidationRequirements
            ),
            rollbackPlan = RollbackPlan(
                procedure = blueprint.experimentDesign.rollbackCondition,
                criteria = blueprint.experimentDesign.rollbackCondition
            ),
            technicalDetails = blueprint,
            blueprintArtifactId = blueprint.id,
            status = IntelligenceLifecycleState.NEEDS_REVIEW,
            createdAt = System.currentTimeMillis()
        )

        dao.insertImprovement(improvement.toEntityImprovement())
        recordLifecycleEvent(improvement.id, null, improvement.status, "Improvement package generated from finding ${finding.id}")

        createAttentionItem(
            sourceType = "Improvement",
            sourceId = improvement.id,
            type = AttentionType.DECISION_REQUIRED,
            priority = if (improvement.risk == "HIGH") DecisionPriority.HIGH else DecisionPriority.MEDIUM,
            title = "Review: ${improvement.title}",
            summary = improvement.summary,
            whyItMatters = "Optimization requires human authorization.",
            requiresAction = true,
            deepLink = "improvement/${improvement.id}"
        )

        val artifact = BlueprintArtifact(
            blueprintId = blueprint.id,
            strategyBlueprint = blueprint,
            lifecycleState = BlueprintLifecycleState.PROPOSED
        )
        val artifactEntity = BlueprintArtifactEntity(
            id = "ART-${UUID.randomUUID().toString().take(8).uppercase()}",
            blueprintId = blueprint.id,
            improvementId = improvement.id,
            proposalVersion = 1,
            version = "1.0.0",
            dataJson = moshi.adapter(BlueprintArtifact::class.java).toJson(artifact)
        )
        dao.insertArtifact(artifactEntity)
        transitionFindingState(finding.id, IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT, "Finding transitioned to suggested improvement ${improvement.id}")
        return improvement
    }

    private suspend fun createRegressionAlertFromFinding(finding: Finding): String {
        val bp = finding.technicalDetails
        val report = bp.closedLoopReport ?: return "N/A"
        
        val alert = RegressionAlertEntity(
            id = "REG-${UUID.randomUUID().toString().take(8).uppercase()}",
            improvementId = "N/A",
            runId = "N/A",
            artifactId = bp.id,
            sessionId = "N/A",
            severity = if (report.outcomeClassification == OutcomeClassification.REGRESSION_DETECTED) RegressionSeverity.HIGH else RegressionSeverity.MEDIUM,
            affectedMetric = bp.targetState.targetMetric,
            baselineValue = report.baselineScore,
            preRegressionValue = report.baselineScore,
            currentResult = report.measuredScore,
            change = report.measuredScore - report.baselineScore,
            evidenceIdsJson = "[]",
            confidence = report.overallConfidence,
            status = RegressionAlertStatus.ACTIVE,
            recommendation = "Investigate performance drop in ${bp.targetState.targetMetric}."
        )
        dao.insertRegressionAlert(alert)
        
        createAttentionItem(
            sourceType = "Regression",
            sourceId = alert.id,
            type = AttentionType.REGRESSION_DETECTED,
            priority = DecisionPriority.CRITICAL,
            title = "Regression: ${alert.affectedMetric}",
            summary = "Performance dropped from ${alert.baselineValue} to ${alert.currentResult}",
            whyItMatters = "Immediate investigation or rollback required.",
            requiresAction = true,
            deepLink = "improvement/${alert.improvementId}"
        )
        return alert.id
    }

    suspend fun transitionFindingState(id: String, newState: IntelligenceLifecycleState, reason: String? = null) {
        val entity = dao.getFindingById(id) ?: return
        if (canTransition(entity.lifecycleState, newState)) {
            val updated = entity.copy(lifecycleState = newState)
            dao.updateFinding(updated)
            recordLifecycleEvent(id, entity.lifecycleState, newState, reason)
        } else {
            throw IllegalStateException("Invalid transition from ${entity.lifecycleState} to $newState")
        }
    }

    suspend fun transitionImprovementState(id: String, newState: IntelligenceLifecycleState, reason: String? = null) {
        val entity = dao.getImprovementById(id) ?: return
        if (canTransition(entity.status, newState)) {
            val updated = entity.copy(status = newState)
            dao.updateImprovement(updated)
            recordLifecycleEvent(id, entity.status, newState, reason)
        } else {
            throw IllegalStateException("Invalid transition from ${entity.status} to $newState")
        }
    }

    suspend fun proposeImprovement(findingId: String, blueprint: StrategyBlueprint): SuggestedImprovement {
        val existing = dao.getImprovementsForFinding(findingId).first().firstOrNull()
        if (existing != null) {
            return existing.toDomainImprovement()
        }

        val improvement = SuggestedImprovement(
            id = "IMP-${UUID.randomUUID().toString().take(8).uppercase()}",
            findingId = findingId,
            title = blueprint.title,
            summary = blueprint.description,
            priority = if (blueprint.expectedOutcome.riskLevel == "HIGH") "HIGH" else "MEDIUM",
            expectedImpact = blueprint.expectedOutcome.expectedImprovement,
            risk = blueprint.expectedOutcome.riskLevel,
            confidence = blueprint.diagnosis.diagnosticConfidence.toConfidenceLevel(),
            evidenceCount = blueprint.evidence.productionCount + blueprint.evidence.experimentalCount + blueprint.evidence.simulationCount,
            source = blueprint.evidence.provenance,
            classification = FindingClassification.IMPROVEMENT_OPPORTUNITY,
            rationale = blueprint.strategySelection.rationale,
            whatWillChange = blueprint.proposedModifications.joinToString(", ") { "${it.component}.${it.parameter}" },
            whatWillNotChange = "Base system architecture.",
            proposedChanges = ProposedChanges(blueprint.proposedModifications),
            implementationPlan = ImplementationPlan(
                steps = blueprint.executionPlan.intendedActions,
                targetComponents = blueprint.executionPlan.affectedComponents
            ),
            verificationPlan = VerificationPlan(
                criteria = blueprint.targetState.successCriteria,
                testCases = blueprint.productionValidationRequirements
            ),
            rollbackPlan = RollbackPlan(
                procedure = blueprint.experimentDesign.rollbackCondition,
                criteria = blueprint.experimentDesign.rollbackCondition
            ),
            technicalDetails = blueprint,
            blueprintArtifactId = blueprint.id,
            status = IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT,
            createdAt = System.currentTimeMillis()
        )

        dao.insertImprovement(improvement.toEntityImprovement())
        recordLifecycleEvent(improvement.id, null, improvement.status, "Improvement proposed")
        transitionFindingState(findingId, IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT, "Finding has suggested improvement ${improvement.id}")
        return improvement
    }

    private suspend fun recordLifecycleEvent(
        targetId: String,
        fromState: IntelligenceLifecycleState?,
        toState: IntelligenceLifecycleState,
        reason: String? = null,
        metadata: Map<String, String>? = null
    ) {
        val event = LifecycleEventEntity(
            targetId = targetId,
            fromState = fromState,
            toState = toState,
            reason = reason,
            metadataJson = metadata?.let { metadataAdapter.toJson(it) }
        )
        dao.insertLifecycleEvent(event)
    }

    suspend fun approveImprovement(improvementId: String, artifact: BlueprintArtifact, reason: String? = null) {
        val improvementEntity = dao.getImprovementById(improvementId) ?: return
        val currentReport = improvementEntity.toDomainImprovement().technicalDetails?.closedLoopReport
        if (currentReport != null) {
            val snapshot = EvidenceSnapshotEntity(
                improvementId = improvementId,
                blueprintId = artifact.blueprintId,
                reportJson = moshi.adapter(ClosedLoopReport::class.java).toJson(currentReport)
            )
            dao.insertEvidenceSnapshot(snapshot)
        }

        val blueprint = artifact.strategyBlueprint
        val manifest = BlueprintImplementationPlanner.planImplementation(blueprint)
        val instructions = BuilderInstructionGenerator.generate(blueprint, manifest)
        
        val authorizedArtifact = artifact.copy(
            lifecycleState = BlueprintLifecycleState.APPROVED,
            strategyBlueprint = blueprint.copy(builderInstructions = instructions, identity = blueprint.identity.copy(status = StrategyStatus.PROPOSED)),
            builderInstructions = instructions,
            implementationManifest = manifest
        )

        val artifactEntity = BlueprintArtifactEntity(
            id = "ART-${UUID.randomUUID().toString().take(8).uppercase()}",
            blueprintId = artifact.blueprintId,
            improvementId = improvementId,
            proposalVersion = improvementEntity.version,
            version = artifact.blueprintVersion,
            dataJson = moshi.adapter(BlueprintArtifact::class.java).toJson(authorizedArtifact)
        )
        dao.insertArtifact(artifactEntity)

        val updatedImprovement = improvementEntity.copy(
            status = IntelligenceLifecycleState.APPROVED,
            blueprintArtifactId = artifactEntity.id
        )
        dao.updateImprovement(updatedImprovement)
        recordLifecycleEvent(improvementId, improvementEntity.status, IntelligenceLifecycleState.APPROVED, reason)
        dao.deleteActiveAttentionItemByDeduplicationKey("Improvement:$improvementId:${AttentionType.DECISION_REQUIRED}")
        transitionFindingState(improvementEntity.findingId, IntelligenceLifecycleState.APPROVED, "Improvement $improvementId approved")
    }

    fun validateScope(improvement: SuggestedImprovement, artifact: BlueprintArtifact): ScopeValidationResult {
        val approvedMods = improvement.proposedChanges.modifications.map { "${it.component}.${it.parameter}" }.toSet()
        val technicalMods = artifact.strategyBlueprint.proposedModifications.map { "${it.component}.${it.parameter}" }.toSet()
        if (!technicalMods.containsAll(approvedMods)) return ScopeValidationResult.Incomplete(listOf("Missing technical parameters"))
        if (!approvedMods.containsAll(technicalMods)) return ScopeValidationResult.Mismatch("Unauthorized changes")
        return ScopeValidationResult.Valid
    }

    fun getArtifactsForImprovement(improvementId: String): Flow<List<BlueprintArtifact>> = dao.getArtifactsForImprovement(improvementId).map { entities ->
        entities.map { moshi.adapter(BlueprintArtifact::class.java).fromJson(it.dataJson)!! }
    }

    /**
     * Updates an existing Blueprint Artifact. Blocks updates if the artifact is APPROVED.
     */
    suspend fun updateBlueprintArtifact(improvementId: String, artifact: BlueprintArtifact) {
        val existingEntities = dao.getArtifactsForImprovement(improvementId).first()
        val approved = existingEntities.any { 
            val domain = moshi.adapter(BlueprintArtifact::class.java).fromJson(it.dataJson)
            domain?.lifecycleState == BlueprintLifecycleState.APPROVED 
        }
        
        if (approved) {
            throw IllegalStateException("Cannot modify an APPROVED Blueprint. Create a new version instead.")
        }
        
        val entity = BlueprintArtifactEntity(
            id = artifact.blueprintId,
            blueprintId = artifact.blueprintId,
            improvementId = improvementId,
            proposalVersion = 1,
            version = artifact.blueprintVersion,
            dataJson = moshi.adapter(BlueprintArtifact::class.java).toJson(artifact)
        )
        dao.insertArtifact(entity)
    }

    suspend fun createBlueprintArtifactVersion(improvementId: String, artifact: BlueprintArtifact) {
        val improvementEntity = dao.getImprovementById(improvementId) ?: return
        val improvement = improvementEntity.toDomainImprovement()
        
        val validation = validateScope(improvement, artifact)
        if (validation is ScopeValidationResult.Mismatch || validation is ScopeValidationResult.Incomplete) {
            transitionImprovementState(improvementId, IntelligenceLifecycleState.NEEDS_REVIEW, "Technical scope mismatch. Reapproval required.")
        }
        
        val entity = BlueprintArtifactEntity(
            id = "ART-${UUID.randomUUID().toString().take(8).uppercase()}",
            blueprintId = artifact.blueprintId,
            improvementId = improvementId,
            proposalVersion = 1,
            version = artifact.blueprintVersion,
            dataJson = moshi.adapter(BlueprintArtifact::class.java).toJson(artifact)
        )
        dao.insertArtifact(entity)
    }


    suspend fun planImplementation(improvementId: String) {
        val improvementEntity = dao.getImprovementById(improvementId) ?: return
        if (improvementEntity.status != IntelligenceLifecycleState.APPROVED && 
            improvementEntity.status != IntelligenceLifecycleState.IMPLEMENTATION_PLANNED &&
            improvementEntity.status != IntelligenceLifecycleState.IMPLEMENTATION_FAILED &&
            improvementEntity.status != IntelligenceLifecycleState.VERIFICATION_FAILED) {
            throw IllegalStateException("Implementation blocked: Current status: ${improvementEntity.status}")
        }
        val artifactEntity = if (improvementEntity.blueprintArtifactId != null) {
            dao.getArtifactById(improvementEntity.blueprintArtifactId) ?: throw IllegalStateException("Blueprint Artifact not found for ID: ${improvementEntity.blueprintArtifactId}")
        } else {
            dao.getArtifactsForImprovement(improvementId).first().firstOrNull() 
                ?: throw IllegalStateException("No Blueprint Artifact found for improvement $improvementId")
        }
        
        val updated = improvementEntity.copy(status = IntelligenceLifecycleState.IMPLEMENTATION_PLANNED)
        dao.updateImprovement(updated)
        recordLifecycleEvent(improvementId, improvementEntity.status, IntelligenceLifecycleState.IMPLEMENTATION_PLANNED)

        val run = ImplementationRunEntity(
            id = "RUN-${UUID.randomUUID().toString().take(8).uppercase()}",
            improvementId = improvementId,
            artifactId = artifactEntity.id,
            proposalVersion = improvementEntity.version,
            startTime = System.currentTimeMillis(),
            status = IntelligenceActionStatus.PENDING
        )
        dao.insertImplementationRun(run)
    }

    suspend fun getImplementationPackage(improvementId: String): ImplementationPackage {
        val improvementEntity = dao.getImprovementById(improvementId) ?: throw IllegalArgumentException("Improvement not found")
        val improvement = improvementEntity.toDomainImprovement()
        val artifactEntity = if (improvementEntity.blueprintArtifactId != null) {
            dao.getArtifactById(improvementEntity.blueprintArtifactId) ?: throw IllegalStateException("Artifact not found for ID: ${improvementEntity.blueprintArtifactId}")
        } else {
            dao.getArtifactsForImprovement(improvementId).first().firstOrNull()
                ?: throw IllegalStateException("Artifact not found")
        }
        val artifact = moshi.adapter(BlueprintArtifact::class.java).fromJson(artifactEntity.dataJson)!!
        val manifest = artifact.implementationManifest ?: BlueprintImplementationPlanner.planImplementation(artifact.strategyBlueprint)
        val blueprint = artifact.strategyBlueprint
        val approvalEvent = dao.getLifecycleHistory(improvementId).first().find { it.toState == IntelligenceLifecycleState.APPROVED }

        return ImplementationPackage(
            improvementId = improvementId,
            findingId = improvement.findingId,
            blueprintArtifactId = artifactEntity.id,
            blueprintVersion = artifact.blueprintVersion,
            contractId = "CONTRACT-${improvementId.takeLast(8)}",
            title = improvement.title,
            summary = improvement.summary,
            technicalObjective = blueprint.diagnosis.problemStatement,
            approvedScope = manifest.proposedModifications.map { "${it.componentName}.${it.methodOrProperty}" },
            whyApproved = approvalEvent?.reason ?: "Optimization approved by user.",
            evidenceSummary = "Based on production samples.",
            expectedImpact = blueprint.expectedOutcome.expectedImprovement,
            risk = blueprint.expectedOutcome.riskLevel,
            filesAffected = manifest.proposedModifications.map { it.sourceFile }.distinct(),
            classesAffected = manifest.proposedModifications.map { it.className }.distinct(),
            functionsAffected = manifest.proposedModifications.map { it.methodOrProperty }.distinct(),
            dataModelChanges = blueprint.proposedModifications.joinToString(", ") { "${it.component}.${it.parameter}" },
            implementationSteps = blueprint.executionPlan.intendedActions.map { it.action },
            acceptanceCriteria = blueprint.targetState.successCriteria,
            verificationTests = blueprint.productionValidationRequirements,
            rollbackPlan = blueprint.experimentDesign.rollbackCondition,
            androidStudioPrompt = artifact.builderInstructions?.toFormattedString(),
            approvalTimestamp = approvalEvent?.timestamp ?: improvement.createdAt
        )
    }

    suspend fun startImplementation(runId: String) {
        val run = dao.getImplementationRunById(runId) ?: return
        dao.updateImplementationRun(run.copy(status = IntelligenceActionStatus.IN_PROGRESS))
        val improvement = dao.getImprovementById(run.improvementId) ?: return
        if (improvement.status != IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS) {
            transitionImprovementState(improvement.id, IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS)
        }
    }

    suspend fun completeImplementation(runId: String, notes: String? = null, changedFiles: List<String> = emptyList()) {
        val run = dao.getImplementationRunById(runId) ?: return
        val artifactEntity = dao.getArtifactById(run.artifactId) ?: throw IllegalStateException("Artifact not found")
        val artifact = moshi.adapter(BlueprintArtifact::class.java).fromJson(artifactEntity.dataJson)!!
        val approvedFiles = artifact.implementationManifest?.proposedModifications?.flatMap { it.filesToModify }
            ?.map { normalizePath(it) }?.toSet() ?: emptySet()
        val unauthorized = changedFiles.filter { normalizePath(it) !in approvedFiles }
        val deviation = unauthorized.isNotEmpty()
        
        val updatedRun = run.copy(
            status = IntelligenceActionStatus.COMPLETED,
            endTime = System.currentTimeMillis(),
            notes = notes,
            changedFilesJson = stringListAdapter.toJson(changedFiles),
            deviationDetected = deviation,
            deviationDetails = if (deviation) "Unauthorized files modified: $unauthorized" else null
        )
        dao.updateImplementationRun(updatedRun)

        val newState = if (deviation) IntelligenceLifecycleState.DEVIATION_DETECTED else IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE
        transitionImprovementState(run.improvementId, newState)
        
        if (deviation) {
            createAttentionItem(
                sourceType = "Implementation", sourceId = run.id, type = AttentionType.INTEGRITY_WARNING,
                priority = DecisionPriority.HIGH, title = "Scope Deviation Detected",
                summary = "Modified files outside approved scope.", whyItMatters = "Integrity check failed.",
                requiresAction = true, deepLink = "improvement/${run.improvementId}"
            )
        }
    }

    suspend fun failImplementation(runId: String, error: String) {
        val run = dao.getImplementationRunById(runId) ?: return
        dao.updateImplementationRun(run.copy(status = IntelligenceActionStatus.FAILED, endTime = System.currentTimeMillis(), resultSummary = error))
        transitionImprovementState(run.improvementId, IntelligenceLifecycleState.IMPLEMENTATION_FAILED, error)
        createAttentionItem(
            sourceType = "Implementation", sourceId = run.id, type = AttentionType.EXECUTION_FAILURE,
            priority = DecisionPriority.HIGH, title = "Implementation Failed", summary = error,
            whyItMatters = "Stalled optimization.", requiresAction = true, deepLink = "improvement/${run.improvementId}"
        )
    }

    suspend fun retryImplementation(improvementId: String) {
        transitionImprovementState(improvementId, IntelligenceLifecycleState.APPROVED, "Retrying.")
        planImplementation(improvementId)
    }

    suspend fun resolveDeviation(improvementId: String, approveNewScope: Boolean) {
        val newState = if (approveNewScope) IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE else IntelligenceLifecycleState.IMPLEMENTATION_PLANNED
        transitionImprovementState(improvementId, newState, if (approveNewScope) "Approved deviation." else "Rejected deviation.")
    }

    suspend fun rejectImprovement(id: String, reason: String) {
        transitionImprovementState(id, IntelligenceLifecycleState.REJECTED, reason)
    }

    suspend fun startVerification(improvementId: String, runId: String) {
        val run = dao.getImplementationRunById(runId)
        if (run?.deviationDetected == true) {
            throw IllegalStateException("Scope deviation detected. Resolve deviation before verification.")
        }
        transitionImprovementState(improvementId, IntelligenceLifecycleState.VERIFICATION_IN_PROGRESS)
    }

    suspend fun recordVerificationResult(
        improvementId: String, runId: String, buildPassed: Boolean, testsPassed: Boolean,
        regressionPassed: Boolean, dbIntegrityPassed: Boolean, scopeCompliant: Boolean,
        acceptanceCriteriaResults: Map<String, String>, technicalDetails: String? = null
    ) {
        val overallPassed = buildPassed && testsPassed && regressionPassed && dbIntegrityPassed && scopeCompliant
        val result = VerificationResultEntity(
            id = "VER-${UUID.randomUUID().toString().take(8).uppercase()}",
            improvementId = improvementId, runId = runId, artifactId = "N/A",
            buildPassed = buildPassed, testsPassed = testsPassed, regressionPassed = regressionPassed,
            dbIntegrityPassed = dbIntegrityPassed, scopeCompliant = scopeCompliant,
            acceptanceCriteriaResultsJson = stringMapAdapter.toJson(acceptanceCriteriaResults),
            technicalDetailsJson = technicalDetails,
            overallPassed = overallPassed
        )
        dao.insertVerificationResult(result)
        val newState = if (overallPassed) IntelligenceLifecycleState.MONITORING else IntelligenceLifecycleState.VERIFICATION_FAILED
        transitionImprovementState(improvementId, newState)
        if (overallPassed) {
             val run = dao.getImplementationRunById(runId) ?: return
             startMonitoring(improvementId, runId)
        }
    }

    suspend fun investigateRegression(alertId: String) {
        val alert = dao.getRegressionAlertById(alertId) ?: return
        dao.updateRegressionAlert(alert.copy(status = RegressionAlertStatus.INVESTIGATING))
    }

    suspend fun markRegressionFalsePositive(alertId: String, reason: String) {
        val alert = dao.getRegressionAlertById(alertId) ?: return
        dao.updateRegressionAlert(alert.copy(
            status = RegressionAlertStatus.FALSE_POSITIVE,
            recommendation = "False Positive: $reason"
        ))
        if (alert.improvementId != "N/A") {
            transitionImprovementState(alert.improvementId, IntelligenceLifecycleState.VALIDATED, "Regression marked as False Positive: $reason")
            val imp = dao.getImprovementById(alert.improvementId)
            if (imp != null) {
                transitionFindingState(imp.findingId, IntelligenceLifecycleState.VALIDATED, "Regression marked as False Positive: $reason")
            }
        }
    }

    suspend fun approveRollback(alertId: String) {
        val alert = dao.getRegressionAlertById(alertId) ?: return
        val improvementId = alert.improvementId
        if (improvementId == "N/A") return
        
        transitionImprovementState(improvementId, IntelligenceLifecycleState.ROLLBACK_RECOMMENDED, "Rollback approved for regression alert $alertId")
        val imp = dao.getImprovementById(improvementId)
        if (imp != null) {
            transitionFindingState(imp.findingId, IntelligenceLifecycleState.ROLLBACK_RECOMMENDED, "Rollback approved for regression alert $alertId")
        }
        
        val artifacts = dao.getArtifactsForImprovement(improvementId).first()
        val artifactId = artifacts.firstOrNull()?.id ?: "N/A"
        
        val rollbackRun = RollbackRunEntity(
            id = "ROL-${UUID.randomUUID().toString().take(8).uppercase()}",
            improvementId = improvementId,
            regressionId = alertId,
            originalRunId = alert.runId,
            artifactId = artifactId,
            startTime = System.currentTimeMillis(),
            status = IntelligenceActionStatus.PENDING
        )
        dao.insertRollbackRun(rollbackRun)
    }

    suspend fun executeRollback(runId: String) {
        val run = dao.getRollbackRunById(runId) ?: return
        val updatedRun = run.copy(
            status = IntelligenceActionStatus.COMPLETED,
            endTime = System.currentTimeMillis(),
            notes = "Rollback executed successfully."
        )
        dao.updateRollbackRun(updatedRun)
        
        transitionImprovementState(run.improvementId, IntelligenceLifecycleState.ROLLED_BACK, "Rollback run $runId executed.")
        val imp = dao.getImprovementById(run.improvementId)
        if (imp != null) {
            transitionFindingState(imp.findingId, IntelligenceLifecycleState.ROLLED_BACK, "Rollback executed for improvement ${run.improvementId}")
        }
    }

    suspend fun createCorrectiveImprovement(alertId: String, blueprint: StrategyBlueprint): SuggestedImprovement {
        val alert = dao.getRegressionAlertById(alertId) ?: throw IllegalArgumentException("Regression alert not found")
        val originalImprovementId = alert.improvementId
        
        val corrective = SuggestedImprovement(
            id = "COR-${UUID.randomUUID().toString().take(8).uppercase()}",
            findingId = "Corrective for $originalImprovementId",
            title = "Corrective Action: ${blueprint.title}",
            summary = "Remediation for regression detected in alert $alertId. Original improvement: $originalImprovementId",
            priority = "HIGH",
            expectedImpact = "Restore baseline performance",
            risk = "LOW",
            confidence = ConfidenceLevel.HIGH,
            evidenceCount = 1,
            source = "Regression Handler",
            classification = FindingClassification.ACTION_REQUIRED,
            rationale = "A corrective action is required to resolve a performance regression.",
            whatWillChange = blueprint.proposedModifications.joinToString(", ") { "${it.component}.${it.parameter}" },
            whatWillNotChange = "Base system architecture",
            proposedChanges = ProposedChanges(blueprint.proposedModifications),
            implementationPlan = ImplementationPlan(
                steps = blueprint.executionPlan.intendedActions,
                targetComponents = blueprint.executionPlan.affectedComponents
            ),
            verificationPlan = VerificationPlan(
                criteria = blueprint.targetState.successCriteria,
                testCases = blueprint.productionValidationRequirements
            ),
            rollbackPlan = RollbackPlan(
                procedure = blueprint.experimentDesign.rollbackCondition,
                criteria = blueprint.experimentDesign.rollbackCondition
            ),
            technicalDetails = blueprint,
            blueprintArtifactId = blueprint.id,
            status = IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT,
            createdAt = System.currentTimeMillis()
        )

        dao.insertImprovement(corrective.toEntityImprovement())
        recordLifecycleEvent(corrective.id, null, corrective.status, "Corrective improvement proposed for regression alert $alertId")
        return corrective
    }

    suspend fun performIntegrityAudit(id: String, scope: String): IntegrityAuditResult {
        val issues = mutableListOf<IntegrityIssue>()
        val improvement = dao.getImprovementById(id)?.toDomainImprovement()
        
        if (improvement != null) {
            val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
            if (System.currentTimeMillis() - improvement.createdAt > thirtyDaysMs) {
                issues.add(IntegrityIssue(IntegritySeverity.MEDIUM, "STALE_EVIDENCE", "The evidence supporting this optimization is over 30 days old."))
            }
            
            val allFindings = dao.getAllFindings().first().map { it.toDomainFinding() }
            val targetBlueprint = improvement.technicalDetails
            if (targetBlueprint != null) {
                val targetMetric = targetBlueprint.targetState.targetMetric
                val conflicts = allFindings.filter { finding ->
                    finding.dateDiscovered > improvement.createdAt &&
                    finding.classification == FindingClassification.NO_ACTION_REQUIRED &&
                    finding.technicalDetails.targetState.targetMetric == targetMetric
                }
                if (conflicts.isNotEmpty()) {
                    issues.add(IntegrityIssue(IntegritySeverity.HIGH, "CONFLICTING_INTELLIGENCE", "A newer finding indicates no optimization is required for $targetMetric."))
                }
            }
        }
        
        val status = when {
            issues.any { it.severity == IntegritySeverity.CRITICAL || it.severity == IntegritySeverity.HIGH } -> IntegrityStatus.FAIL
            issues.isNotEmpty() -> IntegrityStatus.WARNING
            else -> IntegrityStatus.PASS
        }
        
        val audit = IntegrityAuditResult(
            id = "AUD-${UUID.randomUUID().toString().take(8).uppercase()}",
            targetId = id,
            timestamp = System.currentTimeMillis(),
            status = status,
            scope = scope,
            issues = issues,
            recommendedAction = if (status == IntegrityStatus.FAIL) "Re-evaluate optimization proposal" else null
        )
        
        dao.insertAudit(audit.toEntityAudit())
        return audit
    }

    suspend fun getEvidenceSnapshot(improvementId: String): EvidenceSnapshot? = dao.getEvidenceSnapshot(improvementId)?.toDomainSnapshot()

    fun getImplementationRuns(improvementId: String): Flow<List<ImplementationRun>> = dao.getImplementationRunsForImprovement(improvementId).map { entities ->
        entities.map { it.toDomainRun() }
    }

    fun getVerificationResults(improvementId: String): Flow<List<VerificationResult>> = dao.getVerificationResultsForImprovement(improvementId).map { entities ->
        entities.map { it.toDomainVerification() }
    }

    fun getMonitoringSessions(improvementId: String): Flow<List<MonitoringSession>> = dao.getMonitoringSessionsForImprovement(improvementId).map { entities ->
        entities.map { it.toDomainMonitoring() }
    }

    fun getValidationResults(improvementId: String): Flow<List<ValidationResult>> = dao.getValidationResultsForImprovement(improvementId).map { entities ->
        entities.map { it.toDomainValidation() }
    }

    fun getRegressionAlerts(improvementId: String): Flow<List<RegressionAlert>> = dao.getRegressionAlertsForImprovement(improvementId).map { entities ->
        entities.map { it.toDomainRegression() }
    }

    fun getRollbackRuns(improvementId: String): Flow<List<RollbackRun>> = dao.getRollbackRunsForImprovement(improvementId).map { entities ->
        entities.map { it.toDomainRollback() }
    }

    fun getAuditHistory(targetId: String): Flow<List<IntegrityAuditResult>> = dao.getAuditHistory(targetId).map { entities ->
        entities.map { it.toDomainAudit() }
    }

    suspend fun generateReconstructionPackage(id: String): ReconstructionPackage {
        val improvementEntity = dao.getImprovementById(id) ?: throw IllegalArgumentException("Improvement not found: $id")
        val improvement = improvementEntity.toDomainImprovement()
        val finding = dao.getFindingById(improvement.findingId)?.toDomainFinding()
        
        val artifactEntity = if (improvementEntity.blueprintArtifactId != null) {
            dao.getArtifactById(improvementEntity.blueprintArtifactId)
        } else {
            dao.getArtifactsForImprovement(id).first().firstOrNull()
        }

        val artifact = if (artifactEntity != null) {
            moshi.adapter(BlueprintArtifact::class.java).fromJson(artifactEntity.dataJson)!!
        } else {
            val blueprint = improvement.technicalDetails ?: finding?.technicalDetails ?: throw IllegalStateException("No Blueprint Artifact found for improvement $id")
            BlueprintArtifact(
                blueprintId = blueprint.id,
                strategyBlueprint = blueprint,
                lifecycleState = BlueprintLifecycleState.PROPOSED
            )
        }
        
        val runs = dao.getImplementationRunsForImprovement(id).first().map { it.toDomainRun() }
        val verResults = dao.getVerificationResultsForImprovement(id).first().map { it.toDomainVerification() }
        val monSessions = dao.getMonitoringSessionsForImprovement(id).first().map { it.toDomainMonitoring() }
        val valResults = dao.getValidationResultsForImprovement(id).first().map { it.toDomainValidation() }
        val regressions = dao.getRegressionAlertsForImprovement(id).first().map { it.toDomainRegression() }
        val rollbacks = dao.getRollbackRunsForImprovement(id).first().map { it.toDomainRollback() }
        
        return ReconstructionPackage(
            improvementId = id,
            findingId = improvement.findingId,
            blueprintArtifact = artifact,
            finding = finding,
            approvalState = improvement.status.name,
            implementationRuns = runs,
            verificationResults = verResults,
            monitoringSessions = monSessions,
            validationResults = valResults,
            regressionRecords = regressions,
            rollbackRecords = rollbacks
        )
    }

    suspend fun importReconstructionPackage(pkg: ReconstructionPackage): ImportResult {
        try {
            val existing = dao.getImprovementById(pkg.improvementId)
            if (existing != null) {
                return ImportResult.Conflict(pkg.improvementId, "An improvement with ID ${pkg.improvementId} already exists.")
            }
            
            val finding = pkg.finding
            if (finding != null) {
                dao.insertFinding(finding.toEntityFinding())
            } else {
                val blueprint = pkg.blueprintArtifact.strategyBlueprint
                val dummyFinding = Finding(
                    id = pkg.findingId,
                    title = blueprint.title,
                    summary = blueprint.description,
                    classification = FindingClassification.IMPROVEMENT_OPPORTUNITY,
                    confidence = ConfidenceLevel.HIGH,
                    dateDiscovered = System.currentTimeMillis(),
                    technicalDetails = blueprint,
                    lifecycleState = IntelligenceLifecycleState.SYSTEM_ANALYSIS
                )
                dao.insertFinding(dummyFinding.toEntityFinding())
            }
            
            val artifactEntity = BlueprintArtifactEntity(
                id = "ART-${UUID.randomUUID().toString().take(8).uppercase()}",
                blueprintId = pkg.blueprintArtifact.blueprintId,
                improvementId = pkg.improvementId,
                proposalVersion = 1,
                version = pkg.blueprintArtifact.blueprintVersion,
                dataJson = moshi.adapter(BlueprintArtifact::class.java).toJson(pkg.blueprintArtifact)
            )
            dao.insertArtifact(artifactEntity)
            
            val blueprint = pkg.blueprintArtifact.strategyBlueprint
            val status = pkg.approvalState?.let { IntelligenceLifecycleState.valueOf(it) } ?: IntelligenceLifecycleState.APPROVED
            
            val improvement = SuggestedImprovement(
                id = pkg.improvementId,
                findingId = pkg.findingId,
                title = blueprint.title,
                summary = blueprint.description,
                priority = if (blueprint.expectedOutcome.riskLevel == "HIGH") "HIGH" else "MEDIUM",
                expectedImpact = blueprint.expectedOutcome.expectedImprovement,
                risk = blueprint.expectedOutcome.riskLevel,
                confidence = blueprint.diagnosis.diagnosticConfidence.toConfidenceLevel(),
                evidenceCount = blueprint.evidence.productionCount + blueprint.evidence.experimentalCount + blueprint.evidence.simulationCount,
                source = "Imported Package",
                classification = FindingClassification.IMPROVEMENT_OPPORTUNITY,
                rationale = "Reconstructed from imported package",
                whatWillChange = blueprint.proposedModifications.joinToString(", ") { "${it.component}.${it.parameter}" },
                whatWillNotChange = "Core architecture",
                proposedChanges = ProposedChanges(blueprint.proposedModifications),
                implementationPlan = ImplementationPlan(
                    steps = blueprint.executionPlan.intendedActions,
                    targetComponents = blueprint.executionPlan.affectedComponents
                ),
                verificationPlan = VerificationPlan(
                    criteria = blueprint.targetState.successCriteria,
                    testCases = blueprint.productionValidationRequirements
                ),
                rollbackPlan = RollbackPlan(
                    procedure = blueprint.experimentDesign.rollbackCondition,
                    criteria = blueprint.experimentDesign.rollbackCondition
                ),
                technicalDetails = blueprint,
                blueprintArtifactId = artifactEntity.id,
                status = status,
                createdAt = System.currentTimeMillis()
            )
            dao.insertImprovement(improvement.toEntityImprovement())
            recordLifecycleEvent(improvement.id, null, improvement.status, "Improvement imported from Reconstruction Package")
            
            pkg.implementationRuns.forEach { run ->
                dao.insertImplementationRun(ImplementationRunEntity(
                    id = run.id,
                    improvementId = run.improvementId,
                    artifactId = run.artifactId,
                    proposalVersion = run.proposalVersion,
                    startTime = run.startTime,
                    endTime = run.endTime,
                    status = run.status,
                    notes = run.notes,
                    changedFilesJson = stringListAdapter.toJson(run.changedFiles),
                    deviationDetected = run.deviationDetected,
                    deviationDetails = run.deviationDetails,
                    resultSummary = run.resultSummary
                ))
            }
            
            pkg.verificationResults.forEach { result ->
                dao.insertVerificationResult(VerificationResultEntity(
                    id = result.id,
                    improvementId = result.improvementId,
                    runId = result.runId,
                    artifactId = result.artifactId,
                    timestamp = result.timestamp,
                    buildPassed = result.buildPassed,
                    testsPassed = result.testsPassed,
                    regressionPassed = result.regressionPassed,
                    dbIntegrityPassed = result.dbIntegrityPassed,
                    scopeCompliant = result.scopeCompliant,
                    acceptanceCriteriaResultsJson = stringMapAdapter.toJson(result.acceptanceCriteriaResults),
                    technicalDetailsJson = result.technicalDetails,
                    overallPassed = result.overallPassed
                ))
            }
            
            pkg.monitoringSessions.forEach { session ->
                dao.insertMonitoringSession(MonitoringSessionEntity(
                    id = session.id,
                    improvementId = session.improvementId,
                    runId = session.runId,
                    artifactId = session.artifactId,
                    startTime = session.startTime,
                    status = session.status,
                    baselineMetricsJson = doubleMapAdapter.toJson(session.baselineMetrics),
                    currentMetricsJson = doubleMapAdapter.toJson(session.currentMetrics),
                    requiredSampleCount = session.requiredSampleCount,
                    currentSampleCount = session.currentSampleCount,
                    durationDays = session.durationDays,
                    regressionDetected = session.regressionDetected,
                    confidence = session.confidence,
                    validationOutcome = session.validationOutcome,
                    evidenceIdsJson = stringListAdapter.toJson(session.evidenceIds)
                ))
            }
            
            pkg.validationResults.forEach { result ->
                dao.insertValidationResult(VerificationResultEntity(
                    id = result.id,
                    improvementId = result.improvementId,
                    runId = "N/A",
                    artifactId = "N/A",
                    buildPassed = true,
                    testsPassed = true,
                    regressionPassed = true,
                    dbIntegrityPassed = true,
                    scopeCompliant = true,
                    acceptanceCriteriaResultsJson = "{}",
                    overallPassed = true
                ).let {
                    ValidationResultEntity(
                        id = result.id,
                        improvementId = result.improvementId,
                        sessionId = result.sessionId,
                        timestamp = result.timestamp,
                        outcome = result.outcome,
                        evidenceSummary = result.evidenceSummary,
                        baselineValue = result.baselineValue,
                        finalValue = result.finalValue,
                        change = result.change,
                        sampleCount = result.sampleCount,
                        confidence = result.confidence,
                        regressionSeverity = result.regressionSeverity
                    )
                })
            }
            
            return ImportResult.Success(pkg.improvementId, isIdempotent = false)
        } catch (e: Exception) {
            return ImportResult.Failure(e.message ?: "Failed to import Reconstruction Package")
        }
    }

    fun getMasterReportFlow(): Flow<MasterIntelligenceReport> {
        return combine(
            dao.getAllFindings(),
            dao.getAllImprovements(),
            dao.observeCheckpoint("LAST_REVIEW")
        ) { findingsEntities, improvementsEntities, checkpoint ->
            val lastReviewedAt = checkpoint?.timestamp ?: 0L
            val findings = findingsEntities.map { it.toDomainFinding() }
            val improvements = improvementsEntities.map { it.toDomainImprovement() }
            val validatedCount = improvements.count { it.status == IntelligenceLifecycleState.VALIDATED }
            val regressions = findings.count { it.lifecycleState == IntelligenceLifecycleState.REGRESSION_DETECTED }
            
            val newFindings = findings.count { it.dateDiscovered > lastReviewedAt }
            val newImprovements = improvements.count { it.createdAt > lastReviewedAt }
            
            MasterIntelligenceReport(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                dataThrough = System.currentTimeMillis(),
                executiveSummary = ExecutiveSummary(
                    systemHealth = if (regressions > 0) "NEEDS ATTENTION" else "HEALTHY",
                    plainEnglishSummary = "System is stable with $validatedCount optimizations validated.",
                    metrics = IntelligenceMetrics(0, 0, 0, 0, 0, validatedCount, regressions)
                ),
                sinceLastReview = ReviewUpdate(newFindings, newImprovements, 0, 0, 0, emptyList()),
                systemAnalysis = generateSystemAnalysis(findings, regressions),
                productIntelligence = DomainIntelligence("Product", "Healthy", emptyList(), emptyList(), emptyList(), emptyList()),
                engagement = DomainIntelligence("Engagement", "Healthy", emptyList(), emptyList(), emptyList(), emptyList()),
                personalization = PersonalizationIntelligence("Stable", 50.0, 50.0, 0.0, 1000, 0.9, "Stable"),
                retention = DomainIntelligence("Retention", "Healthy", emptyList(), emptyList(), emptyList(), emptyList()),
                monetization = DomainIntelligence("Monetization", "No Data", emptyList(), emptyList(), emptyList(), emptyList()),
                technicalHealth = TechnicalHealthIntelligence("Optimal", 0.0, 0, 400, "Healthy", 0),
                improvementPipeline = improvements,
                implementationOverview = emptyList(),
                risksAndRegressions = emptyList(),
                recentlyValidated = emptyList()
            )
        }
    }

    fun getDecisionCenterFlow(): Flow<DecisionCenterState> {
        return combine(
            dao.getAllImprovements(),
            dao.getAllFindings(),
            dao.getAllRegressionAlerts()
        ) { improvementsEntities, findingsEntities, alertsEntities ->
            val improvements = improvementsEntities.map { it.toDomainImprovement() }
            val findings = findingsEntities.map { it.toDomainFinding() }
            val alerts = alertsEntities.map { it.toDomainRegression() }

            val criticalIssues = mutableListOf<DecisionActionItem>()

            // Add findings that are regressions
            findings.filter { it.classification == FindingClassification.REGRESSION || it.lifecycleState == IntelligenceLifecycleState.REGRESSION_DETECTED }
                .forEach {
                    criticalIssues.add(DecisionActionItem(it.id, it.title, it.summary, "Detected regression requiring immediate attention", DecisionPriority.CRITICAL, it.dateDiscovered, it.id, "INVESTIGATE", ReviewStatus.UNREAD))
                }

            // Add regression alerts
            alerts.filter { it.status == RegressionAlertStatus.ACTIVE }
                .forEach {
                    criticalIssues.add(DecisionActionItem(it.id, "Regression: ${it.affectedMetric}", it.recommendation, "Automatic monitor detected metric shift", DecisionPriority.CRITICAL, it.timestamp, it.improvementId, "ROLLBACK", ReviewStatus.UNREAD))
                }

            DecisionCenterState(
                attentionItems = emptyList(),
                criticalIssues = criticalIssues,
                awaitingDecision = improvements.filter { it.status == IntelligenceLifecycleState.NEEDS_REVIEW },
                inProgress = emptyList(),
                recentlyCompleted = emptyList(),
                whatChanged = emptyList(),
                lastReviewedAt = 0L
            )
        }
    }

    fun getAllIntelligenceEvents(): Flow<List<IntelligenceEventEntity>> = dao.getAllIntelligenceEvents()


    private suspend fun startMonitoring(improvementId: String, runId: String) {
        val session = MonitoringSessionEntity(
            id = "MON-${UUID.randomUUID().toString().take(8).uppercase()}",
            improvementId = improvementId, runId = runId, artifactId = "N/A",
            startTime = System.currentTimeMillis(), status = MonitoringStatus.ACTIVE,
            baselineMetricsJson = "{}", currentMetricsJson = "{}",
            requiredSampleCount = 1000, currentSampleCount = 0, durationDays = 7,
            regressionDetected = false, confidence = 0.0, evidenceIdsJson = "[]"
        )
        dao.insertMonitoringSession(session)
    }

    suspend fun updateMonitoringProgress(sessionId: String, newSamples: Int, currentMetricValue: Double, evidenceIds: List<String>) {
        val session = dao.getMonitoringSessionById(sessionId) ?: return
        
        // Extract baseline from session metrics
        val baseline = moshi.adapter<Map<String, Double>>(Types.newParameterizedType(Map::class.java, String::class.java, java.lang.Double::class.java))
            .fromJson(session.baselineMetricsJson) ?: emptyMap()
        val baselineValue = baseline.values.firstOrNull() ?: 50.0
        
        val totalSamples = session.currentSampleCount + newSamples
        val regression = currentMetricValue < baselineValue * 0.95 // 5% regression threshold
        
        val currentMetrics = mapOf("Personalization Score" to currentMetricValue)
        val updated = session.copy(
            currentSampleCount = totalSamples, 
            regressionDetected = regression,
            currentMetricsJson = doubleMapAdapter.toJson(currentMetrics),
            confidence = (totalSamples.toDouble() / session.requiredSampleCount.toDouble()).coerceAtMost(1.0)
        )
        dao.updateMonitoringSession(updated)
        
        if (regression) {
            val alert = RegressionAlertEntity(
                id = "REG-${UUID.randomUUID().toString().take(8).uppercase()}",
                improvementId = session.improvementId,
                runId = session.runId,
                artifactId = session.artifactId,
                sessionId = sessionId,
                severity = RegressionSeverity.HIGH,
                affectedMetric = "Personalization Score",
                baselineValue = baselineValue,
                preRegressionValue = baselineValue,
                currentResult = currentMetricValue,
                change = currentMetricValue - baselineValue,
                evidenceIdsJson = stringListAdapter.toJson(evidenceIds),
                confidence = updated.confidence,
                status = RegressionAlertStatus.ACTIVE,
                recommendation = "Rollback recommended: Performance dropped to $currentMetricValue"
            )
            dao.insertRegressionAlert(alert)
            
            createAttentionItem(
                sourceType = "Regression",
                sourceId = alert.id,
                type = AttentionType.REGRESSION_DETECTED,
                priority = DecisionPriority.CRITICAL,
                title = "Regression: Personalization Score",
                summary = "Performance dropped from $baselineValue to $currentMetricValue",
                whyItMatters = "Active optimization causing production regression.",
                requiresAction = true,
                deepLink = "improvement/${session.improvementId}"
            )

            finalizeValidation(session.improvementId, sessionId, IntelligenceLifecycleState.REGRESSION_DETECTED)
        } else if (totalSamples >= session.requiredSampleCount) {
            finalizeValidation(session.improvementId, sessionId, IntelligenceLifecycleState.VALIDATED)
        }
    }

    suspend fun finalizeValidation(improvementId: String, sessionId: String, outcome: IntelligenceLifecycleState) {
        val session = dao.getMonitoringSessionById(sessionId) ?: return
        val baseline = doubleMapAdapter.fromJson(session.baselineMetricsJson) ?: emptyMap()
        val baselineValue = baseline.values.firstOrNull() ?: 50.0
        
        val current = doubleMapAdapter.fromJson(session.currentMetricsJson) ?: emptyMap()
        val finalValue = current.values.firstOrNull() ?: baselineValue

        val result = ValidationResultEntity(
            id = "VAL-${UUID.randomUUID().toString().take(8).uppercase()}",
            improvementId = improvementId, 
            sessionId = sessionId, 
            outcome = outcome,
            evidenceSummary = "Validated result with ${session.currentSampleCount} samples.", 
            baselineValue = baselineValue, 
            finalValue = finalValue,
            change = finalValue - baselineValue,
            sampleCount = session.currentSampleCount, 
            confidence = session.confidence, 
            regressionSeverity = if (outcome == IntelligenceLifecycleState.REGRESSION_DETECTED) "CRITICAL" else null
        )
        dao.insertValidationResult(result)
        
        // Persist outcome state in session for history
        dao.updateMonitoringSession(session.copy(status = MonitoringStatus.COMPLETED, validationOutcome = outcome))
        
        transitionImprovementState(improvementId, outcome)
        val imp = dao.getImprovementById(improvementId)
        if (imp != null) {
            transitionFindingState(imp.findingId, outcome)
        }
    }

    fun getAllImplementationRuns(): Flow<List<ImplementationRun>> = dao.getAllImplementationRuns().map { entities ->
        entities.map { it.toDomainRun() }
    }

    fun getAllMonitoringSessions(): Flow<List<MonitoringSession>> = dao.getAllMonitoringSessions().map { entities ->
        entities.map { it.toDomainMonitoring() }
    }

    fun getAllRegressionAlerts(): Flow<List<RegressionAlert>> = dao.getAllRegressionAlerts().map { entities ->
        entities.map { it.toDomainRegression() }
    }

    fun getAllAttentionItems(): Flow<List<AttentionItem>> = dao.getAllAttentionItems().map { entities ->
        entities.map { it.toDomainAttention() }
    }

    suspend fun markAsSeen(targetId: String) {
        val now = System.currentTimeMillis()
        dao.insertReviewMetadata(ReviewMetadataEntity(targetId, firstSeenTimestamp = now, lastSeenTimestamp = now, status = ReviewStatus.SEEN))
    }

    suspend fun markAsReviewed(targetId: String) {
        val now = System.currentTimeMillis()
        dao.insertReviewMetadata(ReviewMetadataEntity(targetId, reviewedTimestamp = now, status = ReviewStatus.REVIEWED))
    }

    suspend fun markAllAsReviewed() {
        dao.insertCheckpoint(UserCheckpointEntity("LAST_REVIEW", System.currentTimeMillis()))
    }

    suspend fun generateMasterReport(lastReviewedAt: Long): MasterIntelligenceReport {
        val findings = dao.getAllFindings().first().map { it.toDomainFinding() }
        val improvements = dao.getAllImprovements().first().map { it.toDomainImprovement() }
        
        val validatedImprovements = improvements.filter { it.status == IntelligenceLifecycleState.VALIDATED }
        val regressionsCount = findings.count { it.lifecycleState == IntelligenceLifecycleState.REGRESSION_DETECTED }

        // Review Update Logic
        val newFindings = findings.count { it.dateDiscovered > lastReviewedAt }
        val newImprovements = improvements.count { it.createdAt > lastReviewedAt }
        val newValidations = validatedImprovements.count { it.createdAt > lastReviewedAt }
        val newApprovals = improvements.count { it.status == IntelligenceLifecycleState.APPROVED && it.createdAt > lastReviewedAt }
        val newRegressionsCount = findings.count { it.lifecycleState == IntelligenceLifecycleState.REGRESSION_DETECTED && it.dateDiscovered > lastReviewedAt }

        val reviewChanges = mutableListOf<IntelligenceChange>()
        findings.filter { it.dateDiscovered > lastReviewedAt }.forEach {
            reviewChanges.add(IntelligenceChange(
                title = it.title,
                description = it.summary,
                timestamp = it.dateDiscovered,
                type = "Finding",
                state = it.lifecycleState,
                targetId = it.id
            ))
        }
        improvements.filter { it.createdAt > lastReviewedAt }.forEach {
            reviewChanges.add(IntelligenceChange(
                title = it.title,
                description = it.summary,
                timestamp = it.createdAt,
                type = "Improvement",
                state = it.status,
                targetId = it.id
            ))
        }

        // For recently validated, we aggregate the results
        val recentlyValidatedResults = mutableListOf<ValidationResult>()
        validatedImprovements.forEach { imp ->
            val results = dao.getValidationResultsForImprovement(imp.id).first().map { it.toDomainValidation() }
            recentlyValidatedResults.addAll(results)
        }

        return MasterIntelligenceReport(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            dataThrough = System.currentTimeMillis(),
            executiveSummary = ExecutiveSummary(
                systemHealth = if (regressionsCount > 0) "NEEDS ATTENTION" else "HEALTHY",
                plainEnglishSummary = "System is stable with ${validatedImprovements.size} optimizations validated.",
                metrics = IntelligenceMetrics(0, 0, 0, 0, 0, validatedImprovements.size, regressionsCount)
            ),
            sinceLastReview = ReviewUpdate(
                newFindings = newFindings,
                newImprovements = newImprovements,
                newApprovals = newApprovals,
                newValidations = newValidations,
                newRegressions = newRegressionsCount,
                items = reviewChanges.sortedByDescending { it.timestamp }
            ),
            systemAnalysis = generateSystemAnalysis(findings, regressionsCount),
            productIntelligence = DomainIntelligence("Product", "Healthy", emptyList(), emptyList(), emptyList(), emptyList()),
            engagement = DomainIntelligence("Engagement", "Healthy", emptyList(), emptyList(), emptyList(), emptyList()),
            personalization = PersonalizationIntelligence("Stable", 50.0, 50.0, 0.0, 1000, 0.9, "Stable"),
            retention = DomainIntelligence("Retention", "Healthy", emptyList(), emptyList(), emptyList(), emptyList()),
            monetization = DomainIntelligence("Monetization", "No Data", emptyList(), emptyList(), emptyList(), emptyList()),
            technicalHealth = TechnicalHealthIntelligence("Optimal", 0.0, 0, 400, "Healthy", 0),
            improvementPipeline = improvements,
            implementationOverview = emptyList(),
            risksAndRegressions = emptyList(),
            recentlyValidated = recentlyValidatedResults.sortedByDescending { it.timestamp }
        )
    }

    private fun generateSystemAnalysis(
        findings: List<Finding>,
        regressions: Int
    ): SystemAnalysisSummary {
        val hasRegression = regressions > 0 || findings.any { it.classification == FindingClassification.REGRESSION }
        val actionRequired = hasRegression || findings.any { it.classification == FindingClassification.ACTION_REQUIRED || it.classification == FindingClassification.IMPROVEMENT_OPPORTUNITY }

        val actionStatus = when {
            hasRegression -> ActionStatus.ACTION_REQUIRED
            findings.any { it.classification == FindingClassification.ACTION_REQUIRED || it.classification == FindingClassification.IMPROVEMENT_OPPORTUNITY } -> ActionStatus.REVIEW_RECOMMENDED
            findings.any { it.lifecycleState == IntelligenceLifecycleState.NEEDS_REVIEW } -> ActionStatus.REVIEW_RECOMMENDED
            findings.any { it.confidence == ConfidenceLevel.LOW } -> ActionStatus.MORE_EVIDENCE_NEEDED
            else -> ActionStatus.NO_ACTION_REQUIRED
        }

        val whatsWorking = findings.filter { it.classification == FindingClassification.IMPROVEMENT_OPPORTUNITY || it.classification == FindingClassification.ACTION_REQUIRED }
            .map { "Positive trend: ${it.title} (measured increase in performance)" }

        val whatsNotWorking = mutableListOf<String>()
        if (hasRegression) {
            whatsNotWorking.add("Performance regression in recent optimizations")
            findings.filter { it.classification == FindingClassification.REGRESSION }
                .forEach { whatsNotWorking.add("Detected regression: ${it.title}") }
        }

        return SystemAnalysisSummary(
            summary = if (hasRegression) "Regressions detected" else if (actionRequired) "Action recommended" else "Stable",
            whatsWorking = whatsWorking,
            whatsNotWorking = whatsNotWorking,
            actionRequired = actionRequired,
            recommendation = if (hasRegression) "Rollback required" else if (actionRequired) "Review suggested improvements" else "None",
            latestFinding = findings.firstOrNull(),
            evidenceSummary = null,
            confidenceExplanation = if (findings.any { it.confidence == ConfidenceLevel.LOW }) "Some assessments have limited sample size" else "High",
            actionStatus = actionStatus
        )
    }

    suspend fun saveReportSnapshot(report: MasterIntelligenceReport) {
        val snapshotReport = report.copy(isSnapshot = true)
        dao.insertSavedReport(SavedIntelligenceReportEntity(
            report.id, report.timestamp, report.dataThrough, 30, 
            moshi.adapter(MasterIntelligenceReport::class.java).toJson(snapshotReport)
        ))
    }

    fun getAllSavedReports(): Flow<List<MasterIntelligenceReport>> = dao.getAllSavedReports().map { entities ->
        entities.map { moshi.adapter(MasterIntelligenceReport::class.java).fromJson(it.reportJson)!! }
    }

    fun observeCheckpoint(id: String): Flow<Long> = dao.observeCheckpoint(id).map { it?.timestamp ?: 0L }

    suspend fun getDecisionCenterState(): DecisionCenterState {
        val findings = dao.getAllFindings().first().map { it.toDomainFinding() }
        val improvements = dao.getAllImprovements().first().map { it.toDomainImprovement() }
        val alerts = dao.getAllRegressionAlerts().first().map { it.toDomainRegression() }

        val criticalIssues = mutableListOf<DecisionActionItem>()

        findings.filter { it.classification == FindingClassification.REGRESSION || it.lifecycleState == IntelligenceLifecycleState.REGRESSION_DETECTED }
            .forEach {
                criticalIssues.add(DecisionActionItem(it.id, it.title, it.summary, "Detected regression requiring immediate attention", DecisionPriority.CRITICAL, it.dateDiscovered, it.id, "INVESTIGATE", ReviewStatus.UNREAD))
            }

        alerts.filter { it.status == RegressionAlertStatus.ACTIVE }
            .forEach {
                criticalIssues.add(DecisionActionItem(it.id, "Regression: ${it.affectedMetric}", it.recommendation, "Automatic monitor detected metric shift", DecisionPriority.CRITICAL, it.timestamp, it.improvementId, "ROLLBACK", ReviewStatus.UNREAD))
            }

        return DecisionCenterState(
            attentionItems = emptyList(),
            criticalIssues = criticalIssues,
            awaitingDecision = improvements.filter { it.status == IntelligenceLifecycleState.NEEDS_REVIEW },
            inProgress = emptyList(),
            recentlyCompleted = emptyList(),
            whatChanged = emptyList(),
            lastReviewedAt = 0L
        )
    }

    private fun canTransition(from: IntelligenceLifecycleState, to: IntelligenceLifecycleState): Boolean {
        return when (from) {
            IntelligenceLifecycleState.FINDING_DETECTED -> to in listOf(
                IntelligenceLifecycleState.SYSTEM_ANALYSIS,
                IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT,
                IntelligenceLifecycleState.REGRESSION_DETECTED
            )
            IntelligenceLifecycleState.SYSTEM_ANALYSIS -> to == IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT
            IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT -> to in listOf(
                IntelligenceLifecycleState.NEEDS_REVIEW,
                IntelligenceLifecycleState.APPROVED,
                IntelligenceLifecycleState.REJECTED
            )
            IntelligenceLifecycleState.NEEDS_REVIEW -> to in listOf(
                IntelligenceLifecycleState.APPROVED,
                IntelligenceLifecycleState.REJECTED,
                IntelligenceLifecycleState.NEEDS_MORE_INFORMATION
            )
            IntelligenceLifecycleState.NEEDS_MORE_INFORMATION -> to == IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT
            IntelligenceLifecycleState.APPROVED -> to in listOf(
                IntelligenceLifecycleState.IMPLEMENTATION_PLANNED,
                IntelligenceLifecycleState.VALIDATED,
                IntelligenceLifecycleState.REGRESSION_DETECTED,
                IntelligenceLifecycleState.NEEDS_REVIEW
            )
            IntelligenceLifecycleState.IMPLEMENTATION_PLANNED -> to in listOf(
                IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS,
                IntelligenceLifecycleState.IMPLEMENTATION_FAILED,
                IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE
            )
            IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS -> to in listOf(
                IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE,
                IntelligenceLifecycleState.IMPLEMENTATION_FAILED,
                IntelligenceLifecycleState.DEVIATION_DETECTED
            )
            IntelligenceLifecycleState.DEVIATION_DETECTED -> to in listOf(
                IntelligenceLifecycleState.APPROVED,
                IntelligenceLifecycleState.IMPLEMENTATION_PLANNED,
                IntelligenceLifecycleState.REJECTED,
                IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE
            )
            IntelligenceLifecycleState.IMPLEMENTATION_FAILED -> to in listOf(
                IntelligenceLifecycleState.IMPLEMENTATION_PLANNED,
                IntelligenceLifecycleState.APPROVED
            )
            IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE -> to in listOf(
                IntelligenceLifecycleState.VERIFICATION_IN_PROGRESS,
                IntelligenceLifecycleState.MONITORING,
                IntelligenceLifecycleState.VERIFICATION_FAILED
            )
            IntelligenceLifecycleState.VERIFICATION_IN_PROGRESS -> to in listOf(
                IntelligenceLifecycleState.VERIFICATION_PASSED,
                IntelligenceLifecycleState.VERIFICATION_FAILED,
                IntelligenceLifecycleState.MONITORING
            )
            IntelligenceLifecycleState.VERIFICATION_PASSED -> to == IntelligenceLifecycleState.MONITORING
            IntelligenceLifecycleState.VERIFICATION_FAILED -> to == IntelligenceLifecycleState.IMPLEMENTATION_PLANNED
            IntelligenceLifecycleState.MONITORING -> to in listOf(
                IntelligenceLifecycleState.VALIDATED,
                IntelligenceLifecycleState.INCONCLUSIVE,
                IntelligenceLifecycleState.REGRESSION_DETECTED
            )
            IntelligenceLifecycleState.REGRESSION_DETECTED -> to == IntelligenceLifecycleState.ROLLBACK_RECOMMENDED
            IntelligenceLifecycleState.ROLLBACK_RECOMMENDED -> to == IntelligenceLifecycleState.ROLLED_BACK
            else -> false
        }
    }

    private fun ClosedLoopReport.toClassification(): FindingClassification {
        return when (this.outcomeClassification) {
            OutcomeClassification.INSUFFICIENT_DATA -> FindingClassification.INSUFFICIENT_EVIDENCE
            OutcomeClassification.BASELINE_ESTABLISHED -> FindingClassification.INFORMATIONAL
            OutcomeClassification.NO_MEASURABLE_CHANGE -> FindingClassification.NO_ACTION_REQUIRED
            OutcomeClassification.IMPROVEMENT_DETECTED -> FindingClassification.IMPROVEMENT_OPPORTUNITY
            OutcomeClassification.SIGNIFICANT_IMPROVEMENT -> FindingClassification.IMPROVEMENT_OPPORTUNITY
            OutcomeClassification.REGRESSION_DETECTED -> FindingClassification.REGRESSION
            OutcomeClassification.EXPERIMENT_INCONCLUSIVE -> FindingClassification.INSUFFICIENT_EVIDENCE
        }
    }
    private fun ClosedLoopReport.toConfidenceLevel(): ConfidenceLevel {
        return when {
            overallConfidence >= 0.8 -> ConfidenceLevel.HIGH
            overallConfidence >= 0.4 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    }

    private fun Double.toConfidenceLevel(): ConfidenceLevel {
        return when {
            this >= 0.8 -> ConfidenceLevel.HIGH
            this >= 0.4 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    }

    private suspend fun createAttentionItem(sourceType: String, sourceId: String, type: AttentionType, priority: DecisionPriority, title: String, summary: String, whyItMatters: String, requiresAction: Boolean, deepLink: String) {
        dao.insertAttentionItem(AttentionItemEntity(UUID.randomUUID().toString(), sourceType, sourceId, type, priority, title, summary, whyItMatters, AttentionStatus.NEW, System.currentTimeMillis(), null, requiresAction, deepLink, "$sourceType:$sourceId:$type"))
    }
}
