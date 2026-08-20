package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.compatibility.AuraPlaybackRouter
import com.example.compatibility.PlaybackRouteResult
import com.example.data.ClipCandidate
import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.util.ClipExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClipExporterTest {

    @Test
    fun `test playback route resolution for playable media`() {
        val playableItem = MediaItem(
            id = "test_vid_1",
            title = "Playable Video",
            mediaType = "VIDEO",
            uriPath = "content://media/external/video/media/100",
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )

        val route = AuraPlaybackRouter.resolveRoute(playableItem)
        assertTrue(route is PlaybackRouteResult.Playable)
        val playableRoute = route as PlaybackRouteResult.Playable
        assertEquals("content://media/external/video/media/100", playableRoute.playUri)
    }

    @Test
    fun `test playback route resolution for media requiring conversion`() {
        val conversionItem = MediaItem(
            id = "test_vid_2",
            title = "Raw Video",
            mediaType = "VIDEO",
            uriPath = "/sdcard/Movies/raw_video.mkv",
            compatibilityStatus = CompatibilityStatus.PLAYABLE_AFTER_CONVERSION,
            compatibilityReason = "MKV container requires conversion"
        )

        val route = AuraPlaybackRouter.resolveRoute(conversionItem)
        assertTrue(route is PlaybackRouteResult.NeedsConversion)
        val convertRoute = route as PlaybackRouteResult.NeedsConversion
        assertEquals("MKV container requires conversion", convertRoute.reason)
    }

    @Test
    fun `test playback route resolution for corrupt media`() {
        val corruptItem = MediaItem(
            id = "test_vid_3",
            title = "Corrupt Video",
            mediaType = "VIDEO",
            uriPath = "",
            compatibilityStatus = CompatibilityStatus.CORRUPT,
            compatibilityReason = "Media item has no valid URI path"
        )

        val route = AuraPlaybackRouter.resolveRoute(corruptItem)
        assertTrue(route is PlaybackRouteResult.Corrupt)
    }

    @Test
    fun `test clip export handling of invalid or empty source media`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val emptyItem = MediaItem(
            id = "empty_1",
            title = "Empty Item",
            mediaType = "VIDEO",
            uriPath = "",
            compatibilityStatus = CompatibilityStatus.CORRUPT
        )
        val clip = ClipCandidate(
            title = "Invalid Range Clip",
            startTimeMs = 0L,
            endTimeMs = 5000L,
            durationSec = 5,
            relevanceScorePercent = 80,
            selectionReason = "Test"
        )

        val result = ClipExporter.exportClipAndSave(
            context = context,
            sourceItem = emptyItem,
            clip = clip,
            repository = com.example.data.MediaRepository.instance
        )

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("corrupt") || result.errorMessage!!.contains("no valid URI"))
    }

    @Test
    fun `test timestamp clamping logic for short or boundary clip requests`() {
        val sourceDurationMs = 12_000L // 12 seconds
        val requestedStartMs = -5000L // Negative start time
        val requestedEndMs = 20_000L // End time beyond duration

        val clampedStart = requestedStartMs.coerceAtLeast(0L)
        var clampedEnd = requestedEndMs
        if (clampedEnd <= clampedStart) {
            clampedEnd = minOf(clampedStart + 5000L, sourceDurationMs)
        }
        if (sourceDurationMs > 0 && clampedEnd > sourceDurationMs) {
            clampedEnd = sourceDurationMs
        }

        assertEquals(0L, clampedStart)
        assertEquals(12_000L, clampedEnd)
    }

    @Test
    fun `test clip looping boundary check logic`() {
        val clipStartMs = 10_000L
        val clipEndMs = 25_000L

        // Simulation 1: Position within clip range
        var pos = 15_000L
        var shouldSeekToStart = (pos >= clipEndMs || pos < clipStartMs - 1000L)
        assertFalse("Playback inside clip bounds should not trigger seek", shouldSeekToStart)

        // Simulation 2: Position exceeds clip end time
        pos = 25_500L
        shouldSeekToStart = (pos >= clipEndMs || pos < clipStartMs - 1000L)
        assertTrue("Playback exceeding clip end time must trigger seek to start", shouldSeekToStart)

        // Simulation 3: Position before clip start
        pos = 2_000L
        shouldSeekToStart = (pos >= clipEndMs || pos < clipStartMs - 1000L)
        assertTrue("Playback before clip start range must trigger seek to start", shouldSeekToStart)
    }

    @Test
    fun `test clip back navigation state transition contract`() {
        // Navigation states representation
        var showClipsSheet = false
        var activeClipCandidate: ClipCandidate? = null
        var isSceneLoopActive = false
        var onBackCalledCount = 0

        fun handleBackPress() {
            if (showClipsSheet) {
                showClipsSheet = false
            } else if (activeClipCandidate != null || isSceneLoopActive) {
                activeClipCandidate = null
                isSceneLoopActive = false
                showClipsSheet = true
            } else {
                onBackCalledCount++
            }
        }

        // Step 1: User on Original Video (State A)
        assertFalse(showClipsSheet)
        assertEquals(null, activeClipCandidate)

        // Step 2: Open Clip Menu (State B)
        showClipsSheet = true
        assertTrue(showClipsSheet)

        // Step 3: Select Clip to View (State C)
        val selectedClip = ClipCandidate("Test Clip", 10_000L, 20_000L, 10, 95, "Highlight")
        activeClipCandidate = selectedClip
        isSceneLoopActive = true
        showClipsSheet = false

        // State C active
        assertFalse(showClipsSheet)
        assertNotNull(activeClipCandidate)
        assertTrue(isSceneLoopActive)

        // Step 4: First Back Press -> Returns to State B (Clip Menu)
        handleBackPress()
        assertTrue("First back press from Clip Viewer must open Clip Menu", showClipsSheet)
        assertEquals("First back press must clear active clip candidate", null, activeClipCandidate)
        assertFalse("First back press must deactivate scene loop", isSceneLoopActive)

        // Step 5: Second Back Press -> Returns to State A (Original Video)
        handleBackPress()
        assertFalse("Second back press must dismiss Clip Menu", showClipsSheet)
        assertEquals(0, onBackCalledCount)

        // Step 6: Third Back Press -> Navigates back out of screen
        handleBackPress()
        assertEquals(1, onBackCalledCount)
    }
}
