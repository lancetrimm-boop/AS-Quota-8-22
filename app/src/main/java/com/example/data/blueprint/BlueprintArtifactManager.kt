package com.example.data.blueprint

import android.content.Context
import com.example.data.StrategyBlueprint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Manager for active Blueprint Artifacts within the Aura application.
 * Controls loading, validation, editing, file export, and explicit lifecycle state transitions.
 */
class BlueprintArtifactManager(private val context: Context) {

    private val _currentArtifact = MutableStateFlow<BlueprintArtifact?>(null)
    val currentArtifact: StateFlow<BlueprintArtifact?> = _currentArtifact.asStateFlow()

    private val _validationResult = MutableStateFlow<BlueprintValidationResult?>(null)
    val validationResult: StateFlow<BlueprintValidationResult?> = _validationResult.asStateFlow()

    private val _rawJsonText = MutableStateFlow<String>("")
    val rawJsonText: StateFlow<String> = _rawJsonText.asStateFlow()

    private val _statusMessage = MutableStateFlow<String>("No blueprint loaded.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    init {
        // Pre-load default repository asset blueprint
        loadFromAsset("blueprints/sample_blueprint_v1.json")
        
        // Load agent contracts from registry
        loadAgentRegistry()
    }

    private fun loadAgentRegistry() {
        try {
            val registryJson = context.assets.open("Aura-Intelligence/agents/registry/agent-registry.json").bufferedReader().use { it.readText() }
            // In a real app, I'd parse the registry and then load each contract path.
            // For now, I'll register the known agents with baseline contracts.
            AgentAuthorityEnforcer.registerContract(AgentContract(
                agentId = "AISkipEngine",
                writePermissions = listOf("AISkipEngine"),
                prohibitedActions = listOf("AuraDatabase", "MainActivity")
            ))
            AgentAuthorityEnforcer.registerContract(AgentContract(
                agentId = "PairwiseSystem",
                writePermissions = listOf("PairwiseSystem", "RecommendationEngine"),
                prohibitedActions = listOf("AuraDatabase")
            ))
        } catch (e: Exception) {
            // Fallback or ignore if directory not accessible
        }
    }

    /**
     * Wrap a StrategyBlueprint into a new first-class BlueprintArtifact.
     */
    fun setBlueprint(blueprint: StrategyBlueprint, parentId: String? = null) {
        val manifest = BlueprintImplementationPlanner.planImplementation(blueprint)
        
        // Generate precise instructions using the implementation manifest
        val preciseInstructions = BuilderInstructionGenerator.generate(blueprint, manifest)

        val artifact = BlueprintArtifact(
            schemaVersion = BlueprintArtifact.CURRENT_SCHEMA_VERSION,
            blueprintVersion = blueprint.identity.version,
            blueprintId = blueprint.identity.blueprintId,
            parentBlueprintId = parentId,
            lifecycleState = BlueprintLifecycleState.LOADED,
            strategyBlueprint = blueprint.copy(builderInstructions = preciseInstructions),
            builderInstructions = preciseInstructions,
            implementationManifest = manifest
        )
        val json = BlueprintSerializer.toJson(artifact)
        _rawJsonText.value = json
        _currentArtifact.value = artifact

        val valResult = BlueprintValidator.validate(artifact)
        _validationResult.value = valResult

        _statusMessage.value = if (valResult.isValid) {
            "Blueprint created and validated (State: ${valResult.validatedArtifact?.lifecycleState?.name ?: "VALIDATED"})"
        } else {
            "Blueprint created with validation errors: ${valResult.errorSummary}"
        }
    }

    /**
     * Load a blueprint artifact from repository assets.
     */
    fun loadFromAsset(assetPath: String): Boolean {
        val res = BlueprintLoader.fromAsset(context, assetPath)
        return handleLoadResult(res, "Asset: $assetPath")
    }

    /**
     * Load a blueprint artifact from a local File.
     */
    fun loadFromFile(file: File): Boolean {
        val res = BlueprintLoader.fromFile(file)
        return handleLoadResult(res, "File: ${file.name}")
    }

    /**
     * Load and validate a blueprint artifact (or full reconstruction package) from a JSON string.
     */
    fun loadFromJson(jsonString: String): Boolean {
        val res = BlueprintLoader.fromJsonUnified(jsonString)
        return handleLoadResult(res, "Direct JSON")
    }

    /**
     * Re-validate current raw JSON text after external or in-app text edits.
     */
    fun updateAndValidateJson(newJsonText: String): BlueprintValidationResult {
        _rawJsonText.value = newJsonText
        val res = BlueprintLoader.fromJson(newJsonText)
        handleLoadResult(res, "Edited JSON")
        return res.validationResult ?: BlueprintValidationResult(isValid = false, errors = listOf(BlueprintValidationError("json", res.errorMessage ?: "Parse failure")))
    }

    /**
     * Exports the current valid artifact into internal files directory / repository sync location.
     */
    fun exportCurrentArtifact(): File? {
        val artifact = _currentArtifact.value ?: return null
        val file = BlueprintSerializer.exportToFile(context, artifact)
        _statusMessage.value = "Exported blueprint to file: ${file.name}"
        return file
    }

    /**
     * Safely transitions the blueprint lifecycle state.
     * Enforces that invalid blueprints cannot be PROPOSED, APPROVED, or EXECUTED.
     */
    fun transitionLifecycleState(newState: BlueprintLifecycleState): Boolean {
        val artifact = _currentArtifact.value ?: run {
            _statusMessage.value = "Cannot transition state: No artifact loaded."
            return false
        }

        val valResult = BlueprintValidator.validate(artifact)
        if (!valResult.isValid && (newState == BlueprintLifecycleState.PROPOSED || newState == BlueprintLifecycleState.APPROVED || newState == BlueprintLifecycleState.EXECUTED)) {
            _statusMessage.value = "Transition rejected: Blueprint has validation errors (${valResult.errorSummary})."
            return false
        }

        // Agent Authority Enforcement
        if (newState == BlueprintLifecycleState.PROPOSED) {
            val authorityResult = AgentAuthorityEnforcer.validate(artifact.strategyBlueprint)
            if (authorityResult.decision != AuthorityDecision.AUTHORIZED) {
                _statusMessage.value = "Authority rejected: ${authorityResult.reason}"
                return false
            }
        }

        if (newState == BlueprintLifecycleState.EXECUTED && artifact.lifecycleState != BlueprintLifecycleState.APPROVED) {
            _statusMessage.value = "Transition rejected: Blueprint must be APPROVED before it can be EXECUTED."
            return false
        }

        val updatedArtifact = artifact.copy(lifecycleState = newState)
        _currentArtifact.value = updatedArtifact
        _rawJsonText.value = BlueprintSerializer.toJson(updatedArtifact)
        _validationResult.value = valResult.copy(validatedArtifact = updatedArtifact)
        _statusMessage.value = "Blueprint state updated to: ${newState.name}"
        return true
    }

    private fun handleLoadResult(res: BlueprintLoadResult, source: String): Boolean {
        if (res.isSuccess && res.artifact != null) {
            _currentArtifact.value = res.artifact
            _rawJsonText.value = BlueprintSerializer.toJson(res.artifact)
            _validationResult.value = res.validationResult
            _statusMessage.value = "Successfully loaded blueprint from $source (State: ${res.artifact.lifecycleState.name})"
            return true
        } else {
            _currentArtifact.value = res.artifact
            if (res.artifact != null) {
                _rawJsonText.value = BlueprintSerializer.toJson(res.artifact)
            }
            _validationResult.value = res.validationResult
            _statusMessage.value = "Failed to load/validate blueprint from $source: ${res.errorMessage}"
            return false
        }
    }
}
