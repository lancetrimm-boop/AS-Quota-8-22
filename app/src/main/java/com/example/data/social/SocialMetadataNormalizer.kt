package com.example.data.social

import java.util.Locale

/**
 * Normalizes unstructured external video metadata (titles, descriptions, hashtags, tags)
 * into clean, deduplicated vocabulary tokens compatible with [com.example.data.PersonalizationTraitMapper].
 */
object SocialMetadataNormalizer {

    // Common stop words to exclude from tag tokenization
    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "up", "about", "into", "over", "after",
        "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
        "do", "does", "did", "will", "would", "shall", "should", "can", "could",
        "may", "might", "must", "you", "your", "they", "their", "this", "that",
        "these", "those", "how", "what", "when", "where", "which", "who", "why",
        "video", "watch", "official", "full", "hd", "part", "episode", "season",
        "channel", "subscribe", "like", "comment", "share", "new", "top", "best",
        "click", "here", "vs", "versus"
    )

    // Domain synonym dictionary bridging social/video vernacular to Aura's 24 aesthetic traits
    private val SYNONYM_MAP = mapOf(
        "cyberpunk" to listOf("cinematic", "lighting", "contrast", "dramatic"),
        "neon" to listOf("vibrant", "bright", "lighting"),
        "synth" to listOf("retro", "mood", "nostalgic"),
        "ambient" to listOf("calm", "serene", "relaxed", "minimalist"),
        "modular" to listOf("complex", "intricate"),
        "analog" to listOf("warm", "vintage", "grain"),
        "film" to listOf("grain", "warm", "cinematic"),
        "softlight" to listOf("soft", "lighting"),
        "moody" to listOf("mood", "dramatic", "dark"),
        "night" to listOf("dark", "lighting"),
        "rgb" to listOf("vibrant", "saturation", "bright"),
        "gaming" to listOf("dynamic", "intense"),
        "restrained" to listOf("minimalist", "calm"),
        "clean" to listOf("minimalist", "polished"),
        "atmospheric" to listOf("mood", "depth", "cinematic"),
        "photorealistic" to listOf("sharp", "natural", "real"),
        "4k" to listOf("sharp", "crisp"),
        "lofi" to listOf("relaxed", "grain", "warm", "calm"),
        "chill" to listOf("relaxed", "calm", "serene"),
        "fast" to listOf("motion", "dynamic", "action"),
        "slow" to listOf("calm", "harmony"),
        "nature" to listOf("natural", "organic", "harmony"),
        "landscape" to listOf("spacious", "depth", "natural"),
        "portrait" to listOf("dominant", "close-up", "focus"),
        "golden" to listOf("warm", "bright", "lighting"),
        "sunset" to listOf("warm", "vibrant", "mood"),
        "monochrome" to listOf("muted", "contrast", "minimalist"),
        "blackandwhite" to listOf("muted", "contrast", "classic"),
        "noir" to listOf("dark", "dramatic", "contrast")
    )

    /**
     * Extracts and normalizes tokens from all candidate metadata fields.
     */
    fun normalize(
        title: String,
        description: String = "",
        rawTags: List<String> = emptyList()
    ): List<String> {
        val result = LinkedHashSet<String>()

        // 1. Process explicit raw tags first (highest precision)
        rawTags.forEach { rawTag ->
            val cleanTags = extractTokens(rawTag)
            result.addAll(cleanTags)
        }

        // 2. Process title (high aesthetic signal)
        val titleTokens = extractTokens(title)
        result.addAll(titleTokens)

        // 3. Process snippet/description (lower weight, take first 50 words to avoid garbage inflation)
        val firstDescriptionSlice = description.take(300)
        val descTokens = extractTokens(firstDescriptionSlice)
        // Only take up to 8 tokens from description to prevent noise overwhelming the title
        result.addAll(descTokens.take(8))

        return result.toList()
    }

    /**
     * Splits a text fragment, strips hashtags/punctuation/numbers, applies synonyms, and filters stopwords.
     */
    fun extractTokens(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val tokens = mutableListOf<String>()
        
        // Remove URLs and email patterns if any
        val cleanText = text
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[^a-zA-Z0-9#\\-\\s]"), " ")

        // Split on whitespace, commas, dashes, underscores
        val rawWords = cleanText.split(Regex("[\\s,_/]+"))

        for (word in rawWords) {
            // Strip leading hashtags (#neon -> neon) and hyphens
            var token = word.trim().lowercase(Locale.ROOT)
            while (token.startsWith("#")) {
                token = token.removePrefix("#")
            }
            token = token.trim('-', '_', '.')

            if (token.length < 3) continue // Skip single/double letter noise
            if (token.all { it.isDigit() }) continue // Skip pure numbers (e.g., "10", "2024")
            if (STOP_WORDS.contains(token)) continue // Skip common stopwords

            // Check synonym expansion
            val mappedSynonyms = SYNONYM_MAP[token]
            if (mappedSynonyms != null) {
                tokens.addAll(mappedSynonyms)
            } else {
                tokens.add(token)
            }
        }

        return tokens.distinct()
    }
}
