package com.example.data

import com.example.compatibility.AuraMediaCompatibilityEngine

object RecommendationEngine {

    data class DiscoverCategories(
        val nextObsession: MediaItem?,
        val freshForYou: List<MediaItem>,
        val underTheRadar: List<MediaItem>,
        val aLittleDifferent: List<MediaItem>,
        val fromYourFavorites: List<MediaItem>,
        val continueWatching: MediaItem?,
        val wildcard: List<MediaItem> = emptyList(),
        val deepDiscovery: List<MediaItem> = emptyList()
    )

    fun computeDiscoverCategories(
        allMedia: List<MediaItem>,
        tasteDNA: TasteDNA = TasteDNA(),
        profile: TasteDNA.PreferenceProfile = TasteDNA.PreferenceProfile(),
        strategy: RecommendationStrategy? = null,
        stats: IntelligenceStats = IntelligenceStats(),
        creatorProfiles: Map<String, CreatorProfile> = emptyMap()
    ): DiscoverCategories {
        // Phase 10: Unified filter for playable media
        val itemsOnly = allMedia.filter { 
            it.itemCount == null && 
            AuraMediaCompatibilityEngine.isEligibleForImport(it.compatibilityStatus) &&
            !it.isDeleted
        }
        val usedIds = mutableSetOf<String>()
        val usedContentIds = mutableSetOf<String>()

        /**
         * Selects a single representative from a content group.
         */
        fun isContentDuplicate(item: MediaItem): Boolean {
            val contentId = item.parentContentId ?: item.contentHash ?: item.id
            return usedContentIds.contains(contentId)
        }

        fun markContentUsed(item: MediaItem) {
            val contentId = item.parentContentId ?: item.contentHash ?: item.id
            usedContentIds.add(contentId)
            usedIds.add(item.id)
        }

        fun scoreAndAnnotate(item: MediaItem, baseReason: String, overrideStrategy: RecommendationStrategy? = null): Pair<MediaItem, Float> {
            val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats, creatorProfiles)
            val effectiveStrategy = overrideStrategy ?: strategy
            val score = if (effectiveStrategy != null) {
                ExplorationEngine.calculatePolicyScore(evidence, effectiveStrategy)
            } else {
                // Fallback for safety
                item.rating + (if (item.isFavorite) 2f else 0f) - (item.exposureCount * 0.1f)
            }

            // Determine specific selection reason based on evidence and strategy
            val reason = when {
                evidence.exploitationScore > 0.7 && (effectiveStrategy?.exploitationWeight ?: 0f) > 1.0 -> "High predicted match"
                evidence.uncertaintyScore > 0.7 -> "Aura is learning your preference for this category"
                evidence.noveltyScore > 0.7 -> "You haven't explored this style yet"
                item.isFavorite -> "Similar to content you rated highly"
                else -> baseReason
            }

            return item.copy(selectionReason = reason) to score
        }

        // 0. Resolve specialized strategies for different buckets
        val systemState = ConfidenceEngine.calculateDiscoveryState(allMedia, stats)
        val wildcardStrategy = DiscoveryPolicyManager.resolveStrategy(DiscoveryPolicy(), UserIntent(), RecommendationObjective.WILDCARD_DISCOVERY, systemState, tasteDNA, profile)
        val deepStrategy = DiscoveryPolicyManager.resolveStrategy(DiscoveryPolicy(), UserIntent(), RecommendationObjective.DEEP_DISCOVERY, systemState, tasteDNA, profile)

        // 0. Continue Watching: Item in progress
        val continueWatching = itemsOnly.filter { it.progress > 0f }.maxByOrNull { it.progress }
        if (continueWatching != null) {
            markContentUsed(continueWatching)
        }

        // 1. Your Next Obsession: Highest predicted engagement item (THE HERO)
        // Calculated first so it can take the absolute best item
        val nextObsession = itemsOnly
            .filter { it.id !in usedIds && !isContentDuplicate(it) }
            .map { scoreAndAnnotate(it, "Your Next Obsession") }
            .maxByOrNull { it.second }
            ?.first

        if (nextObsession != null) {
            markContentUsed(nextObsession)
        }

        // 2. From Your Favorites: items matching user favorite traits
        val favorites = itemsOnly.filter { it.isFavorite }
        val favGenres = favorites.map { it.genre }.toSet()
        val favMoods = favorites.flatMap { it.moodTags }.toSet()

        val fromYourFavoritesCandidate = itemsOnly
            .filter { it.id !in usedIds && !isContentDuplicate(it) }
            .filter { item ->
                item.isFavorite || item.genre in favGenres || item.moodTags.any { it in favMoods }
            }
            .map { scoreAndAnnotate(it, "Similar to your favorites") }
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }

        fromYourFavoritesCandidate.forEach { markContentUsed(it) }

        // 3. Fresh for You: Recently added / unseen
        val freshForYou = itemsOnly
            .filter { it.id !in usedIds && !isContentDuplicate(it) }
            .map { scoreAndAnnotate(it, "Fresh for You") }
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }

        freshForYou.forEach { markContentUsed(it) }

        // 4. Wildcard: Intentional surprise
        val wildcard = itemsOnly
            .filter { it.id !in usedIds && !isContentDuplicate(it) }
            .map { scoreAndAnnotate(it, "Outside your comfort zone", wildcardStrategy) }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }

        wildcard.forEach { markContentUsed(it) }

        // 5. Deep Discovery: Information gain focus
        val deepDiscovery = itemsOnly
            .filter { it.id !in usedIds && !isContentDuplicate(it) }
            .map { scoreAndAnnotate(it, "Deep discovery", deepStrategy) }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }

        deepDiscovery.forEach { markContentUsed(it) }

        // 6. Under the Radar: High quality, lower exposure / niche
        val underTheRadar = itemsOnly
            .filter { it.id !in usedIds && !isContentDuplicate(it) }
            .filter { it.genre == "Nature" || it.genre == "Documentary" || it.genre == "Ambient" }
            .map { scoreAndAnnotate(it, "Under the Radar") }
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }

        underTheRadar.forEach { markContentUsed(it) }

        // 7. A Little Different: Controlled novelty
        val aLittleDifferent = itemsOnly
            .filter { it.id !in usedIds && !isContentDuplicate(it) }
            .map { scoreAndAnnotate(it, "A Little Different") }
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }

        aLittleDifferent.forEach { markContentUsed(it) }

        return DiscoverCategories(
            nextObsession = nextObsession,
            freshForYou = freshForYou,
            underTheRadar = underTheRadar,
            aLittleDifferent = aLittleDifferent,
            fromYourFavorites = fromYourFavoritesCandidate,
            continueWatching = continueWatching,
            wildcard = wildcard,
            deepDiscovery = deepDiscovery
        )
    }

    fun computeObsessions(
        allMedia: List<MediaItem>,
        tasteDNA: TasteDNA = TasteDNA(),
        profile: TasteDNA.PreferenceProfile = TasteDNA.PreferenceProfile(),
        policy: DiscoveryPolicy = DiscoveryPolicy(),
        stats: IntelligenceStats = IntelligenceStats(),
        creatorProfiles: Map<String, CreatorProfile> = emptyMap()
    ): List<ObsessionRecommendation> {
        val systemState = ConfidenceEngine.calculateDiscoveryState(allMedia, stats)
        val strategy = DiscoveryPolicyManager.resolveStrategy(
            policy = policy,
            intent = UserIntent(),
            objective = RecommendationObjective.GENERAL_DISCOVERY,
            systemState = systemState,
            tasteDNA = tasteDNA,
            profile = profile
        )
        
        val categories = computeDiscoverCategories(allMedia, tasteDNA, profile, strategy, stats, creatorProfiles)
        val obsessions = mutableListOf<ObsessionRecommendation>()

        // 1. Hero / Next Obsession
        categories.nextObsession?.let { item ->
            obsessions.add(ObsessionRecommendation(
                id = "hero_${item.id}",
                title = "Your Next Obsession",
                subtitle = item.selectionReason ?: "Highest predicted match based on your vibe",
                strategy = ObsessionStrategy.Hero,
                previewItems = listOf(item),
                confidenceScore = 0.95f,
                emotionalRole = EmotionalRole.HIGH_CONFIDENCE
            ))
        }

        // 2. Fresh Arrivals (Now independent of Hero presence)
        if (categories.freshForYou.isNotEmpty()) {
            obsessions.add(ObsessionRecommendation(
                id = "fresh_arrivals",
                title = "Fresh Arrivals",
                subtitle = "Recently discovered content matching your evolving taste",
                strategy = ObsessionStrategy.FreshArrivals,
                previewItems = categories.freshForYou.take(3),
                confidenceScore = 0.85f,
                emotionalRole = EmotionalRole.EMERGING_INTEREST
            ))
        }

        // 3. The Remix / From Favorites
        if (categories.fromYourFavorites.isNotEmpty()) {
            obsessions.add(ObsessionRecommendation(
                id = "fav_remix",
                title = "The Remix",
                subtitle = "New discoveries that feel like your saved favorites",
                strategy = ObsessionStrategy.FavoriteRemix,
                previewItems = categories.fromYourFavorites.take(3),
                confidenceScore = 0.80f,
                emotionalRole = EmotionalRole.DEEPENING
            ))
        }

        // 4. Hidden Gems
        if (categories.underTheRadar.isNotEmpty()) {
            obsessions.add(ObsessionRecommendation(
                id = "hidden_gems",
                title = "Hidden Gems",
                subtitle = "Highly relevant content you haven't interacted with yet",
                strategy = ObsessionStrategy.HiddenGems,
                previewItems = categories.underTheRadar.take(3),
                confidenceScore = 0.75f,
                emotionalRole = EmotionalRole.EXPLORATION
            ))
        }

        // 5. Novelty / A Little Different
        if (categories.aLittleDifferent.isNotEmpty()) {
            obsessions.add(ObsessionRecommendation(
                id = "novelty_pulse",
                title = "A Little Different",
                subtitle = "Exploring styles just outside your typical comfort zone",
                strategy = ObsessionStrategy.NoveltyPulse,
                previewItems = categories.aLittleDifferent.take(3),
                confidenceScore = 0.70f,
                emotionalRole = EmotionalRole.WILDCARD
            ))
        }

        // 6. Wildcard
        if (categories.wildcard.isNotEmpty()) {
            obsessions.add(ObsessionRecommendation(
                id = "wildcard_surprise",
                title = "Wildcard",
                subtitle = "A random departure from your usual patterns",
                strategy = ObsessionStrategy.NoveltyPulse,
                previewItems = categories.wildcard.take(3),
                confidenceScore = 0.65f,
                emotionalRole = EmotionalRole.WILDCARD
            ))
        }

        // 7. Deep Discovery
        if (categories.deepDiscovery.isNotEmpty()) {
            obsessions.add(ObsessionRecommendation(
                id = "deep_discovery",
                title = "Deep Discovery",
                subtitle = "Learning more about your nuanced preferences",
                strategy = ObsessionStrategy.DeepDiscovery,
                previewItems = categories.deepDiscovery.take(3),
                confidenceScore = 0.60f,
                emotionalRole = EmotionalRole.DEEPENING
            ))
        }

        return obsessions
    }

    /**
     * Specialized scoring for Discovery (AI Sort / Discover) that accounts for exposure hygiene.
     */
    fun scoreItemForDiscovery(
        item: MediaItem,
        winsMap: Map<String, Int> = emptyMap(),
        lossesMap: Map<String, Int> = emptyMap(),
        favGenres: Set<String> = emptySet(),
        favMoods: Set<String> = emptySet(),
        tasteDNA: TasteDNA = TasteDNA(),
        profile: TasteDNA.PreferenceProfile = TasteDNA.PreferenceProfile(),
        now: Long = System.currentTimeMillis()
    ): Float {
        // AI Sort Rule (Phase 5): MUST NOT return explicitly rated media
        if (item.rating > 0f) return -1000f

        var score = scoreItemForPairwise(item, winsMap, lossesMap, favGenres, favMoods, tasteDNA, profile)

        // Phase 8: Exposure Memory & Repetition Penalty
        score -= item.exposureCount * 2.0f

        item.lastExposedTimestamp?.let { last ->
            val hourMs = 3600000L
            val elapsed = now - last
            if (elapsed < hourMs) {
                score -= 10.0f * (1.0f - (elapsed.toFloat() / hourMs))
            }
        }
        
        if (item.viewCount == 0 && item.exposureCount == 0) {
            score += 3.0f * tasteDNA.explorationPropensity.toFloat()
        }

        return score
    }

    /**
     * Calculates personalized score for a media item for Pairwise ranking.
     */
    fun scoreItemForPairwise(
        item: MediaItem,
        winsMap: Map<String, Int> = emptyMap(),
        lossesMap: Map<String, Int> = emptyMap(),
        favGenres: Set<String> = emptySet(),
        favMoods: Set<String> = emptySet(),
        tasteDNA: TasteDNA = TasteDNA(),
        profile: TasteDNA.PreferenceProfile = TasteDNA.PreferenceProfile()
    ): Float {
        var score = item.rating * (profile.contentSimilarityWeight.toFloat() * 5.0f)

        if (item.isFavorite) score += 3.0f

        if (item.genre in favGenres) {
            score += 2.0f * profile.collaborativeWeight.toFloat() * 2.5f
        }

        val traits = PersonalizationTraitMapper.getTraitAdjustments(item.moodTags)
        traits.forEach { (dim, presence) ->
            val itemTraitValue = (presence + 1.0) / 2.0
            val userPref = when(dim) {
                "vibrancy" -> tasteDNA.effectiveVibrancy
                "contrast" -> tasteDNA.effectiveContrast
                "sharpness" -> tasteDNA.effectiveSharpness
                "symmetry" -> tasteDNA.effectiveSymmetry
                "complexity" -> tasteDNA.effectiveComplexity
                "naturalism" -> tasteDNA.effectiveNaturalism
                "novelty" -> tasteDNA.effectiveNovelty
                "lighting" -> tasteDNA.effectiveLighting
                "colorTemperature" -> tasteDNA.effectiveColorTemp
                "texture" -> tasteDNA.effectiveTexture
                "motion" -> tasteDNA.effectiveMotion
                "dynamicRange" -> tasteDNA.effectiveDynamicRange
                "framing" -> tasteDNA.effectiveFraming
                "depth" -> tasteDNA.effectiveDepth
                "warmth" -> tasteDNA.effectiveWarmth
                "saturation" -> tasteDNA.effectiveSaturation
                "elegance" -> tasteDNA.effectiveElegance
                "minimalism" -> tasteDNA.effectiveMinimalism
                "grain" -> tasteDNA.effectiveGrain
                "focus" -> tasteDNA.effectiveFocus
                "density" -> tasteDNA.effectiveDensity
                "rhythm" -> tasteDNA.effectiveRhythm
                "mood" -> tasteDNA.effectiveMood
                "harmony" -> tasteDNA.effectiveHarmony
                else -> 0.5
            }
            val alignment = 1.0 - Math.abs(userPref - itemTraitValue)
            score += (alignment * 1.5f).toFloat()
        }

        score += (item.eloRating.toFloat() - 1500f) / 10.0f

        val wins = winsMap[item.id] ?: 0
        val losses = lossesMap[item.id] ?: 0
        score += (wins - losses) * 0.5f * profile.collaborativeWeight.toFloat()

        if (item.viewCount == 0) {
            score += profile.noveltyWeight.toFloat() * tasteDNA.effectiveNovelty.toFloat() * 5.0f
        }

        score += (item.viewCount.coerceAtMost(10)) * 0.2f
        score += item.progress * 1.5f

        return score
    }

    /**
     * Scores all eligible media items across the library and returns the Top-100 ranked pool.
     */
    fun getTop100PairwiseCandidates(
        allMedia: List<MediaItem>,
        winsMap: Map<String, Int> = emptyMap(),
        lossesMap: Map<String, Int> = emptyMap(),
        mediaTypeFilter: String = "ALL",
        tasteDNA: TasteDNA = TasteDNA(),
        profile: TasteDNA.PreferenceProfile = TasteDNA.PreferenceProfile(),
        strategy: RecommendationStrategy? = null,
        stats: IntelligenceStats = IntelligenceStats(),
        creatorProfiles: Map<String, CreatorProfile> = emptyMap(),
        compareStrategy: CompareStrategy = CompareStrategy.PERSONALIZED,
        compareSort: CompareSortOption = CompareSortOption.RECOMMENDED
    ): List<Pair<MediaItem, Float>> {
        val now = System.currentTimeMillis()
        val eligible = allMedia.filter { item ->
            val isPlayable = item.itemCount == null && AuraMediaCompatibilityEngine.isEligibleForImport(item.compatibilityStatus)
            val matchesType = when (mediaTypeFilter.uppercase()) {
                "PHOTO", "PHOTOS" -> item.mediaType.uppercase() in listOf("PHOTO", "IMAGE")
                "VIDEO", "VIDEOS" -> item.mediaType.uppercase() in listOf("VIDEO", "MOVIE")
                else -> true
            }
            isPlayable && matchesType && !item.isDeleted
        }

        if (eligible.isEmpty()) return emptyList()

        // Phase 10: Policy-aware scoring
        val usedContentIds = mutableSetOf<String>()
        val scored = eligible.map { item ->
            val score = when (compareStrategy) {
                CompareStrategy.PERSONALIZED -> {
                    if (strategy != null) {
                        val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats, creatorProfiles, now)
                        ExplorationEngine.calculatePolicyScore(evidence, strategy)
                    } else {
                        val favGenres = allMedia.filter { it.isFavorite }.map { it.genre }.toSet()
                        val favMoods = allMedia.filter { it.isFavorite }.flatMap { it.moodTags }.toSet()
                        scoreItemForPairwise(item, winsMap, lossesMap, favGenres, favMoods, tasteDNA, profile) - (item.exposureCount * 1.0f)
                    }
                }
                CompareStrategy.REDISCOVER -> {
                    val ageBonus = if (item.lastViewedTimestamp != null) {
                        (now - item.lastViewedTimestamp!!).toDouble() / (1000.0 * 60 * 60 * 24 * 7) // weeks
                    } else 100.0
                    (item.rating.toDouble() * 20.0) + (item.viewCount.toDouble() * 2.0) + ageBonus
                }
                CompareStrategy.LEAST_INTERACTED -> {
                    val comparisonCount = (winsMap[item.id] ?: 0) + (lossesMap[item.id] ?: 0)
                    100f / (item.exposureCount + comparisonCount + 1f)
                }
                CompareStrategy.EXPLORE -> {
                    val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats, creatorProfiles, now)
                    // High novelty/uncertainty focus
                    evidence.uncertaintyScore * 5f + evidence.noveltyScore * 5f
                }
            }
            Pair(item, score.toFloat())
        }

        // Apply Sorting before selecting top 100
        val sorted = when (compareSort) {
            CompareSortOption.RECOMMENDED -> scored.sortedWith(
                compareByDescending<Pair<MediaItem, Float>> { it.second }
                    .thenByDescending { it.first.dateAdded }
                    .thenByDescending { it.first.parentContentId == null }
                    .thenBy { it.first.id }
            )
            CompareSortOption.NEWEST -> scored.sortedWith(
                compareByDescending<Pair<MediaItem, Float>> { it.first.dateAdded }
                    .thenByDescending { it.second }
                    .thenByDescending { it.first.parentContentId == null }
                    .thenBy { it.first.id }
            )
            CompareSortOption.OLDEST -> scored.sortedWith(
                compareBy<Pair<MediaItem, Float>> { it.first.dateAdded }
                    .thenByDescending { it.second }
                    .thenByDescending { it.first.parentContentId == null }
                    .thenBy { it.first.id }
            )
            CompareSortOption.LARGEST_FILES -> scored.sortedWith(
                compareByDescending<Pair<MediaItem, Float>> { it.first.sizeBytes }
                    .thenByDescending { it.second }
                    .thenByDescending { it.first.parentContentId == null }
                    .thenBy { it.first.id }
            )
            CompareSortOption.SMALLEST_FILES -> scored.sortedWith(
                compareBy<Pair<MediaItem, Float>> { it.first.sizeBytes }
                    .thenByDescending { it.second }
                    .thenByDescending { it.first.parentContentId == null }
                    .thenBy { it.first.id }
            )
        }

        val deduplicated = mutableListOf<Pair<MediaItem, Float>>()
        for (candidate in sorted) {
            val contentId = candidate.first.parentContentId ?: candidate.first.contentHash ?: candidate.first.id
            if (!usedContentIds.contains(contentId)) {
                deduplicated.add(candidate)
                usedContentIds.add(contentId)
            }
            if (deduplicated.size >= 100) break
        }

        return deduplicated
    }

    /**
     * Selects the next optimal candidate pair from the Top-100 pool balancing relevance,
     * under-comparison information value, diversity, and recent repetition penalties.
     */
    fun selectNextPairFromPool(
        top100Pool: List<Pair<MediaItem, Float>>,
        comparisonCounts: Map<String, Int> = emptyMap(),
        recentPairs: List<Pair<String, String>> = emptyList(),
        recentItemIds: List<String> = emptyList(),
        mediaTypeFilter: String = "ALL",
        randomSeed: Long = System.currentTimeMillis(),
        strategy: RecommendationStrategy? = null,
        tasteDNA: TasteDNA = TasteDNA(),
        creatorProfiles: Map<String, CreatorProfile> = emptyMap(),
        compareStrategy: CompareStrategy = CompareStrategy.PERSONALIZED
    ): Pair<MediaItem, MediaItem>? {
        val filteredPool = top100Pool.filter { (item, _) ->
            when (mediaTypeFilter.uppercase()) {
                "PHOTO", "PHOTOS" -> item.mediaType.uppercase() in listOf("PHOTO", "IMAGE")
                "VIDEO", "VIDEOS" -> item.mediaType.uppercase() in listOf("VIDEO", "MOVIE")
                else -> true
            }
        }

        if (filteredPool.size < 2) {
            return null
        }

        val poolItems = filteredPool.map { it.first }
        var bestPair: Pair<MediaItem, MediaItem>? = null
        var maxScore = -1e9f

        val random = kotlin.random.Random(randomSeed)
        val candidatesToEvaluate = mutableListOf<Pair<MediaItem, MediaItem>>()

        val maxIndex = filteredPool.size.coerceAtMost(30)
        for (i in 0 until maxIndex) {
            val itemA = poolItems[i]
            for (j in i + 1 until filteredPool.size) {
                val itemB = poolItems[j]
                candidatesToEvaluate.add(itemA to itemB)
            }
        }

        for ((itemA, itemB) in candidatesToEvaluate) {
            val scoreA = filteredPool.find { it.first.id == itemA.id }?.second ?: 0f
            val scoreB = filteredPool.find { it.first.id == itemB.id }?.second ?: 0f
            val avgRelevance = (scoreA + scoreB) / 2.0f

            val expectedA = PairwiseEloEngine.calculateExpectedScore(itemA.eloRating, itemB.eloRating)
            val infoValue = PairwiseEloEngine.calculateInformationValue(expectedA).toFloat() * 100.0f

            val pairGain = if (strategy != null || compareStrategy != CompareStrategy.PERSONALIZED) {
                ExplorationEngine.calculatePairInformationGain(itemA, itemB, tasteDNA)
            } else 0f

            val diversity = if (itemA.genre != itemB.genre || itemA.mediaType != itemB.mediaType) 1.5f else 0.0f

            val exactPairRepeat = recentPairs.any {
                (it.first == itemA.id && it.second == itemB.id) || (it.first == itemB.id && it.second == itemA.id)
            }
            val repetitionPenalty = if (exactPairRepeat) 100.0f else 0.0f

            val recentCountA = recentItemIds.count { it == itemA.id }
            val recentCountB = recentItemIds.count { it == itemB.id }
            val itemRepetitionPenalty = (recentCountA + recentCountB) * 15.0f

            val totalValue = when (compareStrategy) {
                CompareStrategy.REDISCOVER -> {
                    avgRelevance - repetitionPenalty - itemRepetitionPenalty
                }
                CompareStrategy.LEAST_INTERACTED -> {
                    avgRelevance - repetitionPenalty - itemRepetitionPenalty
                }
                CompareStrategy.EXPLORE -> {
                    infoValue + pairGain * 10f - repetitionPenalty - itemRepetitionPenalty
                }
                CompareStrategy.PERSONALIZED -> {
                    val exploreWeight = strategy?.explorationWeight ?: 1.0f
                    val exploitWeight = strategy?.exploitationWeight ?: 1.0f
                    (avgRelevance * exploitWeight) + 
                    ((infoValue + pairGain * 10f) * exploreWeight) +
                    (diversity * (strategy?.diversityWeight ?: 0.4f)) - 
                    repetitionPenalty - 
                    itemRepetitionPenalty
                }
            } + (random.nextFloat() * 0.5f)

            if (totalValue > maxScore) {
                maxScore = totalValue
                bestPair = itemA to itemB
            }
        }

        return bestPair ?: (poolItems[0] to poolItems[1])
    }
}
