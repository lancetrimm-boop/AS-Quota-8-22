package com.example.data.intelligence

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaItem
import com.example.data.TasteDNA
import com.example.data.db.AuraDatabase
import com.example.data.db.MediaEntity
import com.example.data.db.PairwiseOutcomeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IntelligenceReportingTest {

    private lateinit var database: AuraDatabase
    private lateinit var engine: IntelligenceReportingEngine

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AuraDatabase::class.java
        ).allowMainThreadQueries().build()
        
        engine = IntelligenceReportingEngine(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testGenerateSnapshotReport_Accuracy() = runBlocking {
        // 1. Setup Data
        val dna = TasteDNA(vibrancy = 0.8, learnedVibrancy = 0.8, isFineTuningEnabled = true) 
        
        // Add some media and pairwise outcomes
        database.mediaDao().insert(MediaEntity(id = "m1", title = "Test", mediaType = "PHOTO", playCount = 5, exposureCount = 10, moodTagsJson = "vibrant,dramatic"))
        repeat(15) { i ->
            database.pairwiseDao().insertOutcome(PairwiseOutcomeEntity(
                optionAId = "m1", 
                optionBId = "m2", 
                chosenId = "m1", 
                roundNumber = i,
                outcomeType = "VOTE",
                timestamp = System.currentTimeMillis()
            ))
        }

        // 2. Generate Report
        val report = engine.generateSnapshotReport(dna)

        // 3. Verify Versioning
        assertEquals(1, report.schemaVersion)
        assertEquals(1, report.maturity.schemaVersion)

        // 4. Verify Maturity (15 votes -> LEARNING)
        assertEquals(CalibrationStatus.LEARNING, report.maturity.calibrationStatus)
        assertEquals(15, report.maturity.pairwiseComparisonsCompleted)
        assertTrue(report.maturity.personalizationConfidence > 0.3)
        
        // 5. Verify Coverage
        // m1 has exposureCount=10, so it's counted in coverage. library has 1 item. coverage=1.0
        assertEquals(1.0, report.maturity.dataCoverage, 0.01)

        // 6. Verify Engagement (5 views / 10 exposures -> 0.5)
        assertEquals(0.5, report.engagement.completionRate, 0.01)

        // 7. Verify Taste Profile
        assertEquals(0.8, report.tasteProfile.dimensions["Vibrancy"]!!, 0.01)
        assertTrue(report.tasteProfile.topTraits.contains("Strong Vibrancy"))
    }

    @Test
    fun testTasteClusters_EvidenceSelection() = runBlocking {
        // Setup a DNA with strong cinematic preference
        val cinematicDNA = TasteDNA(
            depth = 0.9,
            lighting = 0.9,
            contrast = 0.9,
            dynamicRange = 0.9
        )
        
        // Add a matching media item with high engagement
        database.mediaDao().insert(MediaEntity(
            id = "cinematic_item",
            title = "Cinematic Shot",
            mediaType = "PHOTO",
            moodTagsJson = "cinematic,dramatic,depth",
            isFavorite = true,
            playCount = 10
        ))
        
        // Add an unrelated media item
        database.mediaDao().insert(MediaEntity(
            id = "unrelated_item",
            title = "Simple Shot",
            mediaType = "PHOTO",
            moodTagsJson = "simple,flat",
            isFavorite = false
        ) )

        val report = engine.generateSnapshotReport(cinematicDNA)
        val clusters = report.tasteProfile.tasteClusters
        
        // Verify cluster generation
        assertTrue(clusters.isNotEmpty())
        val cinematicCluster = clusters.find { it.categoryId == "cinematic" }
        assertNotNull(cinematicCluster)
        
        // Verify evidence selection
        assertEquals("cinematic_item", cinematicCluster?.representativeMediaId)
        assertTrue(cinematicCluster!!.strengthScore >= 0.8)
        assertEquals("Strong", cinematicCluster.strengthLabel)
        assertEquals("High", cinematicCluster.confidenceLabel)
        assertFalse(cinematicCluster.isVideo)
        
        // Verify dynamic description
        assertTrue(cinematicCluster.description.contains("Aura detected strong preference for"))
        assertTrue(cinematicCluster.description.contains("depth"))
        assertTrue(cinematicCluster.description.contains("lighting"))
    }

    @Test
    fun testMinimalAndCleanCategory() = runBlocking {
        val dna = TasteDNA(minimalism = 0.9)
        val report = engine.generateSnapshotReport(dna)
        val minimal = report.tasteProfile.tasteClusters.find { it.categoryId == "minimal" }
        
        assertNotNull(minimal)
        assertEquals("Minimal & Clean", minimal?.title)
    }

    @Test
    fun testEvidenceDeduplication() = runBlocking {
        val dna = TasteDNA(vibrancy = 0.9, depth = 0.9)
        
        // Add only one item that matches both
        database.mediaDao().insert(MediaEntity(
            id = "perfect_match",
            title = "Perfect Match",
            mediaType = "PHOTO",
            moodTagsJson = "vibrant,cinematic,depth",
            playCount = 10
        ))
        
        // Add another item that is a weaker match for vibrant
        database.mediaDao().insert(MediaEntity(
            id = "weak_match",
            title = "Weak Match",
            mediaType = "PHOTO",
            moodTagsJson = "vibrant",
            playCount = 1
        ))

        val report = engine.generateSnapshotReport(dna)
        val clusters = report.tasteProfile.tasteClusters
        
        val cinematic = clusters.find { it.categoryId == "cinematic" }
        val vibrant = clusters.find { it.categoryId == "vibrant" }
        
        // Since library size is 2 (< 6), reuse is allowed in the current logic.
        // Wait, if library size < 6, it filters: `it.id !in usedIds || library.size < 6`
        // So they might both pick "perfect_match".
        
        // Let's test with library size >= 6 to ensure deduplication.
        repeat(5) { i ->
            database.mediaDao().insert(MediaEntity(id = "other_$i", title = "Other", mediaType = "PHOTO"))
        }
        
        val report2 = engine.generateSnapshotReport(dna)
        val clusters2 = report2.tasteProfile.tasteClusters
        
        val cinematic2 = clusters2.find { it.categoryId == "cinematic" }
        val vibrant2 = clusters2.find { it.categoryId == "vibrant" }
        
        assertNotEquals(cinematic2?.representativeMediaId, vibrant2?.representativeMediaId)
    }

    @Test
    fun testEmptyLibrary_NoEvidence() = runBlocking {
        val dna = TasteDNA(vibrancy = 0.9)
        val report = engine.generateSnapshotReport(dna)
        
        // With strict evidence requirement, no cluster should be added if library is empty
        val vibrantCluster = report.tasteProfile.tasteClusters.find { it.categoryId == "vibrant" }
        assertNull("Cluster without evidence must not be present", vibrantCluster)
        assertTrue(report.tasteProfile.tasteClusters.isEmpty())
    }

    @Test
    fun testClusterFiltering_MissingThumbnail() = runBlocking {
        val dna = TasteDNA(vibrancy = 0.9)
        
        // Add item that matches BUT HAS NO THUMBNAIL URL
        database.mediaDao().insert(MediaEntity(
            id = "no_thumb",
            title = "No Thumbnail",
            mediaType = "PHOTO",
            moodTagsJson = "vibrant",
            imageUrl = "" // Empty URL
        ))

        val report = engine.generateSnapshotReport(dna)
        assertTrue("Cluster with empty thumbnail URL must be filtered out", report.tasteProfile.tasteClusters.isEmpty())
    }

    @Test
    fun testRecommendationInsight_Provenance() = runBlocking {
        val dna = TasteDNA(vibrancy = 0.8)
        val item = MediaItem(
            id = "test_item",
            title = "Vibrant Sunset",
            mediaType = "PHOTO",
            moodTags = listOf("vibrant", "dramatic")
        )

        val insight = engine.generateRecommendationInsight(item, dna)

        assertEquals("test_item", insight.mediaId)
        assertTrue(insight.contributingFactors.any { it.label.contains("Vibrancy") })
        assertEquals("Source: Local Taste DNA & 2 Visual Signals", insight.provenance)
    }

    @Test
    fun testPrivacy_NoPIIInReport() = runBlocking {
        val dna = TasteDNA()
        database.mediaDao().insert(MediaEntity(
            id = "m1", 
            title = "PRIVATE_TITLE_SHOULD_NOT_LEAK", 
            mediaType = "PHOTO",
            uriPath = "/local/path/to/file.jpg"
        ))

        val report = engine.generateSnapshotReport(dna)
        val insight = engine.generateRecommendationInsight(MediaItem(id = "m1", title = "PRIVATE_TITLE", mediaType = "PHOTO"), dna)

        // Verify no local paths or raw titles in snapshots
        val reportString = report.toString()
        assertFalse(reportString.contains("PRIVATE_TITLE"))
        assertFalse(reportString.contains("/local/path"))
        
        val insightString = insight.toString()
        assertFalse(insightString.contains("PRIVATE_TITLE"))
    }

    @Test
    fun testGenerateVisualDescription_Quality() = runBlocking {
        // 1. Test strong traits mapping
        val strongDNA = TasteDNA(
            vibrancy = 0.9,
            saturation = 0.9,
            contrast = 0.9
        )
        
        // Mock data to ensure topTraits is populated
        database.mediaDao().insert(MediaEntity(id = "m1", title = "Test", mediaType = "PHOTO", moodTagsJson = "vibrant"))
        repeat(11) { i ->
            database.pairwiseDao().insertOutcome(PairwiseOutcomeEntity(
                optionAId = "m1", optionBId = "m2", chosenId = "m1", roundNumber = i
            ))
        }

        val report = engine.generateSnapshotReport(strongDNA)
        val description = report.tasteProfile.description

        assertTrue("Description should mention vivid/high-energy palettes", 
            description.contains("vivid, high-energy color palettes"))
        assertTrue("Description should mention dramatic, high-contrast visuals", 
            description.contains("dramatic, high-contrast visuals"))
        assertFalse("Description should not be empty", description.isBlank())

        // 2. Test balanced/versatile profile
        val balancedDNA = TasteDNA() // All 0.5
        val balancedReport = engine.generateSnapshotReport(balancedDNA)
        val balancedDesc = balancedReport.tasteProfile.description

        assertTrue("Balanced DNA should produce versatile description", 
            balancedDesc.contains("versatile and balanced"))
    }
}
