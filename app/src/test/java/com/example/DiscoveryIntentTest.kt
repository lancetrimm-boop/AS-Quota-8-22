package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class DiscoveryIntentTest {

    @Test
    fun testIntentInterpretation_UnseenOnly() {
        val intent = DiscoveryIntentInterpreter.interpret("Find me something I haven't seen before.")
        assertEquals(IntentFocus.UNSEEN_ONLY, intent.focus)
    }

    @Test
    fun testIntentInterpretation_SimilarToFavorites() {
        val intent = DiscoveryIntentInterpreter.interpret("Show me something similar to my favorites.")
        assertEquals(IntentFocus.SIMILAR_TO_FAVORITES, intent.focus)
    }

    @Test
    fun testIntentInterpretation_SurpriseMe() {
        val intent = DiscoveryIntentInterpreter.interpret("Surprise me.")
        assertEquals(IntentFocus.SURPRISE_ME, intent.focus)
    }

    @Test
    fun testIntentInterpretation_CompletelyDifferent() {
        val intent = DiscoveryIntentInterpreter.interpret("Show me something completely different.")
        assertEquals(IntentFocus.COMPLETELY_DIFFERENT, intent.focus)
    }

    @Test
    fun testIntentInterpretation_HiddenCompatibility() {
        val intent = DiscoveryIntentInterpreter.interpret("Find something I would never pick but might love.")
        assertEquals(IntentFocus.HIDDEN_COMPATIBILITY, intent.focus)
    }

    @Test
    fun testIntentInterpretation_TasteExpansion() {
        val intent = DiscoveryIntentInterpreter.interpret("Help me discover a new style.")
        assertEquals(IntentFocus.TASTE_EXPANSION, intent.focus)
    }

    @Test
    fun testIntentInterpretation_FallbackToDefault() {
        val intent = DiscoveryIntentInterpreter.interpret("Just show me media.")
        assertEquals(IntentFocus.DEFAULT, intent.focus)
    }

    @Test
    fun testPolicyResolution_WithHiddenCompatibilityIntent() {
        val policy = DiscoveryPolicy(mode = DiscoveryMode.BALANCED)
        val intent = DiscoveryIntentInterpreter.interpret("Show me a hidden gem I'd love.")
        
        val strategy = DiscoveryPolicyManager.resolveStrategy(policy, intent)
        
        // HIDDEN_COMPATIBILITY boosts exploration (uncertainty) and exploitation (predicted match)
        assertTrue("Should increase exploration", strategy.explorationWeight > 0.4f)
        assertTrue("Should increase exploitation", strategy.exploitationWeight > 0.6f)
    }

    @Test
    fun testPolicyResolution_WithCompletelyDifferentIntent() {
        val policy = DiscoveryPolicy(mode = DiscoveryMode.PERSONALIZED)
        val intent = DiscoveryIntentInterpreter.interpret("Something completely different please.")
        
        // Use high-confidence system state to isolate intent logic from adaptive adjustments
        val systemState = SystemDiscoveryState(globalConfidence = 1.0f, libraryCoverage = 1.0f)
        val strategy = DiscoveryPolicyManager.resolveStrategy(policy, intent, systemState = systemState)
        
        // Base PERSONALIZED exploit is 0.8. Intent reduces it by 0.4.
        assertEquals(0.4f, strategy.exploitationWeight, 0.01f)
        // Base PERSONALIZED novelty is 0.1. Intent adds 0.6.
        assertEquals(0.7f, strategy.noveltyWeight, 0.01f)
    }
}
