package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class SocialDiscoveryTest {

    private val tasteDNA = TasteDNA()
    private val stats = IntelligenceStats()

    private val externalItem = MediaItem(
        id = "social_1",
        title = "External Viral Hit",
        mediaType = "VIDEO",
        genre = "Trends",
        sourcePlatform = "TIKTOK",
        creatorId = "creator_42",
        creatorName = "ViralCreator",
        viewCount = 5,
        rating = 0f
    )

    @Test
    fun testExplorationEngine_WithCreatorAffinity() {
        val creatorId = "creator_42"
        val creatorProfiles = mapOf(
            creatorId to CreatorProfile(id = creatorId, name = "ViralCreator", platform = "TIKTOK", affinityScore = 0.9)
        )
        
        val evidence = ExplorationEngine.calculateEvidence(
            externalItem, 
            tasteDNA, 
            stats, 
            creatorProfiles
        )
        
        assertTrue("Creator affinity should boost exploitation score", evidence.exploitationScore > 0.5f)
        assertEquals(0.9f, evidence.creatorAffinityScore, 0.01f)
    }

    @Test
    fun testExplorationEngine_WithUnknownCreator_BoostsNovelty() {
        val creatorProfiles = emptyMap<String, CreatorProfile>()
        
        val evidence = ExplorationEngine.calculateEvidence(
            externalItem, 
            tasteDNA, 
            stats, 
            creatorProfiles
        )
        
        assertTrue("Unknown creator should boost novelty score", evidence.noveltyScore > 0.5f)
        assertEquals(0.6f, evidence.creatorNoveltyScore, 0.01f)
    }

    @Test
    fun testSocialDiscoveryManager_DetectsEmergingPreference() {
        // Content that doesn't align with current DNA but has high engagement
        val nonAlignedHighEngagementItem = externalItem.copy(
            moodTags = listOf("minimalist"), // Assume DNA is maximalist
            viewCount = 10
        )
        
        val event = SocialDiscoveryManager.processInteraction(
            nonAlignedHighEngagementItem,
            tasteDNA,
            SocialDiscoveryState()
        )
        
        assertNotNull("Should detect emerging preference for high engagement non-aligned content", event)
        assertEquals("CREATOR", event!!.type)
        assertEquals("creator_42", event.identifier)
    }
}
