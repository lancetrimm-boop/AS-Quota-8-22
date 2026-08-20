package com.example.data.blueprint

/**
 * Validation error for invalid fields or broken rules in a blueprint artifact.
 */
data class BlueprintValidationError(
    val field: String,
    val message: String
)

/**
 * Validation warning for non-fatal inconsistencies or potential issues.
 */
data class BlueprintValidationWarning(
    val field: String,
    val message: String
)

/**
 * Comprehensive validation result for a blueprint artifact.
 */
data class BlueprintValidationResult(
    val isValid: Boolean,
    val errors: List<BlueprintValidationError> = emptyList(),
    val warnings: List<BlueprintValidationWarning> = emptyList(),
    val validatedArtifact: BlueprintArtifact? = null
) {
    val errorSummary: String
        get() = errors.joinToString("; ") { "[${it.field}] ${it.message}" }

    val warningSummary: String
        get() = warnings.joinToString("; ") { "[${it.field}] ${it.message}" }
}
