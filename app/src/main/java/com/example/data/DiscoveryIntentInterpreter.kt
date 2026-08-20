package com.example.data

/**
 * Interprets natural language requests into structured UserIntent overrides.
 * This ensures that AI recommendations are governed by the centralized Discovery Policy Layer.
 */
object DiscoveryIntentInterpreter {

    /**
     * Maps a natural language string to a supported IntentFocus.
     * Uses a controlled taxonomy to maintain determinism.
     */
    fun interpret(request: String): UserIntent {
        val r = request.lowercase().trim()
        
        val focus = when {
            r.contains("haven't seen") || r.contains("unseen") || r.contains("new content") -> 
                IntentFocus.UNSEEN_ONLY
                
            r.contains("similar to") || r.contains("like my favorites") || r.contains("more of this") -> 
                IntentFocus.SIMILAR_TO_FAVORITES
                
            r.contains("surprise") || r.contains("random") -> 
                IntentFocus.SURPRISE_ME
                
            r.contains("completely different") || r.contains("something else") -> 
                IntentFocus.COMPLETELY_DIFFERENT
                
            r.contains("never pick") || r.contains("hidden") || r.contains("hidden gem") -> 
                IntentFocus.HIDDEN_COMPATIBILITY
                
            r.contains("new style") || r.contains("expand") || r.contains("discover") -> 
                IntentFocus.TASTE_EXPANSION
                
            r.contains("deep") || r.contains("learn") -> 
                IntentFocus.DEEP_DISCOVERY
                
            else -> IntentFocus.DEFAULT
        }

        // Mapping natural language to mode overrides where appropriate
        val modeOverride = when {
            r.contains("aggressive") || r.contains("explore more") -> DiscoveryMode.EXPLORATORY
            r.contains("safe") || r.contains("just play") -> DiscoveryMode.PERSONALIZED
            else -> null
        }

        return UserIntent(
            modeOverride = modeOverride,
            focus = focus
        )
    }
}
