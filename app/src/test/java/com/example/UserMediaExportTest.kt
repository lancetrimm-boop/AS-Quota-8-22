package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ClipCandidate
import com.example.data.MediaItem
import com.example.data.db.MediaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserMediaExportTest {

    @Test
    fun `test exported clip entity classification`() {
        val clip = ClipCandidate(
            title = "Climax Segment",
            startTimeMs = 10000L,
            endTimeMs = 25000L,
            durationSec = 15,
            relevanceScorePercent = 92,
            selectionReason = "High motion energy"
        )

        val sourceItem = MediaItem(
            id = "src_1",
            title = "Test Video",
            mediaType = "VIDEO",
            uriPath = "content://media/external/video/media/1"
        )

        val entity = MediaEntity(
            id = "aura_clip_123456_10000",
            title = "${sourceItem.title} — ${clip.title}",
            mediaType = "VIDEO",
            year = 2026,
            duration = "15s",
            durationMs = 15000L,
            genre = "Aura Generated Clips",
            imageUrl = "content://media/external/video/media/99",
            rating = 5.0f,
            category = "User Media",
            aiSummary = "Aura AI Generated Clip (${clip.selectionReason})",
            moodTagsJson = "User Media,Aura Generated,Clip,Video",
            uriPath = "content://media/external/video/media/99",
            dateAdded = System.currentTimeMillis()
        )

        assertEquals("VIDEO", entity.mediaType)
        assertEquals("Aura Generated Clips", entity.genre)
        assertEquals("User Media", entity.category)
        assertTrue(entity.title.contains("Climax Segment"))
        assertTrue(entity.moodTagsJson.contains("Aura Generated"))
    }

    @Test
    fun `test user media filtering logic`() {
        val items = listOf(
            MediaItem(id = "p1", title = "Photo 1", mediaType = "PHOTO"),
            MediaItem(id = "v1", title = "Video 1", mediaType = "VIDEO"),
            MediaItem(
                id = "aura_clip_1",
                title = "Aura Clip 1",
                mediaType = "VIDEO",
                genre = "Aura Generated Clips",
                category = "User Media"
            ),
            MediaItem(
                id = "aura_snapshot_12345",
                title = "Snapshot — Test Video",
                mediaType = "PHOTO",
                genre = "User Screenshots",
                category = "User Media",
                moodTags = listOf("User Media", "Snapshot", "Photo")
            )
        )

        val photos = items.filter { it.mediaType.equals("PHOTO", ignoreCase = true) }
        val videos = items.filter { it.mediaType.equals("VIDEO", ignoreCase = true) }
        val auraClips = items.filter {
            it.genre.contains("Aura Generated", ignoreCase = true) ||
                    it.id.startsWith("aura_clip_")
        }
        val userScreenshots = items.filter {
            it.genre.equals("User Screenshots", ignoreCase = true) ||
            it.genre.equals("User Screenshot", ignoreCase = true) ||
            it.id.startsWith("aura_snapshot_") ||
            it.moodTags.contains("Snapshot")
        }

        assertEquals(2, photos.size)
        assertEquals(2, videos.size)
        assertEquals(1, auraClips.size)
        assertEquals("Aura Clip 1", auraClips.first().title)
        assertEquals(1, userScreenshots.size)
        assertEquals("Snapshot — Test Video", userScreenshots.first().title)
    }
}
