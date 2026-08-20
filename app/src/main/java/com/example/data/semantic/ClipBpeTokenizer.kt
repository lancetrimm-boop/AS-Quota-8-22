package com.example.data.semantic

import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Android-compatible implementation of the CLIP BPE Tokenizer.
 *
 * Requirements:
 * - Vocabulary from mobileclip_vocab.json
 * - Merges from mobileclip_merges.txt
 * - Standard CLIP byte-level encoding
 * - Max sequence length 77
 * - SOT and EOT padding
 */
class ClipBpeTokenizer(
    private val vocab: Map<String, Int>,
    merges: List<Pair<String, String>>
) {
    private val bpeRanks: Map<Pair<String, String>, Int> = merges.withIndex().associate { it.value to it.index }
    private val byteEncoder: Map<Int, Char> = bytesToUnicode()
    private val cache = mutableMapOf<String, List<String>>()
    
    // Pattern for CLIP pre-tokenization
    private val pattern = Pattern.compile("<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]+|[^\\s\\p{L}\\p{N}]+", Pattern.CASE_INSENSITIVE)

    companion object {
        const val MAX_SEQ_LENGTH = 77
        const val SOT_TOKEN = "<start_of_text>"
        const val EOT_TOKEN = "<end_of_text>"

        /**
         * Standard CLIP byte-to-unicode mapping.
         */
        private fun bytesToUnicode(): Map<Int, Char> {
            val bs = mutableListOf<Int>()
            for (b in '!'.code..'~'.code) bs.add(b)
            for (b in '¡'.code..'¬'.code) bs.add(b)
            for (b in '®'.code..'ÿ'.code) bs.add(b)
            
            val cs = bs.map { it.toChar() }.toMutableList()
            var n = 0
            for (b in 0..255) {
                if (b !in bs) {
                    bs.add(b)
                    cs.add((256 + n).toChar())
                    n++
                }
            }
            return bs.zip(cs).toMap()
        }

        /**
         * Factory method to create a tokenizer from raw asset content.
         */
        fun fromAssets(vocabJson: String, mergesText: String): ClipBpeTokenizer {
            // Simple manual parse for a flat Map<String, Int> to avoid org.json JVM mocking issues
            val vocabMap = mutableMapOf<String, Int>()
            val entryPattern = Pattern.compile("\"((?:\\\\\"|[^\"])+)\":\\s*(\\d+)")
            val matcher = entryPattern.matcher(vocabJson)
            while (matcher.find()) {
                val key = matcher.group(1)
                val value = matcher.group(2).toInt()
                val unescapedKey = key.replace("\\\"", "\"")
                vocabMap[unescapedKey] = value
            }

            val mergesList = mergesText.lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val parts = line.split(" ")
                    Pair(parts[0], parts[1])
                }

            return ClipBpeTokenizer(vocabMap, mergesList)
        }
    }

    /**
     * Tokenizes text and returns the 77-token long input IDs required by CLIP.
     */
    fun tokenize(text: String): LongArray {
        val tokens = mutableListOf<Int>()
        tokens.add(vocab[SOT_TOKEN] ?: throw IllegalStateException("SOT token missing"))

        val matcher = pattern.matcher(text.lowercase())
        val words = mutableListOf<String>()
        while (matcher.find()) {
            words.add(matcher.group())
        }

        for (word in words) {
            // Encode word into byte-mapped string
            val encodedWord = word.toByteArray(Charsets.UTF_8).map { byteEncoder[it.toInt() and 0xFF]!! }.joinToString("")
            val bpeTokens = bpe(encodedWord)
            for (token in bpeTokens) {
                vocab[token]?.let { tokens.add(it) }
            }
        }

        // Add EOT token
        val eotId = vocab[EOT_TOKEN] ?: throw IllegalStateException("EOT token missing")
        if (tokens.size < MAX_SEQ_LENGTH) {
            tokens.add(eotId)
        } else {
            // Truncate if necessary, but EOT must be at 76
            tokens[MAX_SEQ_LENGTH - 1] = eotId
        }

        // Pad with 0s (Standard CLIP pads with 0)
        val result = LongArray(MAX_SEQ_LENGTH)
        for (i in 0 until minOf(tokens.size, MAX_SEQ_LENGTH)) {
            result[i] = tokens[i].toLong()
        }
        
        return result
    }

    private fun bpe(token: String): List<String> {
        if (token in cache) return cache[token]!!
        
        // Initial split into characters (with end-of-word marked if appropriate? No, CLIP marks with </w>)
        // Wait, MobileCLIP vocab seems to use </w> as suffix for tokens that end a word.
        // The standard CLIP logic is: split into characters, and the LAST character gets the </w> suffix.
        
        var word = token.map { it.toString() }.toMutableList()
        word[word.size - 1] = word.last() + "</w>"
        
        var pairs = getPairs(word)
        if (pairs.isEmpty()) {
            return listOf(word.joinToString(""))
        }

        while (true) {
            val bigram = pairs.minByOrNull { bpeRanks[it] ?: Int.MAX_VALUE } ?: break
            if (bigram !in bpeRanks) break

            val first = bigram.first
            val second = bigram.second
            val newWord = mutableListOf<String>()
            var i = 0
            while (i < word.size) {
                var found = false
                for (j in i until word.size - 1) {
                    if (word[j] == first && word[j + 1] == second) {
                        newWord.addAll(word.subList(i, j))
                        newWord.add(first + second)
                        i = j + 2
                        found = true
                        break
                    }
                }
                if (!found) {
                    newWord.addAll(word.subList(i, word.size))
                    break
                }
            }
            word = newWord
            if (word.size == 1) break
            pairs = getPairs(word)
        }
        
        cache[token] = word
        return word
    }

    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        var prevChar = word[0]
        for (i in 1 until word.size) {
            val char = word[i]
            pairs.add(Pair(prevChar, char))
            prevChar = char
        }
        return pairs
    }
}
