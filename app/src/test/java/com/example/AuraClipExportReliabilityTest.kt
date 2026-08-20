package com.example

import com.example.data.ClipCandidate
import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.util.ClipExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AuraClipExportReliabilityTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        repository = MediaRepository(testDispatcher)
    }

    private fun createMediaItem(id: String, duration: Long = 60000L): MediaItem {
        return MediaItem(
            id = id,
            title = "Test Video",
            mediaType = "VIDEO",
            durationMs = duration,
            uriPath = "content://media/external/video/media/1",
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun testInvalidABBoundariesRejected() = runTest(testDispatcher) {
        val item = createMediaItem("1")
        
        // Negative start
        val res1 = ClipExporter.exportClipAndSave(
            context = mock(android.content.Context::class.java),
            sourceItem = item,
            clip = ClipCandidate("Clip", -1000L, 5000L, 6, 100, ""),
            repository = repository
        )
        assertFalse("Negative start should be rejected", res1.isSuccess)

        // End before start
        val res2 = ClipExporter.exportClipAndSave(
            context = mock(android.content.Context::class.java),
            sourceItem = item,
            clip = ClipCandidate("Clip", 5000L, 4000L, 6, 100, ""),
            repository = repository
        )
        assertFalse("End before start should be rejected", res2.isSuccess)

        // Start beyond duration
        val res3 = ClipExporter.exportClipAndSave(
            context = mock(android.content.Context::class.java),
            sourceItem = item,
            clip = ClipCandidate("Clip", 70000L, 80000L, 10, 100, ""),
            repository = repository
        )
        assertFalse("Start beyond duration should be rejected", res3.isSuccess)
    }
}
