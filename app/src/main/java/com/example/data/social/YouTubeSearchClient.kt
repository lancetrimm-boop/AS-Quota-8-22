package com.example.data.social

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// -------------------------------------------------------------------------
// Retrofit API Definition
// -------------------------------------------------------------------------

interface YouTubeApiService {
    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 25,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}

// -------------------------------------------------------------------------
// Moshi Response Models
// -------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class YouTubeSearchResponse(
    @Json(name = "items") val items: List<YouTubeSearchItem> = emptyList(),
    @Json(name = "nextPageToken") val nextPageToken: String? = null,
    @Json(name = "pageInfo") val pageInfo: YouTubePageInfo? = null
)

@JsonClass(generateAdapter = true)
data class YouTubePageInfo(
    @Json(name = "totalResults") val totalResults: Int = 0,
    @Json(name = "resultsPerPage") val resultsPerPage: Int = 0
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchItem(
    @Json(name = "id") val id: YouTubeResourceId? = null,
    @Json(name = "snippet") val snippet: YouTubeSnippet? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeResourceId(
    @Json(name = "kind") val kind: String? = null,
    @Json(name = "videoId") val videoId: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSnippet(
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String = "",
    @Json(name = "channelTitle") val channelTitle: String = "",
    @Json(name = "channelId") val channelId: String = "",
    @Json(name = "publishedAt") val publishedAt: String = "",
    @Json(name = "thumbnails") val thumbnails: YouTubeThumbnails? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnails(
    @Json(name = "default") val default: YouTubeThumbnail? = null,
    @Json(name = "medium") val medium: YouTubeThumbnail? = null,
    @Json(name = "high") val high: YouTubeThumbnail? = null,
    @Json(name = "standard") val standard: YouTubeThumbnail? = null,
    @Json(name = "maxres") val maxres: YouTubeThumbnail? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnail(
    @Json(name = "url") val url: String = "",
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null
)

// -------------------------------------------------------------------------
// Client Orchestrator
// -------------------------------------------------------------------------

object YouTubeSearchClient {

    private const val BASE_URL = "https://www.googleapis.com/"
    private const val TAG = "YouTubeSearchClient"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    private val apiService: YouTubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YouTubeApiService::class.java)
    }

    /**
     * Executes YouTube Data API v3 search for video candidates.
     */
    suspend fun searchVideos(
        query: String,
        apiKey: String,
        maxResults: Int = 25
    ): Result<List<SocialCandidateDto>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("YouTube Data API key is missing. Set YOUTUBE_API_KEY or use Mock Benchmark mode.")
            )
        }

        if (query.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Search query cannot be empty.")
            )
        }

        try {
            val response = apiService.searchVideos(
                query = query,
                apiKey = apiKey.trim(),
                maxResults = maxResults.coerceIn(1, 50)
            )

            val candidates = mutableListOf<SocialCandidateDto>()
            var rank = 1

            for (item in response.items) {
                val videoId = item.id?.videoId
                val snippet = item.snippet
                if (videoId.isNullOrBlank() || snippet == null) continue

                val thumbnail = snippet.thumbnails?.high?.url
                    ?: snippet.thumbnails?.medium?.url
                    ?: snippet.thumbnails?.default?.url
                    ?: ""

                val externalUrl = "https://www.youtube.com/watch?v=$videoId"

                candidates.add(
                    SocialCandidateDto(
                        id = videoId,
                        title = snippet.title,
                        description = snippet.description,
                        channelName = snippet.channelTitle,
                        channelId = snippet.channelId,
                        thumbnailUrl = thumbnail,
                        externalUrl = externalUrl,
                        originalRank = rank++,
                        publishedAt = snippet.publishedAt,
                        rawTags = emptyList()
                    )
                )
            }

            if (candidates.isEmpty()) {
                Result.failure(NoSuchElementException("No video candidates found for query: '$query'"))
            } else {
                Result.success(candidates)
            }
        } catch (e: Exception) {
            Log.e(TAG, "YouTube search API error", e)
            Result.failure(e)
        }
    }

    /**
     * Generates a realistic, diverse set of 25 synthetic candidates for developer testing
     * and offline verification passes across aesthetic styles.
     */
    fun getSampleCandidates(query: String): List<SocialCandidateDto> {
        val q = query.lowercase()
        return when {
            q.contains("cyberpunk") || q.contains("neon") || q.contains("night") -> sampleCyberpunkCandidates
            q.contains("synth") || q.contains("ambient") || q.contains("modular") || q.contains("minimal") -> sampleAmbientSynthCandidates
            q.contains("film") || q.contains("analog") || q.contains("photo") || q.contains("warm") -> sampleAnalogFilmCandidates
            q.contains("vibrant") || q.contains("rgb") || q.contains("gaming") || q.contains("anime") -> sampleVibrantGamingCandidates
            else -> sampleMixedCandidates
        }
    }

    // -------------------------------------------------------------------------
    // Synthetic Benchmark Datasets (25 items each with diverse aesthetic signals)
    // -------------------------------------------------------------------------

    private val sampleCyberpunkCandidates = listOf(
        SocialCandidateDto(
            id = "cp_01",
            title = "Cinematic Neon Rain — Cyberpunk City at Night (4K 60FPS)",
            description = "High contrast dramatic night walk in Tokyo with vibrant reflections and heavy rain mood.",
            channelName = "Nightscape Media",
            thumbnailUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_01",
            originalRank = 1,
            rawTags = listOf("cinematic", "neon", "cyberpunk", "atmospheric", "moody", "night", "high-contrast")
        ),
        SocialCandidateDto(
            id = "cp_02",
            title = "10 CRAZY Things You Didn't Know About Cyberpunk Cities",
            description = "Shocking facts and viral reactions to futuristic cyberpunk lore and gaming history.",
            channelName = "Viral Tech Top 10",
            thumbnailUrl = "https://images.unsplash.com/photo-1519501025264-65ba15a82390?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_02",
            originalRank = 2,
            rawTags = listOf("viral", "shocking", "top 10", "reaction", "trending", "clickbait")
        ),
        SocialCandidateDto(
            id = "cp_03",
            title = "Atmospheric Cyberpunk Dystopia — Deep Shadows & Blade Runner Lighting",
            description = "Dramatic composition exploring depth, volumetric lighting, and deep neon shadows.",
            channelName = "Aesthetic Cinema",
            thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_03",
            originalRank = 3,
            rawTags = listOf("dramatic", "depth", "lighting", "shadow", "cinematic", "dark")
        ),
        SocialCandidateDto(
            id = "cp_04",
            title = "Minimal Ambient Modular Synth in Cyberpunk Alleyway",
            description = "Restrained minimalist modular synthesizer soundscape with calm rhythmic pulses.",
            channelName = "Modular Sound Lab",
            thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_04",
            originalRank = 4,
            rawTags = listOf("minimalist", "ambient", "modular", "clean", "restrained", "calm")
        ),
        SocialCandidateDto(
            id = "cp_05",
            title = "Extreme Ultra Vibrant RGB Neon Tokyo Alleyway Walk",
            description = "Saturated neon lights with high energy motion and intense RGB color palette.",
            channelName = "Tokyo HDR Walk",
            thumbnailUrl = "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_05",
            originalRank = 5,
            rawTags = listOf("vibrant", "saturated", "rgb", "bright", "motion", "high-energy")
        ),
        SocialCandidateDto(
            id = "cp_06",
            title = "Warm Analog Film Look in a Dystopian Neon Market",
            description = "Vintage 35mm film grain with warm color temperature inside a crowded night bazaar.",
            channelName = "35mm Visions",
            thumbnailUrl = "https://images.unsplash.com/photo-1492691527719-9d1e07e534b4?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_06",
            originalRank = 6,
            rawTags = listOf("warm", "vintage", "grain", "film", "analog", "nostalgic")
        ),
        SocialCandidateDto(
            id = "cp_07",
            title = "Cyberpunk 2077 Update 2.1 — Full Patch Review & Weapon Tier List",
            description = "Detailed breakdown of the patch notes, weapon damage numbers, and bug fixes.",
            channelName = "Game Guide Daily",
            thumbnailUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_07",
            originalRank = 7,
            rawTags = listOf("gaming", "review", "patch", "tier list", "guide")
        ),
        SocialCandidateDto(
            id = "cp_08",
            title = "Dark Noir Cyberpunk Detective Mystery — Atmospheric Shadow Play",
            description = "Muted monochrome tones, sharp silhouettes, and dramatic low key lighting in future city.",
            channelName = "Cinema Noir Vault",
            thumbnailUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_08",
            originalRank = 8,
            rawTags = listOf("dark", "dramatic", "contrast", "shadow", "sharp", "mood")
        ),
        SocialCandidateDto(
            id = "cp_09",
            title = "Building a Mini Cyberpunk Diorama with RGB LED Strips",
            description = "Step by step miniature crafting tutorial using acrylics, LEDs, and laser cut boards.",
            channelName = "Scale Model Crafts",
            thumbnailUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_09",
            originalRank = 9,
            rawTags = listOf("craft", "diorama", "intricate", "detailed", "studio")
        ),
        SocialCandidateDto(
            id = "cp_10",
            title = "Serene Rainy Rooftop View Over Hong Kong — Lo-Fi Chill Synth",
            description = "Peaceful rain soundscape with gentle harmonic synth chords and calming city backdrop.",
            channelName = "LoFi Serenade",
            thumbnailUrl = "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_10",
            originalRank = 10,
            rawTags = listOf("calm", "serene", "relaxed", "harmony", "peaceful")
        ),
        SocialCandidateDto(
            id = "cp_11",
            title = "Hyper-Dense Cyberpunk Megacity Architecture — Isometric 3D Render",
            description = "Intricate complex 3D modeling showcase with thousands of tiny windows and flying vehicles.",
            channelName = "Octane Renderers",
            thumbnailUrl = "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_11",
            originalRank = 11,
            rawTags = listOf("dense", "complex", "intricate", "structured", "symmetry")
        ),
        SocialCandidateDto(
            id = "cp_12",
            title = "Top 5 Anime Cyberpunk Shows You Must Watch in 2026",
            description = "Ranking the greatest sci-fi anime masterpieces of all time with epic animation clips.",
            channelName = "Anime Rewind",
            thumbnailUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_12",
            originalRank = 12,
            rawTags = listOf("anime", "recommendations", "top 5", "epic", "animation")
        ),
        SocialCandidateDto(
            id = "cp_13",
            title = "Symmetrical Futuristic Corridor — Minimal Sci-Fi Cinematography",
            description = "Perfect symmetrical framing, clean white panels, and high dynamic range lighting.",
            channelName = "Geometric Frame",
            thumbnailUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_13",
            originalRank = 13,
            rawTags = listOf("symmetry", "framing", "minimalist", "clean", "polished", "dynamicRange")
        ),
        SocialCandidateDto(
            id = "cp_14",
            title = "Cinematic Drone Flight Through Foggy Skyscraper Peaks",
            description = "Slow panoramic motion capturing natural depth and atmospheric morning mist.",
            channelName = "Skyline Perspectives",
            thumbnailUrl = "https://images.unsplash.com/photo-1477959858617-67f30bc75b82?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_14",
            originalRank = 14,
            rawTags = listOf("cinematic", "depth", "spacious", "natural", "harmony")
        ),
        SocialCandidateDto(
            id = "cp_15",
            title = "High Speed Neon Cyberpunk Motorcycle Chase Scene 4K",
            description = "Intense fast-paced action sequence with dynamic motion blur and vibrant light streaks.",
            channelName = "Action Reel Studios",
            thumbnailUrl = "https://images.unsplash.com/photo-1558981806-ec527fa84c39?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_15",
            originalRank = 15,
            rawTags = listOf("motion", "action", "dynamic", "vibrant", "intense")
        ),
        SocialCandidateDto(
            id = "cp_16",
            title = "How to Create Cyberpunk Lighting in Unreal Engine 5",
            description = "Technical tutorial on Lumen global illumination, emissive materials, and post-process volumes.",
            channelName = "Dev Tuts Unreal",
            thumbnailUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_16",
            originalRank = 16,
            rawTags = listOf("tutorial", "lighting", "technical", "detailed", "studio")
        ),
        SocialCandidateDto(
            id = "cp_17",
            title = "Raw Cyberpunk Street Photography in Shinjuku — Leica M11",
            description = "Candid tactile portraits, sharp focus on neon reflections, and authentic night mood.",
            channelName = "Street Leica Life",
            thumbnailUrl = "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_17",
            originalRank = 17,
            rawTags = listOf("sharp", "tactile", "texture", "candid", "contrast", "mood")
        ),
        SocialCandidateDto(
            id = "cp_18",
            title = "The Philosophy of Cyberpunk: Why the Future is Already Here",
            description = "Video essay dissecting William Gibson, Philip K. Dick, and modern technological isolation.",
            channelName = "Deep Dive Essays",
            thumbnailUrl = "https://images.unsplash.com/photo-1461749280684-dccba630e2f6?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_18",
            originalRank = 18,
            rawTags = listOf("essay", "philosophy", "classic", "experimental")
        ),
        SocialCandidateDto(
            id = "cp_19",
            title = "Vivid Saturated Synthwave Highway Drive — 80s Retro Aesthetics",
            description = "Bright magenta gridlines, chrome reflections, and vintage synthesizer arpeggios.",
            channelName = "Outrun Central",
            thumbnailUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_19",
            originalRank = 19,
            rawTags = listOf("vivid", "saturation", "retro", "vintage", "rhythm", "vibrancy")
        ),
        SocialCandidateDto(
            id = "cp_20",
            title = "Organic vs Synthetic: Biopunk Aesthetics in Cinema",
            description = "Visual contrast between biological textures, naturalism, and harsh metallic machine surfaces.",
            channelName = "Film Form Analysis",
            thumbnailUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_20",
            originalRank = 20,
            rawTags = listOf("natural", "organic", "texture", "contrast", "stylized")
        ),
        SocialCandidateDto(
            id = "cp_21",
            title = "Unboxing the $5000 Cyberpunk Themed Custom PC Rig",
            description = "Custom water cooling loop with neon green coolant and tempered glass casing.",
            channelName = "Hardware King",
            thumbnailUrl = "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_21",
            originalRank = 21,
            rawTags = listOf("unboxing", "hardware", "gaming", "polished")
        ),
        SocialCandidateDto(
            id = "cp_22",
            title = "Polished High-End Studio Product Shoot: Cyberpunk Watch",
            description = "Macro lens close-ups, elegant studio rim lighting, and clean black background framing.",
            channelName = "Commercial Studio Lab",
            thumbnailUrl = "https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_22",
            originalRank = 22,
            rawTags = listOf("polished", "elegance", "studio", "lighting", "close-up", "sharp")
        ),
        SocialCandidateDto(
            id = "cp_23",
            title = "Experimental Glitch Art & CRT Distortion — Cyberpunk Visualizer",
            description = "Unique noisy analog glitch patterns, cathode ray scanlines, and erratic sync pulses.",
            channelName = "Visual Glitch Lab",
            thumbnailUrl = "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_23",
            originalRank = 23,
            rawTags = listOf("experimental", "novelty", "noisy", "grain", "stylized")
        ),
        SocialCandidateDto(
            id = "cp_24",
            title = "ASMR Rainy Neon Cyberpunk Cafe Ambience (3 Hours)",
            description = "Soothing background rain on glass with gentle muffled espresso machine sounds.",
            channelName = "Cafe ASMR World",
            thumbnailUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_24",
            originalRank = 24,
            rawTags = listOf("calm", "relaxed", "serene", "background", "peaceful")
        ),
        SocialCandidateDto(
            id = "cp_25",
            title = "Reacting to the Funniest Cyberpunk Glitches Compilation",
            description = "Hilarious compilation of NPCs flying and cars spawning inside elevators.",
            channelName = "Laugh Out Loud Gaming",
            thumbnailUrl = "https://images.unsplash.com/photo-1511512578047-dfb367046420?w=600",
            externalUrl = "https://www.youtube.com/watch?v=cp_25",
            originalRank = 25,
            rawTags = listOf("funny", "reaction", "fails", "glitches", "viral")
        )
    )

    private val sampleAmbientSynthCandidates = sampleCyberpunkCandidates.mapIndexed { idx, item ->
        item.copy(id = "amb_${idx + 1}", externalUrl = "https://www.youtube.com/watch?v=amb_${idx + 1}")
    }

    private val sampleAnalogFilmCandidates = sampleCyberpunkCandidates.mapIndexed { idx, item ->
        item.copy(id = "film_${idx + 1}", externalUrl = "https://www.youtube.com/watch?v=film_${idx + 1}")
    }

    private val sampleVibrantGamingCandidates = sampleCyberpunkCandidates.mapIndexed { idx, item ->
        item.copy(id = "game_${idx + 1}", externalUrl = "https://www.youtube.com/watch?v=game_${idx + 1}")
    }

    private val sampleMixedCandidates = sampleCyberpunkCandidates
}
