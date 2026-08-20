package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object MediaThumbnailFetcher {
    private const val TAG = "MediaThumbnailFetcher"
    private const val THUMBNAIL_DIR = "thumbnails"
    private const val MAX_CONCURRENT_EXTRACTIONS = 4
    private const val TARGET_THUMBNAIL_SIZE = 512

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    // Bounded concurrency for expensive extraction
    private val extractionSemaphore = Semaphore(MAX_CONCURRENT_EXTRACTIONS)

    suspend fun getThumbnail(context: Context, uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext null
        
        // 1. Memory Cache lookup (Fastest)
        memoryCache.get(uriString)?.let { return@withContext it }

        val cacheKey = generateCacheKey(uriString)
        val cacheFile = getCacheFile(context, cacheKey)

        // 2. Disk Cache lookup (Persistent)
        if (cacheFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(uriString, bitmap)
                    return@withContext bitmap
                } else {
                    // Corrupt cache file, remove it
                    cacheFile.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode cached thumbnail: ${e.message}")
                cacheFile.delete()
            }
        }

        // 3. Extraction with bounded concurrency (Expensive)
        ensureActive()
        
        return@withContext try {
            extractionSemaphore.withPermit {
                ensureActive()
                // Re-check memory cache in case it was populated while waiting for permit
                memoryCache.get(uriString)?.let { return@withPermit it }

                val bitmap = extractThumbnail(context, uriString)
                
                if (bitmap != null) {
                    saveToDiskCache(cacheFile, bitmap)
                    memoryCache.put(uriString, bitmap)
                }
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail extraction failed for $uriString: ${e.message}")
            null
        }
    }

    private fun extractThumbnail(context: Context, uriString: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(uriString)
            if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                retriever.setDataSource(context, uri)
            } else if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                retriever.setDataSource(uriString, HashMap<String, String>())
            } else {
                retriever.setDataSource(uriString)
            }

            // Extract frame at 1s (1_000_000 microseconds)
            // Use scaled extraction for memory safety if supported (API 27+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    1000000L, 
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 
                    TARGET_THUMBNAIL_SIZE, 
                    TARGET_THUMBNAIL_SIZE
                )
            } else {
                retriever.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: retriever.frameAtTime
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed: ${e.message}")
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors on older platforms
            }
        }
    }

    private fun saveToDiskCache(file: File, bitmap: Bitmap) {
        val tempFile = File(file.parent, "${file.name}.tmp")
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            // Atomic rename to avoid partial files
            if (!tempFile.renameTo(file)) {
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save thumbnail to disk: ${e.message}")
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun getCacheFile(context: Context, key: String): File {
        val dir = File(context.cacheDir, THUMBNAIL_DIR)
        return File(dir, "$key.jpg")
    }

    private fun generateCacheKey(uriString: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(uriString.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback to hashcode string if digest fails
            uriString.hashCode().toString()
        }
    }

    fun removeThumbnail(uriString: String) {
        memoryCache.remove(uriString)
    }
    
    fun clearDiskCache(context: Context) {
        try {
            val dir = File(context.cacheDir, THUMBNAIL_DIR)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear disk cache: ${e.message}")
        }
    }
}

object MediaCompatibility {
    fun isSupportedVideo(mimeType: String?, uriString: String): Boolean {
        if (mimeType != null && mimeType.startsWith("video/")) return true
        val lower = uriString.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
                lower.endsWith(".3gp") || lower.contains("video") || lower.startsWith("content://media/external/video")
    }

    fun isSupportedPhoto(mimeType: String?, uriString: String): Boolean {
        if (mimeType != null && mimeType.startsWith("image/")) return true
        val lower = uriString.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || lower.endsWith(".heic") || lower.contains("image") ||
                lower.startsWith("content://media/external/images")
    }
}
