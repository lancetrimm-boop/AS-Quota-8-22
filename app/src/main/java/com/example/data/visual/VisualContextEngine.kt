package com.example.data.visual

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.MediaRepository
import com.example.data.semantic.EmbeddingProvider
import com.example.data.semantic.EmbeddingResult
import com.example.data.semantic.SemanticInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * VisualContextEngine (V1)
 *
 * Captures and asynchronously analyzes visual characteristics of video frames
 * surrounding the moment of a user Like event. Maps extracted features
 * (brightness, warmth, saturation, contrast, texture) into existing Taste DNA dimensions.
 *
 * Invariants:
 * - Best-effort: visual analysis failure NEVER affects or rolls back a Like event.
 * - Non-blocking: strictly offloads extraction and pixel processing to Dispatchers.IO.
 * - Bounded concurrency: strictly max 2 concurrent MediaMetadataRetriever extractions.
 * - Low-resolution scaling: decodes max 256x256 frames to minimize memory footprint.
 * - Deduplication: suppresses repeated extractions for the same 2-second playback bucket.
 */
class VisualContextEngine(
    private val repository: MediaRepository,
    private val visualProvider: EmbeddingProvider? = null,
    private val semanticRepo: com.example.data.semantic.SemanticRepresentationRepository? = null,
    private val candidateRetriever: com.example.data.semantic.SemanticCandidateRetriever? = null
) {
    companion object {
        private const val TAG = "VisualContextEngine"
        private const val TARGET_FRAME_SIZE = 256
        private const val DEDUPLICATION_WINDOW_MS = 5000L
        private const val DEDUPLICATION_BUCKET_MS = 2000L
    }

    private val extractionSemaphore = Semaphore(2)
    private val recentJobs = ConcurrentHashMap<String, Long>()

    data class VisualFeatures(
        val brightness: Double, // 0.0 (dark) to 1.0 (bright)
        val warmth: Double,     // 0.0 (cool/blue) to 1.0 (warm/red)
        val saturation: Double, // 0.0 (monochrome) to 1.0 (vivid)
        val contrast: Double,   // 0.0 (flat) to 1.0 (high dynamic range)
        val texture: Double     // 0.0 (smooth/soft) to 1.0 (sharp/textured)
    )

    /**
     * Checks if this visual-context analysis is a duplicate of a recent job within the deduplication window.
     */
    private fun isDuplicate(mediaId: String, playbackPositionMs: Long): Boolean {
        val bucket = playbackPositionMs / DEDUPLICATION_BUCKET_MS
        val dedupeKey = "${mediaId}_$bucket"
        val now = System.currentTimeMillis()

        // Prune stale cache entries older than 60s
        recentJobs.entries.removeIf { now - it.value > 60_000L }

        val lastRun = recentJobs[dedupeKey]
        if (lastRun != null && (now - lastRun) < DEDUPLICATION_WINDOW_MS) {
            Log.d(TAG, "Analysis skipped due to deduplication for key: $dedupeKey")
            return true
        }
        recentJobs[dedupeKey] = now
        return false
    }

    /**
     * Main entry point for processing visual context of a Like event.
     */
    suspend fun processLikeContext(
        mediaId: String,
        uri: String,
        playbackPositionMs: Long,
        durationMs: Long,
        context: Context?
    ) = withContext(Dispatchers.IO) {
        if (isDuplicate(mediaId, playbackPositionMs)) {
            return@withContext
        }

        Log.d(TAG, "Visual context analysis started for media: $mediaId at ${playbackPositionMs}ms")

        try {
            val bitmaps = extractSampledFrames(mediaId, uri, playbackPositionMs, durationMs, context)
            if (bitmaps.isEmpty()) {
                Log.w(TAG, "Extraction produced 0 valid frames for $mediaId")
                return@withContext
            }

            val visualEmbeddings = mutableListOf<FloatArray>()
            val frameMetrics = mutableListOf<VisualFeatures>()
            for (bitmap in bitmaps) {
                // PHASE 7: Connect MobileCLIP visual embedding generation
                visualProvider?.let { provider ->
                    if (provider.isReady()) {
                        val result = provider.generateEmbedding(
                            mediaId = mediaId,
                            input = SemanticInput.ExplicitBitmap(bitmap),
                            sourceDataHash = "frame_temp"
                        )
                        when (result) {
                            is EmbeddingResult.Success -> {
                                visualEmbeddings.add(result.representation.vector)
                            }
                            is EmbeddingResult.Failure -> {
                                Log.w(TAG, "MobileCLIP inference failed for $mediaId: ${result.message}")
                            }
                        }
                    }
                }

                val metrics = calculatePixelMetrics(bitmap)
                if (metrics != null) {
                    frameMetrics.add(metrics)
                }

                try {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    // Ignore recycling exceptions
                }
            }

            // PHASE 7: Aggregate and Persist Visual Semantic Embedding
            if (visualEmbeddings.isNotEmpty() && semanticRepo != null && visualProvider != null) {
                val aggregatedVector = com.example.data.semantic.VectorMath.mean(visualEmbeddings)
                val normalizedVector = com.example.data.semantic.VectorMath.l2Normalize(aggregatedVector)
                
                val mediaItem = repository.mediaItemsMap.value[mediaId]
                val contentHash = mediaItem?.contentHash ?: "v1_visual_${System.currentTimeMillis()}"
                
                val descriptor = visualProvider.descriptor
                val representationId = "sem_${mediaId}_visual_${descriptor.modelId}_v${descriptor.modelVersion}"
                
                val representation = com.example.data.semantic.SemanticRepresentation(
                    id = representationId,
                    mediaId = mediaId,
                    type = com.example.data.semantic.SemanticRepresentationType.VISUAL,
                    modelDescriptor = descriptor,
                    dimensionality = descriptor.dimensionality,
                    vector = normalizedVector,
                    sourceDataHash = contentHash,
                    confidence = 1.0f
                )
                
                semanticRepo.saveRepresentation(representation)
                candidateRetriever?.onRepresentationAdded(representation)
                Log.i(TAG, "Successfully persisted aggregated 512d visual embedding for: $mediaId")
            }

            if (frameMetrics.isEmpty()) {
                Log.w(TAG, "Pixel analysis produced 0 valid metrics for $mediaId")
                return@withContext
            }

            val aggregated = aggregateMetrics(frameMetrics)
            applyTasteDnaAdjustment(aggregated, playbackPositionMs)
            Log.d(TAG, "Visual context analysis completed for media: $mediaId at ${playbackPositionMs}ms ($aggregated)")
        } catch (e: Exception) {
            Log.e(TAG, "Visual context analysis failed for $mediaId: ${e.message}", e)
        }
    }

    /**
     * Samples up to 3 frames around the Like moment: T-1000ms, T, T+1000ms.
     */
    private suspend fun extractSampledFrames(
        mediaId: String,
        uri: String,
        playbackPositionMs: Long,
        durationMs: Long,
        context: Context?
    ): List<Bitmap> = extractionSemaphore.withPermit {
        val dur = durationMs.coerceAtLeast(0L)
        val pos = playbackPositionMs.coerceIn(0L, if (dur > 0L) dur else Long.MAX_VALUE)

        val tCenter = pos
        val tPre = (pos - 1000L).coerceAtLeast(0L)
        val tPost = if (dur > 0L) (pos + 1000L).coerceAtMost(dur) else (pos + 1000L)

        val sampleTimesMs = listOf(tPre, tCenter, tPost).distinct()
        val bitmaps = mutableListOf<Bitmap>()
        val retriever = MediaMetadataRetriever()

        try {
            val uriObj = Uri.parse(uri)
            if (context != null && (uri.startsWith("content://") || uri.startsWith("file://"))) {
                retriever.setDataSource(context, uriObj)
            } else if (uri.startsWith("http://") || uri.startsWith("https://")) {
                retriever.setDataSource(uri, HashMap<String, String>())
            } else {
                retriever.setDataSource(uri)
            }

            for (timeMs in sampleTimesMs) {
                val timeUs = timeMs * 1000L
                val frame: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        TARGET_FRAME_SIZE,
                        TARGET_FRAME_SIZE
                    )
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { raw ->
                        val scaled = Bitmap.createScaledBitmap(raw, TARGET_FRAME_SIZE, TARGET_FRAME_SIZE, true)
                        if (scaled != raw) {
                            raw.recycle()
                        }
                        scaled
                    }
                }
                if (frame != null) {
                    bitmaps.add(frame)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame extraction exception for $mediaId: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release exception
            }
        }

        bitmaps
    }

    /**
     * Calculates pixel-level metrics for a single frame.
     * Guaranteed safe against division by zero, null, NaN, and Infinity.
     */
    private fun calculatePixelMetrics(bitmap: Bitmap): VisualFeatures? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val pixelCount = width * height
        if (pixelCount <= 0) return null

        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalLuminance = 0.0
        var totalWarmth = 0.0
        var totalSaturation = 0.0
        val luminances = DoubleArray(pixelCount)

        for (i in 0 until pixelCount) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val rf = r / 255.0
            val gf = g / 255.0
            val bf = b / 255.0

            // 1. Luminance: ITU-R BT.709 relative luminance
            val lum = 0.2126 * rf + 0.7152 * gf + 0.0722 * bf
            luminances[i] = lum
            totalLuminance += lum

            // 2. Color warmth: Normalized red vs blue balance (-1.0 to 1.0 -> 0.0 to 1.0)
            val rgbSum = (r + g + b).toDouble()
            val pixelWarmth = if (rgbSum > 0.0) {
                (r - b).toDouble() / (rgbSum + 0.001)
            } else {
                0.0
            }
            totalWarmth += pixelWarmth

            // 3. Saturation: HSV saturation channel
            val maxCh = max(rf, max(gf, bf))
            val minCh = min(rf, min(gf, bf))
            val sat = if (maxCh > 0.0) (maxCh - minCh) / maxCh else 0.0
            totalSaturation += sat
        }

        val meanBrightness = (totalLuminance / pixelCount).coerceIn(0.0, 1.0)
        val meanWarmthRaw = (totalWarmth / pixelCount).coerceIn(-1.0, 1.0)
        val meanWarmth = ((meanWarmthRaw + 1.0) / 2.0).coerceIn(0.0, 1.0)
        val meanSaturation = (totalSaturation / pixelCount).coerceIn(0.0, 1.0)

        // 4. Contrast: Standard deviation of luminance distribution (max std dev for [0,1] is 0.5)
        var varianceSum = 0.0
        for (i in 0 until pixelCount) {
            val diff = luminances[i] - meanBrightness
            varianceSum += diff * diff
        }
        val variance = varianceSum / pixelCount
        val stdDev = sqrt(max(0.0, variance))
        val contrast = (stdDev * 2.0).coerceIn(0.0, 1.0)

        // 5. Texture / Sharpness: Lightweight spatial edge-gradient energy
        var gradientSum = 0.0
        var gradientSamples = 0
        val step = 2 // Sample every 2 pixels for speed
        for (y in 0 until height - step step step) {
            for (x in 0 until width - step step step) {
                val idx = y * width + x
                val idxRight = y * width + (x + step)
                val idxDown = (y + step) * width + x

                val deltaX = kotlin.math.abs(luminances[idxRight] - luminances[idx])
                val deltaY = kotlin.math.abs(luminances[idxDown] - luminances[idx])
                gradientSum += (deltaX + deltaY)
                gradientSamples++
            }
        }
        val meanGradient = if (gradientSamples > 0) gradientSum / gradientSamples else 0.0
        val texture = (meanGradient * 4.0).coerceIn(0.0, 1.0)

        return VisualFeatures(
            brightness = if (meanBrightness.isNaN()) 0.5 else meanBrightness,
            warmth = if (meanWarmth.isNaN()) 0.5 else meanWarmth,
            saturation = if (meanSaturation.isNaN()) 0.5 else meanSaturation,
            contrast = if (contrast.isNaN()) 0.5 else contrast,
            texture = if (texture.isNaN()) 0.5 else texture
        )
    }

    /**
     * Aggregates features across all valid sampled frames using arithmetic mean.
     */
    private fun aggregateMetrics(metricsList: List<VisualFeatures>): VisualFeatures {
        val count = metricsList.size.toDouble()
        val avgBrightness = metricsList.sumOf { it.brightness } / count
        val avgWarmth = metricsList.sumOf { it.warmth } / count
        val avgSaturation = metricsList.sumOf { it.saturation } / count
        val avgContrast = metricsList.sumOf { it.contrast } / count
        val avgTexture = metricsList.sumOf { it.texture } / count

        return VisualFeatures(
            brightness = avgBrightness.coerceIn(0.0, 1.0),
            warmth = avgWarmth.coerceIn(0.0, 1.0),
            saturation = avgSaturation.coerceIn(0.0, 1.0),
            contrast = avgContrast.coerceIn(0.0, 1.0),
            texture = avgTexture.coerceIn(0.0, 1.0)
        )
    }

    /**
     * Maps extracted visual features to existing Taste DNA dimensions and updates Taste DNA.
     */
    private fun applyTasteDnaAdjustment(features: VisualFeatures, playbackPositionMs: Long) {
        val dna = repository.tasteDNA.value
        if (!dna.isFineTuningEnabled) return

        var updatedDna = dna
        val limit = MediaRepository.TOTAL_ADJUSTMENT_LIMIT
        val step = MediaRepository.MAX_ADJUSTMENT_PER_VOTE

        // (feature - 0.5) * 2.0 maps [0.0, 1.0] to [-1.0, 1.0]
        fun computeAdjustment(featureValue: Double): Double {
            return (featureValue - 0.5) * 2.0 * step
        }

        val adjLighting = computeAdjustment(features.brightness)
        val adjContrast = computeAdjustment(features.contrast)
        val adjWarmth = computeAdjustment(features.warmth)
        val adjColorTemp = -computeAdjustment(features.warmth) // Warmer color = lower color temperature
        val adjSaturation = computeAdjustment(features.saturation)
        val adjVibrancy = computeAdjustment(features.saturation)
        val adjTexture = computeAdjustment(features.texture)
        val adjSharpness = computeAdjustment(features.texture)

        updatedDna = updatedDna.updateLearnedDimension("lighting", adjLighting, limit)
        updatedDna = updatedDna.updateLearnedDimension("dynamicRange", adjContrast, limit)
        updatedDna = updatedDna.updateLearnedDimension("contrast", adjContrast, limit)
        updatedDna = updatedDna.updateLearnedDimension("warmth", adjWarmth, limit)
        updatedDna = updatedDna.updateLearnedDimension("colorTemperature", adjColorTemp, limit)
        updatedDna = updatedDna.updateLearnedDimension("saturation", adjSaturation, limit)
        updatedDna = updatedDna.updateLearnedDimension("vibrancy", adjVibrancy, limit)
        updatedDna = updatedDna.updateLearnedDimension("texture", adjTexture, limit)
        updatedDna = updatedDna.updateLearnedDimension("sharpness", adjSharpness, limit)

        if (updatedDna != dna) {
            val totalSec = playbackPositionMs / 1000L
            val min = totalSec / 60L
            val sec = totalSec % 60L
            val tenths = (playbackPositionMs % 1000L) / 100L
            val timeStr = String.format(Locale.US, "%02d:%02d.%d", min, sec, tenths)

            repository.updateTasteDNA(
                updatedDna,
                isUserGenerated = false,
                evidenceCategory = "Visual Context Like ($timeStr)"
            )
        }
    }
}
