package com.example

import com.example.data.MediaItem
import com.example.data.MediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeeSimilarTest {

    private lateinit var repository: MediaRepository

    @Before
    fun setUp() {
        repository = MediaRepository()
    }

    @Test
    fun testReferenceItemIsExcludedFromResults() {
        val refItem = MediaItem(
            id = "ref_1",
            title = "Cinematic Sunset",
            mediaType = "VIDEO",
            genre = "Nature",
            category = "Travel",
            moodTags = listOf("Sunset", "Calm")
        )
        val candidate1 = MediaItem(
            id = "cand_1",
            title = "Mountain Sunset",
            mediaType = "VIDEO",
            genre = "Nature",
            category = "Travel",
            moodTags = listOf("Sunset")
        )

        repository.setMediaItemsForTesting(listOf(refItem, candidate1))

        val results = repository.getSimilarMedia(refItem)
        assertFalse("Results should not contain the reference item", results.any { it.id == refItem.id })
        assertTrue("Results should contain similar candidate", results.any { it.id == candidate1.id })
    }

    @Test
    fun testResultsAreBoundedWhenLibraryIsLarge() {
        val refItem = MediaItem(
            id = "ref_10",
            title = "Nature Documentary",
            mediaType = "VIDEO",
            genre = "Nature",
            category = "Documentary",
            moodTags = listOf("Wild")
        )

        // Generate 2,000 items in the library
        val largeLibrary = (1..2000).map { i ->
            MediaItem(
                id = "item_$i",
                title = "Nature Video $i",
                mediaType = "VIDEO",
                genre = "Nature",
                category = "Documentary",
                moodTags = listOf("Wild")
            )
        }

        repository.setMediaItemsForTesting(listOf(refItem) + largeLibrary)

        val results = repository.getSimilarMedia(refItem)

        // Verify bounds: results must be <= 30 items and NOT 2,000 items!
        assertTrue("Result size must be bounded to max 30 items, but was ${results.size}", results.size <= 30)
        assertFalse("Results should not contain reference item", results.any { it.id == refItem.id })
    }

    @Test
    fun testCandidatesAreRankedBySimilarity() {
        val refItem = MediaItem(
            id = "ref_hero",
            title = "Cyberpunk Action Game",
            mediaType = "VIDEO",
            genre = "Action",
            category = "Gaming",
            moodTags = listOf("Futuristic", "HighEnergy", "Neon")
        )

        val highSimilarity = MediaItem(
            id = "high_sim",
            title = "Cyberpunk Trailer",
            mediaType = "VIDEO",
            genre = "Action",
            category = "Gaming",
            moodTags = listOf("Futuristic", "Neon")
        )

        val lowSimilarity = MediaItem(
            id = "low_sim",
            title = "Calm Cooking Video",
            mediaType = "VIDEO",
            genre = "Lifestyle",
            category = "Food",
            moodTags = listOf("Relaxing")
        )

        repository.setMediaItemsForTesting(listOf(refItem, lowSimilarity, highSimilarity))

        val results = repository.getSimilarMedia(refItem)

        assertTrue("Results should contain high similarity item", results.any { it.id == highSimilarity.id })
        if (results.size >= 1) {
            assertEquals("Highest similarity item should be first", highSimilarity.id, results.first().id)
        }
    }

    @Test
    fun testDifferentReferenceItemsProduceDifferentCandidateSets() {
        val refNature = MediaItem(
            id = "ref_nature",
            title = "Deep Ocean Wonders",
            mediaType = "VIDEO",
            genre = "Nature",
            category = "Documentary",
            moodTags = listOf("Underwater", "Serene")
        )

        val refConcert = MediaItem(
            id = "ref_concert",
            title = "Rock Festival Live",
            mediaType = "VIDEO",
            genre = "Music",
            category = "Performance",
            moodTags = listOf("Loud", "Energetic")
        )

        val natureItem = MediaItem(
            id = "item_ocean",
            title = "Coral Reef Life",
            mediaType = "VIDEO",
            genre = "Nature",
            category = "Documentary",
            moodTags = listOf("Underwater")
        )

        val musicItem = MediaItem(
            id = "item_rock",
            title = "Guitar Solo Live",
            mediaType = "VIDEO",
            genre = "Music",
            category = "Performance",
            moodTags = listOf("Energetic")
        )

        repository.setMediaItemsForTesting(listOf(refNature, refConcert, natureItem, musicItem))

        val natureResults = repository.getSimilarMedia(refNature)
        val concertResults = repository.getSimilarMedia(refConcert)

        assertEquals("Nature query should prioritize nature content", natureItem.id, natureResults.firstOrNull()?.id)
        assertEquals("Concert query should prioritize music content", musicItem.id, concertResults.firstOrNull()?.id)
    }

    @Test
    fun testHighlyRatedUnrelatedItemDoesNotOutrankSimilarLowerRatedItem() {
        val refItem = MediaItem(
            id = "ref_scifi",
            title = "Sci-Fi Space Odyssey",
            mediaType = "VIDEO",
            genre = "Sci-Fi",
            category = "Movies",
            moodTags = listOf("Futuristic", "Space"),
            rating = 1.0f
        )

        val similarLowerRated = MediaItem(
            id = "sim_low_rate",
            title = "Space Station Documentary",
            mediaType = "VIDEO",
            genre = "Sci-Fi",
            category = "Movies",
            moodTags = listOf("Futuristic"),
            rating = 1.0f
        )

        val unrelatedHighlyRated = MediaItem(
            id = "unrel_high_rate",
            title = "Cooking Masterclass",
            mediaType = "VIDEO",
            genre = "Culinary",
            category = "Education",
            moodTags = listOf("Gourmet"),
            rating = 5.0f
        )

        repository.setMediaItemsForTesting(listOf(refItem, similarLowerRated, unrelatedHighlyRated))

        val results = repository.getSimilarMedia(refItem)

        assertEquals("Similar item must outrank unrelated highly rated item", similarLowerRated.id, results.firstOrNull()?.id)
    }

    @Test
    fun testFavoritedUnrelatedItemDoesNotReceiveSimilarityAdvantage() {
        val refItem = MediaItem(
            id = "ref_1",
            title = "Jazz Performance",
            mediaType = "VIDEO",
            genre = "Music",
            category = "Live",
            moodTags = listOf("Smooth", "Acoustic"),
            isFavorite = true
        )

        val similarNonFavorite = MediaItem(
            id = "sim_non_fav",
            title = "Jazz Saxophone Session",
            mediaType = "VIDEO",
            genre = "Music",
            category = "Live",
            moodTags = listOf("Smooth"),
            isFavorite = false
        )

        val unrelatedFavorite = MediaItem(
            id = "unrel_fav",
            title = "Action Movie Trailer",
            mediaType = "VIDEO",
            genre = "Action",
            category = "Blockbuster",
            moodTags = listOf("Explosive"),
            isFavorite = true
        )

        repository.setMediaItemsForTesting(listOf(refItem, similarNonFavorite, unrelatedFavorite))

        val results = repository.getSimilarMedia(refItem)

        assertEquals("Similar non-favorite item must outrank unrelated favorite item", similarNonFavorite.id, results.firstOrNull()?.id)
    }

    @Test
    fun testEquivalentSimilarityScoresDoNotUseRatingAsTieBreaker() {
        val refItem = MediaItem(
            id = "ref_1",
            title = "Forest Walk",
            mediaType = "VIDEO",
            genre = "Nature",
            category = "Relaxation",
            moodTags = listOf("Green", "Quiet")
        )

        // Item A added earlier (dateAdded = 1000) with 1 star rating
        val itemA = MediaItem(
            id = "item_a",
            title = "Forest Hike",
            mediaType = "VIDEO",
            genre = "Nature",
            category = "Relaxation",
            moodTags = listOf("Green"),
            rating = 1.0f,
            dateAdded = 2000L
        )

        // Item B added later (dateAdded = 1000) with 5 star rating
        val itemB = MediaItem(
            id = "item_b",
            title = "Forest Stream",
            mediaType = "VIDEO",
            genre = "Nature",
            category = "Relaxation",
            moodTags = listOf("Green"),
            rating = 5.0f,
            dateAdded = 1000L
        )

        repository.setMediaItemsForTesting(listOf(refItem, itemA, itemB))

        val results = repository.getSimilarMedia(refItem)

        assertEquals("Tie-breaker must use dateAdded/recency over rating", itemA.id, results.firstOrNull()?.id)
    }

    @Test
    fun testMatchingReferenceMetadataIncreasesSimilarity() {
        val refItem = MediaItem(
            id = "ref_1",
            title = "Cyberpunk Neo Tokyo",
            mediaType = "VIDEO",
            genre = "Sci-Fi",
            category = "Animation",
            moodTags = listOf("Cyberpunk", "Neon", "Night")
        )

        val itemTwoTags = MediaItem(
            id = "item_2_tags",
            title = "Neo Tokyo Drive",
            mediaType = "VIDEO",
            genre = "Sci-Fi",
            category = "Animation",
            moodTags = listOf("Cyberpunk", "Neon")
        )

        val itemOneTag = MediaItem(
            id = "item_1_tag",
            title = "Cyber City",
            mediaType = "VIDEO",
            genre = "Sci-Fi",
            category = "Animation",
            moodTags = listOf("Cyberpunk")
        )

        repository.setMediaItemsForTesting(listOf(refItem, itemOneTag, itemTwoTags))

        val results = repository.getSimilarMedia(refItem)

        assertEquals("More matching metadata must yield higher rank", itemTwoTags.id, results.firstOrNull()?.id)
    }

    @Test
    fun testQualityThresholdExcludesWeakMatches() {
        val refItem = MediaItem(
            id = "ref_1",
            title = "Sunset at the Lake",
            mediaType = "VIDEO",
            genre = "Nature"
        )

        // Weak match: only one title token matches, different media type (score = 6)
        val weakMatch = MediaItem(
            id = "weak_1",
            title = "Lake in the Morning",
            mediaType = "PHOTO",
            genre = "Urban"
        )

        repository.setMediaItemsForTesting(listOf(refItem, weakMatch))

        val results = repository.getSimilarMedia(refItem)

        assertTrue("Weak matches (score < 8) should be excluded", results.isEmpty())
    }

    @Test
    fun testNoFallbackWhenNoMatchesFound() {
        val refItem = MediaItem(
            id = "ref_photo",
            title = "Unique Abstract Pattern",
            mediaType = "PHOTO",
            genre = "Abstract"
        )

        val unrelated = MediaItem(
            id = "unrelated_1",
            title = "Something Else",
            mediaType = "PHOTO",
            genre = "Sports"
        )

        repository.setMediaItemsForTesting(listOf(refItem, unrelated))

        val results = repository.getSimilarMedia(refItem)

        assertTrue("Should not fall back to unrelated same-type items", results.isEmpty())
    }

    @Test
    fun testReturnBetweenTenAndThirtyWhenGenuinelySimilar() {
        val refItem = MediaItem(id = "ref", title = "Title", genre = "Nature", mediaType = "VIDEO")
        
        // 15 genuinely similar items (same genre + same media type -> score 12)
        val similarCandidates = (1..15).map { i ->
            MediaItem(id = "sim_$i", title = "Sim $i", genre = "Nature", mediaType = "VIDEO")
        }

        repository.setMediaItemsForTesting(listOf(refItem) + similarCandidates)

        val results = repository.getSimilarMedia(refItem)
        assertEquals(15, results.size)
    }

    @Test
    fun testSeeSimilarIsolatedFromRecommendationEngine() {
        val refItem = MediaItem(
            id = "ref_1",
            title = "Classical Piano Sonata",
            mediaType = "AUDIO",
            genre = "Classical",
            category = "Music",
            moodTags = listOf("Piano", "Calm")
        )

        val candidate = MediaItem(
            id = "cand_1",
            title = "Classical Piano Concerto",
            mediaType = "AUDIO",
            genre = "Classical",
            category = "Music",
            moodTags = listOf("Piano")
        )

        repository.setMediaItemsForTesting(listOf(refItem, candidate))

        val results = repository.getSimilarMedia(refItem)

        assertTrue("See Similar should run standalone using reference item without altering recommendation engine state", results.contains(candidate))
    }
}
