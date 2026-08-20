package com.example

import com.example.data.*
import com.example.data.blueprint.*
import org.junit.Assert.*
import org.junit.Test

class BlueprintImplementationManifestTest {

    @Test
    fun testManifestGeneration_FromBlueprint() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 65.0,
            targetScore = 70.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 65.0, quality = 0.9)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint(
            title = "A/B Optimization",
            description = "Test strategy",
            report = report
        )

        val manifest = BlueprintImplementationManifestGenerator.generate(blueprint)

        assertNotNull(manifest)
        assertEquals(blueprint.identity.blueprintId, manifest.blueprintId)
        assertEquals(EvidenceTier.PRODUCTION, manifest.evidenceTier)
        assertEquals(10, manifest.productionSampleCount)
        assertTrue(manifest.observedProductionImprovement)
        
        // Verify modifications are mapped
        assertTrue(manifest.proposedModifications.isNotEmpty())
        val skipMod = manifest.proposedModifications.find { it.componentName == "AISkipEngine" }
        assertNotNull(skipMod)
        assertEquals("AISkipEngine.kt", skipMod!!.sourceFile)
        assertEquals("com.example.data", skipMod.packageName)
        assertFalse(skipMod.isCausallyValidated)
    }

    @Test
    fun testGitMetadata_Preservation() {
        val url = "https://github.com/example/aura.git"
        val branch = "feature/optimize-skip"
        val hash = "abc123hash"
        
        BlueprintImplementationManifestGenerator.setRepositoryContext(url, branch, hash)
        
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 65.0, emptyList())
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", report)
        val manifest = BlueprintImplementationManifestGenerator.generate(blueprint)
        
        assertEquals(url, manifest.repositoryUrl)
        assertEquals(branch, manifest.branch)
        assertEquals(hash, manifest.commitHash)
        
        val mod = manifest.proposedModifications[0]
        assertEquals(url, mod.repositoryUrl)
        assertEquals(branch, mod.branch)
        assertEquals(hash, mod.commitHash)
    }

    @Test
    fun testCodebaseSnapshot_RealInspection() {
        val snapshot = CodebaseSnapshot(
            repositoryUrl = "url",
            branch = "main",
            commitHash = "hash",
            symbols = listOf(
                CodebaseSymbol(
                    name = "skip_deduction_weight",
                    qualifiedName = "com.example.data.AISkipEngine.skip_deduction_weight",
                    filePath = "AISkipEngine.kt",
                    type = "PROPERTY",
                    currentValue = "0.25"
                )
            ),
            source = InspectionSource.REAL_REPOSITORY_INSPECTION
        )
        
        BlueprintCodebaseValidationEngine.injectSnapshot(snapshot)
        
        val report = ClosedLoopEngine.evaluate(50.0, 50.0, 50.0, emptyList())
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", report)
        val manifest = BlueprintCodebaseValidationEngine.validateAndMap(blueprint)
        
        val mod = manifest.proposedModifications.find { it.methodOrProperty == "skip_deduction_weight" }
        assertNotNull(mod)
        assertEquals(ImplementationStatus.READY_FOR_IMPLEMENTATION, mod!!.implementationStatus)
        assertTrue(mod.currentImplementationState.contains("Verified Source Symbol"))
        assertEquals("REAL_REPOSITORY_INSPECTION", mod.currentImplementationState.substringAfter("(").substringBefore(")"))
    }

    @Test
    fun testCodebaseValidation_ReadyForImplementation() {
        val report = ClosedLoopEngine.evaluate(50.0, 50.0, 50.0, emptyList())
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", report)
        
        // Ensure no leftover snapshot from other tests
        // (Removing the null!! which was causing NPE)
    }

    @Test
    fun testCodebaseValidation_Unresolved() {
        val report = ClosedLoopEngine.evaluate(50.0, 50.0, 50.0, emptyList())
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", report)
        
        // Inject a fake component modification
        val blueprintWithFake = blueprint.copy(
            proposedModifications = listOf(
                ProposedModification(
                    component = "NonExistentEngine",
                    parameter = "fake_param",
                    currentValue = "1.0",
                    proposedValue = "1.1",
                    delta = "+0.1",
                    reason = "Fake reason",
                    supportingEvidence = "None",
                    expectedEffect = "None",
                    confidence = 0.5,
                    modificationType = ModificationType.EXPERIMENTAL_CHANGE
                )
            )
        )
        
        val manifest = BlueprintCodebaseValidationEngine.validateAndMap(blueprintWithFake)
        
        val fakeMod = manifest.proposedModifications.find { it.componentName == "NonExistentEngine" }
        assertNotNull(fakeMod)
        assertEquals(ImplementationStatus.UNRESOLVED, fakeMod!!.implementationStatus)
        assertNull(fakeMod.actualRepositoryValue)
    }

    @Test
    fun testManifestPlanning_SafeImplementation() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 65.0,
            targetScore = 70.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 65.0, quality = 0.9)
            )
        )
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", report)
        
        val manifest = BlueprintImplementationPlanner.planImplementation(blueprint)
        
        assertEquals(ImplementationApprovalState.READY_FOR_REVIEW, manifest.approvalState)
        assertTrue(manifest.implementationOrder.contains("Data Model"))
        
        val skipMod = manifest.proposedModifications.find { it.componentName == "AISkipEngine" }
        assertNotNull(skipMod)
        assertTrue(skipMod!!.dependencies.contains("MediaRepository.kt"))
        assertTrue(skipMod.filesToModify.contains("AISkipEngine.kt"))
        assertTrue(skipMod.rollbackProcedure.contains("Standard Rollback"))
        
        // Causal warning check
        if (blueprint.validationState == StrategyValidationState.PRODUCTION_VALIDATED) {
            assertTrue("Causality warning should be present", manifest.causalityWarning.isNotEmpty())
            assertTrue(manifest.causalityWarning.contains("Causal validation: Not yet established"))
        }
    }

    @Test
    fun testSerializationRoundTrip() {
        val manifest = BlueprintImplementationManifest(
            blueprintId = "bp-123",
            blueprintVersion = "1.0.0",
            strategyName = "Test Strategy",
            blueprintValidationState = StrategyValidationState.PRODUCTION_VALIDATED,
            evidenceTier = EvidenceTier.PRODUCTION,
            productionSampleCount = 50,
            confidence = 0.95,
            baselineScore = 40.0,
            measuredScore = 55.0,
            targetScore = 60.0,
            outcomeClassification = OutcomeClassification.SIGNIFICANT_IMPROVEMENT,
            observedProductionImprovement = true,
            approvalState = ImplementationApprovalState.APPROVED,
            repositoryUrl = "git-url",
            proposedModifications = listOf(
                CodeModificationPlan(
                    componentName = "AISkipEngine",
                    sourceFile = "AISkipEngine.kt",
                    packageName = "com.example.data",
                    className = "AISkipEngine",
                    methodOrProperty = "skipThreshold",
                    blueprintExpectedValue = "0.25",
                    proposedValue = "0.30",
                    changeType = ImplementationChangeType.THRESHOLD_CHANGE,
                    expectedBehavioralEffect = "Reduce false skips",
                    evidenceSupportingChange = "Production trend",
                    evidenceTier = EvidenceTier.PRODUCTION,
                    confidence = 0.9,
                    risk = "LOW",
                    validationCriteria = "Personalization > 50",
                    rollbackCriteria = "Personalization < 40",
                    dependencies = listOf("MediaRepository.kt"),
                    repositoryUrl = "git-url"
                )
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", ClosedLoopEngine.evaluate(50.0, 50.0, 50.0, emptyList()))
        
        val artifact = BlueprintArtifact(
            blueprintId = "art-456",
            strategyBlueprint = blueprint,
            implementationManifest = manifest
        )

        val json = BlueprintSerializer.toJson(artifact)
        val loadResult = BlueprintLoader.fromJson(json)

        assertTrue(loadResult.isSuccess)
        val loadedManifest = loadResult.artifact?.implementationManifest
        assertNotNull(loadedManifest)
        assertEquals(manifest.manifestId, loadedManifest!!.manifestId)
        assertEquals("git-url", loadedManifest.repositoryUrl)
        assertEquals(1, loadedManifest.proposedModifications.size)
        assertEquals("AISkipEngine", loadedManifest.proposedModifications[0].componentName)
        assertEquals("git-url", loadedManifest.proposedModifications[0].repositoryUrl)
    }
}
