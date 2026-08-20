package com.example.data.blueprint

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Metadata representing the state of the codebase at a specific point in time.
 * This artifact is exchanged between the IDE (Android Studio) and the Aura app.
 */
@JsonClass(generateAdapter = true)
data class CodebaseSnapshot(
    @Json(name = "repository_url") val repositoryUrl: String,
    @Json(name = "branch") val branch: String,
    @Json(name = "commit_hash") val commitHash: String,
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @Json(name = "symbols") val symbols: List<CodebaseSymbol> = emptyList(),
    @Json(name = "inspection_source") val source: InspectionSource = InspectionSource.IMPORTED_INSPECTION
)

enum class InspectionSource {
    REAL_REPOSITORY_INSPECTION,
    EXTERNAL_IDE_INSPECTION,
    IMPORTED_INSPECTION,
    CACHED_INSPECTION,
    VIRTUAL_TEST_REGISTRY
}

@JsonClass(generateAdapter = true)
data class CodebaseSymbol(
    @Json(name = "name") val name: String,
    @Json(name = "qualified_name") val qualifiedName: String,
    @Json(name = "file_path") val filePath: String,
    @Json(name = "type") val type: String, // CLASS, FUNCTION, PROPERTY, ENTITY
    @Json(name = "current_value") val currentValue: String? = null,
    @Json(name = "dependencies") val dependencies: List<String> = emptyList()
)
