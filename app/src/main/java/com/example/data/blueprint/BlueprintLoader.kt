package com.example.data.blueprint

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

/**
 * Result of loading an external or repository Blueprint artifact.
 */
data class BlueprintLoadResult(
    val isSuccess: Boolean,
    val artifact: BlueprintArtifact? = null,
    val validationResult: BlueprintValidationResult? = null,
    val errorMessage: String? = null
)

/**
 * Loads Blueprint Artifacts from JSON string, File, or Android Repository Assets.
 * Never silently accepts an invalid blueprint — always parses schema and performs validation.
 */
object BlueprintLoader {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(BlueprintArtifact::class.java)
    private val reconstructionAdapter = moshi.adapter(ReconstructionPackage::class.java)
    private val legacyStrategyAdapter = moshi.adapter(com.example.data.StrategyBlueprint::class.java)

    /**
     * Detects and loads either a standalone BlueprintArtifact, a full ReconstructionPackage,
     * or a legacy standalone StrategyBlueprint.
     */
    fun fromJsonUnified(json: String): BlueprintLoadResult {
        if (json.isBlank()) return BlueprintLoadResult(false, errorMessage = "JSON is empty.")
        
        return try {
            when {
                json.contains("package_id") && json.contains("blueprint_artifact") -> {
                    val pkg = reconstructionAdapter.fromJson(json)
                        ?: return BlueprintLoadResult(false, errorMessage = "Failed to parse ReconstructionPackage.")
                    fromJson(moshi.adapter(BlueprintArtifact::class.java).toJson(pkg.blueprintArtifact))
                }
                json.contains("blueprint_id") && json.contains("strategy_blueprint") -> {
                    // Modern BlueprintArtifact
                    fromJson(json)
                }
                json.contains("identity") && json.contains("diagnosis") -> {
                    // Legacy StrategyBlueprint - wrap it in an artifact
                    val legacy = legacyStrategyAdapter.fromJson(json)
                        ?: return BlueprintLoadResult(false, errorMessage = "Failed to parse legacy StrategyBlueprint.")
                    val artifact = BlueprintArtifact(
                        blueprintId = legacy.identity.blueprintId,
                        blueprintVersion = legacy.identity.version,
                        strategyBlueprint = legacy
                    )
                    fromJson(moshi.adapter(BlueprintArtifact::class.java).toJson(artifact))
                }
                else -> BlueprintLoadResult(false, errorMessage = "Unknown JSON schema.")
            }
        } catch (e: Exception) {
            BlueprintLoadResult(false, errorMessage = "Unified parse error: ${e.message}")
        }
    }

    /**
     * Loads and validates a BlueprintArtifact from a JSON string.
     */
    fun fromJson(json: String): BlueprintLoadResult {
        if (json.isBlank()) {
            return BlueprintLoadResult(
                isSuccess = false,
                errorMessage = "JSON string is blank or empty."
            )
        }

        return try {
            val rawParsed = adapter.fromJson(json)
                ?: return BlueprintLoadResult(
                    isSuccess = false,
                    errorMessage = "Failed to parse JSON into BlueprintArtifact (null object returned)."
                )

            val manifest = if (rawParsed.implementationManifest == null) {
                BlueprintImplementationPlanner.planImplementation(rawParsed.strategyBlueprint)
            } else {
                rawParsed.implementationManifest
            }

            // Generate/Update instructions using manifest context
            val preciseInstructions = BuilderInstructionGenerator.generate(rawParsed.strategyBlueprint, manifest)

            val parsed = rawParsed.copy(
                strategyBlueprint = rawParsed.strategyBlueprint.copy(builderInstructions = preciseInstructions),
                builderInstructions = preciseInstructions,
                implementationManifest = manifest
            )

            val validation = BlueprintValidator.validate(parsed)
            BlueprintLoadResult(
                isSuccess = validation.isValid,
                artifact = parsed,
                validationResult = validation,
                errorMessage = if (validation.isValid) null else "Validation failed: ${validation.errorSummary}"
            )
        } catch (e: Exception) {
            BlueprintLoadResult(
                isSuccess = false,
                errorMessage = "JSON parsing syntax error: ${e.message}"
            )
        }
    }

    /**
     * Loads and validates a BlueprintArtifact from a File.
     */
    fun fromFile(file: File): BlueprintLoadResult {
        if (!file.exists() || !file.canRead()) {
            return BlueprintLoadResult(
                isSuccess = false,
                errorMessage = "Blueprint file does not exist or is unreadable: ${file.absolutePath}"
            )
        }
        return fromJson(file.readText())
    }

    /**
     * Loads and validates a BlueprintArtifact from Android Repository Assets.
     */
    fun fromAsset(context: Context, assetPath: String): BlueprintLoadResult {
        return try {
            val content = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            fromJson(content)
        } catch (e: Exception) {
            BlueprintLoadResult(
                isSuccess = false,
                errorMessage = "Failed to open asset '$assetPath': ${e.message}"
            )
        }
    }
}
