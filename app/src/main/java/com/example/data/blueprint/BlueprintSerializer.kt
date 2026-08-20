package com.example.data.blueprint

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

/**
 * Serializes Blueprint Artifacts into clean, formatted versioned JSON representations.
 */
object BlueprintSerializer {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(BlueprintArtifact::class.java).indent("  ")
    private val reconstructionAdapter = moshi.adapter(ReconstructionPackage::class.java).indent("  ")

    /**
     * Serializes a BlueprintArtifact into formatted JSON.
     */
    fun toJson(artifact: BlueprintArtifact): String {
        return adapter.toJson(artifact)
    }

    /**
     * Serializes a ReconstructionPackage into formatted JSON.
     */
    fun toJson(pkg: ReconstructionPackage): String {
        return reconstructionAdapter.toJson(pkg)
    }

    /**
     * Exports a BlueprintArtifact to a file.
     */
    fun exportToFile(context: Context, artifact: BlueprintArtifact, customFilename: String? = null): File {
        val blueprintsDir = File(context.filesDir, "blueprints").apply { if (!exists()) mkdirs() }
        val filename = customFilename ?: "blueprint_${artifact.blueprintId.take(8)}_v${artifact.blueprintVersion}.json"
        val targetFile = File(blueprintsDir, filename)
        targetFile.writeText(toJson(artifact))
        return targetFile
    }

    /**
     * Exports a ReconstructionPackage to a file.
     */
    fun exportToFile(context: Context, pkg: ReconstructionPackage): File {
        val exportsDir = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }
        val filename = "recon_${pkg.improvementId.take(8)}_${System.currentTimeMillis()}.json"
        val targetFile = File(exportsDir, filename)
        targetFile.writeText(toJson(pkg))
        return targetFile
    }
}
