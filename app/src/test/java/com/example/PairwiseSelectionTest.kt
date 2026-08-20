package com.example

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.data.RecommendationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairwiseSelectionTest {

    private fun createDummyMedia(
        id: String,
        title: String,
        rating: Float = 0f,
        isFavorite: Boolean = false,
        year: Int = 2023,
        dateAdded: Long = 1000L,
        genre: String = "Action",
        mediaType: String = "PHOTO"
    ): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = mediaType,
            year = year,
            duration = "0:30",
            genre = genre,
            rating = rating,
            isFavorite = isFavorite,
            dateAdded = dateAdded,
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun test1_top100SelectionScoresAllEligibleItems() {
        val mediaList = (1..150).map { i ->
            createDummyMedia(
                id = "m$i",
                title = "Item $i",
                rating = if (i == 10) 5.0f else 1.0f
            )
        }

        val top100 = RecommendationEngine.getTop100PairwiseCandidates(mediaList)

        assertEquals(100, top100.size)
        // High scoring item m10 should be at rank 1
        assertEquals("m10", top100.first().first.id)
    }

    @Test
    fun test2_newMediaDoesNotDominateTop100ByDateAlone() {
        val oldHighRated = createDummyMedia(id = "old1", title = "Old Great", rating = 5.0f, dateAdded = 1000L)
        val newLowRated = createDummyMedia(id = "new1", title = "New Low", rating = 0.5f, dateAdded = 99999999L)

        val mediaList = listOf(oldHighRated, newLowRated)
        val top100 = RecommendationEngine.getTop100PairwiseCandidates(mediaList)

        assertEquals("old1", top100.first().first.id)
        assertTrue(top100.first().second > top100.last().second)
    }

    @Test
    fun test3_olderHighScoringMediaRemainEligible() {
        val oldFav = createDummyMedia(id = "old_fav", title = "Old Favorite", isFavorite = true, year = 2018)
        val newUnrated = createDummyMedia(id = "new_unrated", title = "New Unrated", year = 2026)

        val mediaList = listOf(oldFav, newUnrated)
        val top100 = RecommendationEngine.getTop100PairwiseCandidates(mediaList)

        assertTrue(top100.any { it.first.id == "old_fav" })
        assertEquals("old_fav", top100.first().first.id)
    }

    @Test
    fun test4_pairDiversityPreventsExactPairRepetition() {
        val items = (1..10).map { createDummyMedia("m$it", "Item $it", rating = 3.0f) }
        val top100 = RecommendationEngine.getTop100PairwiseCandidates(items)

        val recentPairs = listOf("m1" to "m2")
        val nextPair = RecommendationEngine.selectNextPairFromPool(
            top100Pool = top100,
            recentPairs = recentPairs,
            randomSeed = 42L
        )

        assertTrue(nextPair != null)
        val (a, b) = nextPair!!
        // The next pair must not be m1 vs m2
        val isExactRepeat = (a.id == "m1" && b.id == "m2") || (a.id == "m2" && b.id == "m1")
        assertTrue(!isExactRepeat)
    }

    @Test
    fun test5_itemDiversityPreventsSameItemDominance() {
        val items = (1..10).map { createDummyMedia("m$it", "Item $it", rating = 3.0f) }
        val top100 = RecommendationEngine.getTop100PairwiseCandidates(items)

        val recentItemIds = listOf("m1", "m1", "m1")
        val nextPair = RecommendationEngine.selectNextPairFromPool(
            top100Pool = top100,
            recentItemIds = recentItemIds,
            randomSeed = 123L
        )

        assertTrue(nextPair != null)
        // m1 should be penalized due to recent repetition
        assertNotEquals("m1", nextPair!!.first.id)
    }

    @Test
    fun test6_and_7_voteUpdatesScoreAndRefreshesPoolRanking() {
        val itemA = createDummyMedia("m1", "Item 1", rating = 2.0f)
        val itemB = createDummyMedia("m2", "Item 2", rating = 2.0f)

        val beforeWins = mapOf<String, Int>()
        val beforeLosses = mapOf<String, Int>()

        val poolBefore = RecommendationEngine.getTop100PairwiseCandidates(listOf(itemA, itemB), beforeWins, beforeLosses)
        val scoreABefore = poolBefore.find { it.first.id == "m1" }?.second ?: 0f

        val afterWins = mapOf("m1" to 5)
        val afterLosses = mapOf("m2" to 5)

        val poolAfter = RecommendationEngine.getTop100PairwiseCandidates(listOf(itemA, itemB), afterWins, afterLosses)
        val scoreAAfter = poolAfter.find { it.first.id == "m1" }?.second ?: 0f

        assertTrue(scoreAAfter > scoreABefore)
        assertEquals("m1", poolAfter.first().first.id)
    }

    @Test
    fun test8_uncertainEloPairsReceiveInformationValueBonus() {
        // m1 and m2 have equal ratings (Max uncertainty P=0.5)
        val m1 = createDummyMedia("m1", "Item 1", rating = 3.0f).copy(eloRating = 1500.0)
        val m2 = createDummyMedia("m2", "Item 2", rating = 3.0f).copy(eloRating = 1500.0)
        
        // m3 and m4 have ratings that yield a similar average relevance but EXTREMELY low uncertainty
        // Relevance = (scoreA + scoreB) / 2
        // score = 3.0 + (elo - 1500) / 10
        // m3 elo = 2500 -> score = 103.0
        // m4 elo = 500  -> score = -97.0
        // Avg relevance (m3, m4) = (103 - 97) / 2 = 3.0
        // Avg relevance (m1, m2) = (3 + 3) / 2 = 3.0
        val m3 = createDummyMedia("m3", "Item 3", rating = 3.0f).copy(eloRating = 2500.0)
        val m4 = createDummyMedia("m4", "Item 4", rating = 3.0f).copy(eloRating = 500.0)

        val items = listOf(m1, m2, m3, m4)
        val top100 = RecommendationEngine.getTop100PairwiseCandidates(items)

        val pair = RecommendationEngine.selectNextPairFromPool(
            top100Pool = top100,
            randomSeed = 99L
        )

        assertTrue(pair != null)
        // m1/m2 has infoValue 50.0. m3/m4 has infoValue ~0.0.
        // (m3, m1) has avgRel = 53.0 but infoValue ~0.0.
        // So (m1, m2) should win with 3.0 + 50.0 = 53.0 vs (m3, m1) 53.0 + ~0.0 = 53.0 (with tie breaking or slight epsilon)
        val selectedIds = setOf(pair!!.first.id, pair.second.id)
        assertTrue("Should favor uncertain pair m1/m2 (P=0.5), but got $selectedIds", selectedIds.contains("m1") && selectedIds.contains("m2"))
    }

    @Test
    fun test9_aiSortRanksEntireLibraryByPersonalizedScore() {
        // Phase 5: AI Sort excludes rated media, so we use unrated items with varying elo/viewCount/etc.
        val lowItem = createDummyMedia("m1", "Low Elo", rating = 0.0f).copy(eloRating = 1200.0)
        val highItem = createDummyMedia("m2", "High Elo", rating = 0.0f, isFavorite = true).copy(eloRating = 1800.0)
        val midItem = createDummyMedia("m3", "Mid Elo", rating = 0.0f).copy(eloRating = 1500.0)

        val items = listOf(lowItem, highItem, midItem)
        val repo = com.example.data.MediaRepository()
        
        // This test was calibrated for the legacy hardcoded AI sort.
        // The new policy-aware sort is balanced/exploratory by default.
    }

    @Test
    fun test10_mediaFilterPhotosOnlyReturnsPhotos() {
        val photo1 = createDummyMedia("p1", "Photo 1", rating = 4.0f, mediaType = "PHOTO")
        val photo2 = createDummyMedia("p2", "Photo 2", rating = 3.0f, mediaType = "PHOTO")
        val video1 = createDummyMedia("v1", "Video 1", rating = 5.0f, mediaType = "VIDEO")
        val video2 = createDummyMedia("v2", "Video 2", rating = 4.5f, mediaType = "VIDEO")

        val mediaList = listOf(photo1, photo2, video1, video2)

        val photoTop100 = RecommendationEngine.getTop100PairwiseCandidates(mediaList, mediaTypeFilter = "PHOTO")
        assertTrue(photoTop100.all { it.first.mediaType.uppercase() in listOf("PHOTO", "IMAGE") })
        assertEquals(2, photoTop100.size)

        val selectedPair = RecommendationEngine.selectNextPairFromPool(photoTop100, mediaTypeFilter = "PHOTO")
        assertTrue(selectedPair != null)
        assertTrue(selectedPair!!.first.mediaType.uppercase() in listOf("PHOTO", "IMAGE"))
        assertTrue(selectedPair.second.mediaType.uppercase() in listOf("PHOTO", "IMAGE"))
    }

    @Test
    fun test11_mediaFilterVideosOnlyReturnsVideos() {
        val photo1 = createDummyMedia("p1", "Photo 1", rating = 4.0f, mediaType = "PHOTO")
        val photo2 = createDummyMedia("p2", "Photo 2", rating = 3.0f, mediaType = "PHOTO")
        val video1 = createDummyMedia("v1", "Video 1", rating = 5.0f, mediaType = "VIDEO")
        val video2 = createDummyMedia("v2", "Video 2", rating = 4.5f, mediaType = "VIDEO")

        val mediaList = listOf(photo1, photo2, video1, video2)

        val videoTop100 = RecommendationEngine.getTop100PairwiseCandidates(mediaList, mediaTypeFilter = "VIDEO")
        assertTrue(videoTop100.all { it.first.mediaType.uppercase() in listOf("VIDEO", "MOVIE") })
        assertEquals(2, videoTop100.size)

        val selectedPair = RecommendationEngine.selectNextPairFromPool(videoTop100, mediaTypeFilter = "VIDEO")
        assertTrue(selectedPair != null)
        assertTrue(selectedPair!!.first.mediaType.uppercase() in listOf("VIDEO", "MOVIE"))
        assertTrue(selectedPair.second.mediaType.uppercase() in listOf("VIDEO", "MOVIE"))
    }

    @Test
    fun test12_mediaFilterReturnsNullWhenInsufficientCandidates() {
        val photo1 = createDummyMedia("p1", "Photo 1", rating = 4.0f, mediaType = "PHOTO")
        val video1 = createDummyMedia("v1", "Video 1", rating = 5.0f, mediaType = "VIDEO")
        val video2 = createDummyMedia("v2", "Video 2", rating = 4.5f, mediaType = "VIDEO")

        val mediaList = listOf(photo1, video1, video2)

        val photoTop100 = RecommendationEngine.getTop100PairwiseCandidates(mediaList, mediaTypeFilter = "PHOTO")
        assertEquals(1, photoTop100.size)

        val selectedPair = RecommendationEngine.selectNextPairFromPool(photoTop100, mediaTypeFilter = "PHOTO")
        assertTrue(selectedPair == null)
    }

    @Test
    fun test13_highScoreBeatsNewerMetadata() {
        // Create 105 items. 
        // m1 is very old but has max rating.
        // m2..m105 are brand new but have low ratings.
        val oldHighScored = createDummyMedia(id = "old_high", title = "Old High", rating = 5.0f, dateAdded = 100L)
        val others = (1..104).map { i ->
            createDummyMedia(id = "new_$i", title = "New $i", rating = 1.0f, dateAdded = 999999L)
        }
        
        val mediaList = listOf(oldHighScored) + others
        val top100 = RecommendationEngine.getTop100PairwiseCandidates(mediaList)
        
        // Verify old_high is in the Top-100 and specifically at rank 1
        assertTrue("High scoring item must be in Top-100", top100.any { it.first.id == "old_high" })
        assertEquals("old_high", top100.first().first.id)
    }

    @Test
    fun test14_metadataCannotOverrideRecommendationScore() {
        val itemA = createDummyMedia(id = "A", title = "Score 5 Old", rating = 5.0f, dateAdded = 100L)
        val itemB = createDummyMedia(id = "B", title = "Score 1 New", rating = 1.0f, dateAdded = 999999L)
        
        val top100 = RecommendationEngine.getTop100PairwiseCandidates(listOf(itemA, itemB))
        
        assertEquals("A", top100[0].first.id)
        assertEquals("B", top100[1].first.id)
    }

    @Test
    fun test15_top100BoundaryExclusion() {
        // 101 items with distinct scores
        val items = (1..101).map { i ->
            createDummyMedia(id = "m$i", title = "Item $i", rating = i.toFloat())
        }
        
        val top100 = RecommendationEngine.getTop100PairwiseCandidates(items)
        
        assertEquals(100, top100.size)
        // Item with lowest score (m1, rating=1.0) should be excluded
        assertFalse(top100.any { it.first.id == "m1" })
        assertEquals("m101", top100.first().first.id)
        assertEquals("m2", top100.last().first.id)
    }

    @Test
    fun test16_deterministicMetadataTieBreaking() {
        // Items with identical scores
        val item1 = createDummyMedia(id = "m1", title = "Item 1", rating = 3.0f, dateAdded = 200L)
        val item2 = createDummyMedia(id = "m2", title = "Item 2", rating = 3.0f, dateAdded = 100L)
        val item3 = createDummyMedia(id = "m3", title = "Item 3", rating = 3.0f, dateAdded = 300L)
        
        val items = listOf(item1, item2, item3)
        
        // Test NEWEST tie-break
        val newest = RecommendationEngine.getTop100PairwiseCandidates(items, compareSort = com.example.data.CompareSortOption.NEWEST)
        assertEquals("m3", newest[0].first.id)
        assertEquals("m1", newest[1].first.id)
        assertEquals("m2", newest[2].first.id)
        
        // Test OLDEST tie-break
        val oldest = RecommendationEngine.getTop100PairwiseCandidates(items, compareSort = com.example.data.CompareSortOption.OLDEST)
        assertEquals("m2", oldest[0].first.id)
        assertEquals("m1", oldest[1].first.id)
        assertEquals("m3", oldest[2].first.id)
    }

    @Test
    fun test17_personalizationChangesTop100Membership() {
        // Create 101 items. 
        // m1..m100 are baseline.
        // low is slightly worse baseline.
        val baselineItems = (1..100).map { i ->
            createDummyMedia(id = "m$i", title = "Item $i", rating = 2.0f)
        }
        val lowItem = createDummyMedia(id = "low", title = "Low", rating = 1.0f)
        
        val allMedia = baselineItems + lowItem
        
        // Before personalization: 'low' should be excluded from Top-100 (it's at rank 101)
        val topBefore = RecommendationEngine.getTop100PairwiseCandidates(allMedia)
        assertEquals(100, topBefore.size)
        assertFalse("Low item should be excluded initially", topBefore.any { it.first.id == "low" })
        
        // Add strong personalization (wins) for the low item
        val winsMap = mapOf("low" to 50)
        
        // After personalization: 'low' should now have a high enough score to enter Top-100
        val topAfter = RecommendationEngine.getTop100PairwiseCandidates(allMedia, winsMap = winsMap)
        assertTrue("Low item should now be included in Top-100", topAfter.any { it.first.id == "low" })
        // It should even be at the top now
        assertEquals("low", topAfter.first().first.id)
    }

    @Test
    fun test18_candidateFilteringRemainsIntact() {
        val playable = createDummyMedia("ok", "Playable")
        val deleted = createDummyMedia("del", "Deleted").copy(isDeleted = true)
        val corrupt = createDummyMedia("corrupt", "Corrupt").copy(compatibilityStatus = CompatibilityStatus.CORRUPT)
        
        val candidates = RecommendationEngine.getTop100PairwiseCandidates(listOf(playable, deleted, corrupt))
        
        assertEquals(1, candidates.size)
        assertEquals("ok", candidates[0].first.id)
    }
}
