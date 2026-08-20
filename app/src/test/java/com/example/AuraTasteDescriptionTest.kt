package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.TasteDNA
import com.example.data.db.AuraDatabase
import com.example.data.intelligence.IntelligenceReportingEngine
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
class AuraTasteDescriptionTest {

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
    fun testVividStyleDescription() = runBlocking {
        val dna = TasteDNA(
            vibrancy = 0.8,
            saturation = 0.8,
            isFineTuningEnabled = false
        )
        val report = engine.generateSnapshotReport(dna)
        val desc = report.tasteProfile.description
        
        assertTrue("Should mention vivid color", desc.contains("vivid") || desc.contains("energy"))
        assertTrue("Should mention color palettes", desc.contains("color palettes"))
    }

    @Test
    fun testMinimalistStyleDescription() = runBlocking {
        val dna = TasteDNA(
            minimalism = 0.9,
            isFineTuningEnabled = false
        )
        val report = engine.generateSnapshotReport(dna)
        val desc = report.tasteProfile.description
        
        assertTrue("Should mention minimalist", desc.contains("minimalist"))
    }

    @Test
    fun testMutedDynamicDescription() = runBlocking {
        val dna = TasteDNA(
            vibrancy = 0.2,
            saturation = 0.2,
            motion = 0.8,
            isFineTuningEnabled = false
        )
        val report = engine.generateSnapshotReport(dna)
        val desc = report.tasteProfile.description
        
        assertTrue("Should mention muted", desc.contains("muted"))
        assertTrue("Should mention dynamic motion", desc.contains("dynamic") && desc.contains("motion"))
    }

    @Test
    fun testLearningStateDescription() = runBlocking {
        val dna = TasteDNA() // All 0.5
        val report = engine.generateSnapshotReport(dna)
        val desc = report.tasteProfile.description
        
        assertTrue("Should mention learning", desc.contains("learning"))
    }
}
