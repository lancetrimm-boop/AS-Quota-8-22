package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class DiscoveryPolicyTest {

    @Test
    fun testPersonalizedPolicy_HighExploitation() {
        val policy = DiscoveryPolicy(mode = DiscoveryMode.PERSONALIZED)
        // High confidence scenario
        val systemState = SystemDiscoveryState(globalConfidence = 1.0f)
        val strategy = DiscoveryPolicyManager.resolveStrategy(policy, systemState = systemState)
        
        assertTrue("Exploitation should be high (was ${strategy.exploitationWeight})", strategy.exploitationWeight >= 0.7f)
        assertTrue("Exploration should be low", strategy.explorationWeight <= 0.2f)
    }

    @Test
    fun testExploratoryPolicy_HighExploration() {
        val policy = DiscoveryPolicy(mode = DiscoveryMode.EXPLORATORY)
        val systemState = SystemDiscoveryState(globalConfidence = 1.0f)
        val strategy = DiscoveryPolicyManager.resolveStrategy(policy, systemState = systemState)
        
        assertTrue("Exploration should be high", strategy.explorationWeight >= 0.4f)
        assertTrue("Exploitation should be low", strategy.exploitationWeight <= 0.3f)
    }

    @Test
    fun testTemporaryIntent_OverridesGlobalPolicy() {
        val policy = DiscoveryPolicy(mode = DiscoveryMode.PERSONALIZED)
        val intent = UserIntent(modeOverride = DiscoveryMode.EXPLORATORY)
        val strategy = DiscoveryPolicyManager.resolveStrategy(policy, intent)
        
        // Should behave like EXPLORATORY despite global PERSONALIZED
        assertTrue("Intent override should increase exploration", strategy.explorationWeight >= 0.4f)
    }

    @Test
    fun testSurpriseIntent_HighNovelty() {
        val policy = DiscoveryPolicy(mode = DiscoveryMode.BALANCED)
        val intent = UserIntent(focus = IntentFocus.SURPRISE_ME)
        val strategy = DiscoveryPolicyManager.resolveStrategy(policy, intent)
        
        assertTrue("Surprise intent should boost novelty", strategy.noveltyWeight >= 0.5f)
    }

    @Test
    fun testLowConfidence_AdaptiveExploration() {
        val policy = DiscoveryPolicy(mode = DiscoveryMode.PERSONALIZED)
        // System is unsure about user (e.g., cold start)
        val systemState = SystemDiscoveryState(globalConfidence = 0.1f)
        val strategy = DiscoveryPolicyManager.resolveStrategy(policy, systemState = systemState)
        
        assertTrue("Low confidence should adaptive-boost exploration", strategy.explorationWeight > 0.3f)
    }

    @Test
    fun testLowLibraryCoverage_BoostsNovelty() {
        val policy = DiscoveryPolicy(mode = DiscoveryMode.PERSONALIZED)
        val systemState = SystemDiscoveryState(libraryCoverage = 0.05f)
        val strategy = DiscoveryPolicyManager.resolveStrategy(policy, systemState = systemState)
        
        // Base novelty for PERSONALIZED is 0.1, should be boosted
        assertTrue("Low library coverage should boost novelty", strategy.noveltyWeight > 0.1f)
    }

    @Test
    fun testHighRepetition_BoostsDiversityAndPenalty() {
        val policy = DiscoveryPolicy(mode = DiscoveryMode.BALANCED)
        val systemState = SystemDiscoveryState(repetitionRate = 0.8f)
        val strategy = DiscoveryPolicyManager.resolveStrategy(policy, systemState = systemState)
        
        // Base penalty for BALANCED is 0.3
        assertTrue("High repetition should boost familiarity penalty", strategy.familiarityPenalty > 0.3f)
        // Base diversity for BALANCED is 0.4
        assertTrue("High repetition should boost diversity weight", strategy.diversityWeight > 0.4f)
    }

    @Test
    fun testConfidenceEngine_CalculatesCorrectState() {
        val items = listOf(
            MediaItem(id = "1", title = "T1", mediaType = "PHOTO", viewCount = 1, rating = 4f),
            MediaItem(id = "2", title = "T2", mediaType = "PHOTO", viewCount = 0, rating = 0f)
        )
        val stats = IntelligenceStats(personalizationScore = 50, totalComparisons = 10)
        
        val state = ConfidenceEngine.calculateDiscoveryState(items, stats)
        
        assertEquals(0.5f, state.libraryCoverage, 0.01f)
        assertEquals(0.5f, state.ratingCoverage, 0.01f)
        // personalization (0.5 * 0.4) + rating (0.5 * 0.4) + comparison (0.1 * 0.2) = 0.2 + 0.2 + 0.02 = 0.42
        assertEquals(0.42f, state.globalConfidence, 0.01f)
    }
}
