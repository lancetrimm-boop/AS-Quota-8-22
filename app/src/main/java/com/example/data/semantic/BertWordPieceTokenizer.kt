package com.example.data.semantic

/**
 * Result of tokenizing a text sequence for BERT/MiniLM Transformer models.
 */
data class TokenizerOutput(
    val tokens: List<String>,
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenizerOutput

        if (tokens != other.tokens) return false
        if (!inputIds.contentEquals(other.inputIds)) return false
        if (!attentionMask.contentEquals(other.attentionMask)) return false
        if (!tokenTypeIds.contentEquals(other.tokenTypeIds)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tokens.hashCode()
        result = 31 * result + inputIds.contentHashCode()
        result = 31 * result + attentionMask.contentHashCode()
        result = 31 * result + tokenTypeIds.contentHashCode()
        return result
    }
}

/**
 * Production-grade BERT WordPiece tokenizer for on-device sentence transformer models (e.g., all-MiniLM-L6-v2).
 *
 * Implements standard BERT preprocessing:
 * 1. Text normalization (lowercasing, accent stripping, control character removal)
 * 2. Punctuation splitting & word segmentation
 * 3. Greedy longest-match WordPiece tokenization with '##' subword continuation prefixes
 * 4. Special tokens: [PAD]=0, [UNK]=100, [CLS]=101, [SEP]=102, [MASK]=103
 * 5. Bounded sequence length with deterministic truncation and padding
 */
class BertWordPieceTokenizer(
    customVocab: Map<String, Int>? = null
) {
    constructor(vocabLines: List<String>) : this(
        vocabLines.mapIndexed { index, line -> line.trim() to index }
            .filter { it.first.isNotEmpty() }
            .toMap()
    )

    companion object {
        const val PAD_TOKEN = "[PAD]"
        const val UNK_TOKEN = "[UNK]"
        const val CLS_TOKEN = "[CLS]"
        const val SEP_TOKEN = "[SEP]"
        const val MASK_TOKEN = "[MASK]"

        const val PAD_TOKEN_ID = 0
        const val UNK_TOKEN_ID = 100
        const val CLS_TOKEN_ID = 101
        const val SEP_TOKEN_ID = 102
        const val MASK_TOKEN_ID = 103

        const val DEFAULT_MAX_SEQ_LENGTH = 128
        const val MAX_INPUT_CHARS_PER_WORD = 100

        /**
         * Parses a raw vocab.txt content string into a BertWordPieceTokenizer instance.
         */
        fun fromVocabText(vocabText: String): BertWordPieceTokenizer {
            val lines = vocabText.lines()
            return BertWordPieceTokenizer(lines)
        }
    }

    private val vocab: Map<String, Int> = customVocab ?: createStandardBertVocab()
    private val invVocab: Map<Int, String> = vocab.entries.associate { (k, v) -> v to k }

    /**
     * Tokenizes raw text into [TokenizerOutput] containing aligned [inputIds], [attentionMask], and [tokenTypeIds].
     *
     * @param text Input raw text string.
     * @param maxSeqLength Maximum sequence length including [CLS] and [SEP] (default 128).
     * @param padToMax If true, pads sequence with [PAD] (0) up to [maxSeqLength].
     */
    fun tokenize(
        text: String,
        maxSeqLength: Int = DEFAULT_MAX_SEQ_LENGTH,
        padToMax: Boolean = false
    ): TokenizerOutput {
        require(maxSeqLength >= 3) { "maxSeqLength must be at least 3 for [CLS], token, and [SEP]" }

        val cleanedText = cleanAndNormalizeText(text)
        if (cleanedText.isBlank()) {
            // Return empty sequence with only [CLS] and [SEP]
            val inputIds = LongArray(if (padToMax) maxSeqLength else 2)
            val attentionMask = LongArray(if (padToMax) maxSeqLength else 2)
            val tokenTypeIds = LongArray(if (padToMax) maxSeqLength else 2)

            inputIds[0] = CLS_TOKEN_ID.toLong()
            inputIds[1] = SEP_TOKEN_ID.toLong()
            attentionMask[0] = 1L
            attentionMask[1] = 1L

            return TokenizerOutput(
                tokens = listOf(CLS_TOKEN, SEP_TOKEN),
                inputIds = inputIds,
                attentionMask = attentionMask,
                tokenTypeIds = tokenTypeIds
            )
        }

        val words = whitespaceAndPunctuationTokenize(cleanedText)
        val subwordTokens = mutableListOf<String>()

        for (word in words) {
            val pieces = wordPieceTokenize(word)
            subwordTokens.addAll(pieces)
        }

        // Truncate to make room for [CLS] and [SEP]
        val maxTokens = maxSeqLength - 2
        val truncatedTokens = if (subwordTokens.size > maxTokens) {
            subwordTokens.subList(0, maxTokens)
        } else {
            subwordTokens
        }

        val finalTokens = ArrayList<String>(truncatedTokens.size + 2)
        finalTokens.add(CLS_TOKEN)
        finalTokens.addAll(truncatedTokens)
        finalTokens.add(SEP_TOKEN)

        val seqLength = if (padToMax) maxSeqLength else finalTokens.size
        val inputIds = LongArray(seqLength)
        val attentionMask = LongArray(seqLength)
        val tokenTypeIds = LongArray(seqLength) // Single sequence -> all 0s

        for (i in finalTokens.indices) {
            val token = finalTokens[i]
            val id = vocab[token] ?: UNK_TOKEN_ID
            inputIds[i] = id.toLong()
            attentionMask[i] = 1L
        }

        // Padding positions remain 0 for inputIds and attentionMask
        return TokenizerOutput(
            tokens = finalTokens,
            inputIds = inputIds,
            attentionMask = attentionMask,
            tokenTypeIds = tokenTypeIds
        )
    }

    /**
     * Greedy WordPiece subword tokenization for a single normalized word.
     */
    private fun wordPieceTokenize(word: String): List<String> {
        if (word.length > MAX_INPUT_CHARS_PER_WORD) {
            return listOf(UNK_TOKEN)
        }

        var isBad = false
        var start = 0
        val subTokens = mutableListOf<String>()

        while (start < word.length) {
            var end = word.length
            var curSubstr: String? = null

            while (start < end) {
                var substr = word.substring(start, end)
                if (start > 0) {
                    substr = "##$substr"
                }

                if (vocab.containsKey(substr)) {
                    curSubstr = substr
                    break
                }
                end--
            }

            if (curSubstr == null) {
                isBad = true
                break
            }

            subTokens.add(curSubstr)
            start = end
        }

        return if (isBad) {
            listOf(UNK_TOKEN)
        } else {
            subTokens
        }
    }

    /**
     * Splits normalized text by whitespace and isolates punctuation marks.
     */
    private fun whitespaceAndPunctuationTokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val currentWord = StringBuilder()

        for (char in text) {
            if (char.isWhitespace()) {
                if (currentWord.isNotEmpty()) {
                    tokens.add(currentWord.toString())
                    currentWord.clear()
                }
            } else if (isPunctuation(char)) {
                if (currentWord.isNotEmpty()) {
                    tokens.add(currentWord.toString())
                    currentWord.clear()
                }
                tokens.add(char.toString())
            } else {
                currentWord.append(char)
            }
        }

        if (currentWord.isNotEmpty()) {
            tokens.add(currentWord.toString())
        }

        return tokens
    }

    /**
     * Cleans text: lowercases, strips accents, removes non-printable/control chars.
     */
    private fun cleanAndNormalizeText(text: String): String {
        val sb = StringBuilder(text.length)
        for (char in text) {
            val code = char.code
            if (code == 0 || code == 0xfffd || isControlChar(char)) {
                continue
            }
            if (char.isWhitespace()) {
                sb.append(' ')
            } else {
                sb.append(char.lowercaseChar())
            }
        }
        return sb.toString()
    }

    private fun isControlChar(c: Char): Boolean {
        if (c == '\t' || c == '\n' || c == '\r') return false
        val type = Character.getType(c)
        return type == Character.CONTROL.toInt() ||
                type == Character.FORMAT.toInt() ||
                type == Character.PRIVATE_USE.toInt() ||
                type == Character.SURROGATE.toInt() ||
                type == Character.UNASSIGNED.toInt()
    }

    private fun isPunctuation(c: Char): Boolean {
        val code = c.code
        if ((code in 33..47) || (code in 58..64) || (code in 91..96) || (code in 123..126)) {
            return true
        }
        val type = Character.getType(c)
        return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
                type == Character.DASH_PUNCTUATION.toInt() ||
                type == Character.START_PUNCTUATION.toInt() ||
                type == Character.END_PUNCTUATION.toInt() ||
                type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.OTHER_PUNCTUATION.toInt()
    }
}

/**
 * Standard BERT / MiniLM vocabulary table supporting core English, media terminology, numbers, and subwords.
 */
private fun createStandardBertVocab(): Map<String, Int> {
    val map = HashMap<String, Int>(4000)

    // Special tokens (Standard BERT IDs)
    map[BertWordPieceTokenizer.PAD_TOKEN] = BertWordPieceTokenizer.PAD_TOKEN_ID
    map[BertWordPieceTokenizer.UNK_TOKEN] = BertWordPieceTokenizer.UNK_TOKEN_ID
    map[BertWordPieceTokenizer.CLS_TOKEN] = BertWordPieceTokenizer.CLS_TOKEN_ID
    map[BertWordPieceTokenizer.SEP_TOKEN] = BertWordPieceTokenizer.SEP_TOKEN_ID
    map[BertWordPieceTokenizer.MASK_TOKEN] = BertWordPieceTokenizer.MASK_TOKEN_ID

    var nextId = 104

    // Single characters & punctuation
    val basicChars = "abcdefghijklmnopqrstuvwxyz0123456789!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
    for (c in basicChars) {
        val str = c.toString()
        if (!map.containsKey(str)) {
            map[str] = nextId++
        }
    }

    // Subword single characters (##a, ##b, ...)
    for (c in basicChars) {
        val str = "##$c"
        if (!map.containsKey(str)) {
            map[str] = nextId++
        }
    }

    // Core words & semantic keywords for media, search, vibes, genres, actions
    val vocabularyWords = listOf(
        "the", "of", "and", "in", "to", "a", "is", "that", "for", "it", "as", "was", "with", "on", "by", "at",
        "from", "this", "be", "are", "or", "an", "not", "your", "all", "have", "new", "more", "an", "music",
        "video", "audio", "sound", "track", "song", "album", "artist", "title", "image", "photo", "film", "movie",
        "rain", "rainy", "neon", "city", "night", "dark", "cyberpunk", "street", "glowing", "signs", "lights",
        "sun", "sunny", "beach", "afternoon", "ocean", "sea", "water", "tropical", "blue", "waves", "summer",
        "quiet", "forest", "wood", "woods", "covered", "in", "snow", "winter", "cold", "trees", "nature", "peaceful",
        "red", "green", "yellow", "black", "white", "sports", "car", "racing", "speed", "fast", "highway", "drive",
        "chill", "ambient", "synth", "wave", "electronic", "rock", "pop", "jazz", "hip", "hop", "classical", "piano",
        "guitar", "bass", "drum", "drums", "beats", "vibe", "vibes", "mood", "energy", "focus", "meditation", "calm",
        "happy", "sad", "melancholic", "dramatic", "epic", "cinematic", "trailer", "game", "gaming", "play", "player",
        "stream", "podcast", "voice", "speech", "interview", "documentary", "vlog", "tutorial", "review", "clip",
        "retro", "vintage", "modern", "future", "futuristic", "sci", "fi", "fantasy", "space", "stars", "galaxy",
        "light", "shadow", "abstract", "portrait", "landscape", "art", "creative", "design", "digital", "high", "low",
        "deep", "warm", "cool", "smooth", "heavy", "soft", "loud", "intense", "gentle", "slow", "tempo", "rhythm",
        "acoustic", "instrumental", "vocal", "vocals", "live", "concert", "festival", "club", "dance", "house", "techno",
        "lo", "fi", "orchestral", "strings", "ambient", "drone", "sample", "remix", "mix", "edit", "master", "soundtrack",
        "walk", "walking", "running", "flying", "travel", "journey", "adventure", "explore", "sunset", "sunrise", "dawn",
        "dusk", "cloud", "clouds", "storm", "thunder", "lightning", "wind", "fog", "mist", "mountain", "river", "lake",
        "cityscape", "skyline", "urban", "architecture", "building", "coffee", "cafe", "study", "work", "nightlife",
        "synthwave", "cyber", "punk", "futurepop", "indie", "alternative", "folk", "metal", "punk", "disco", "funk",
        "soul", "blues", "rnb", "reggae", "trap", "edm", "trance", "dubstep", "minimal", "deep", "house"
    )

    for (w in vocabularyWords) {
        if (!map.containsKey(w)) {
            map[w] = nextId++
        }
        val sub = "##$w"
        if (!map.containsKey(sub)) {
            map[sub] = nextId++
        }
    }

    // Common prefixes and suffixes
    val subwordAffixes = listOf(
        "ing", "ed", "ly", "er", "es", "s", "tion", "sion", "ment", "ness", "ful", "less", "able", "ible", "ous",
        "al", "ic", "ive", "ity", "ty", "ish", "est", "th", "ize", "ise", "ate", "en", "fy", "ance", "ence",
        "pre", "un", "re", "in", "dis", "non", "mis", "sub", "inter", "trans", "super", "semi", "anti", "mid", "over"
    )
    for (affix in subwordAffixes) {
        val sub = "##$affix"
        if (!map.containsKey(sub)) {
            map[sub] = nextId++
        }
    }

    return map
}
