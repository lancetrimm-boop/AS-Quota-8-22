package com.example

import com.example.data.ClosedLoopEngine
import com.example.data.EvidenceRecord
import com.example.data.EvidenceTier
import com.example.data.ModificationType
import com.example.data.ProposedModification
import com.example.data.StrategyBlueprintGenerator
import com.example.data.blueprint.BlueprintArtifact
import com.example.data.blueprint.BlueprintLifecycleState
import com.example.data.blueprint.BlueprintLoader
import com.example.data.blueprint.BlueprintSerializer
import com.example.data.blueprint.BlueprintValidator
import org.junit.Assert.*
import org.junit.Test

class BlueprintArtifactTest {

    @Test
    fun test01_SerializationAndDeserializationRoundTrip() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 65.0,
            targetScore = 70.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 100, score = 65.0, quality = 0.85)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint(
            title = "Serialization Test Blueprint",
            description = "Test blueprint description",
            report = report
        )

        val artifact = BlueprintArtifact(
            schemaVersion = "1.0.0",
            blueprintVersion = "1.0.0",
            blueprintId = "test-bp-123",
            parentBlueprintId = null,
            lifecycleState = BlueprintLifecycleState.LOADED,
            strategyBlueprint = blueprint
        )

        val json = BlueprintSerializer.toJson(artifact)
        assertNotNull(json)
        assertTrue(json.contains("\"schema_version\": \"1.0.0\""))
        assertTrue(json.contains("\"blueprint_id\": \"test-bp-123\""))

        val loadResult = BlueprintLoader.fromJson(json)
        assertTrue(loadResult.isSuccess)
        assertNotNull(loadResult.artifact)

        val loaded = loadResult.artifact!!
        assertEquals("1.0.0", loaded.schemaVersion)
        assertEquals("test-bp-123", loaded.blueprintId)
        assertEquals("Serialization Test Blueprint", loaded.strategyBlueprint.title)
    }

    @Test
    fun test02_Validation_DetectsMissingRequiredFields() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 60.0,
            targetScore = 70.0,
            evidenceList = emptyList()
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint(
            title = "Test Title",
            description = "Test",
            report = report
        ).run {
            copy(strategySelection = strategySelection.copy(selectedStrategy = ""))
        }

        val artifact = BlueprintArtifact(
            schemaVersion = "1.0.0",
            blueprintVersion = "1.0.0",
            blueprintId = "", // Blank blueprint_id!
            strategyBlueprint = blueprint
        )

        val valResult = BlueprintValidator.validate(artifact)
        assertFalse(valResult.isValid)
        assertTrue(valResult.errors.any { it.field == "blueprint_id" })
        assertTrue(valResult.errors.any { it.field == "title" })
    }

    @Test
    fun test03_Validation_DetectsMalformedVersions() {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", report)

        val malformedArtifact = BlueprintArtifact(
            schemaVersion = "v1.0-alpha", // Malformed semver
            blueprintVersion = "invalid_version", // Malformed semver
            blueprintId = "id-123",
            strategyBlueprint = blueprint
        )

        val valResult = BlueprintValidator.validate(malformedArtifact)
        assertFalse(valResult.isValid)
        assertTrue(valResult.errors.any { it.field == "schema_version" })
        assertTrue(valResult.errors.any { it.field == "blueprint_version" })
    }

    @Test
    fun test04_Validation_DetectsInconsistentEvidence_ZeroProductionWithRecommendedChange() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 60.0,
            targetScore = 70.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.SIMULATION, sampleCount = 100, score = 60.0, quality = 0.8)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", report)

        // Inject a RECOMMENDED_CHANGE modification despite 0 production samples
        val badBlueprint = blueprint.copy(
            proposedModifications = listOf(
                ProposedModification(
                    component = "AISkipEngine",
                    parameter = "skipThreshold",
                    currentValue = "0.2",
                    proposedValue = "0.3",
                    delta = "+0.1",
                    reason = "Unjustified change",
                    supportingEvidence = "None",
                    expectedEffect = "Unknown",
                    confidence = 0.9,
                    modificationType = ModificationType.RECOMMENDED_CHANGE
                )
            )
        )

        val artifact = BlueprintArtifact(
            schemaVersion = "1.0.0",
            blueprintVersion = "1.0.0",
            blueprintId = "id-456",
            strategyBlueprint = badBlueprint
        )

        val valResult = BlueprintValidator.validate(artifact)
        assertFalse(valResult.isValid)
        assertTrue(valResult.errors.any { it.field == "proposedModifications" && it.message.contains("RECOMMENDED_CHANGE") })
    }

    @Test
    fun test05_Validation_DetectsInvalidTargetScores() {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", report)

        // Set target value to invalid 150.0 (> 100.0)
        val invalidScoreBlueprint = blueprint.copy(
            targetState = blueprint.targetState.copy(targetValue = 150.0)
        )

        val artifact = BlueprintArtifact(
            schemaVersion = "1.0.0",
            blueprintVersion = "1.0.0",
            blueprintId = "id-789",
            strategyBlueprint = invalidScoreBlueprint
        )

        val valResult = BlueprintValidator.validate(artifact)
        assertFalse(valResult.isValid)
        assertTrue(valResult.errors.any { it.field == "targetState.targetValue" })
    }

    @Test
    fun test06_LifecycleStateTransitions_EnforcesValidation() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 65.0,
            targetScore = 70.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 100, score = 65.0, quality = 0.85)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Valid Title", "Desc", report)

        val validArtifact = BlueprintArtifact(
            schemaVersion = "1.0.0",
            blueprintVersion = "1.0.0",
            blueprintId = "valid-bp-1",
            lifecycleState = BlueprintLifecycleState.LOADED,
            strategyBlueprint = blueprint
        )

        val valResult = BlueprintValidator.validate(validArtifact)
        assertTrue(valResult.isValid)
        assertEquals(BlueprintLifecycleState.VALIDATED, valResult.validatedArtifact?.lifecycleState)
    }

    @Test
    fun test07_BuilderInstructionGenerator_GeneratesCorrectPromptsInOrder() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 65.0,
            targetScore = 70.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 100, score = 65.0, quality = 0.85)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint(
            title = "AURA Recommendation Weight Blueprint",
            description = "Optimize recommendation weights based on trial data",
            report = report
        ).run {
            copy(
                proposedModifications = listOf(
                    ProposedModification(
                        component = "AISkipEngine",
                        parameter = "skipThreshold",
                        currentValue = "0.25",
                        proposedValue = "0.30",
                        delta = "+0.05",
                        reason = "Optimize skip detection sensitivity based on trial data",
                        supportingEvidence = "Experimental trial evidence",
                        expectedEffect = "+15% signal capture",
                        confidence = 0.85,
                        modificationType = ModificationType.RECOMMENDED_CHANGE
                    )
                )
            )
        }

        val instructions = com.example.data.blueprint.BuilderInstructionGenerator.generate(blueprint)
        assertNotNull(instructions)
        assertTrue(instructions.prompts.isNotEmpty())

        val titles = instructions.prompts.map { it.title }
        assertEquals("FORENSIC INSPECTION", titles[0])
        assertTrue(titles.contains("TESTING"))
        assertTrue(titles.contains("REGRESSION AUDIT"))
        assertEquals("FINAL VERIFICATION", titles.last())

        // Verify sequential prompt step numbering 1..N
        instructions.prompts.forEachIndexed { index, prompt ->
            assertEquals(index + 1, prompt.stepNumber)
        }

        // Verify modification detail explicit requirements
        val promptTextCombined = instructions.prompts.joinToString("\n") { it.promptText }
        assertTrue(promptTextCombined.contains("AISkipEngine"))
        assertTrue(promptTextCombined.contains("skipThreshold"))
        assertTrue(promptTextCombined.contains("Current Value: 0.25"))
        assertTrue(promptTextCombined.contains("Proposed Value: 0.30"))
        assertTrue(promptTextCombined.contains("Optimize skip detection sensitivity"))

        // Verify standard safeguard rules exist across prompts
        assertTrue(promptTextCombined.contains("Inspect existing"))
        assertTrue(promptTextCombined.contains("Preserve working functionality"))
        assertTrue(promptTextCombined.contains("Avoid destructive"))
        assertTrue(promptTextCombined.contains("Avoid deleting existing data"))
        assertTrue(promptTextCombined.contains("Acceptance Criteria"))
        assertTrue(promptTextCombined.contains("compile_applet"))
        assertTrue(promptTextCombined.contains("checkpoint"))
    }
}
