package com.example.compatibility

import com.example.data.CompatibilityStatus
import com.example.data.ConversionStatus
import com.example.data.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraCompatibilityEngineTest {

    @Test
    fun testResolveRoute_PlayableItem() {
        val item = MediaItem(
            id = "test_1",
            title = "Test Video",
            mediaType = "VIDEO",
            uriPath = "file:///sdcard/test.mp4",
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            containerFormat = "MP4",
            videoCodec = "video/avc",
            audioCodec = "audio/mp4a-latm"
        )

        val route = AuraPlaybackRouter.resolveRoute(item)
        assertTrue(route is PlaybackRouteResult.Playable)
        val playable = route as PlaybackRouteResult.Playable
        assertEquals("file:///sdcard/test.mp4", playable.playUri)
        assertEquals(false, playable.isSoftwareDecode)
    }

    @Test
    fun testResolveRoute_SoftwareDecodeItem() {
        val item = MediaItem(
            id = "test_2",
            title = "Software Decode Video",
            mediaType = "VIDEO",
            uriPath = "file:///sdcard/test_vp9.webm",
            compatibilityStatus = CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE,
            containerFormat = "WebM",
            videoCodec = "video/x-vnd.on2.vp9",
            audioCodec = "audio/opus"
        )

        val route = AuraPlaybackRouter.resolveRoute(item)
        assertTrue(route is PlaybackRouteResult.Playable)
        val playable = route as PlaybackRouteResult.Playable
        assertEquals("file:///sdcard/test_vp9.webm", playable.playUri)
        assertEquals(true, playable.isSoftwareDecode)
    }

    @Test
    fun testResolveRoute_NeedsConversionItem() {
        val item = MediaItem(
            id = "test_3",
            title = "Incompatible Codec Video",
            mediaType = "VIDEO",
            uriPath = "file:///sdcard/test_custom.flv",
            compatibilityStatus = CompatibilityStatus.PLAYABLE_AFTER_CONVERSION,
            containerFormat = "FLV",
            videoCodec = "video/x-vnd.on2.vp6",
            compatibilityReason = "Video codec requires conversion"
        )

        val route = AuraPlaybackRouter.resolveRoute(item)
        assertTrue(route is PlaybackRouteResult.NeedsConversion)
        val needsConv = route as PlaybackRouteResult.NeedsConversion
        assertEquals("Video codec requires conversion", needsConv.reason)
    }

    @Test
    fun testResolveRoute_ConvertedItemTakesPrecedence() {
        val item = MediaItem(
            id = "test_4",
            title = "Converted Video",
            mediaType = "VIDEO",
            uriPath = "file:///sdcard/original.flv",
            compatibilityStatus = CompatibilityStatus.PLAYABLE_AFTER_CONVERSION,
            conversionStatus = ConversionStatus.CONVERTED,
            convertedUri = "file:///cache/aura_converted/converted.mp4"
        )

        val route = AuraPlaybackRouter.resolveRoute(item)
        assertTrue(route is PlaybackRouteResult.Playable)
        val playable = route as PlaybackRouteResult.Playable
        assertEquals("file:///cache/aura_converted/converted.mp4", playable.playUri)
    }

    @Test
    fun testResolveRoute_CorruptItem() {
        val item = MediaItem(
            id = "test_5",
            title = "Corrupt File",
            mediaType = "VIDEO",
            uriPath = "file:///sdcard/corrupt.mp4",
            compatibilityStatus = CompatibilityStatus.CORRUPT,
            compatibilityReason = "Corrupt header"
        )

        val route = AuraPlaybackRouter.resolveRoute(item)
        assertTrue(route is PlaybackRouteResult.Corrupt)
        val corrupt = route as PlaybackRouteResult.Corrupt
        assertEquals("Corrupt header", corrupt.reason)
    }
}
