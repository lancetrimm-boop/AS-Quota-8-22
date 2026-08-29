package com.example.data

import android.content.ContentUris
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.example.compatibility.AuraMediaCompatibilityEngine
import com.example.compatibility.AuraMediaConverter
import com.example.compatibility.ConversionResult
import com.example.compatibility.MediaCompatibilityReport
import com.example.data.contribution.ConsentState
import com.example.data.contribution.ContributionConsentManager
import com.example.data.contribution.ContributionQueueRepository
import com.example.data.contribution.SharedPreferencesConsentStorage
import com.example.data.db.RejectedMediaEntity
import com.example.data.blueprint.BlueprintArtifactManager
import com.example.data.blueprint.BlueprintImplementationManifest
import com.example.data.blueprint.BlueprintImplementationValidator
import com.example.data.db.AISkipEventEntity
import com.example.data.db.AuraDatabase
import com.example.data.db.ClipInteractionEntity
import com.example.data.db.CreatorEntity
import com.example.data.db.LegacyDatabaseEncryptionMigrator
import com.example.data.db.SQLCipherInitializer
import com.example.data.db.EvidenceEntity
import com.example.data.db.MediaEntity
import com.example.data.db.MicroMomentEntity
import com.example.data.db.PairwiseOutcomeEntity
import com.example.data.db.SearchHistoryEntity
import com.example.data.db.TuningAuditEntity
import com.example.data.db.UserPreferenceEntity
import com.example.data.semantic.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.example.data.CompareSelectionSession
import com.example.data.CompareMediaTypeFilter
import com.example.data.CompareStrategy
import com.example.data.CompareSortOption
import com.example.ui.models.LibraryItemUi
import java.util.UUID
import kotlin.random.Random

data class ClipInteractionSummary(
    val title: String,
    val previewCount: Int,
    val selectCount: Int,
    val exportCount: Int,
    val score: Int
)

data class AISkipStats(
    val totalSkipForwards: Int = 0,
    val totalSkipBacks: Int = 0,
    val totalSkipReversals: Int = 0,
    val totalWatchedDestinations: Int = 0
)

data class EngagementMetrics(
    val totalPlays: Int = 0,
    val favoriteCount: Int = 0,
    val averageRating: Float = 0f,
    val personalizationScore: Int = 0,
    val totalComparisons: Int = 0,
    val microMomentCount: Int = 0,
    val itemsDiscovered: Int = 0,
    val totalClipPreviews: Int = 0,
    val totalClipSelections: Int = 0,
    val totalClipExports: Int = 0,
    val topEngagedClips: List<ClipInteractionSummary> = emptyList(),
    val pairwiseDiagnostics: PairwiseDiagnostics = PairwiseDiagnostics(),
    val aiSkipStats: AISkipStats = AISkipStats()
)

data class PairwiseDiagnostics(
    val totalEligibleMedia: Int = 0,
    val top100CandidatePoolSize: Int = 0,
    val comparedCandidateCount: Int = 0,
    val neverComparedCount: Int = 0,
    val poolRefreshTimestamp: Long = 0L,
    val topCandidateIds: List<String> = emptyList(),
    val recentRepetitionCount: Int = 0,
    val lastSelectionReason: String = "Not initialized"
)



data class ImportProgressState(
    val isImporting: Boolean = false,
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val progressPercent: Int = 0,
    val statusText: String = ""
)

data class ScanProgressState(
    val scanSessionId: Long = 0L,
    val isScanning: Boolean = false,
    val discoveredCount: Int = 0,
    val processedCount: Int = 0,
    val failedCount: Int = 0,
    val totalCount: Int = 0,
    val statusText: String = "",
    val isComplete: Boolean = false,
    val errorCode: ScanError? = null
)

enum class ScanError {
    PERMISSION_DENIED,
    STORAGE_ACCESS_FAILED,
    DATABASE_ERROR,
    MIGRATION_FAILED,
    DATABASE_CORRUPT,
    ENCRYPTION_ERROR,
    QUERY_EMPTY,
    UNKNOWN_ERROR,
    QUERY_FAILURE,
    CANCELED
}

/**
 * Result of the discovery phase of a media scan.
 * Defines the authoritative boundary for deletion reconciliation.
 */
sealed class DiscoveryResult {
    data class Complete(
        val entities: List<MediaEntity>,
        val discoveredIds: Set<String>,
        val scannedVolumes: Set<String>,
        val scannedMediaTypes: Set<String>
    ) : DiscoveryResult()

    data class Incomplete(
        val reason: String,
        val errorCode: ScanError,
        val cause: Throwable? = null
    ) : DiscoveryResult()
}

data class PlaylistState(
    val items: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val authoritativeMediaId: String? = null,
    val sourceTitle: String = ""
) {
    val currentItem: MediaItem? get() = items.getOrNull(currentIndex)
    val hasNext: Boolean get() = currentIndex < items.size - 1
    val hasPrevious: Boolean get() = currentIndex > 0
}

enum class DatabaseState {
    NOT_INITIALIZED,
    INITIALIZING,
    TRANSITION_REQUIRED,
    TRANSITIONING,
    VERIFYING,
    READY,
    TRANSITION_FAILED,
    CORRUPTED,
    ENCRYPTION_FAILED
}

class MediaRepository(
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val tasteDnaAdapter = moshi.adapter(TasteDNA::class.java)
    private val profileAdapter = moshi.adapter(TasteDNA.PreferenceProfile::class.java)
    private val discoveryPolicyAdapter = moshi.adapter(DiscoveryPolicy::class.java)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MediaRepository", "Fatal coroutine error in background scope", throwable)
    }
    private val scope = CoroutineScope(dispatcher + SupervisorJob() + exceptionHandler)
    private var database: AuraDatabase? = null
    fun getDatabase(): AuraDatabase? = database

    var blueprintArtifactManager: com.example.data.blueprint.BlueprintArtifactManager? = null
        private set

    @Volatile
    var intelligenceRepository: IntelligenceRepository? = null
        private set

    @Volatile
    var playbackErrorLogRepository: PlaybackErrorLogRepository? = null
        private set

    @Volatile
    var contributionQueueRepository: ContributionQueueRepository? = null
        private set

    @Volatile
    var conversionQueueRepository: ConversionQueueRepository? = null
        private set

    @Volatile
    var semanticRepresentationRepository: SemanticRepresentationRepository? = null
        private set

    @Volatile
    var embeddingProvider: EmbeddingProvider? = null
        private set

    @Volatile
    var mobileCLIPProvider: MobileCLIPEmbeddingProvider? = null
        private set

    @Volatile
    var semanticCandidateRetriever: SemanticCandidateRetriever? = null
        private set

    @Volatile
    var semanticSearchService: SemanticSearchService? = null
        private set

    @Volatile
    var hybridSearchEngine: HybridSearchEngine? = null
        private set

    private val _consentState = MutableStateFlow(ConsentState.NOT_DECIDED)
    val consentState: StateFlow<ConsentState> = _consentState.asStateFlow()

    val momentDispatcher = AuraMomentDispatcher(this)
    val safeDeleteManager = com.example.data.cleanup.SafeDeleteManager(this)
    var visualContextEngine = com.example.data.visual.VisualContextEngine(this)
    private var applicationContext: Context? = null

    private val _databaseState = MutableStateFlow(DatabaseState.NOT_INITIALIZED)
    val databaseState: StateFlow<DatabaseState> = _databaseState.asStateFlow()

    private val _databaseErrorMessage = MutableStateFlow<String?>(null)
    val databaseErrorMessage: StateFlow<String?> = _databaseErrorMessage.asStateFlow()

    private val _compareSelectionSession = MutableStateFlow(CompareSelectionSession())
    val compareSelectionSession: StateFlow<CompareSelectionSession> = _compareSelectionSession.asStateFlow()

    private val _compareMediaType = MutableStateFlow(CompareMediaTypeFilter.PHOTOS)
    val compareMediaType: StateFlow<CompareMediaTypeFilter> = _compareMediaType.asStateFlow()

    private val _compareStrategy = MutableStateFlow(CompareStrategy.PERSONALIZED)
    val compareStrategy: StateFlow<CompareStrategy> = _compareStrategy.asStateFlow()

    private val _compareSort = MutableStateFlow(CompareSortOption.RECOMMENDED)
    val compareSort: StateFlow<CompareSortOption> = _compareSort.asStateFlow()

    fun setCompareMediaType(filter: CompareMediaTypeFilter) {
        _compareMediaType.value = filter
        refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = true)
    }

    fun setCompareStrategy(strategy: CompareStrategy) {
        _compareStrategy.value = strategy
        refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = true)
    }

    fun setCompareSort(sort: CompareSortOption) {
        _compareSort.value = sort
        refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = true)
    }

    fun startCompareSelectionSession(selectedIds: Set<String>) {
        if (selectedIds.size < 4) {
            Log.w("MediaRepository", "Compare Selection requires at least 4 items.")
            return
        }
        _compareSelectionSession.value = CompareSelectionSession(
            isActive = true,
            selectedIds = selectedIds,
            originalCount = selectedIds.size,
            roundNumber = 1,
            comparedPairIds = emptyList()
        )
        refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = true)
    }

    fun restartCompareSelectionSession() {
        _compareSelectionSession.update { 
            CompareSelectionSession(
                isActive = true,
                selectedIds = it.selectedIds,
                originalCount = it.originalCount,
                roundNumber = 1,
                comparedPairIds = emptyList()
            )
        }
        refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = true)
    }

    fun exitCompareSelectionSession() {
        _compareSelectionSession.value = CompareSelectionSession()
        refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = true)
    }

    fun recordCompareSelectionVote(chosenId: String) = recordComparisonVote(chosenId)
    fun skipCompareSelectionPair() = skipComparison()

    // AI Fine-Tuning Constraints
    companion object {
        const val MAX_ADJUSTMENT_PER_VOTE = 0.01 // Small increment
        const val MAX_ADJUSTMENT_PER_SKIP = 0.02
        const val TOTAL_ADJUSTMENT_LIMIT = 0.20 // AI can't drift more than 20% from manual baseline

        val instance = MediaRepository()
        fun getInstance(context: Context): MediaRepository {
            instance.initDatabase(context)
            return instance
        }
    }

    private val emptyMediaItem = MediaItem(
        id = "",
        title = "",
        mediaType = "PHOTO",
        year = 2024,
        duration = "",
        genre = "Media"
    )

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    /**
     * OPTIMIZED READ-ONLY ACCESS TO MEDIA BY ID
     */
    val mediaItemsMap: StateFlow<Map<String, MediaItem>> = _mediaItems
        .map { list -> list.associateBy { it.id } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _watchHistory = MutableStateFlow<List<MediaItem>>(emptyList())
    val watchHistory: StateFlow<List<MediaItem>> = _watchHistory.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    fun recordSearch(query: String) {
        if (query.isBlank()) return
        scope.launch(Dispatchers.IO) {
            database?.let { db ->
                // Remove previous instances of the same query to keep it unique and at the top
                db.searchHistoryDao().deleteSearchByQuery(query)
                db.searchHistoryDao().insertSearch(SearchHistoryEntity(query = query))
            }
        }
    }

    fun clearSearchHistory() {
        scope.launch(Dispatchers.IO) {
            database?.searchHistoryDao()?.clearSearchHistory()
        }
    }



    private val _importProgress = MutableStateFlow(ImportProgressState())
    val importProgress: StateFlow<ImportProgressState> = _importProgress.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgressState())
    val scanProgress: StateFlow<ScanProgressState> = _scanProgress.asStateFlow()

    private var activeScanJob: kotlinx.coroutines.Job? = null

    // Reactive Library UI State
    private val _libraryFilter = MutableStateFlow("ALL")
    val libraryFilterFlow: StateFlow<String> = _libraryFilter.asStateFlow()
    var libraryFilter: String 
        get() = _libraryFilter.value
        set(value) { _libraryFilter.value = value }

    fun refreshSort() {
        _librarySessionSeed.value = System.currentTimeMillis()
        Log.d("AURA_SORT_DIAG", "refreshSort called. New seed: ${_librarySessionSeed.value}")
    }

    private val _activeSortCategory = MutableStateFlow(SortCategory.STANDARD)
    val activeSortCategory: StateFlow<SortCategory> = _activeSortCategory.asStateFlow()
    var sortCategory: SortCategory
        get() = _activeSortCategory.value
        set(value) { 
            _activeSortCategory.value = value 
            scope.launch {
                database?.userPreferenceDao()?.insertPreference(com.example.data.db.UserPreferenceEntity("active_sort_category", value.name))
            }
        }

    private val _selectedStandardSort = MutableStateFlow(StandardSortOption.NEWEST_FIRST)
    val selectedStandardSort: StateFlow<StandardSortOption> = _selectedStandardSort.asStateFlow()
    var standardSort: StandardSortOption
        get() = _selectedStandardSort.value
        set(value) {
            if (value == StandardSortOption.RANDOM) {
                refreshSort()
            }
            _selectedStandardSort.value = value
            scope.launch {
                database?.userPreferenceDao()?.insertPreference(com.example.data.db.UserPreferenceEntity("selected_standard_sort", value.name))
            }
        }

    private val _selectedIntelligentSort = MutableStateFlow(IntelligentSortOption.PERSONALIZED)
    val selectedIntelligentSort: StateFlow<IntelligentSortOption> = _selectedIntelligentSort.asStateFlow()
    var intelligentSort: IntelligentSortOption
        get() = _selectedIntelligentSort.value
        set(value) { 
            if (value == IntelligentSortOption.SURPRISE_ME) {
                refreshSort()
            }
            _selectedIntelligentSort.value = value 
            scope.launch {
                database?.userPreferenceDao()?.insertPreference(com.example.data.db.UserPreferenceEntity("selected_intelligent_sort", value.name))
            }
        }

    private val _libraryRepeatMode = MutableStateFlow(false) // false = OFF, true = ONE
    val libraryRepeatMode: StateFlow<Boolean> = _libraryRepeatMode.asStateFlow()
    var repeatMode: Boolean
        get() = _libraryRepeatMode.value
        set(value) { _libraryRepeatMode.value = value }

    private val _librarySearchQuery = MutableStateFlow("")
    var librarySearchQuery: String
        get() = _librarySearchQuery.value
        set(value) { _librarySearchQuery.value = value }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val librarySearchQueryFlow: Flow<String> = _librarySearchQuery.debounce(300).distinctUntilChanged()

    private val _librarySessionSeed = MutableStateFlow(System.currentTimeMillis())
    var librarySessionSeed: Long
        get() = _librarySessionSeed.value
        set(value) { _librarySessionSeed.value = value }

    var libraryCurrentPage: Int = 1
    var libraryScrollIndex: Int = 0
    var libraryScrollOffset: Int = 0

    var discoverScrollIndex: Int = 0
    var discoverScrollOffset: Int = 0

    private val _discoverSnapshot = MutableStateFlow<DiscoverSnapshot?>(null)
    val discoverSnapshot: StateFlow<DiscoverSnapshot?> = _discoverSnapshot.asStateFlow()

    fun updateDiscoverSnapshot(snapshot: DiscoverSnapshot) {
        _discoverSnapshot.value = snapshot
    }

    fun updateConsentState(state: ConsentState) {
        contributionQueueRepository?.consentManager?.setConsentState(state)
    }

    fun isDiscoverSnapshotStale(): Boolean {
        val current = _discoverSnapshot.value ?: return true
        val elapsed = System.currentTimeMillis() - current.generationId
        return elapsed > 30 * 60 * 1000L // 30 minutes staleness
    }

    private val _activePlaylist = MutableStateFlow<PlaylistState?>(null)
    val activePlaylist: StateFlow<PlaylistState?> = _activePlaylist.asStateFlow()

    private val _isPlayerActive = MutableStateFlow(false)
    val isPlayerActive: StateFlow<Boolean> = _isPlayerActive.asStateFlow()

    // AURA P1 STABILITY: Startup Readiness Logic
    private val _isLibraryReady = MutableStateFlow(false)
    val isLibraryReady: StateFlow<Boolean> = _isLibraryReady.asStateFlow()

    // AURA PHASE 2: Playback Session State
    private var _lastPlaybackPositionMs = 0L
    val lastPlaybackPositionMs: Long get() = _lastPlaybackPositionMs

    private var _playbackSessionId = com.example.util.AuraPlaybackDiagnostics.createSessionId()
    val playbackSessionId: String get() = _playbackSessionId

    fun refreshPlaybackSessionId() {
        _playbackSessionId = com.example.util.AuraPlaybackDiagnostics.createSessionId()
    }

    private var _isResumingFromBackground = false
    val isResumingFromBackground: Boolean get() = _isResumingFromBackground

    fun updatePlaybackPosition(positionMs: Long) {
        _lastPlaybackPositionMs = positionMs
    }

    fun setResumingFromBackground(resuming: Boolean) {
        _isResumingFromBackground = resuming
    }

    private val _pairwiseState = MutableStateFlow(
        PairwiseComparison(
            id = "p1",
            roundNumber = 1,
            totalRounds = 50,
            optionA = emptyMediaItem,
            optionB = emptyMediaItem
        )
    )
    val pairwiseState: StateFlow<PairwiseComparison> = _pairwiseState.asStateFlow()

    private val _intelligenceStats = MutableStateFlow(IntelligenceStats())
    val intelligenceStats: StateFlow<IntelligenceStats> = _intelligenceStats.asStateFlow()

    private val _tasteDNA = MutableStateFlow(TasteDNA())
    val tasteDNA: StateFlow<TasteDNA> = _tasteDNA.asStateFlow()

    private val _preferenceProfile = MutableStateFlow(TasteDNA.PreferenceProfile())
    val preferenceProfile: StateFlow<TasteDNA.PreferenceProfile> = _preferenceProfile.asStateFlow()

    private val _discoveryPolicy = MutableStateFlow(DiscoveryPolicy())
    val discoveryPolicy: StateFlow<DiscoveryPolicy> = _discoveryPolicy.asStateFlow()

    private val _userIntent = MutableStateFlow(UserIntent())
    val userIntent: StateFlow<UserIntent> = _userIntent.asStateFlow()

    private val _creatorProfiles = MutableStateFlow<Map<String, CreatorProfile>>(emptyMap())
    val creatorProfiles: StateFlow<Map<String, CreatorProfile>> = _creatorProfiles.asStateFlow()

    private val _storedEvidence = MutableStateFlow<List<EvidenceRecord>>(emptyList())
    val storedEvidence: StateFlow<List<EvidenceRecord>> = _storedEvidence.asStateFlow()

    /**
     * INTERNAL PRE-SORTED LIST OF FULL MEDIA ITEMS
     * Used for playlist generation to avoid passing heavy entities to the UI layer.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val latestSortedFullItemsFlow: StateFlow<List<MediaItem>> = combine(
        mediaItems, _tasteDNA, _preferenceProfile, _libraryFilter, 
        _activeSortCategory, _selectedStandardSort, _selectedIntelligentSort,
        librarySearchQueryFlow, _librarySessionSeed, _discoveryPolicy, 
        _userIntent, _intelligenceStats, _creatorProfiles
    ) { args -> args }
    .flatMapLatest { args ->
        flow {
            @Suppress("UNCHECKED_CAST")
            val items = args[0] as List<MediaItem>
            val dna = args[1] as TasteDNA
            val profile = args[2] as TasteDNA.PreferenceProfile
            val filter = args[3] as String
            val category = args[4] as SortCategory
            val standardSort = args[5] as StandardSortOption
            val intelligentSort = args[6] as IntelligentSortOption
            val query = args[7] as String
            val seed = args[8] as Long
            val policy = args[9] as DiscoveryPolicy
            val intent = args[10] as UserIntent
            val stats = args[11] as IntelligenceStats
            @Suppress("UNCHECKED_CAST")
            val creators = args[12] as Map<String, CreatorProfile>

            android.util.Log.d("AURA_SORT_FLOW", "latestSortedFullItemsFlow recomputing. Query: '$query', Category: $category, Sort: ${if(category == SortCategory.STANDARD) standardSort else intelligentSort}")

            android.util.Log.d("AURA_DATA_FLOW", "Recomputing latestSortedFullItemsFlow. Query: '$query', Category: $category, Sort: ${if(category == SortCategory.STANDARD) standardSort else intelligentSort}")

            val sorted = getFilteredAndSortedMedia(
                filterType = filter,
                sortCategory = category,
                standardSort = standardSort,
                intelligentSort = intelligentSort,
                sessionSeed = seed,
                inputItems = items,
                tasteDNA = dna,
                profile = profile,
                policy = policy,
                intent = intent,
                stats = stats,
                creatorProfiles = creators
            )
            
            if (query.isBlank()) {
                android.util.Log.d("AURA_DATA_FLOW", "Emission: Sorted list of ${sorted.size} items.")
                emit(sorted)
            } else {
                val basePool = items.filter { item ->
                    matchesFilterType(item, filter) && isItemVisibleInLibrary(item)
                }
                android.util.Log.d("AURA_SEARCH_FLOW", "Searching. Query: '$query', Pool size: ${basePool.size}")

                val engine = hybridSearchEngine
                if (engine != null) {
                    val result = engine.search(query)
                    val hybridResults = if (result.isSuccess && result.candidates.isNotEmpty()) {
                        val itemsMap = items.associateBy { it.id }
                        result.candidates.mapNotNull { itemsMap[it.mediaId] }
                    } else {
                        performLegacySearch(basePool, query)
                    }
                    
                    val eligibleIds = basePool.map { it.id }.toSet()
                    val finalResults = hybridResults.filter { it.id in eligibleIds }
                    android.util.Log.d("AURA_SEARCH_FLOW", "Search result count: ${finalResults.size}")
                    emit(finalResults)
                } else {
                    val legacy = performLegacySearch(basePool, query)
                    android.util.Log.d("AURA_SEARCH_FLOW", "Legacy search result count: ${legacy.size}")
                    emit(legacy)
                }
            }
        }
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * LATEST AVAILABLE AI SORT RECOMMENDATION (Reactive UI Model)
     * Optimized to emit lightweight UI models on a background thread.
     * Mode-aware identity ensures UI refreshes even if top results are similar.
     */
    val latestAiSortRecommendation: StateFlow<List<LibraryItemUi>> = latestSortedFullItemsFlow
        .map { items -> 
            val uiItems = items.map { it.toLibraryItemUi() }
            Log.d("AURA_UI_FLOW", "latestAiSortRecommendation emitting ${uiItems.size} items.")
            uiItems
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun MediaItem.toLibraryItemUi(): LibraryItemUi {
        return LibraryItemUi(
            id = id,
            title = title,
            mediaType = mediaType,
            imageUrl = imageUrl,
            uriPath = uriPath,
            duration = duration,
            selectionReason = selectionReason
        )
    }

    /**
     * Sets the active playlist using the current pre-sorted library items.
     * AURA PHASE 1: Accepts the explicit list from the UI to ensure sync.
     */
    fun setLibraryPlaylist(items: List<MediaItem>, initialIndex: Int) {
        setPlaylist(items, initialIndex, "Library")
    }

    fun addMediaEntity(entity: MediaEntity) {
        scope.launch {
            database?.mediaDao()?.insert(entity)
        }
    }

    fun addRejectedMedia(entity: RejectedMediaEntity) {
        scope.launch {
            database?.rejectedMediaDao()?.insert(entity)
        }
    }

    private var initJob: Job? = null

    fun resetDatabase(context: Context) {
        scope.launch {
            Log.w("AURA_RESET", "Quarantining database and starting fresh...")
            _databaseState.value = DatabaseState.INITIALIZING
            database?.close()
            database = null
            
            val dbPath = context.getDatabasePath("aura_intelligence.db")
            if (dbPath.exists()) {
                val quarantinePath = context.getDatabasePath("aura_quarantine_${System.currentTimeMillis()}.db")
                dbPath.renameTo(quarantinePath)
                Log.i("AURA_RESET", "Database quarantined to: ${quarantinePath.name}")
            }
            
            _mediaItems.value = emptyList()
            initDatabase(context)
        }
    }

    fun initDatabase(context: Context) {
        applicationContext = context.applicationContext
        if (blueprintArtifactManager == null) {
            blueprintArtifactManager = com.example.data.blueprint.BlueprintArtifactManager(context.applicationContext)
        }
        
        if (_databaseState.value == DatabaseState.READY) return

        synchronized(this) {
            if (initJob?.isActive == true) return
            
            // Set state to INITIALIZING immediately to signal that the process has started
            _databaseErrorMessage.value = null
            _databaseState.value = DatabaseState.INITIALIZING
            Log.d("AURA_INIT", "Starting secure database initialization...")

            initJob = scope.launch {
                try {
                    // 1. Initialize SQLCipher native libraries before ANY database operations
                    SQLCipherInitializer.initialize(context)

                    val dbPath = context.getDatabasePath("aura_intelligence.db")
                    if (dbPath.exists()) {
                        try {
                            val hexKey = com.example.data.db.PassphraseManager.getPassphraseAsHex(context)
                            net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(dbPath.absolutePath, hexKey.toByteArray(), null, net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE, null).use { rawDb ->
                                Log.d("AURA_INIT", "Current raw DB version: ${rawDb.version}")
                                if (rawDb.version == 0) {
                                    // RECOVERY: If version is 0 (likely due to export without version preservation), 
                                    // set to a safe baseline that matches the found schema (v12 includes contentHash)
                                    Log.w("AURA_INIT", "Detected version 0 database (export remnant). Repairing to version 12.")
                                    rawDb.version = 12
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("AURA_INIT", "Could not check/repair raw DB version: ${e.message}")
                        }
                    }

                    // 2. Handle Encryption Transition if required
                    val transitionResult = LegacyDatabaseEncryptionMigrator.ensureEncryption(context)
                    
                    when (transitionResult) {
                        is LegacyDatabaseEncryptionMigrator.TransitionResult.Failure -> {
                            _databaseErrorMessage.value = transitionResult.reason
                            val state = when {
                                transitionResult.reason.contains("corrupt", ignoreCase = true) -> DatabaseState.CORRUPTED
                                transitionResult.reason.contains("key", ignoreCase = true) || 
                                transitionResult.reason.contains("encryption", ignoreCase = true) -> DatabaseState.ENCRYPTION_FAILED
                                else -> DatabaseState.TRANSITION_FAILED
                            }
                            _databaseState.value = state
                            Log.e("AURA_INIT", "Transition failed: ${transitionResult.reason}")
                            return@launch
                        }
                        LegacyDatabaseEncryptionMigrator.TransitionResult.Success -> {
                            Log.i("AURA_INIT", "Transition successful.")
                        }
                        else -> {
                            Log.d("AURA_INIT", "No transition required or already encrypted.")
                        }
                    }

                    _databaseState.value = DatabaseState.VERIFYING
                    // 3. Initialize Room
                    val db = AuraDatabase.getInstance(context)
                    database = db

                    val consentStorage = SharedPreferencesConsentStorage(context)
                    val consentManager = ContributionConsentManager(consentStorage)
                    contributionQueueRepository = ContributionQueueRepository(db.contributionQueueDao(), consentManager)

                    scope.launch {
                        consentManager.consentStateFlow.collect {
                            _consentState.value = it
                        }
                    }

                    intelligenceRepository = IntelligenceRepository(db.intelligenceDao(), this@MediaRepository, moshi, scope, db)
                    playbackErrorLogRepository = PlaybackErrorLogRepository(db.playbackErrorLogDao(), scope)
                    conversionQueueRepository = ConversionQueueRepository(
                        db.conversionJobDao(), 
                        db.userPreferenceDao(),
                        androidx.work.WorkManager.getInstance(context)
                    )
                    semanticRepresentationRepository = RoomSemanticRepresentationRepository(db.semanticRepresentationDao())
                    
                    // ONNX MODEL ACTIVATION (Phase 4)
                    val realEngine = try {
                        val modelBytes = context.assets.open("models/all-minilm-l6-v2.onnx").use { it.readBytes() }
                        OnnxRuntimeMiniLMInferenceEngine(modelBytes = modelBytes)
                    } catch (e: Exception) {
                        Log.e("MediaRepository", "Failed to load ONNX model from assets. Falling back to local engine.", e)
                        LocalMiniLMInferenceEngine()
                    }

                    val realTokenizer = try {
                        val vocabText = context.assets.open("models/vocab.txt").use { it.bufferedReader().readText() }
                        BertWordPieceTokenizer.fromVocabText(vocabText)
                    } catch (e: Exception) {
                        Log.e("MediaRepository", "Failed to load vocab.txt from assets. Using standard vocab.", e)
                        BertWordPieceTokenizer()
                    }

                    embeddingProvider = MiniLMEmbeddingProvider(engine = realEngine, tokenizer = realTokenizer)
                    
                    val retriever = DefaultSemanticCandidateRetriever(semanticRepresentationRepository!!)
                    semanticCandidateRetriever = retriever

                    semanticSearchService = DefaultSemanticSearchService(embeddingProvider!!, retriever)
                    
                    val lexicalRetriever = ProductionLexicalRetriever()
                    val personalizationScorer = object : PersonalizationScorer {
                        override fun score(mediaId: String): Float = scoreMediaItemForPersonalization(mediaId)
                    }

                    hybridSearchEngine = DefaultHybridSearchEngine(
                        semanticService = semanticSearchService!!,
                        lexicalRetriever = lexicalRetriever,
                        personalizationScorer = personalizationScorer
                    )

                    // PHASE 7: MobileCLIP Activation
                    try {
                        val mobileClipBytes = context.assets.open("models/mobileclip_s0_image.onnx").use { it.readBytes() }
                        val mobileClipEngine = OnnxRuntimeMobileCLIPInferenceEngine(modelBytes = mobileClipBytes)
                        mobileCLIPProvider = MobileCLIPEmbeddingProvider(engine = mobileClipEngine)
                        
                        // Text Provider for query embedding
                        val clipVocab = context.assets.open("models/mobileclip_vocab.json").use { it.bufferedReader().readText() }
                        val clipMerges = context.assets.open("models/mobileclip_merges.txt").use { it.bufferedReader().readText() }
                        val clipTokenizer = ClipBpeTokenizer.fromAssets(clipVocab, clipMerges)
                        
                        val mobileClipTextBytes = context.assets.open("models/mobileclip_s0_text.onnx").use { it.readBytes() }
                        val mobileClipTextEngine = OnnxRuntimeMobileCLIPTextInferenceEngine(modelBytes = mobileClipTextBytes)
                        val mobileClipTextProvider = MobileCLIPTextEmbeddingProvider(mobileClipTextEngine, clipTokenizer)
                        
                        val mobileClipVisualSearchService = DefaultSemanticSearchService(mobileClipTextProvider, retriever)
                        val mobileClipVisualRetriever = DefaultMobileCLIPVisualRetriever(mobileClipVisualSearchService)

                        // Re-initialize VisualContextEngine with the neural provider, repository, and retriever
                        visualContextEngine = com.example.data.visual.VisualContextEngine(
                            this@MediaRepository, 
                            mobileCLIPProvider,
                            semanticRepresentationRepository,
                            semanticCandidateRetriever
                        )
                        Log.i("MediaRepository", "MobileCLIP image encoder and visual retriever activated.")
                        
                        // Update hybrid engine with visual channel
                        hybridSearchEngine = DefaultHybridSearchEngine(
                            semanticService = semanticSearchService!!,
                            lexicalRetriever = lexicalRetriever,
                            visualRetriever = mobileClipVisualRetriever,
                            personalizationScorer = personalizationScorer
                        )
                    } catch (e: Exception) {
                        Log.e("MediaRepository", "Failed to load MobileCLIP model from assets.", e)
                    }

                    // AURA STABILITY: Ensure all mandatory repositories are initialized before marking READY
                    checkNotNull(intelligenceRepository) { "intelligenceRepository failed to initialize" }
                    checkNotNull(playbackErrorLogRepository) { "playbackErrorLogRepository failed to initialize" }
                    checkNotNull(conversionQueueRepository) { "conversionQueueRepository failed to initialize" }
                    checkNotNull(semanticRepresentationRepository) { "semanticRepresentationRepository failed to initialize" }
                    checkNotNull(hybridSearchEngine) { "hybridSearchEngine failed to initialize" }

                    _databaseState.value = DatabaseState.READY
                    
                    // AURA SEARCH FIX: Backfill semantics for existing media items
                    launch {
                        backfillSemantics()
                    }

                    // Cleanup legacy backups if transition was stable
                    LegacyDatabaseEncryptionMigrator.cleanupLegacyBackups(context)

                    try {
                        db.mediaDao().deleteSampleMedia()
                        db.mediaDao().purgeUnsupportedMedia()
                    } catch (e: Exception) {
                        // Ignore non-critical cleanup failures
                    }

                    // Launch non-blocking data collection immediately
                    launch {
                        Log.d("AURA_SCAN_RUNTIME", "[REPO] mediaItems Flow collection starting.")
                        db.mediaDao().getAllMedia()
                            .conflate()
                            .transform { entities ->
                                emit(entities)
                                // AURA P1 STABILITY: Batch UI updates during heavy ingestion.
                                // If a scan is active, we introduce a cooldown to prevent
                                // UI list churn and excessive recomposition.
                                if (_scanProgress.value.isScanning) {
                                    delay(3000)
                                }
                            }
                            .collect { entities ->
                            Log.d("AURA_SCAN_RUNTIME", "[REPO] mediaItems Flow emitted. Raw entity count: ${entities.size}")
                            val items = entities.map { it.toMediaItem() }.filter { item ->
                                // Enforce Photos and Videos ONLY
                                val isCorrectType = item.mediaType in listOf("PHOTO", "VIDEO", "Photo", "Movie")
                                
                                // AURA PHASE 1: Validity Filtering (Secondary check for safety)
                                val isValid = !item.isDeleted && item.compatibilityStatus !in listOf(
                                    CompatibilityStatus.CORRUPT,
                                    CompatibilityStatus.UNSUPPORTED,
                                    CompatibilityStatus.DELETED
                                )
                                
                                val keep = isCorrectType && isValid
                                if (!keep) {
                                    Log.v("AURA_SCAN_RUNTIME", "[REPO] Flow: Filtering out item: ${item.title} (Type: ${item.mediaType}, Status: ${item.compatibilityStatus})")
                                }
                                keep
                            }
                            Log.d("AURA_SCAN_RUNTIME", "[REPO] mediaItems Flow processing complete. Final item count: ${items.size}")
                            _mediaItems.value = items

                            // AURA P1 STABILITY: Identity-Authoritative Atomic Update
                            // Preserves authoritativeMediaId and re-derives currentIndex based on ID location.
                            _activePlaylist.update { current ->
                                if (current == null) return@update null

                                val updatedItems = current.items.map { snapshotItem ->
                                    items.find { it.id == snapshotItem.id } ?: snapshotItem
                                }

                                // Locate current ID in the updated list to derive new index
                                val authoritativeId = current.authoritativeMediaId
                                val newIndex = if (authoritativeId != null) {
                                    val idx = updatedItems.indexOfFirst { it.id == authoritativeId }
                                    if (idx != -1) idx else current.currentIndex.coerceAtMost(updatedItems.size - 1)
                                } else {
                                    current.currentIndex.coerceAtMost(updatedItems.size - 1)
                                }

                                current.copy(
                                    items = updatedItems,
                                    currentIndex = newIndex,
                                    authoritativeMediaId = updatedItems.getOrNull(newIndex)?.id
                                )
                            }

                            // Update pairwise options if available
                            val available = items.filter { it.itemCount == null }
                            if (available.size >= 2) {
                                refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = false)
                            }
                        }
                    }

                    launch {
                        db.mediaDao().getWatchHistory().collect { entities ->
                            _watchHistory.value = entities.map { it.toMediaItem() }
                        }
                    }

                    launch {
                        db.searchHistoryDao().getRecentSearches().collect { entities ->
                            _recentSearches.value = entities.map { it.query }
                        }
                    }

                    // Load Past Pairwise Outcomes
                    launch {
                        db.pairwiseDao().getAllOutcomes().collect { outcomes ->
                            outcomes.forEach { outcome ->
                                if (outcome.outcomeType == "VOTE" && outcome.chosenId.isNotEmpty()) {
                                    pairwiseWins[outcome.chosenId] = (pairwiseWins[outcome.chosenId] ?: 0) + 1
                                    val loser = if (outcome.chosenId == outcome.optionAId) outcome.optionBId else outcome.optionAId
                                    pairwiseLosses[loser] = (pairwiseLosses[loser] ?: 0) + 1
                                }
                                comparisonCounts[outcome.optionAId] = (comparisonCounts[outcome.optionAId] ?: 0) + 1
                                comparisonCounts[outcome.optionBId] = (comparisonCounts[outcome.optionBId] ?: 0) + 1
                            }
                        }
                    }

                    // Load Taste DNA and Preference Profile from database
                    launch {
                        val tasteJson = db.userPreferenceDao().getPreference("taste_dna")?.value
                        if (tasteJson != null) {
                            try {
                                tasteDnaAdapter.fromJson(tasteJson)?.let { 
                                    _tasteDNA.value = it.sanitize() 
                                }
                            } catch (e: Exception) {
                                Log.e("MediaRepository", "Failed to load Taste DNA", e)
                            }
                        }

                        val profileJson = db.userPreferenceDao().getPreference("preference_profile")?.value
                        if (profileJson != null) {
                            try {
                                profileAdapter.fromJson(profileJson)?.let { 
                                    _preferenceProfile.value = it.sanitize() 
                                }
                            } catch (e: Exception) {
                                Log.e("MediaRepository", "Failed to load Preference Profile", e)
                            }
                        }

                        val policyJson = db.userPreferenceDao().getPreference("discovery_policy")?.value
                        if (policyJson != null) {
                            try {
                                discoveryPolicyAdapter.fromJson(policyJson)?.let { _discoveryPolicy.value = it }
                            } catch (e: Exception) {
                                Log.e("MediaRepository", "Failed to load Discovery Policy", e)
                            }
                        }

                        // Load Sort Preferences
                        db.userPreferenceDao().getPreference("active_sort_category")?.value?.let { name ->
                            try { _activeSortCategory.value = SortCategory.valueOf(name) } catch (e: Exception) {}
                        }
                        db.userPreferenceDao().getPreference("selected_standard_sort")?.value?.let { name ->
                            try { _selectedStandardSort.value = StandardSortOption.valueOf(name) } catch (e: Exception) {}
                        }
                        db.userPreferenceDao().getPreference("selected_intelligent_sort")?.value?.let { name ->
                            val migratedName = when (name) {
                                "EXPLORE", "LEAST_INTERACTED" -> "DISCOVER"
                                "BEST_MATCH" -> "PERSONALIZED"
                                else -> name
                            }
                            try { 
                                _selectedIntelligentSort.value = IntelligentSortOption.valueOf(migratedName) 
                            } catch (e: Exception) {
                                // Fallback for other obsolete sort options
                                _selectedIntelligentSort.value = IntelligentSortOption.PERSONALIZED
                            }
                        }
                    }

                    // Load Creator Profiles from structured table
                    launch {
                        db.creatorDao().getAllCreators().collect { entities ->
                            _creatorProfiles.value = entities.associate { entity ->
                                entity.id to CreatorProfile(
                                    id = entity.id,
                                    name = entity.name,
                                    platform = entity.platform,
                                    affinityScore = entity.affinityScore,
                                    interactionCount = entity.interactionCount,
                                    lastInteractionTimestamp = entity.lastInteractionTimestamp,
                                    topMoodTags = if (entity.topMoodTagsJson.isBlank()) emptyList() else entity.topMoodTagsJson.split(",")
                                )
                            }
                        }
                    }

                    // Collect stored evidence
                    launch {
                        db.evidenceDao().getAllEvidence().collect { entities ->
                            _storedEvidence.value = entities.map {
                                EvidenceRecord(
                                    id = it.id,
                                    tier = EvidenceTier.valueOf(it.tier),
                                    sampleCount = it.sampleCount,
                                    score = it.score,
                                    quality = it.quality,
                                    source = it.source,
                                    timestamp = it.timestamp,
                                    associatedManifestId = it.associatedManifestId
                                )
                            }
                        }
                    }

                    // Heavy background processing starts AFTER data flows are active
                    launch {
                        try {
                            reconcileExistingMedia(context)

                            // AURA P1 STABILITY: Startup Readiness Logic
                            // If database already contains items, mark as ready immediately.
                            // Cached data is valid for interaction while reconciliation/scan continues.
                            val existingCount = db.mediaDao().getCount()
                            if (existingCount > 0) {
                                _isLibraryReady.value = true
                                Log.d("AURA_INIT", "Library marked READY (Cached data found: $existingCount items)")
                            }

                            scanLocalMedia(context)
                        } catch (e: Exception) {
                            Log.e("MediaRepository", "Background maintenance failed", e)
                        }
                    }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) {
                        Log.w("AURA_INIT", "Database initialization coroutine was cancelled.")
                        throw t
                    }
                    Log.e("AURA_INIT", "CRITICAL: Secure database initialization failed", t)
                    _databaseErrorMessage.value = t.message ?: t.javaClass.simpleName
                    _databaseState.value = DatabaseState.TRANSITION_FAILED
                }
            }

            // Guaranteed Terminal State Guard
            initJob?.invokeOnCompletion { throwable ->
                val finalState = _databaseState.value
                if (finalState == DatabaseState.READY || 
                    finalState == DatabaseState.TRANSITION_FAILED || 
                    finalState == DatabaseState.CORRUPTED || 
                    finalState == DatabaseState.ENCRYPTION_FAILED) {
                    return@invokeOnCompletion
                }

                // If we reach here, the coroutine terminated without setting a terminal state
                if (throwable is kotlinx.coroutines.CancellationException) {
                    Log.w("AURA_INIT", "Initialization job completed with cancellation. Forcing FAILURE state.")
                    _databaseState.value = DatabaseState.TRANSITION_FAILED
                    _databaseErrorMessage.value = "Initialization interrupted."
                } else if (throwable != null) {
                    Log.e("AURA_INIT", "Initialization job completed with unhandled error. Forcing FAILURE state.", throwable)
                    _databaseState.value = DatabaseState.TRANSITION_FAILED
                    _databaseErrorMessage.value = throwable.message ?: throwable.javaClass.simpleName
                } else {
                    Log.w("AURA_INIT", "Initialization job completed unexpectedly. Forcing FAILURE state.")
                    _databaseState.value = DatabaseState.TRANSITION_FAILED
                    _databaseErrorMessage.value = "Initialization failed to complete normally."
                }
            }
        }
    }

    fun updateDiscoveryPolicy(policy: DiscoveryPolicy) {
        _discoveryPolicy.value = policy
        scope.launch {
            val json = discoveryPolicyAdapter.toJson(policy)
            database?.userPreferenceDao()?.insertPreference(UserPreferenceEntity("discovery_policy", json))
        }
    }

    fun updateCreatorProfiles(profiles: Map<String, CreatorProfile>) {
        _creatorProfiles.value = profiles
        scope.launch {
            val db = database ?: return@launch
            profiles.values.forEach { profile ->
                db.creatorDao().insert(CreatorEntity(
                    id = profile.id,
                    name = profile.name,
                    platform = profile.platform,
                    affinityScore = profile.affinityScore,
                    interactionCount = profile.interactionCount,
                    lastInteractionTimestamp = profile.lastInteractionTimestamp,
                    topMoodTagsJson = profile.topMoodTags.joinToString(",")
                ))
            }
        }
    }

    fun logInteraction(item: MediaItem) {
        // 1. Update Interaction Counters & Timestamps
        val timestamp = System.currentTimeMillis()
        _mediaItems.update { list ->
            list.map { if (it.id == item.id) it.copy(viewCount = it.viewCount + 1, lastViewedTimestamp = timestamp) else it }
        }
        
        scope.launch {
            database?.mediaDao()?.let { dao ->
                val entity = dao.getMediaById(item.id)
                if (entity != null) {
                    dao.update(entity.copy(playCount = entity.playCount + 1, lastViewedTimestamp = timestamp))
                }
            }
        }

        // 2. Automatic Taste DNA Learning from meaningful engagement
        val dna = _tasteDNA.value
        if (dna.isFineTuningEnabled) {
            val adjustments = PersonalizationTraitMapper.getEffectiveTraitAdjustments(item)
            var updatedDna = dna
            
            // Engagement is a positive signal but should be very gradual (0.005)
            val engagementIncrement = 0.005
            
            adjustments.forEach { (dim, multiplier) ->
                val amount = multiplier * engagementIncrement
                updatedDna = updatedDna.updateLearnedDimension(dim, amount, TOTAL_ADJUSTMENT_LIMIT)
            }
            
            if (updatedDna != dna) {
                updateTasteDNA(updatedDna, isUserGenerated = false, evidenceCategory = "Media Engagement (View)")
            }
        }

        // 3. Update Preference Profile Learning
        learnPreferenceSignals(item, isSuccess = true)

        // 4. Prepare for future Social Discover learning events
        val event = SocialDiscoveryManager.processInteraction(item, _tasteDNA.value, SocialDiscoveryState())
        if (event != null) {
            Log.i("MediaRepository", "Social Learning Event: ${event.identifier} - ${event.type}")
            // Trigger system confidence/personalizationScore bump if success detected
            _intelligenceStats.update { it.copy(personalizationScore = (it.personalizationScore + 1).coerceAtMost(100)) }
            
            // Phase 3A: Enqueue sanitized telemetry if consent is granted
            contributionQueueRepository?.let { repo ->
                if (repo.isConsentGranted()) {
                    com.example.data.contribution.ContributionDataSanitizer.sanitizeTelemetryEvent(
                        interactionType = "VIEW",
                        feedbackCategory = "ENGAGEMENT",
                        score = 1.0,
                        timestampMs = timestamp
                    )?.let { payload ->
                        scope.launch {
                            repo.enqueueRecommendationFeedback(payload)
                        }
                    }
                }
            }
        }
    }

    fun recordMediaCompletion(id: String) {
        val item = getMediaItemById(id) ?: return
        
        // 1. Update Persistent Progress to 100%
        _mediaItems.update { list ->
            list.map { if (it.id == id) it.copy(progress = 1.0f) else it }
        }
        
        scope.launch {
            database?.mediaDao()?.let { dao ->
                val entity = dao.getMediaById(id)
                if (entity != null) {
                    dao.update(entity.copy(progress = 1.0f))
                }
            }
        }

        // 2. Automatic Taste DNA Learning from Completion (Strong Positive Signal)
        val dna = _tasteDNA.value
        if (dna.isFineTuningEnabled) {
            val adjustments = PersonalizationTraitMapper.getEffectiveTraitAdjustments(item)
            var updatedDna = dna
            
            // Completion is a strong positive signal (0.01)
            val completionIncrement = 0.01
            
            adjustments.forEach { (dim, multiplier) ->
                val amount = multiplier * completionIncrement
                updatedDna = updatedDna.updateLearnedDimension(dim, amount, TOTAL_ADJUSTMENT_LIMIT)
            }
            
            if (updatedDna != dna) {
                updateTasteDNA(updatedDna, isUserGenerated = false, evidenceCategory = "Media Completion")
            }
        }
        
        // 3. Update Preference Profile Learning
        learnPreferenceSignals(item, isCompletion = true, isSuccess = true)
    }

    private fun learnPreferenceSignals(
        item: MediaItem,
        isCompletion: Boolean = false,
        isDiscovery: Boolean = false,
        isSuccess: Boolean = true
    ) {
        val currentProfile = _preferenceProfile.value
        val reason = item.selectionReason ?: ""
        
        // 1. Content Similarity: user engages with similar content OR high completion
        val simPlus = if (isCompletion || reason.contains("match", ignoreCase = true) || reason.contains("favorite", ignoreCase = true)) 1 else 0
        
        // 2. Collaborative Signals: user accepts discovery recommendations
        val colPlus = if (isDiscovery || reason.contains("Discovery", ignoreCase = true) || reason.contains("Obsession", ignoreCase = true)) 1 else 0
        
        // 3. Diversity Bonus: engages with different categories successfully
        val divPlus = if (isSuccess && (reason.contains("different", ignoreCase = true) || reason.contains("comfort zone", ignoreCase = true) || reason.contains("under the radar", ignoreCase = true))) 1 else 0
        
        // 4. Novelty Weight: unfamiliar recommendations receive positive engagement
        val novPlus = if (item.viewCount == 0 && isSuccess && reason.contains("novelty", ignoreCase = true)) 1 else 0

        val nextInteractions = currentProfile.interactionsCount + 1
        
        val stagedProfile = currentProfile.copy(
            interactionsCount = nextInteractions,
            similaritySignal = currentProfile.similaritySignal + simPlus,
            collaborativeSignal = currentProfile.collaborativeSignal + colPlus,
            diversitySignal = currentProfile.diversitySignal + divPlus,
            noveltySignal = currentProfile.noveltySignal + novPlus
        )

        // Rule: Update only after confidence threshold (min 10)
        if (nextInteractions >= 10) {
            val maxAdj = 0.01 // Max adjustment per batch
            
            // Adjust weights based on accumulated signals
            var updatedProfile = stagedProfile.copy(
                contentSimilarityWeight = stagedProfile.contentSimilarityWeight + (if (stagedProfile.similaritySignal > 3) maxAdj else 0.0),
                collaborativeWeight = stagedProfile.collaborativeWeight + (if (stagedProfile.collaborativeSignal > 3) maxAdj else 0.0),
                diversityWeight = stagedProfile.diversityWeight + (if (stagedProfile.diversitySignal > 3) maxAdj else 0.0),
                noveltyWeight = stagedProfile.noveltyWeight + (if (stagedProfile.noveltySignal > 3) maxAdj else 0.0),
                interactionsCount = 0,
                similaritySignal = 0,
                collaborativeSignal = 0,
                diversitySignal = 0,
                noveltySignal = 0
            ).normalize(targetSum = 2.0)
            
            updatePreferenceProfile(updatedProfile)
            Log.d("AuraLearning", "Preference Profile calibrated: Similarity=${updatedProfile.contentSimilarityWeight}, Collaborative=${updatedProfile.collaborativeWeight}")
        } else {
            // Persist the count/signals without normalizing weights yet
            _preferenceProfile.value = stagedProfile
            scope.launch {
                val json = profileAdapter.toJson(stagedProfile)
                database?.userPreferenceDao()?.insertPreference(UserPreferenceEntity("preference_profile", json))
            }
        }
    }

    private var intentDecayJob: Job? = null
    fun setUserIntent(intent: UserIntent) {
        _userIntent.value = intent
        
        // Phase 12 Final Polish: Intent Decay (30-minute expiration)
        intentDecayJob?.cancel()
        if (intent.focus != IntentFocus.DEFAULT || intent.modeOverride != null) {
            intentDecayJob = scope.launch {
                kotlinx.coroutines.delay(30 * 60 * 1000L) // 30 minutes
                clearUserIntent()
                Log.d("MediaRepository", "Temporary user intent expired. Reset to global policy.")
            }
        }
    }

    fun clearUserIntent() {
        _userIntent.value = UserIntent()
        intentDecayJob?.cancel()
    }

    fun updateTasteDNA(dna: TasteDNA, isUserGenerated: Boolean = false, evidenceCategory: String = "Manual Adjustment") {
        val previousDna = _tasteDNA.value
        _tasteDNA.value = dna
        scope.launch {
            val json = tasteDnaAdapter.toJson(dna)
            database?.userPreferenceDao()?.insertPreference(UserPreferenceEntity("taste_dna", json))
            
            // Record Audit Trail
            recordTuningAudits(previousDna, dna, isUserGenerated, evidenceCategory)

            // Phase 3B.2: Enqueue sanitized contribution if consent is granted
            contributionQueueRepository?.let { repo ->
                if (repo.isConsentGranted() && !isUserGenerated) {
                    com.example.data.contribution.ContributionDataSanitizer.sanitizeTasteDNA(dna)?.let { payload ->
                        repo.enqueueTasteVectorSnapshot(payload)
                    }
                }
            }

            // Re-trigger candidate pool refresh to apply new weights immediately
            refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = false)

            // Emit Emotional Intelligence Signal (only for AI-learned calibrations)
            if (!isUserGenerated && evidenceCategory != "Manual Adjustment") {
                // Check if this is a "Major" calibration (e.g. cumulative significant change)
                // For now, any AI calibration triggers a Pulse, but specific ones can be Insight
                val isMajor = evidenceCategory.contains("Rating") || evidenceCategory.contains("Calibration")
                momentDispatcher.onEvent(
                    AuraMomentDispatcher.IntelligenceEvent.TasteCalibrated(
                        dimension = "Vibe DNA", 
                        delta = 0.0,
                        isMajor = isMajor
                    )
                )
            }
        }
    }

    private suspend fun recordTuningAudits(old: TasteDNA, new: TasteDNA, isUser: Boolean, category: String) {
        val dao = database?.tuningAuditDao() ?: return
        val audits = mutableListOf<TuningAuditEntity>()
        
        fun addAudit(key: String, oldVal: Double, newVal: Double, manual: Double, learned: Double) {
            if (oldVal != newVal) {
                audits.add(TuningAuditEntity(
                    preferenceKey = key,
                    previousEffectiveValue = oldVal,
                    newEffectiveValue = newVal,
                    userBaselineAtTime = manual,
                    aiAdjustmentAtTime = learned - manual,
                    evidenceCategory = category,
                    isUserGenerated = isUser
                ))
            }
        }

        // 24 visual & aesthetic dimensions
        addAudit("vibrancy", old.effectiveVibrancy, new.effectiveVibrancy, new.vibrancy, new.learnedVibrancy)
        addAudit("contrast", old.effectiveContrast, new.effectiveContrast, new.contrast, new.learnedContrast)
        addAudit("sharpness", old.effectiveSharpness, new.effectiveSharpness, new.sharpness, new.learnedSharpness)
        addAudit("symmetry", old.effectiveSymmetry, new.effectiveSymmetry, new.symmetry, new.learnedSymmetry)
        addAudit("complexity", old.effectiveComplexity, new.effectiveComplexity, new.complexity, new.learnedComplexity)
        addAudit("naturalism", old.effectiveNaturalism, new.effectiveNaturalism, new.naturalism, new.learnedNaturalism)
        addAudit("novelty", old.effectiveNovelty, new.effectiveNovelty, new.novelty, new.learnedNovelty)
        addAudit("lighting", old.effectiveLighting, new.effectiveLighting, new.lighting, new.learnedLighting)
        addAudit("colorTemperature", old.effectiveColorTemp, new.effectiveColorTemp, new.colorTemperature, new.learnedColorTemp)
        addAudit("texture", old.effectiveTexture, new.effectiveTexture, new.texture, new.learnedTexture)
        addAudit("motion", old.effectiveMotion, new.effectiveMotion, new.motion, new.learnedMotion)
        addAudit("dynamicRange", old.effectiveDynamicRange, new.effectiveDynamicRange, new.dynamicRange, new.learnedDynamicRange)
        addAudit("framing", old.effectiveFraming, new.effectiveFraming, new.framing, new.learnedFraming)
        addAudit("depth", old.effectiveDepth, new.effectiveDepth, new.depth, new.learnedDepth)
        addAudit("warmth", old.effectiveWarmth, new.effectiveWarmth, new.warmth, new.learnedWarmth)
        addAudit("saturation", old.effectiveSaturation, new.effectiveSaturation, new.saturation, new.learnedSaturation)
        addAudit("elegance", old.effectiveElegance, new.effectiveElegance, new.elegance, new.learnedElegance)
        addAudit("minimalism", old.effectiveMinimalism, new.effectiveMinimalism, new.minimalism, new.learnedMinimalism)
        addAudit("grain", old.effectiveGrain, new.effectiveGrain, new.grain, new.learnedGrain)
        addAudit("focus", old.effectiveFocus, new.effectiveFocus, new.focus, new.learnedFocus)
        addAudit("density", old.effectiveDensity, new.effectiveDensity, new.density, new.learnedDensity)
        addAudit("rhythm", old.effectiveRhythm, new.effectiveRhythm, new.rhythm, new.learnedRhythm)
        addAudit("mood", old.effectiveMood, new.effectiveMood, new.mood, new.learnedMood)
        addAudit("harmony", old.effectiveHarmony, new.effectiveHarmony, new.harmony, new.learnedHarmony)

        // Behavioral
        addAudit("skipSensitivity", old.effectiveSkipSensitivity, new.effectiveSkipSensitivity, new.skipSensitivity, new.learnedSkipSensitivity)
        addAudit("explorationPropensity", old.effectiveExploration, new.effectiveExploration, new.explorationPropensity, new.learnedExploration)
        addAudit("retentionFocus", old.effectiveRetention, new.effectiveRetention, new.retentionFocus, new.learnedRetention)
        addAudit("favoriteSignificance", old.effectiveFavSignificance, new.effectiveFavSignificance, new.favoriteSignificance, new.learnedFavSignificance)

        if (audits.isNotEmpty()) {
            scope.launch {
                audits.forEach { dao.insertAudit(it) }
            }
        }
    }

    fun updatePreferenceProfile(profile: TasteDNA.PreferenceProfile) {
        _preferenceProfile.value = profile
        scope.launch {
            val json = profileAdapter.toJson(profile)
            database?.userPreferenceDao()?.insertPreference(UserPreferenceEntity("preference_profile", json))
            
            // Re-trigger candidate pool refresh to apply new weights immediately
            refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = false)
        }
    }

    /**
     * Resets only the AI-learned personalization layer while preserving user baseline intent.
     */
    fun resetAIFineTuning() {
        val resetDna = _tasteDNA.value.resetFineTuning()
        updateTasteDNA(resetDna, isUserGenerated = true, evidenceCategory = "Reset AI Adjustments")
    }

    fun injectEvidence(tier: EvidenceTier, sampleCount: Int, score: Double, quality: Double, associatedManifestId: String? = null) {
        scope.launch {
            val evidenceId = UUID.randomUUID().toString()
            database?.evidenceDao()?.insertEvidence(
                EvidenceEntity(
                    id = evidenceId,
                    tier = tier.name,
                    sampleCount = sampleCount,
                    score = score,
                    quality = quality,
                    source = if (associatedManifestId != null) "Post-Implementation Agent" else "Manual Injected Agent",
                    timestamp = System.currentTimeMillis(),
                    associatedManifestId = associatedManifestId
                )
            )
            // Trigger Automatic Synchronization
            intelligenceRepository?.onEvidenceAvailable(evidenceId)
        }
    }

    fun clearEvidence() {
        scope.launch {
            database?.evidenceDao()?.clearAll()
        }
    }

    /**
     * Internal personalization scorer for hybrid search integration (Step 10).
     */
    private fun scoreMediaItemForPersonalization(mediaId: String): Float {
        val item = _mediaItems.value.find { it.id == mediaId } ?: return 0.5f
        
        val tasteDNA = _tasteDNA.value
        val stats = _intelligenceStats.value
        val creators = _creatorProfiles.value
        val now = System.currentTimeMillis()
        
        val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats, creators, now)
        
        val systemState = ConfidenceEngine.calculateDiscoveryState(_mediaItems.value, stats)
        val strategy = DiscoveryPolicyManager.resolveStrategy(
            policy = _discoveryPolicy.value,
            intent = _userIntent.value,
            objective = RecommendationObjective.LIBRARY_INTELLIGENT_DISCOVERY,
            systemState = systemState,
            tasteDNA = tasteDNA,
            profile = _preferenceProfile.value
        )
        
        return ExplorationEngine.calculatePolicyScore(evidence, strategy)
    }

    /**
     * Triggers the feedback loop to validate an implemented blueprint.
     */
    fun performImplementationValidation(blueprint: StrategyBlueprint, manifest: BlueprintImplementationManifest) {
        scope.launch {
            // In a real app, this would query fresh production data since implementation date.
            // For this restoration, we use the already collected/stored post-implementation evidence.
            val postEvidence = _storedEvidence.value.filter { it.associatedManifestId == manifest.manifestId }
            
            val (updatedManifest, updatedBlueprint) = BlueprintImplementationValidator.validateImplementation(
                blueprint, manifest, postEvidence
            )
            
            // Save updated artifact
            blueprintArtifactManager?.setBlueprint(updatedBlueprint)
            // Note: setBlueprint will regenerate a fresh manifest, so we might need to 
            // update the existing one if we want to preserve implementation notes.
            // But for this pipeline, a fresh manifest from the new blueprint version is appropriate.
        }
    }

    private val pairwiseWins = mutableMapOf<String, Int>()
    private val pairwiseLosses = mutableMapOf<String, Int>()
    private val comparisonCounts = mutableMapOf<String, Int>()
    private val recentPairs = mutableListOf<Pair<String, String>>()
    private val recentItemIds = mutableListOf<String>()

    private val _pairwiseDiagnostics = MutableStateFlow(PairwiseDiagnostics())
    val pairwiseDiagnostics: StateFlow<PairwiseDiagnostics> = _pairwiseDiagnostics.asStateFlow()

    fun refreshPairwiseCandidatePoolAndSelectNext(
        forceNextPair: Boolean = true
    ) {
        val session = _compareSelectionSession.value
        val currentMediaType = _compareMediaType.value
        val activeFilter: String = when (currentMediaType) {
            CompareMediaTypeFilter.PHOTOS -> "PHOTO"
            CompareMediaTypeFilter.VIDEOS -> "VIDEO"
        }

        val currentPair = _pairwiseState.value
        val allMedia = _mediaItems.value
        
        // HARD SELECTED-ID BOUNDARY
        val items = if (session.isActive) {
            allMedia.filter { it.id in session.selectedIds }
        } else {
            allMedia
        }

        val eligible = items.filter { item ->
            val isPlayable = item.itemCount == null && AuraMediaCompatibilityEngine.isEligibleForImport(item.compatibilityStatus)
            val matchesFilter = when (activeFilter.uppercase()) {
                "PHOTO", "PHOTOS" -> item.mediaType.uppercase() in listOf("PHOTO", "IMAGE")
                "VIDEO", "VIDEOS" -> item.mediaType.uppercase() in listOf("VIDEO", "MOVIE")
                else -> true
            }
            isPlayable && matchesFilter
        }

        // Completion check for session
        if (session.isActive && !session.isComplete) {
            val eligibleCount = eligible.size

            val maxPossiblePairs = (eligibleCount * (eligibleCount - 1)) / 2
            val uniqueComparedPairs = session.comparedPairIds.filter { pair ->
                val itemA = items.find { it.id == pair.first }
                val itemB = items.find { it.id == pair.second }
                itemA != null && itemB != null && matchesMediaFilter(itemA, activeFilter) && matchesMediaFilter(itemB, activeFilter)
            }.map { 
                if (it.first < it.second) it.first to it.second else it.second to it.first 
            }.toSet().size

            if (eligibleCount < 2) {
                _compareSelectionSession.update { it.copy(isComplete = true, completionReason = "Fewer than 2 eligible items remain.") }
            } else if (session.roundNumber > session.maxRounds) {
                _compareSelectionSession.update { it.copy(isComplete = true, completionReason = "Session round limit reached.") }
            } else if (maxPossiblePairs > 0 && uniqueComparedPairs >= maxPossiblePairs) {
                _compareSelectionSession.update { it.copy(isComplete = true, completionReason = "All unique pairs exhausted.") }
            }

            // Re-read session after potential update
            if (_compareSelectionSession.value.isComplete) {
                _pairwiseState.value = PairwiseComparison(
                    id = "p_empty",
                    roundNumber = session.roundNumber,
                    totalRounds = session.maxRounds,
                    optionA = emptyMediaItem,
                    optionB = emptyMediaItem
                )
                updateDiagnostics(eligible, emptyList(), "Session complete: ${_compareSelectionSession.value.completionReason}")
                return
            }
        }

        if (eligible.size < 2) {
            _pairwiseState.value = PairwiseComparison(
                id = "p_empty",
                roundNumber = currentPair.roundNumber,
                totalRounds = 50,
                optionA = emptyMediaItem,
                optionB = emptyMediaItem
            )
            updateDiagnostics(eligible, emptyList(), "Insufficient candidates for filter $activeFilter")
            return
        }

        val top100Pool = RecommendationEngine.getTop100PairwiseCandidates(
            allMedia = items,
            winsMap = pairwiseWins,
            lossesMap = pairwiseLosses,
            mediaTypeFilter = activeFilter,
            tasteDNA = _tasteDNA.value,
            profile = _preferenceProfile.value,
            strategy = DiscoveryPolicyManager.resolveStrategy(
                policy = _discoveryPolicy.value,
                intent = _userIntent.value,
                objective = RecommendationObjective.RANKING_REFINEMENT,
                systemState = ConfidenceEngine.calculateDiscoveryState(items, _intelligenceStats.value),
                tasteDNA = _tasteDNA.value,
                profile = _preferenceProfile.value
            ),
            stats = _intelligenceStats.value,
            creatorProfiles = _creatorProfiles.value,
            compareStrategy = _compareStrategy.value,
            compareSort = _compareSort.value
        )

        val isOptionAMatching = matchesMediaFilter(currentPair.optionA, activeFilter) && eligible.any { it.id == currentPair.optionA.id }
        val isOptionBMatching = matchesMediaFilter(currentPair.optionB, activeFilter) && eligible.any { it.id == currentPair.optionB.id }
        val isOptionAInSelection = !session.isActive || session.selectedIds.contains(currentPair.optionA.id)
        val isOptionBInSelection = !session.isActive || session.selectedIds.contains(currentPair.optionB.id)

        if (!forceNextPair && currentPair.optionA.id.isNotEmpty() && currentPair.optionB.id.isNotEmpty() && isOptionAMatching && isOptionBMatching && isOptionAInSelection && isOptionBInSelection) {
            updateDiagnostics(eligible, top100Pool, "Initial pool loaded")
            return
        }

        val nextPair = RecommendationEngine.selectNextPairFromPool(
            top100Pool = top100Pool,
            comparisonCounts = comparisonCounts,
            recentPairs = if (session.isActive) session.comparedPairIds else recentPairs,
            recentItemIds = if (session.isActive) {
                session.comparedPairIds.flatMap { listOf(it.first, it.second) }.takeLast(20)
            } else recentItemIds,
            mediaTypeFilter = activeFilter,
            randomSeed = _librarySessionSeed.value,
            strategy = DiscoveryPolicyManager.resolveStrategy(
                policy = _discoveryPolicy.value,
                intent = _userIntent.value,
                objective = RecommendationObjective.RANKING_REFINEMENT,
                systemState = ConfidenceEngine.calculateDiscoveryState(items, _intelligenceStats.value),
                tasteDNA = _tasteDNA.value,
                profile = _preferenceProfile.value
            ),
            tasteDNA = _tasteDNA.value,
            creatorProfiles = _creatorProfiles.value,
            compareStrategy = _compareStrategy.value
        )

        if (nextPair != null) {
            val (itemA, itemB) = nextPair
            val newRound = if (session.isActive) session.roundNumber else (if (forceNextPair) currentPair.roundNumber + 1 else currentPair.roundNumber)
            _pairwiseState.value = PairwiseComparison(
                id = "p$newRound",
                roundNumber = newRound,
                totalRounds = if (session.isActive) session.maxRounds else 50,
                optionA = itemA,
                optionB = itemB
            )

            if (!session.isActive) {
                recentPairs.add(0, itemA.id to itemB.id)
                if (recentPairs.size > 10) recentPairs.removeAt(recentPairs.lastIndex)

                recentItemIds.add(0, itemA.id)
                recentItemIds.add(0, itemB.id)
                if (recentItemIds.size > 20) {
                    recentItemIds.removeAt(recentItemIds.lastIndex)
                    if (recentItemIds.isNotEmpty()) recentItemIds.removeAt(recentItemIds.lastIndex)
                }
            }

            val reason = "Selected Top-100 Candidate Pair ($activeFilter): Relevance + Information Value Exploration"
            updateDiagnostics(eligible, top100Pool, reason)
            
            // Phase 7: Record exposures for the selected pair
            recordExposures(listOf(itemA.id, itemB.id))
        } else {
            // SESSION COMPLETION: Pair Exhaustion
            if (session.isActive && !session.isComplete) {
                _compareSelectionSession.update { it.copy(isComplete = true, completionReason = "All eligible unique pairs have been compared.") }
            }

            _pairwiseState.value = PairwiseComparison(
                id = "p_empty",
                roundNumber = currentPair.roundNumber,
                totalRounds = 50,
                optionA = emptyMediaItem,
                optionB = emptyMediaItem
            )
            updateDiagnostics(eligible, top100Pool, "No pair candidates for filter $activeFilter")
        }
    }

    private fun matchesMediaFilter(item: MediaItem, filter: String): Boolean {
        if (item.id.isEmpty()) return false
        return when (filter.uppercase()) {
            "PHOTO", "PHOTOS" -> item.mediaType.uppercase() in listOf("PHOTO", "IMAGE")
            "VIDEO", "VIDEOS" -> item.mediaType.uppercase() in listOf("VIDEO", "MOVIE")
            else -> true
        }
    }

    private fun updateDiagnostics(
        eligible: List<MediaItem>,
        top100Pool: List<Pair<MediaItem, Float>>,
        reason: String
    ) {
        val comparedCount = top100Pool.count { (item, _) -> (comparisonCounts[item.id] ?: 0) > 0 }
        val neverCompared = top100Pool.size - comparedCount
        val topIds = top100Pool.take(10).map { it.first.id }

        _pairwiseDiagnostics.value = PairwiseDiagnostics(
            totalEligibleMedia = eligible.size,
            top100CandidatePoolSize = top100Pool.size,
            comparedCandidateCount = comparedCount,
            neverComparedCount = neverCompared,
            poolRefreshTimestamp = System.currentTimeMillis(),
            topCandidateIds = topIds,
            recentRepetitionCount = recentPairs.size,
            lastSelectionReason = reason
        )
    }


    fun setPlaylist(items: List<MediaItem>, initialIndex: Int, sourceTitle: String = "Playlist") {
        if (items.isEmpty()) {
            _activePlaylist.value = null
            return
        }
        
        // AURA PHASE 2: Reset session state for new selections
        _lastPlaybackPositionMs = 0L
        refreshPlaybackSessionId()
        _isResumingFromBackground = false
        _isPlayerActive.value = true
        
        // AURA P1 STABILITY: Authoritative Playlist Sanitization
        // Only verified playable terminal states are allowed in active playback.
        val visibleStatuses = listOf(
            CompatibilityStatus.PLAYABLE,
            CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE,
            CompatibilityStatus.PLAYABLE_AFTER_CONVERSION,
            CompatibilityStatus.THUMBNAIL_FAILED,
            CompatibilityStatus.NEEDS_TRANSCODE
        )
        val sanitized = items.filter { item ->
            !item.isDeleted && item.compatibilityStatus in visibleStatuses
        }
        
        if (sanitized.isEmpty()) {
            Log.w("PlaylistTrace", "Playlist became empty after sanitization.")
            _activePlaylist.value = null
            return
        }

        // Find the new index of the selected item in the sanitized list
        val originalItem = items.getOrNull(initialIndex)
        val validIndex = if (originalItem != null) {
            val indexInSanitized = sanitized.indexOfFirst { it.id == originalItem.id }
            // If the selected item was filtered out, find the nearest available or default to 0
            if (indexInSanitized != -1) indexInSanitized else 0
        } else {
            0
        }.coerceIn(0, sanitized.size - 1)

        _activePlaylist.value = PlaylistState(
            items = sanitized,
            currentIndex = validIndex,
            authoritativeMediaId = sanitized.getOrNull(validIndex)?.id,
            sourceTitle = sourceTitle
        )
        println("PlaylistTrace: Sanitized snapshot created: ${sanitized.size} items (from ${items.size}), starting at $validIndex. Source: $sourceTitle")
    }

    fun clearPlaylist() {
        // AURA PHASE 2: Clear session state on exit
        _lastPlaybackPositionMs = 0L
        _isResumingFromBackground = false
        _isPlayerActive.value = false
        _activePlaylist.value = null
    }

    fun selectPlaylistItem(index: Int) {
        _activePlaylist.update { current ->
            if (current != null && index in current.items.indices) {
                val nextItem = current.items[index]
                recordView(nextItem.id)
                Log.d("PlaylistTrace", "Selected index $index, item: ${nextItem.title}")
                current.copy(
                    currentIndex = index,
                    authoritativeMediaId = nextItem.id
                )
            } else current
        }
    }

    fun nextPlaylistItem() {
        _activePlaylist.value?.let { current ->
            if (current.hasNext) {
                selectPlaylistItem(current.currentIndex + 1)
            }
        }
    }

    fun previousPlaylistItem() {
        _activePlaylist.value?.let { current ->
            if (current.hasPrevious) {
                selectPlaylistItem(current.currentIndex - 1)
            }
        }
    }

    fun importMediaFromUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val db = database ?: return@launch
            val total = uris.size
            _importProgress.value = ImportProgressState(
                isImporting = true,
                processedFiles = 0,
                totalFiles = total,
                progressPercent = 0,
                statusText = "Starting import..."
            )

            val newEntities = mutableListOf<MediaEntity>()
            uris.forEachIndexed { index, uri ->
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // Ignore permission grant failure on some pickers
                    }

                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    // ENFORCE: Only Photos and Videos
                    val isImage = mimeType.startsWith("image/") || uri.toString().contains("image", ignoreCase = true)
                    val isVideo = mimeType.startsWith("video/") || uri.toString().contains("video", ignoreCase = true)

                    if (isImage || isVideo) {
                        var fileName = if (isVideo) "Video ${System.currentTimeMillis() % 10000}" else "Photo ${System.currentTimeMillis() % 10000}"
                        var sizeBytes = 0L

                        try {
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (cursor.moveToFirst()) {
                                    if (nameIdx != -1) cursor.getString(nameIdx)?.let { if (it.isNotBlank()) fileName = it }
                                    if (sizeIdx != -1) sizeBytes = cursor.getLong(sizeIdx)
                                }
                            }
                        } catch (e: Exception) {
                            // Fallback
                        }

                        val mediaType = if (isVideo) "VIDEO" else "PHOTO"
                        val report = AuraMediaCompatibilityEngine.analyzeMedia(context, uri.toString(), mediaType)

                        // Apply the centralized eligibility gate for manual imports
                        if (!AuraMediaCompatibilityEngine.isEligibleForImport(report.status)) {
                            Log.w("MediaRepository", "Manual Import: Rejecting ineligible media: $fileName (${report.status})")
                            rejectMedia(uri.toString(), fileName, mediaType, report)
                            return@forEachIndexed
                        }

                        // Phase 6: Content Identity
                        val finalSizeBytes = if (sizeBytes > 0) sizeBytes else report.sizeBytes
                        val contentHash = "v1_${finalSizeBytes}_${fileName.hashCode()}"

                        val entity = MediaEntity(
                            id = "import_${System.currentTimeMillis()}_${index}_${uri.hashCode()}",
                            title = fileName,
                            mediaType = mediaType,
                            year = 2024,
                            duration = if (isVideo) {
                                if (report.durationMs > 0) {
                                    val mins = (report.durationMs / 1000) / 60
                                    val secs = (report.durationMs / 1000) % 60
                                    if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                                } else "Video"
                            } else "Photo",
                            genre = "Personal Media",
                            imageUrl = uri.toString(),
                            rating = 0f,
                            category = "Fresh for You",
                            aiSummary = "Imported photo/video",
                            moodTagsJson = "Local,Imported",
                            uriPath = uri.toString(),
                            dateAdded = System.currentTimeMillis(),
                            sizeBytes = finalSizeBytes,
                            durationMs = report.durationMs,
                            width = report.width,
                            height = report.height,
                            contentHash = contentHash,
                            compatibilityStatus = report.status.name,
                            containerFormat = report.containerFormat,
                            videoCodec = report.videoCodec,
                            audioCodec = report.audioCodec,
                            compatibilityReason = report.compatibilityReason,
                            conversionStatus = report.conversionStatus.name,
                            convertedUri = report.convertedUri ?: "",
                            lastCompatibilityCheckTimestamp = System.currentTimeMillis()
                        )
                        newEntities.add(entity)
                    }

                    val processed = index + 1
                    val percent = ((processed.toFloat() / total.toFloat()) * 100).toInt()
                    _importProgress.value = ImportProgressState(
                        isImporting = true,
                        processedFiles = processed,
                        totalFiles = total,
                        progressPercent = percent,
                        statusText = "Importing $processed of $total files ($percent%)"
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException || e is java.util.concurrent.CancellationException) throw e
                    // Skip invalid file
                }
            }

            if (newEntities.isNotEmpty()) {
                db.mediaDao().insertAll(newEntities)
            }

            _importProgress.value = ImportProgressState(
                isImporting = false,
                processedFiles = total,
                totalFiles = total,
                progressPercent = 100,
                statusText = "Import complete!"
            )
        }
    }

    suspend fun scanLocalMedia(context: Context): Boolean {
        val scanId = System.currentTimeMillis()
        Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] scanLocalMedia() called.")

        // 0. Database Readiness Gate
        if (_databaseState.value != DatabaseState.READY) {
            Log.w("AURA_SCAN_RUNTIME", "[$scanId] [REPO] Scan aborted: Database not ready (State: ${_databaseState.value})")
            _scanProgress.value = ScanProgressState(
                scanSessionId = scanId,
                isScanning = false,
                errorCode = ScanError.DATABASE_ERROR,
                statusText = "Secure database is initializing. Please wait."
            )
            return false
        }
        
        // 1. Permission Verification Gate
        val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            Log.w("AURA_SCAN_RUNTIME", "[$scanId] [REPO] Scan aborted: PERMISSION_DENIED")
            _scanProgress.value = ScanProgressState(
                scanSessionId = scanId,
                isScanning = false, 
                errorCode = ScanError.PERMISSION_DENIED,
                statusText = "Permissions required to scan media."
            )
            return false
        }

        // Only allow one active scan at a time. If one is running, join it or wait.
        if (activeScanJob?.isActive == true) {
            Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] Joining already active scan job.")
            activeScanJob?.join()
            return false 
        }
        
        var changed = false
        activeScanJob = scope.launch {
            try {
                Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] Scan coroutine started on thread: ${Thread.currentThread().name}")
                val initialCount = _mediaItems.value.size
                Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] Initial _mediaItems count: $initialCount")
                
                _scanProgress.value = ScanProgressState(scanSessionId = scanId, isScanning = true, statusText = "Discovering media...")
                
                // Yield to allow cancellation to be processed
                kotlinx.coroutines.yield()
                
                // Phase 1: Discovery (Fast)
                Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] discoverLocalMedia() starting.")
                val discoveryResult = try {
                    discoverLocalMedia(context)
                } catch (e: SecurityException) {
                    Log.e("AURA_SCAN_RUNTIME", "[$scanId] [REPO] Storage Access Blocked by System", e)
                    _scanProgress.value = ScanProgressState(scanSessionId = scanId, isScanning = false, errorCode = ScanError.STORAGE_ACCESS_FAILED, statusText = "Storage access blocked.")
                    return@launch
                }
                
                if (discoveryResult is DiscoveryResult.Incomplete) {
                    Log.e("AURA_SCAN_RUNTIME", "[$scanId] [REPO] Discovery incomplete: ${discoveryResult.reason}")
                    _scanProgress.value = ScanProgressState(
                        scanSessionId = scanId,
                        isScanning = false,
                        errorCode = discoveryResult.errorCode,
                        statusText = discoveryResult.reason
                    )
                    return@launch
                }

                // CHECK FOR CANCELLATION after heavy discovery
                if (!isActive) throw kotlinx.coroutines.CancellationException("Cancelled after discovery")

                val result = discoveryResult as DiscoveryResult.Complete
                val discoveredEntities = result.entities
                val allDiscoveredIds = result.discoveredIds

                Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] discoverLocalMedia() complete. Entities to insert: ${discoveredEntities.size}, Total discovered IDs: ${allDiscoveredIds.size}")
                
                if (discoveredEntities.isNotEmpty()) {
                    database?.mediaDao()?.insertAll(discoveredEntities)
                    Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] Database insertAll complete.")
                    changed = true
                }

                // Phase 2: Reconciliation (Delete items no longer on device)
                // PRE-RECONCILIATION CANCELLATION CHECK
                if (!isActive) throw kotlinx.coroutines.CancellationException("Cancelled before reconciliation")
                
                Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] reconcileDeletions() starting.")
                reconcileDeletions(discoveryResult)
                Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] reconcileDeletions() complete.")
                
                val afterReconcile = database?.mediaDao()?.getCount() ?: 0
                Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] DB count after reconciliation: $afterReconcile")
                
                if (afterReconcile != initialCount || changed) {
                    changed = true
                }

                // Phase 3: Processing (Heavy)
                if (!isActive) throw kotlinx.coroutines.CancellationException("Cancelled before processing")

                // AURA P1 STABILITY: Readiness Transition
                // Once discovery Batch 1 is in DB, mark UI as interactive.
                // Filtered flows will start emitting as items transition from ANALYSIS_PENDING to terminal states.
                _isLibraryReady.value = true

                Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] processPendingMedia() starting.")
                processPendingMedia(context, scanId, result.scannedVolumes)
                Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] processPendingMedia() complete.")
                
                val finalCount = database?.mediaDao()?.getCount() ?: 0
                Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] Scan lifecycle complete. Final DB count: $finalCount, changed=$changed")
                _scanProgress.value = ScanProgressState(scanSessionId = scanId, isScanning = false, isComplete = true, totalCount = finalCount, statusText = "Scan complete.")

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException || e is java.util.concurrent.CancellationException) {
                    Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] Scan coroutine CANCELLED. Cause: ${e.message}")
                    _scanProgress.value = _scanProgress.value.copy(scanSessionId = scanId, isScanning = false, isComplete = false, statusText = "Scan cancelled")
                } else {
                    Log.e("AURA_SCAN_RUNTIME", "[$scanId] [REPO] Scan lifecycle EXCEPTION: ${e.javaClass.simpleName}", e)
                    
                    val errorCode = when {
                        e.message?.contains("Migration") == true || e is IllegalStateException -> ScanError.MIGRATION_FAILED
                        e is com.example.data.db.DatabaseSecurityException -> ScanError.ENCRYPTION_ERROR
                        e is android.database.sqlite.SQLiteException -> ScanError.DATABASE_CORRUPT
                        e is java.io.IOException -> ScanError.STORAGE_ACCESS_FAILED
                        else -> ScanError.DATABASE_ERROR
                    }

                    // Build a detailed cause chain for logging and debug UI
                    val causeChain = StringBuilder(e.message ?: "No error message")
                    var currentCause = e.cause
                    while (currentCause != null) {
                        causeChain.append(" -> ").append(currentCause.javaClass.simpleName).append(": ").append(currentCause.message)
                        currentCause = currentCause.cause
                    }

                    _scanProgress.value = ScanProgressState(
                        scanSessionId = scanId,
                        isScanning = false, 
                        errorCode = errorCode, 
                        statusText = causeChain.toString()
                    )
                }
            }
        }
        activeScanJob?.join()
        Log.d("AURA_SCAN_RUNTIME", "[$scanId] [REPO] scanLocalMedia() joining complete. Returning changed=$changed")
        return changed
    }

    private suspend fun discoverLocalMedia(context: Context): DiscoveryResult {
        val db = database ?: return DiscoveryResult.Incomplete("Database not ready", ScanError.DATABASE_ERROR)
        
        val entities = mutableListOf<MediaEntity>()
        val allDiscoveredIds = mutableSetOf<String>()
        val scannedVolumes = mutableSetOf<String>()
        val scannedMediaTypes = mutableSetOf<String>()
        
        Log.d("AURA_SCAN_RUNTIME", "[REPO] discoverLocalMedia: Fetching current items.")
        val currentItems = db.mediaDao().getAllMediaIncludingDeletedSync().associateBy { it.id }
        
        // Volume-Aware Strategy: Iterate through all available external volumes (Primary, SD cards, etc.)
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context)
        } else {
            setOf("external")
        }
        
        Log.d("AURA_SCAN_RUNTIME", "[REPO] discoverLocalMedia: Scanning volumes: $volumes")

        val now = System.currentTimeMillis()
        val retryWindow24h = 24 * 60 * 60 * 1000L
        val retryWindow7d = 7 * 24 * 60 * 60 * 1000L

        volumes.forEach { volumeName ->
            // 1. Scan Videos for this volume
            val videoUri = MediaStore.Video.Media.getContentUri(volumeName)
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.SIZE
            )
            
            val videoCursor = try {
                context.contentResolver.query(videoUri, videoProjection, null, null, null)
            } catch (e: Exception) {
                return DiscoveryResult.Incomplete("Video query failed for volume $volumeName", ScanError.QUERY_FAILURE, e)
            }

            if (videoCursor == null) {
                return DiscoveryResult.Incomplete("Video query returned null for volume $volumeName", ScanError.QUERY_FAILURE)
            }

            videoCursor.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val modColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    
                    val id = cursor.getLong(idColumn)
                    val localId = "local_vid_$id"
                    val contentUri = ContentUris.withAppendedId(videoUri, id)
                    val uriString = contentUri.toString()
                    allDiscoveredIds.add(localId)

                    val title = cursor.getString(titleColumn) ?: "Video $id"
                    val durationMs = cursor.getLong(durationColumn)
                    val dateAdded = cursor.getLong(dateColumn) * 1000
                    val dateModified = cursor.getLong(modColumn) * 1000
                    val sizeBytes = cursor.getLong(sizeColumn)

                    val existing = currentItems[localId]
                    if (existing != null && existing.dateModified == dateModified && existing.sizeBytes == sizeBytes) {
                        val status = try { CompatibilityStatus.valueOf(existing.compatibilityStatus) } catch(e: Exception) { CompatibilityStatus.ANALYSIS_PENDING }
                        val lastCheck = existing.lastCompatibilityCheckTimestamp ?: 0L
                        
                        // Terminal Success or Already Replaced -> Skip
                        val isPlayable = status == CompatibilityStatus.PLAYABLE || 
                                        status == CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE || 
                                        status == CompatibilityStatus.PLAYABLE_AFTER_CONVERSION ||
                                        status == CompatibilityStatus.THUMBNAIL_FAILED ||
                                        status == CompatibilityStatus.REPLACED
                        
                        if (isPlayable) continue

                        // Terminal Failures -> Skip unless window expired
                        val isTerminalFailure = status == CompatibilityStatus.UNSUPPORTED || status == CompatibilityStatus.CORRUPT || status == CompatibilityStatus.UNREADABLE
                        if (isTerminalFailure && (now - lastCheck) < retryWindow7d) continue

                        // Processing Failures -> Skip unless window expired
                        val isRetryableFailure = status == CompatibilityStatus.ANALYSIS_FAILED
                        if (isRetryableFailure && (now - lastCheck) < retryWindow24h) continue
                    }

                    val minutes = (durationMs / 1000) / 60
                    val seconds = (durationMs / 1000) % 60
                    val durationStr = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

                    entities.add(MediaEntity(
                        id = localId, title = title, mediaType = "VIDEO", year = 2024,
                        duration = durationStr, durationMs = durationMs, genre = "Local Media",
                        imageUrl = uriString, uriPath = uriString, dateAdded = dateAdded,
                        dateModified = dateModified, sizeBytes = sizeBytes,
                        compatibilityStatus = CompatibilityStatus.ANALYSIS_PENDING.name
                    ))
                }
            }
            scannedMediaTypes.add("VIDEO")

            // 2. Scan Photos for this volume
            val imageUri = MediaStore.Images.Media.getContentUri(volumeName)
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.TITLE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE
            )

            val imageCursor = try {
                context.contentResolver.query(imageUri, imageProjection, null, null, null)
            } catch (e: Exception) {
                return DiscoveryResult.Incomplete("Image query failed for volume $volumeName", ScanError.QUERY_FAILURE, e)
            }

            if (imageCursor == null) {
                return DiscoveryResult.Incomplete("Image query returned null for volume $volumeName", ScanError.QUERY_FAILURE)
            }

            imageCursor.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.TITLE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val modColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

                    val id = cursor.getLong(idColumn)
                    val localId = "local_img_$id"
                    val contentUri = ContentUris.withAppendedId(imageUri, id)
                    val uriString = contentUri.toString()
                    allDiscoveredIds.add(localId)

                    val title = cursor.getString(titleColumn) ?: "Photo $id"
                    val dateAdded = cursor.getLong(dateColumn) * 1000
                    val dateModified = cursor.getLong(modColumn) * 1000
                    val sizeBytes = cursor.getLong(sizeColumn)

                    val existing = currentItems[localId]
                    if (existing != null && existing.dateModified == dateModified && existing.sizeBytes == sizeBytes) {
                        val status = try { CompatibilityStatus.valueOf(existing.compatibilityStatus) } catch(e: Exception) { CompatibilityStatus.ANALYSIS_PENDING }
                        if (status == CompatibilityStatus.PLAYABLE) continue
                    }

                    entities.add(MediaEntity(
                        id = localId, title = title, mediaType = "PHOTO", year = 2024,
                        duration = "Photo", genre = "Local Photo", imageUrl = uriString,
                        uriPath = uriString, dateAdded = dateAdded, dateModified = dateModified,
                        sizeBytes = sizeBytes, compatibilityStatus = CompatibilityStatus.PLAYABLE.name
                    ))
                }
            }
            scannedMediaTypes.add("PHOTO")
            scannedVolumes.add(volumeName)
        }

        return DiscoveryResult.Complete(entities, allDiscoveredIds, scannedVolumes, scannedMediaTypes)
    }

    internal suspend fun reconcileDeletions(discoveryResult: DiscoveryResult) {
        val db = database ?: return
        
        if (discoveryResult !is DiscoveryResult.Complete) {
            Log.w("AURA_SCAN_RUNTIME", "[REPO] reconcileDeletions aborted: Discovery was not complete/authoritative.")
            return
        }
        
        val result = discoveryResult
        val allDiscoveredIds = result.discoveredIds
        val scannedVolumes = result.scannedVolumes
        val scannedMediaTypes = result.scannedMediaTypes

        val currentItems = db.mediaDao().getAllMediaSync()
        val toDelete = mutableListOf<String>()
        var skippedCount = 0
        
        currentItems.forEach { item ->
            // Periodic cancellation check for large libraries
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            
            // Only reconcile local items
            if (item.id.startsWith("local_")) {
                val itemVolume = extractVolumeFromUri(item.uriPath)
                val itemType = item.mediaType // "PHOTO" or "VIDEO"
                
                val isVolumeVerified = itemVolume != null && scannedVolumes.contains(itemVolume)
                val isTypeVerified = scannedMediaTypes.contains(itemType)

                if (isVolumeVerified && isTypeVerified) {
                    // Item belongs to an authoritatively scanned scope.
                    // If it was not discovered, it is a legitimate deletion.
                    if (item.id !in allDiscoveredIds) {
                        toDelete.add(item.id)
                    }
                } else {
                    // Item is outside the verified scan scope (e.g. unmounted volume or missing media type)
                    // PRESERVE to prevent data loss due to incomplete scan.
                    skippedCount++
                }
            }
        }
        
        if (toDelete.isNotEmpty()) {
            db.mediaDao().deleteByIds(toDelete)
            toDelete.forEach { deleteSemanticDataForMedia(it) }
            Log.d("AURA_SCAN_RUNTIME", "[REPO] reconcileDeletions complete. Purged ${toDelete.size} items via batch delete.")
        }
        
        Log.d("AURA_SCAN_RUNTIME", "[REPO] reconcileDeletions summary: Purged ${toDelete.size} items. Preserved $skippedCount items outside verified scope. Total DB items: ${db.mediaDao().getCount()}")
    }

    /**
     * Extracts the volume name from a MediaStore URI string.
     * e.g. content://media/external_primary/video/media/123 -> external_primary
     */
    private fun extractVolumeFromUri(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.authority == "media") {
                uri.pathSegments.firstOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    internal suspend fun processPendingMedia(context: Context, scanSessionId: Long, scannedVolumes: Set<String>) {
        val db = database ?: return
        val pending = db.mediaDao().getPendingAnalysis()
        val total = pending.size
        
        if (total == 0) {
            _scanProgress.value = ScanProgressState(scanSessionId = scanSessionId, isScanning = false, isComplete = true, statusText = "Library up to date")
            return
        }

        _scanProgress.value = ScanProgressState(
            scanSessionId = scanSessionId,
            isScanning = true,
            totalCount = total,
            statusText = "Analyzing $total new files..."
        )

        val rejectedItems = db.rejectedMediaDao().getAllRejectedMediaSync()
        val rejectedHashes = rejectedItems.mapNotNull { it.contentHash }.toSet()

        val batch = mutableListOf<MediaEntity>()
        val batchSize = 25

        pending.forEachIndexed { index, entity ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            
            _scanProgress.update { it.copy(
                statusText = "Processing ${index + 1} of $total: ${entity.title}",
                processedCount = index
            )}

            try {
                val report = AuraMediaCompatibilityEngine.analyzeMedia(context, entity.uriPath, entity.mediaType)
                
                if (AuraMediaCompatibilityEngine.isEligibleForImport(report.status)) {
                    // Phase 6: Content Identity - Generate a simple hash for deduplication
                    // Use size and title as a stable content identifier
                    val contentHash = "v1_${entity.sizeBytes}_${entity.title.hashCode()}"
                    
                    if (rejectedHashes.contains(contentHash)) {
                        Log.i("MediaRepository", "ProcessPending: Skipping re-import of previously rejected/deleted item: ${entity.title}")
                        db.mediaDao().deleteById(entity.id)
                        deleteSemanticDataForMedia(entity.id)
                        return@forEachIndexed
                    }

                    val updatedEntity = entity.copy(
                        durationMs = if (entity.durationMs > 0) entity.durationMs else report.durationMs,
                        width = report.width,
                        height = report.height,
                        contentHash = contentHash,
                        compatibilityStatus = report.status.name,
                        containerFormat = report.containerFormat,
                        videoCodec = report.videoCodec,
                        audioCodec = report.audioCodec,
                        compatibilityReason = report.compatibilityReason,
                        conversionStatus = report.conversionStatus.name,
                        convertedUri = report.convertedUri ?: "",
                        lastCompatibilityCheckTimestamp = System.currentTimeMillis()
                    )
                    batch.add(updatedEntity)
                    
                    if (batch.size >= batchSize) {
                        db.mediaDao().updateAll(batch)
                        processSemanticsForBatch(batch)
                        batch.clear()
                    }
                } else {
                    // ONLY reject if the status is explicitly ineligible (unsupported/corrupt)
                    // and NOT if it just failed analysis due to timeout/crash.
                    val isPermanentFailure = report.status == CompatibilityStatus.UNSUPPORTED || 
                                            report.status == CompatibilityStatus.CORRUPT || 
                                            report.status == CompatibilityStatus.UNREADABLE
                                            
                    if (isPermanentFailure) {
                        // Safety: If it's UNREADABLE, verify the volume is actually still scanned/mounted
                        // to avoid deleting items during a transient unmount between discovery and processing.
                        val itemVolume = extractVolumeFromUri(entity.uriPath)
                        if (report.status == CompatibilityStatus.UNREADABLE && itemVolume != null && !scannedVolumes.contains(itemVolume)) {
                            Log.w("MediaRepository", "ProcessPending: Item ${entity.title} unreadable but volume $itemVolume not verified. Skipping deletion.")
                            return@forEachIndexed
                        }

                        Log.w("MediaRepository", "ProcessPending: Permanently rejecting ineligible media: ${entity.title} (${report.status})")
                        rejectMedia(entity.uriPath, entity.title, entity.mediaType, report)
                        db.mediaDao().deleteById(entity.id)
                        deleteSemanticDataForMedia(entity.id)
                    } else {
                        Log.w("MediaRepository", "ProcessPending: Transient failure for ${entity.title} (${report.status}). Retaining in DB for retry.")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException || e is java.util.concurrent.CancellationException) throw e
                Log.e("MediaRepository", "ProcessPending: Error analyzing ${entity.title}. Skipping without rejection.", e)
            }
        }

        if (batch.isNotEmpty()) {
            db.mediaDao().updateAll(batch)
            processSemanticsForBatch(batch)
        }

        _scanProgress.value = ScanProgressState(
            scanSessionId = scanSessionId,
            isScanning = false,
            isComplete = true,
            processedCount = total,
            totalCount = total,
            statusText = "Scan complete — $total items processed"
        )
    }

    fun cancelScan() {
        activeScanJob?.cancel()
        _scanProgress.update { it.copy(isScanning = false, isComplete = false, statusText = "Scan cancelled") }
    }

    /**
     * Releases semantic and neural resources.
     */
    fun shutdown() {
        embeddingProvider?.close()
        semanticSearchService = null
        hybridSearchEngine = null
    }

    fun scheduleBackgroundScan(context: Context) {
        try {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<MediaScannerWorker>().build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "AuraMediaScannerWork",
                androidx.work.ExistingWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            // WorkManager fallback
        }
    }

    fun recordPlaybackError(
        error: androidx.media3.common.PlaybackException,
        player: androidx.media3.common.Player,
        mediaItem: MediaItem?
    ) {
        val repo = playbackErrorLogRepository ?: return
        
        scope.launch {
            try {
                val appVer = try {
                    applicationContext?.packageManager?.getPackageInfo(applicationContext?.packageName ?: "", 0)?.versionName ?: "1.0.0"
                } catch (e: Exception) { "1.0.0" }
                
                val diagnosticRecord = com.example.util.AuraPlaybackDiagnostics.captureError(
                    error = error,
                    player = player,
                    mediaItem = mediaItem,
                    sessionId = _playbackSessionId,
                    appVersion = appVer
                )
                
                Log.e("AuraPlaybackDiagnostics", "Recording playback error: ${diagnosticRecord.diagnosticSummary} (Session: ${_playbackSessionId})")
                repo.recordError(diagnosticRecord)
            } catch (t: Throwable) {
                Log.e("AuraPlaybackDiagnostics", "Critical failure in diagnostic collector: ${t.message}", t)
                // Fail-safe: do not crash the player
            }
        }
    }

    fun recordView(id: String) {
        _mediaItems.value.find { it.id == id }?.let { logInteraction(it) }
    }

    fun recordExposure(id: String) {
        scope.launch {
            val timestamp = System.currentTimeMillis()
            _mediaItems.update { items ->
                items.map { item ->
                    if (item.id == id) {
                        item.copy(
                            lastExposedTimestamp = timestamp,
                            exposureCount = item.exposureCount + 1
                        )
                    } else item
                }
            }
            database?.mediaDao()?.let { dao ->
                val entity = dao.getMediaById(id)
                if (entity != null) {
                    dao.update(
                        entity.copy(
                            exposureCount = entity.exposureCount + 1,
                            lastExposedTimestamp = timestamp
                        )
                    )
                }
            }
        }
    }

    private val exposureBuffer = mutableSetOf<String>()
    private var exposureFlushJob: Job? = null

    fun recordExposures(ids: List<String>) {
        if (ids.isEmpty()) return
        
        synchronized(exposureBuffer) {
            exposureBuffer.addAll(ids)
        }

        // Update in-memory state immediately for UI responsiveness
        _mediaItems.update { items ->
            val timestamp = System.currentTimeMillis()
            items.map { item ->
                if (item.id in ids) {
                    item.copy(
                        lastExposedTimestamp = timestamp,
                        exposureCount = item.exposureCount + 1
                    )
                } else item
            }
        }

        // Schedule persistent flush
        if (exposureFlushJob?.isActive != true) {
            exposureFlushJob = scope.launch {
                kotlinx.coroutines.delay(5000) // Buffer for 5 seconds
                flushExposures()
            }
        }
    }

    fun forceFlushExposures() {
        scope.launch {
            flushExposures()
        }
    }

    private suspend fun flushExposures() {
        val idsToFlush = synchronized(exposureBuffer) {
            val copy = exposureBuffer.toList()
            exposureBuffer.clear()
            copy
        }

        if (idsToFlush.isEmpty()) return

        val timestamp = System.currentTimeMillis()
        database?.mediaDao()?.let { dao ->
            val entitiesToUpdate = mutableListOf<com.example.data.db.MediaEntity>()
            idsToFlush.forEach { id ->
                dao.getMediaById(id)?.let { entity ->
                    entitiesToUpdate.add(
                        entity.copy(
                            exposureCount = entity.exposureCount + 1,
                            lastExposedTimestamp = timestamp
                        )
                    )
                }
            }
            if (entitiesToUpdate.isNotEmpty()) {
                Log.d("AuraPerformance", "Flushing ${entitiesToUpdate.size} exposures to database.")
                dao.updateAll(entitiesToUpdate)
            }
        }
    }

    fun updateRating(id: String, rating: Float) {
        scope.launch {
            var updatedItem: MediaItem? = null
            _mediaItems.update { items ->
                items.map { item ->
                    if (item.id == id) {
                        val newItem = item.copy(rating = rating)
                        updatedItem = newItem
                        newItem
                    } else item
                }
            }
            
            database?.mediaDao()?.let { dao ->
                val entity = dao.getMediaById(id)
                if (entity != null) {
                    dao.update(entity.copy(rating = rating))
                }
            }

            // Phase 3A: Enqueue sanitized telemetry if consent is granted
            contributionQueueRepository?.let { repo ->
                if (repo.isConsentGranted()) {
                    com.example.data.contribution.ContributionDataSanitizer.sanitizeTelemetryEvent(
                        interactionType = "RATING",
                        feedbackCategory = "EXPLICIT_FEEDBACK",
                        score = (rating / 5.0).toDouble()
                    )?.let { payload ->
                        repo.enqueueRecommendationFeedback(payload)
                    }
                }
            }

            // Automatic Taste DNA Learning from explicit rating
            val item = updatedItem
            val dna = _tasteDNA.value
            if (item != null && dna.isFineTuningEnabled && rating > 0) {
                val adjustments = PersonalizationTraitMapper.getEffectiveTraitAdjustments(item)
                var updatedDna = dna
                
                // Calibration direction based on star rating
                // 4-5 stars: Positive reinforcement (1.0x)
                // 3 stars: Neutral (0.0x)
                // 1-2 stars: Negative reinforcement (-1.0x)
                val sentimentDirection = when {
                    rating >= 4f -> 1.0
                    rating == 3f -> 0.0
                    else -> -1.0
                }

                if (sentimentDirection != 0.0) {
                    adjustments.forEach { (dim, multiplier) ->
                        val amount = multiplier * MAX_ADJUSTMENT_PER_VOTE * sentimentDirection
                        updatedDna = updatedDna.updateLearnedDimension(dim, amount, TOTAL_ADJUSTMENT_LIMIT)
                    }
                    
                    if (updatedDna != dna) {
                        updateTasteDNA(updatedDna, isUserGenerated = false, evidenceCategory = "Explicit Rating ($rating stars)")
                    }
                }
            }
        }
    }

    fun recordMicroMoment(mediaId: String, tapCount: Int) {
        scope.launch {
            database?.microMomentDao()?.insertMoment(
                MicroMomentEntity(
                    mediaId = mediaId,
                    tapCount = tapCount,
                    timestamp = System.currentTimeMillis()
                )
            )

            _intelligenceStats.update { stats ->
                stats.copy(
                    personalizationScore = (stats.personalizationScore + 1).coerceAtMost(100)
                )
            }
        }
    }

    fun recordCleanupSignal(mediaId: String, category: String, score: Float, isDelete: Boolean) {
        scope.launch {
            // Signal type: cleanup_delete_signal or cleanup_keep_signal
            val signalType = if (isDelete) "cleanup_delete_signal" else "cleanup_keep_signal"
            
            // Log for intelligence pipeline
            Log.i("AuraIntelligence", "Signal Recorded: $signalType (ID: $mediaId, Cat: $category, Score: $score)")
            
            // Phase 5: Direct impact on personalization
            // If deleted, we should reduce affinity for similar traits in Taste DNA
            // If kept, we should increase affinity.
            val item = getMediaItemById(mediaId)
            if (item != null && tasteDNA.value.isFineTuningEnabled) {
                val adjustments = PersonalizationTraitMapper.getEffectiveTraitAdjustments(item)
                val direction = if (isDelete) -1.0 else 1.0
                val multiplier = 0.05 // Significant impact for direct cleanup decisions
                
                var updatedDna = tasteDNA.value
                adjustments.forEach { (dim, amount) ->
                    updatedDna = updatedDna.updateLearnedDimension(dim, amount * direction * multiplier, TOTAL_ADJUSTMENT_LIMIT)
                }
                
                if (updatedDna != tasteDNA.value) {
                    updateTasteDNA(updatedDna, isUserGenerated = false, evidenceCategory = "Cleanup Decision ($category)")
                }
            }
        }
    }

    fun recordLike(id: String) {
        scope.launch {
            // Cumulative Like Evidence: Append exactly one durable Like event
            database?.microMomentDao()?.insertMoment(
                com.example.data.db.MicroMomentEntity(
                    mediaId = id,
                    tapCount = 1,
                    timestamp = System.currentTimeMillis()
                )
            )

            // Contribution / telemetry if consent is granted
            contributionQueueRepository?.let { repo ->
                if (repo.isConsentGranted()) {
                    com.example.data.contribution.ContributionDataSanitizer.sanitizeTelemetryEvent(
                        interactionType = "LIKE",
                        feedbackCategory = "PREFERENCE",
                        score = 1.0
                    )?.let { payload ->
                        repo.enqueueRecommendationFeedback(payload)
                    }
                }
            }
        }
    }

    /**
     * Non-blocking asynchronous enqueue for visual context analysis of a video Like.
     * Guaranteed best-effort: failure never interferes with Like recording or playback.
     */
    fun enqueueVisualLikeContext(
        mediaId: String,
        uri: String,
        playbackPositionMs: Long,
        durationMs: Long
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                visualContextEngine.processLikeContext(
                    mediaId = mediaId,
                    uri = uri,
                    playbackPositionMs = playbackPositionMs,
                    durationMs = durationMs,
                    context = applicationContext
                )
            } catch (e: Exception) {
                Log.e("MediaRepository", "Error in visual like context processing: ${e.message}", e)
            }
        }
    }

    fun addToFavorites(id: String) {
        var updatedItem: MediaItem? = null
        _mediaItems.update { items ->
            items.map { item ->
                if (item.id == id && !item.isFavorite) {
                    val updated = item.copy(isFavorite = true)
                    updatedItem = updated
                    updated
                } else item
            }
        }

        val updated = updatedItem ?: return
        scope.launch {
            database?.mediaDao()?.update(updated.toEntity())

            contributionQueueRepository?.let { repo ->
                if (repo.isConsentGranted()) {
                    com.example.data.contribution.ContributionDataSanitizer.sanitizeTelemetryEvent(
                        interactionType = "FAVORITE",
                        feedbackCategory = "PREFERENCE",
                        score = 1.0
                    )?.let { payload ->
                        repo.enqueueRecommendationFeedback(payload)
                    }
                }
            }

            // Automatic Taste DNA Learning from Favorite addition (Priority 1 Invariant)
            val dna = _tasteDNA.value
            if (dna.isFineTuningEnabled) {
                val adjustments = PersonalizationTraitMapper.getEffectiveTraitAdjustments(updated)
                var updatedDna = dna
                val direction = 1.0

                adjustments.forEach { (dim, multiplier) ->
                    val amount = multiplier * MAX_ADJUSTMENT_PER_VOTE * direction
                    updatedDna = updatedDna.updateLearnedDimension(dim, amount, TOTAL_ADJUSTMENT_LIMIT)
                }

                if (updatedDna != dna) {
                    updateTasteDNA(updatedDna, isUserGenerated = false, evidenceCategory = "Favorite Added")
                }
            }
        }
    }

    fun removeFromFavorites(id: String) {
        var updatedItem: MediaItem? = null
        _mediaItems.update { items ->
            items.map { item ->
                if (item.id == id && item.isFavorite) {
                    val updated = item.copy(isFavorite = false)
                    updatedItem = updated
                    updated
                } else item
            }
        }

        val updated = updatedItem ?: return
        scope.launch {
            database?.mediaDao()?.update(updated.toEntity())

            contributionQueueRepository?.let { repo ->
                if (repo.isConsentGranted()) {
                    com.example.data.contribution.ContributionDataSanitizer.sanitizeTelemetryEvent(
                        interactionType = "UNFAVORITE",
                        feedbackCategory = "PREFERENCE",
                        score = 0.0
                    )?.let { payload ->
                        repo.enqueueRecommendationFeedback(payload)
                    }
                }
            }
            // Invariant: NEVER delete or decrement micro_moments. Zero negative Taste DNA learning.
        }
    }

    fun toggleFavorite(id: String) {
        var updatedItem: MediaItem? = null
        _mediaItems.update { items ->
            items.map { item ->
                if (item.id == id) {
                    val updated = item.copy(isFavorite = !item.isFavorite)
                    updatedItem = updated
                    updated
                } else item
            }
        }

        val updated = updatedItem ?: return
        scope.launch {
            database?.mediaDao()?.update(updated.toEntity())
            
            // Phase 3A: Enqueue sanitized telemetry if consent is granted
            contributionQueueRepository?.let { repo ->
                if (repo.isConsentGranted()) {
                    com.example.data.contribution.ContributionDataSanitizer.sanitizeTelemetryEvent(
                        interactionType = if (updated.isFavorite) "FAVORITE" else "UNFAVORITE",
                        feedbackCategory = "PREFERENCE",
                        score = if (updated.isFavorite) 1.0 else 0.0
                    )?.let { payload ->
                        repo.enqueueRecommendationFeedback(payload)
                    }
                }
            }

            // Cumulative Like Evidence: Record exactly one durable Like event on Favorite OFF -> ON
            if (updated.isFavorite) {
                database?.microMomentDao()?.insertMoment(
                    com.example.data.db.MicroMomentEntity(
                        mediaId = updated.id,
                        tapCount = 1,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            // Automatic Taste DNA Learning from Favorite toggle
            val dna = _tasteDNA.value
            if (updated.isFavorite && dna.isFineTuningEnabled) {
                val adjustments = PersonalizationTraitMapper.getEffectiveTraitAdjustments(updated)
                var updatedDna = dna
                val direction = 1.0

                adjustments.forEach { (dim, multiplier) ->
                    val amount = multiplier * MAX_ADJUSTMENT_PER_VOTE * direction
                    updatedDna = updatedDna.updateLearnedDimension(dim, amount, TOTAL_ADJUSTMENT_LIMIT)
                }

                if (updatedDna != dna) {
                    updateTasteDNA(updatedDna, isUserGenerated = false, evidenceCategory = "Favorite Added")
                }
            }
        }
    }

    suspend fun getLikeCount(id: String): Int {
        return database?.microMomentDao()?.getMomentCountForMedia(id) ?: 0
    }

    @androidx.annotation.VisibleForTesting
    fun setDatabaseForTesting(db: AuraDatabase) {
        this.database = db
    }

    fun getMediaItemById(id: String): MediaItem? {
        return _mediaItems.value.find { it.id == id }
    }

    fun deleteMediaItem(id: String) {
        _mediaItems.update { items ->
            items.filterNot { it.id == id }
        }
        
        // Remove from active session if present
        if (_compareSelectionSession.value.isActive) {
            _compareSelectionSession.update { session ->
                if (id in session.selectedIds) {
                    session.copy(selectedIds = session.selectedIds - id)
                } else session
            }
            refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = false)
        }

        scope.launch {
            database?.mediaDao()?.deleteById(id)
            deleteSemanticDataForMedia(id)
        }
        
        // AURA P1 STABILITY: Identity-Aware Playlist Deletion
        _activePlaylist.update { playlist ->
            if (playlist == null) return@update null

            val updatedList = playlist.items.filterNot { it.id == id }
            if (updatedList.isEmpty()) {
                _isPlayerActive.value = false
                null
            } else {
                val authoritativeId = playlist.authoritativeMediaId
                val newIndex = if (authoritativeId == id) {
                    // Current item deleted, try to stay at same index
                    playlist.currentIndex.coerceAtMost(updatedList.size - 1)
                } else {
                    // Re-locate current ID
                    val idx = updatedList.indexOfFirst { it.id == authoritativeId }
                    if (idx != -1) idx else playlist.currentIndex.coerceAtMost(updatedList.size - 1)
                }

                playlist.copy(
                    items = updatedList,
                    currentIndex = newIndex,
                    authoritativeMediaId = updatedList.getOrNull(newIndex)?.id
                )
            }
        }
    }

    /**
     * Specialized deletion for the Compare screen that treats the deletion of an item
     * as an explicit preference signal for the remaining item in the pair.
     */
    fun deleteComparisonMedia(id: String) {
        val currentPair = _pairwiseState.value
        val itemA = currentPair.optionA
        val itemB = currentPair.optionB

        // Identify if the deleted item is part of the active comparison
        val survivorId = when (id) {
            itemA.id -> itemB.id
            itemB.id -> itemA.id
            else -> null
        }

        // 1. Remove from in-memory list first to ensure the subsequent refresh
        // in recordComparisonVote doesn't pick this item again.
        _mediaItems.update { items -> items.filterNot { it.id == id } }

        // Remove from active session if present
        if (_compareSelectionSession.value.isActive) {
            _compareSelectionSession.update { session ->
                if (id in session.selectedIds) {
                    session.copy(selectedIds = session.selectedIds - id)
                } else session
            }
        }

        if (survivorId != null && survivorId.isNotEmpty()) {
            // 2. Record the win for the survivor item using existing learning pathways.
            // This updates Elo, Taste DNA, and triggers a refresh of the comparison pair.
            recordComparisonVote(survivorId)
        } else {
            // If not in a comparison, just ensure we refresh the pairwise state if needed
            refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = false)
        }

        // 3. Persistent delete from database
        scope.launch {
            database?.mediaDao()?.deleteById(id)
            deleteSemanticDataForMedia(id)
        }

        // 4. Clean up active playlist
        _activePlaylist.value?.let { playlist ->
            val updatedList = playlist.items.filterNot { it.id == id }
            if (updatedList.isEmpty()) {
                _activePlaylist.value = null
                _isPlayerActive.value = false
            } else {
                val newIndex = playlist.currentIndex.coerceAtMost(updatedList.size - 1)
                _activePlaylist.value = playlist.copy(
                    items = updatedList,
                    currentIndex = newIndex
                )
            }
        }
    }

    suspend fun getSimilarMedia(item: MediaItem, requestId: String = "NONE"): List<MediaItem> {
        Log.d("SeeSimilarTrace", "STAGE=START requestId=$requestId sourceId=${item.id} title=\"${item.title}\"")
        val allItems = _mediaItems.value
        val all = allItems.filter { other ->
            other.id != item.id && 
            other.uriPath != item.uriPath && 
            (item.contentHash == null || other.contentHash != item.contentHash) &&
            isItemVisibleInLibrary(other)
        }
        
        Log.d("SeeSimilarTrace", "STAGE=POOL_READY requestId=$requestId poolSize=${all.size}")
        
        if (all.isEmpty()) {
            Log.w("SeeSimilarTrace", "No candidates available for similarity search.")
            return emptyList()
        }

        // 1. Semantic Integration
        val repo = semanticRepresentationRepository
        val retriever = semanticCandidateRetriever
        val semanticCandidates = mutableMapOf<String, Float>() 
        
        if (repo != null && retriever != null) {
            val reps = repo.getForMedia(item.id)
            val refRep = reps.find { it.type == com.example.data.semantic.SemanticRepresentationType.VISUAL }
                ?: reps.find { it.type == com.example.data.semantic.SemanticRepresentationType.CONTENT }
                
            if (refRep != null) {
                val results = retriever.retrieveCandidates(
                    queryVector = refRep.vector,
                    type = refRep.type,
                    descriptor = refRep.modelDescriptor,
                    topK = 50,
                    minSimilarity = 0.3f 
                )
                results.forEach { semanticCandidates[it.mediaId] = it.similarityScore }
                Log.d("SeeSimilarTrace", "STAGE=SEMANTIC_QUERY_COMPLETE requestId=$requestId results=${results.size} modality=${refRep.type}")
            } else {
                Log.d("SeeSimilarTrace", "STAGE=SEMANTIC_QUERY_SKIP requestId=$requestId - No embeddings for source.")
            }
        }

        val itemTags = item.moodTags.filter { it.isNotBlank() }.map { it.lowercase().trim() }.toSet()
        val itemGenre = item.genre.lowercase().trim()
        val itemCategory = item.category.lowercase().trim()
        val isItemVideo = item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("Movie", ignoreCase = true)

        val stopWords = setOf("the", "and", "a", "an", "in", "on", "at", "for", "with", "of", "to", "is", "you")
        val itemTitleTokens = item.title.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 && !stopWords.contains(it) }
            .toSet()

        val scored = all.mapNotNull { other ->
            var contentScore = 0f

            // 1. Semantic Boost
            val semanticScore = semanticCandidates[other.id] ?: 0f
            if (semanticScore > 0.3f) {
                contentScore += (semanticScore * 30f)
            }

            // 2. Mood Tags matching (+12 per matching tag)
            val otherTags = other.moodTags.filter { it.isNotBlank() }.map { it.lowercase().trim() }.toSet()
            val commonTags = itemTags.intersect(otherTags).size
            contentScore += commonTags * 12f

            // 3. Genre matching (+10 for matching genre)
            val otherGenre = other.genre.lowercase().trim()
            if (itemGenre.isNotEmpty() && itemGenre == otherGenre) {
                contentScore += if (itemGenre != "media") 10f else 2f
            }

            // 4. Category matching (+8 for matching category)
            val otherCategory = other.category.lowercase().trim()
            if (itemCategory.isNotEmpty() && itemCategory == otherCategory) {
                contentScore += if (itemCategory != "for you") 8f else 2f
            }

            // 5. Title token overlap (+15 per matching token)
            val otherTitleTokens = other.title.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 2 && !stopWords.contains(it) }
                .toSet()
            val commonTitleTokens = itemTitleTokens.intersect(otherTitleTokens).size
            contentScore += commonTitleTokens * 15f

            // Media type match bonus
            val isOtherVideo = other.mediaType.equals("VIDEO", ignoreCase = true) || other.mediaType.equals("Movie", ignoreCase = true)
            val isSameType = (isOtherVideo == isItemVideo)

            val totalScore = contentScore + (if (isSameType && contentScore > 0) 2f else 0f)

            // Jitter for variety
            val jitter = ((other.id.hashCode() xor item.id.hashCode()).toFloat() / Int.MAX_VALUE.toFloat()).let { if (it < 0) -it else it } * 0.1f
            val finalScore = totalScore + jitter

            if (finalScore >= 4f) {
                Pair(other, finalScore)
            } else {
                null
            }
        }

        val sortedMatches = scored.sortedByDescending { it.second }.map { it.first }

        Log.d("SeeSimilarTrace", "STAGE=SORT_COMPLETE requestId=$requestId results=${sortedMatches.size}")
        sortedMatches.take(5).forEachIndexed { i, m -> 
            val s = scored.find { it.first.id == m.id }?.second ?: 0f
            Log.d("SeeSimilarTrace", "rank=${i+1} id=${m.id} score=$s title=\"${m.title}\"")
        }

        return sortedMatches.take(30)
    }

    fun setMediaItemsForTesting(items: List<MediaItem>) {
        _mediaItems.value = items
    }

    fun moveToTop(id: String) {
        _mediaItems.update { items ->
            val target = items.find { it.id == id } ?: return@update items
            val rest = items.filterNot { it.id == id }
            listOf(target.copy(dateAdded = System.currentTimeMillis())) + rest
        }
        scope.launch {
            database?.mediaDao()?.getMediaById(id)?.let { entity ->
                database?.mediaDao()?.update(entity.copy(dateAdded = System.currentTimeMillis()))
            }
        }
    }

    fun recordComparisonVote(chosenId: String) {
        val currentPair = _pairwiseState.value
        val itemA = currentPair.optionA
        val itemB = currentPair.optionB

        if (itemA.id.isNotEmpty() && itemB.id.isNotEmpty()) {
            val loserId = if (chosenId == itemA.id) itemB.id else itemA.id

            // Update session stats if active
            if (_compareSelectionSession.value.isActive) {
                _compareSelectionSession.update { session ->
                    val newWins = if (chosenId == itemA.id) session.wins + 1 else session.wins
                    val newLosses = if (chosenId == itemB.id) session.losses + 1 else session.losses
                    session.copy(
                        roundNumber = session.roundNumber + 1,
                        wins = newWins,
                        losses = newLosses,
                        comparedPairIds = session.comparedPairIds + (itemA.id to itemB.id)
                    )
                }
            }

            pairwiseWins[chosenId] = (pairwiseWins[chosenId] ?: 0) + 1
            pairwiseLosses[loserId] = (pairwiseLosses[loserId] ?: 0) + 1
            comparisonCounts[itemA.id] = (comparisonCounts[itemA.id] ?: 0) + 1
            comparisonCounts[itemB.id] = (comparisonCounts[itemB.id] ?: 0) + 1

            // True Elo Update
            val expectedA = PairwiseEloEngine.calculateExpectedScore(itemA.eloRating, itemB.eloRating)
            val expectedB = 1.0 - expectedA
            val actualA = if (chosenId == itemA.id) 1.0 else 0.0
            val actualB = if (chosenId == itemB.id) 1.0 else 0.0

            val newRatingA = PairwiseEloEngine.calculateNewRating(itemA.eloRating, actualA, expectedA)
            val newRatingB = PairwiseEloEngine.calculateNewRating(itemB.eloRating, actualB, expectedB)

            scope.launch {
                val db = database ?: return@launch
                val outcome = PairwiseOutcomeEntity(
                    optionAId = itemA.id,
                    optionBId = itemB.id,
                    chosenId = chosenId,
                    roundNumber = currentPair.roundNumber,
                    outcomeType = "VOTE",
                    preRatingA = itemA.eloRating,
                    preRatingB = itemB.eloRating,
                    postRatingA = newRatingA,
                    postRatingB = newRatingB,
                    expectedScoreA = expectedA,
                    kFactor = PairwiseEloEngine.K_FACTOR
                )
                db.pairwiseDao().insertOutcome(outcome)

                // Phase 3B.2: Enqueue sanitized contribution if consent is granted
                contributionQueueRepository?.let { repo ->
                    if (repo.isConsentGranted()) {
                        com.example.data.contribution.ContributionDataSanitizer.sanitizePairwiseOutcome(outcome)?.let { payload ->
                            repo.enqueuePairwiseDelta(payload)
                        }
                    }
                }

                // Persist new ratings to media items
                db.mediaDao().getMediaById(itemA.id)?.let { db.mediaDao().update(it.copy(eloRating = newRatingA)) }
                db.mediaDao().getMediaById(itemB.id)?.let { db.mediaDao().update(it.copy(eloRating = newRatingB)) }
            }

            // Automatic Taste DNA Learning (Enhanced Dimension-Level Calibration)
            val winner = if (chosenId == itemA.id) itemA else itemB
            val loser = if (chosenId == itemA.id) itemB else itemA
            val dna = _tasteDNA.value
            
            if (winner.id.isNotEmpty() && dna.isFineTuningEnabled) {
                val winnerTraits = PersonalizationTraitMapper.getEffectiveTraitAdjustments(winner)
                val loserTraits = PersonalizationTraitMapper.getEffectiveTraitAdjustments(loser)
                var updatedDna = dna

                // Calibration based on choice CONTRAST
                // If user picks A over B, dimensions where A differs from B provide the strongest signal.
                val allAffectedDimensions = (winnerTraits.keys + loserTraits.keys).distinct()
                
                allAffectedDimensions.forEach { dim ->
                    val valWinner = winnerTraits[dim] ?: 0.0
                    val valLoser = loserTraits[dim] ?: 0.0
                    
                    // Contrast is the magnitude of difference in this trait between options
                    val contrast = valWinner - valLoser
                    
                    if (contrast != 0.0) {
                        // Update learned dimension based on contrast direction
                        // Small increment scaled by contrast magnitude
                        val amount = contrast * MAX_ADJUSTMENT_PER_VOTE
                        updatedDna = updatedDna.updateLearnedDimension(dim, amount, TOTAL_ADJUSTMENT_LIMIT)
                    }
                }
                
                if (updatedDna != dna) {
                    updateTasteDNA(updatedDna, isUserGenerated = false, evidenceCategory = "Pairwise Vote (Calibration)")
                    
                    // Emit Signal for dimension calibration
                    scope.launch {
                        momentDispatcher.onEvent(AuraMomentDispatcher.IntelligenceEvent.TasteCalibrated("Preference", 0.0))
                    }
                }
            }

            // Update Preference Profile Learning from Comparison (Discovery signal)
            learnPreferenceSignals(winner, isDiscovery = true, isSuccess = true)
        }

        _intelligenceStats.update { stats ->
            val newStats = stats.copy(
                personalizationScore = (stats.personalizationScore + 1).coerceAtMost(100),
                totalComparisons = stats.totalComparisons + 1
            )
            
            // Emit System Milestone Signal
            scope.launch {
                momentDispatcher.onEvent(
                    AuraMomentDispatcher.IntelligenceEvent.SystemMilestone(
                        accuracy = newStats.personalizationScore,
                        totalVotes = newStats.totalComparisons
                    )
                )
            }
            newStats
        }

        refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = true)
    }

    fun skipComparison() {
        val currentPair = _pairwiseState.value
        val itemA = currentPair.optionA
        val itemB = currentPair.optionB

        if (itemA.id.isNotEmpty() && itemB.id.isNotEmpty()) {
            val sessionActive = _compareSelectionSession.value.isActive
            if (sessionActive) {
                _compareSelectionSession.update { it.copy(
                    roundNumber = it.roundNumber + 1,
                    skips = it.skips + 1,
                    comparedPairIds = it.comparedPairIds + (itemA.id to itemB.id)
                ) }
            }

            comparisonCounts[itemA.id] = (comparisonCounts[itemA.id] ?: 0) + 1
            comparisonCounts[itemB.id] = (comparisonCounts[itemB.id] ?: 0) + 1

            // Elo Update for Skip (Actual Score = 0.5) - Neutral for Session Mode
            if (!sessionActive) {
                val expectedA = PairwiseEloEngine.calculateExpectedScore(itemA.eloRating, itemB.eloRating)
                val expectedB = 1.0 - expectedA
                val newRatingA = PairwiseEloEngine.calculateNewRating(itemA.eloRating, 0.5, expectedA)
                val newRatingB = PairwiseEloEngine.calculateNewRating(itemB.eloRating, 0.5, expectedB)

                scope.launch {
                    val db = database ?: return@launch
                    val outcome = PairwiseOutcomeEntity(
                        optionAId = itemA.id,
                        optionBId = itemB.id,
                        chosenId = "",
                        roundNumber = currentPair.roundNumber,
                        outcomeType = "SKIP",
                        preRatingA = itemA.eloRating,
                        preRatingB = itemB.eloRating,
                        postRatingA = newRatingA,
                        postRatingB = newRatingB,
                        expectedScoreA = expectedA,
                        kFactor = PairwiseEloEngine.K_FACTOR
                    )
                    db.pairwiseDao().insertOutcome(outcome)

                    // Phase 3B.2: Enqueue sanitized contribution if consent is granted
                    contributionQueueRepository?.let { repo ->
                        if (repo.isConsentGranted()) {
                            com.example.data.contribution.ContributionDataSanitizer.sanitizePairwiseOutcome(outcome)?.let { payload ->
                                repo.enqueuePairwiseDelta(payload)
                            }
                        }
                    }

                    db.mediaDao().getMediaById(itemA.id)?.let { db.mediaDao().update(it.copy(eloRating = newRatingA)) }
                    db.mediaDao().getMediaById(itemB.id)?.let { db.mediaDao().update(it.copy(eloRating = newRatingB)) }
                }
            }
        }

        refreshPairwiseCandidatePoolAndSelectNext(forceNextPair = true)
    }


    fun logClipInteraction(
        mediaId: String,
        clipTitle: String,
        startTimeMs: Long,
        endTimeMs: Long,
        type: ClipTelemetryService.InteractionType
    ) {
        scope.launch(Dispatchers.IO) {
            val db = database ?: return@launch
            val dao = db.clipInteractionDao()
            val existing = dao.getInteraction(mediaId, startTimeMs)
            val updated = if (existing != null) {
                when (type) {
                    ClipTelemetryService.InteractionType.PREVIEW -> existing.copy(
                        previewCount = existing.previewCount + 1,
                        lastInteractionTimestamp = System.currentTimeMillis()
                    )
                    ClipTelemetryService.InteractionType.SELECT -> existing.copy(
                        selectCount = existing.selectCount + 1,
                        lastInteractionTimestamp = System.currentTimeMillis()
                    )
                    ClipTelemetryService.InteractionType.EXPORT -> existing.copy(
                        exportCount = existing.exportCount + 1,
                        lastInteractionTimestamp = System.currentTimeMillis()
                    )
                }
            } else {
                ClipInteractionEntity(
                    mediaId = mediaId,
                    clipTitle = clipTitle,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    previewCount = if (type == ClipTelemetryService.InteractionType.PREVIEW) 1 else 0,
                    selectCount = if (type == ClipTelemetryService.InteractionType.SELECT) 1 else 0,
                    exportCount = if (type == ClipTelemetryService.InteractionType.EXPORT) 1 else 0
                )
            }
            dao.insertOrUpdate(updated)
            Log.d("MediaRepository", "Logged clip interaction [$type] for '$clipTitle'")
        }
    }

    fun recordAISkipEvent(
        mediaId: String,
        eventType: String,
        fromPosMs: Long,
        toPosMs: Long
    ) {
        scope.launch {
            val db = database ?: return@launch
            val skipEvent = AISkipEventEntity(
                mediaId = mediaId,
                eventType = eventType,
                fromPosMs = fromPosMs,
                toPosMs = toPosMs,
                timestamp = System.currentTimeMillis()
            )
            db.aiSkipDao().insertEvent(skipEvent)

            // Phase 3B.2: Enqueue sanitized contribution if consent is granted
            contributionQueueRepository?.let { repo ->
                if (repo.isConsentGranted()) {
                    com.example.data.contribution.ContributionDataSanitizer.sanitizeAISkipEvent(skipEvent)?.let { payload ->
                        repo.enqueueSkipCalibration(payload)
                    }
                }
            }

            // Automatic Skip Sensitivity Learning (Individual Level Only)
            val dna = _tasteDNA.value
            if (dna.isFineTuningEnabled) {
                var amount = 0.0
                when (eventType) {
                    "SKIP_REVERSAL" -> amount = -MAX_ADJUSTMENT_PER_SKIP
                    "REPEATED_SKIP" -> amount = MAX_ADJUSTMENT_PER_SKIP
                    "WATCHED_DESTINATION" -> amount = MAX_ADJUSTMENT_PER_SKIP / 2
                }
                
                var updatedDna = dna
                if (amount != 0.0) {
                    updatedDna = updatedDna.updateLearnedDimension("skipSensitivity", amount, TOTAL_ADJUSTMENT_LIMIT)
                }

                // ALSO: Use traits of the skipped media as a negative signal (for SKIP_FORWARD/REPEATED_SKIP)
                if (eventType == "SKIP_FORWARD" || eventType == "REPEATED_SKIP") {
                    val item = getMediaItemById(mediaId)
                    if (item != null) {
                        val adjustments = PersonalizationTraitMapper.getEffectiveTraitAdjustments(item)
                        adjustments.forEach { (dim, multiplier) ->
                            // Skips are negative signals
                            val traitAmount = multiplier * MAX_ADJUSTMENT_PER_SKIP * -1.0
                            updatedDna = updatedDna.updateLearnedDimension(dim, traitAmount, TOTAL_ADJUSTMENT_LIMIT)
                        }
                        
                        // Update Preference Profile Learning - Negative engagement signal
                        learnPreferenceSignals(item, isSuccess = false)
                    }
                }
                
                if (updatedDna != dna) {
                    updateTasteDNA(updatedDna, isUserGenerated = false, evidenceCategory = "AI Skip Behavior ($eventType)")
                    
                    // Emit Emotional Intelligence Signal
                    momentDispatcher.onEvent(AuraMomentDispatcher.IntelligenceEvent.TasteCalibrated("Sensitivity", amount))
                }
            }

            _intelligenceStats.update {
stats ->
                val bump = when (eventType) {
                    "WATCHED_DESTINATION" -> 2
                    "SKIP_REVERSAL" -> 1
                    else -> 1
                }
                stats.copy(
                    personalizationScore = (stats.personalizationScore + bump).coerceAtMost(100)
                )
            }
            Log.d("MediaRepository", "Logged AI Skip Event [$eventType] for media $mediaId ($fromPosMs ms -> $toPosMs ms)")
        }
    }

    suspend fun getEngagementMetrics(): EngagementMetrics {
        val db = database ?: return EngagementMetrics()
        val allItems = _mediaItems.value
        val stats = _intelligenceStats.value
        
        val voteCount = db.pairwiseDao().getVoteCount()
        val clipDao = db.clipInteractionDao()
        val previews = clipDao.getTotalClipPreviews()
        val selections = clipDao.getTotalClipSelections()
        val exports = clipDao.getTotalClipExports()
        val topClipsEntities = clipDao.getTopEngagedClips()

        val skipDao = db.aiSkipDao()
        val skipsForward = skipDao.getTotalSkipForwards()
        val skipsBack = skipDao.getTotalSkipBacks()
        val skipReversals = skipDao.getTotalSkipReversals()
        val watchedDests = skipDao.getTotalWatchedDestinations()
        
        val topClips = topClipsEntities.map { entity ->
            ClipInteractionSummary(
                title = entity.clipTitle,
                previewCount = entity.previewCount,
                selectCount = entity.selectCount,
                exportCount = entity.exportCount,
                score = entity.previewCount * 1 + entity.selectCount * 2 + entity.exportCount * 5
            )
        }
        
        return EngagementMetrics(
            totalPlays = allItems.sumOf { it.viewCount },
            favoriteCount = allItems.count { it.isFavorite },
            averageRating = if (allItems.any { it.rating > 0 }) allItems.filter { it.rating > 0 }.map { it.rating }.average().toFloat() else 0f,
            personalizationScore = stats.personalizationScore,
            totalComparisons = voteCount,
            itemsDiscovered = allItems.size,
            totalClipPreviews = previews,
            totalClipSelections = selections,
            totalClipExports = exports,
            topEngagedClips = topClips,
            pairwiseDiagnostics = _pairwiseDiagnostics.value,
            aiSkipStats = AISkipStats(
                totalSkipForwards = skipsForward,
                totalSkipBacks = skipsBack,
                totalSkipReversals = skipReversals,
                totalWatchedDestinations = watchedDests
            )
        )

    }


    suspend fun generateClosedLoopReport(
        baselineScore: Double = 50.0,
        targetScore: Double = 50.0
    ): ClosedLoopReport {
        val metrics = getEngagementMetrics()
        val productionSamples = metrics.totalPlays + metrics.totalComparisons + metrics.totalClipPreviews
        val evidenceList = mutableListOf<EvidenceRecord>()

        // 1. Add real production telemetry
        if (productionSamples > 0) {
            evidenceList.add(
                EvidenceRecord(
                    tier = EvidenceTier.PRODUCTION,
                    sampleCount = productionSamples,
                    score = metrics.personalizationScore.toDouble(),
                    quality = 0.9,
                    source = "AuraTelemetryService"
                )
            )
        }

        // 2. Add stored evidence (Agents, Experiments, Simulations)
        evidenceList.addAll(_storedEvidence.value)

        return ClosedLoopEngine.evaluate(
            baselineScore = baselineScore,
            measuredScore = if (productionSamples > 0) metrics.personalizationScore.toDouble() else baselineScore,
            targetScore = targetScore,
            evidenceList = evidenceList
        )
    }

    fun getFilteredAndSortedMedia(
        filterType: String, // "ALL", "PHOTO", "VIDEO"
        sortCategory: SortCategory,
        standardSort: StandardSortOption,
        intelligentSort: IntelligentSortOption,
        sessionSeed: Long = 42L,
        inputItems: List<MediaItem> = _mediaItems.value,
        tasteDNA: TasteDNA = _tasteDNA.value,
        profile: TasteDNA.PreferenceProfile = _preferenceProfile.value,
        policy: DiscoveryPolicy = _discoveryPolicy.value,
        intent: UserIntent = _userIntent.value,
        stats: IntelligenceStats = _intelligenceStats.value,
        creatorProfiles: Map<String, CreatorProfile> = _creatorProfiles.value
    ): List<MediaItem> {
        val items = inputItems.filter { item ->
            matchesFilterType(item, filterType) && isItemVisibleInLibrary(item)
        }

        return if (sortCategory == SortCategory.STANDARD) {
            val sorted = when (standardSort) {
                StandardSortOption.NEWEST_FIRST -> items.sortedByDescending { it.dateAdded }
                StandardSortOption.RECENTLY_PLAYED -> items.sortedWith(compareByDescending(nullsFirst()) { it.lastViewedTimestamp })
                StandardSortOption.TITLE_ASC -> items.sortedBy { it.title.lowercase() }
                StandardSortOption.TITLE_DESC -> items.sortedByDescending { it.title.lowercase() }
                StandardSortOption.SHORTEST_DURATION -> items.sortedBy { it.durationMs }
                StandardSortOption.LONGEST_DURATION -> items.sortedByDescending { it.durationMs }
                StandardSortOption.MOST_PLAYED -> items.sortedByDescending { it.viewCount }
                StandardSortOption.LEAST_PLAYED -> items.sortedBy { it.viewCount }
                StandardSortOption.RANDOM -> {
                    items.sortedBy { item ->
                        (item.id + sessionSeed).hashCode()
                    }
                }
            }
            // AURA LABEL FIX: Standard sort results must not display stale ephemeral AI labels.
            // Systems labels like "Retry Analysis" are preserved.
            sorted.map { item ->
                if (isEphemeralReason(item.selectionReason)) item.copy(selectionReason = null) else item
            }
        } else {
            // INTELLIGENT SORTING
            // AURA P1 STABILITY: Evaluation Context Timestamp
            // Capturing a single 'now' ensures ordering does not drift during unrelated metadata emissions.
            val now = System.currentTimeMillis()
            val recentThreshold = 3600000L // 1 hour
            
            when (intelligentSort) {
                IntelligentSortOption.PERSONALIZED -> {
                    val systemState = ConfidenceEngine.calculateDiscoveryState(inputItems, stats)
                    val strategy = DiscoveryPolicyManager.resolveStrategy(
                        policy = policy,
                        intent = intent,
                        objective = RecommendationObjective.LIBRARY_INTELLIGENT_DISCOVERY,
                        systemState = systemState,
                        tasteDNA = tasteDNA,
                        profile = profile
                    )

                    items.filter { item ->
                        val isLiked = item.isFavorite || item.rating >= 4.0f
                        val isRecent = item.lastViewedTimestamp?.let { now - it < recentThreshold } ?: false
                        !isLiked && !isRecent
                    }.map { item ->
                        val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats, creatorProfiles, now)
                        val score = ExplorationEngine.calculatePolicyScore(evidence, strategy)
                        val reason = when {
                            evidence.exploitationScore > 0.6 && evidence.familiarityScore > 0.4 -> "For You"
                            evidence.exploitationScore > 0.5 && evidence.familiarityScore < 0.3 -> "Hidden Gem"
                            strategy.explorationWeight > 0.5 && evidence.explorationScore > 0.6 -> "Best Match"
                            else -> "Personalized"
                        }
                        item.copy(selectionReason = reason) to score
                    }.sortedByDescending { it.second }.map { it.first }
                }

                IntelligentSortOption.DISCOVER -> {
                    val strategy = DiscoveryPolicyManager.resolveStrategy(
                        policy = policy,
                        intent = intent,
                        objective = RecommendationObjective.GENERAL_DISCOVERY,
                        systemState = ConfidenceEngine.calculateDiscoveryState(inputItems, stats),
                        tasteDNA = tasteDNA,
                        profile = profile
                    ).copy(exploitationWeight = 0.2f, explorationWeight = 0.8f) // Favor exploration

                    items.filter { item ->
                        item.viewCount == 0 || item.exposureCount < 3
                    }.map { item ->
                        val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats, creatorProfiles, now)
                        val score = ExplorationEngine.calculatePolicyScore(evidence, strategy)
                        item.copy(selectionReason = "New Discovery") to score
                    }.sortedByDescending { it.second }.map { it.first }
                }

                IntelligentSortOption.REDISCOVER -> {
                    items.filter { item ->
                        val isLiked = item.isFavorite || item.rating >= 4.0f
                        val isRecent = item.lastViewedTimestamp?.let { now - it < recentThreshold } ?: false
                        isLiked && !isRecent
                    }.map { item ->
                        val ageBonus = if (item.lastViewedTimestamp != null) {
                            (now - item.lastViewedTimestamp!!).toDouble() / (1000.0 * 60 * 60 * 24 * 7) // weeks
                        } else 100.0 // Should not happen for Liked items usually, but fallback
                        
                        val score = (item.rating.toDouble() * 20.0) + (item.viewCount.toDouble() * 2.0) + ageBonus
                        item.copy(selectionReason = "Blast from the Past") to score
                    }.sortedByDescending { it.second }.map { it.first }
                }

                IntelligentSortOption.HIDDEN_GEMS -> {
                    val strategy = DiscoveryPolicyManager.resolveStrategy(
                        policy = policy,
                        intent = intent,
                        objective = RecommendationObjective.LIBRARY_INTELLIGENT_DISCOVERY,
                        systemState = ConfidenceEngine.calculateDiscoveryState(inputItems, stats),
                        tasteDNA = tasteDNA,
                        profile = profile
                    )
                    
                    items.filter { item ->
                        item.exposureCount < 5 && item.viewCount < 2 && item.rating == 0f
                    }.map { item ->
                        val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats, creatorProfiles, now)
                        val score = ExplorationEngine.calculatePolicyScore(evidence, strategy)
                        item.copy(selectionReason = "Hidden Gem") to score
                    }.sortedByDescending { it.second }.map { it.first }
                }

                IntelligentSortOption.FAVORITES -> {
                    items.filter { item ->
                        item.isFavorite || item.rating >= 4.0f
                    }.map { item ->
                        val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats, creatorProfiles, now)
                        // Favorites are already high quality, sort by newest added
                        val score = evidence.exploitationScore * 10f + (item.dateAdded.toDouble() / 1e12).toFloat()
                        item.copy(selectionReason = "Your Favorite") to score
                    }.sortedByDescending { it.second }.map { it.first }
                }

                IntelligentSortOption.SURPRISE_ME -> {
                    items.sortedBy { item ->
                        (item.id + sessionSeed).hashCode()
                    }.map { it.copy(selectionReason = "Surprise!") }
                }
            }
        }
    }

    private val EPHEMERAL_AI_REASONS = setOf(
        "Surprise!", "For You", "Hidden Gem", "Best Match", 
        "Personalized", "New Discovery", "Blast from the Past", "Your Favorite"
    )
    private val MATCH_PERCENT_REGEX = Regex("""\d+% Match""")

    private fun isEphemeralReason(reason: String?): Boolean {
        if (reason == null) return false
        return reason in EPHEMERAL_AI_REASONS || MATCH_PERCENT_REGEX.matches(reason)
    }

    private fun MediaItem.toEntity(): MediaEntity {
        // AURA PERSISTENCE FIX: Never persist ephemeral AI recommendation reasons to the DB.
        // This prevents AI labels from "sticking" to items during Favorite/Like updates.
        val persistentReason = if (isEphemeralReason(selectionReason)) null else selectionReason
        
        return MediaEntity(
            id = id,
            title = title,
            mediaType = if (mediaType.equals("VIDEO", ignoreCase = true) || mediaType.equals("Movie", ignoreCase = true)) "VIDEO" else "PHOTO",
            year = year,
            duration = duration,
            genre = genre,
            imageUrl = imageUrl,
            gradientColorsJson = gradientColors.joinToString(","),
            rating = rating,
            isFavorite = isFavorite,
            progress = progress,
            progressText = progressText,
            category = category,
            aiSummary = aiSummary,
            moodTagsJson = moodTags.joinToString(","),
            itemCount = itemCount,
            eloRating = eloRating,
            uriPath = uriPath,
            dateAdded = dateAdded,
            dateModified = dateModified,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            width = width,
            height = height,
            lastViewedTimestamp = lastViewedTimestamp,
            playCount = viewCount,
            exposureCount = exposureCount,
            lastExposedTimestamp = lastExposedTimestamp,
            contentHash = contentHash,
            parentContentId = parentContentId,
            isDeleted = isDeleted,
            compatibilityStatus = compatibilityStatus.name,
            containerFormat = containerFormat,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            compatibilityReason = compatibilityReason,
            conversionStatus = conversionStatus.name,
            convertedUri = convertedUri ?: "",
            lastCompatibilityCheckTimestamp = lastCompatibilityCheckTimestamp,
            selectionReason = persistentReason,
            creatorId = creatorId,
            creatorName = creatorName,
            sourcePlatform = sourcePlatform,
            replacedByMediaId = replacedByMediaId
        )
    }

    private fun MediaEntity.toMediaItem(): MediaItem {
        val isVideo = mediaType.equals("VIDEO", ignoreCase = true) || mediaType.equals("Movie", ignoreCase = true)
        val normalizedType = if (isVideo) "VIDEO" else "PHOTO"
        val gradients = if (isVideo) {
            listOf(0xFF1E1B4BL, 0xFF4338CAL, 0xFF7C3AEDL)
        } else {
            listOf(0xFF311B92L, 0xFF6A1B9AL, 0xFFD946EFL)
        }
        val compStatus = try {
            CompatibilityStatus.valueOf(compatibilityStatus)
        } catch (e: Exception) {
            CompatibilityStatus.PLAYABLE
        }
        val convStatus = try {
            ConversionStatus.valueOf(conversionStatus)
        } catch (e: Exception) {
            ConversionStatus.NONE
        }
        return MediaItem(
            id = id,
            title = title,
            mediaType = normalizedType,
            year = year,
            duration = duration,
            genre = genre,
            imageUrl = imageUrl,
            gradientColors = if (gradientColorsJson.isBlank()) gradients else gradientColorsJson.split(",").mapNotNull { it.toLongOrNull() },
            rating = rating,
            isFavorite = isFavorite,
            progress = progress,
            progressText = progressText,
            category = category,
            aiSummary = aiSummary,
            moodTags = if (moodTagsJson.isEmpty()) emptyList() else moodTagsJson.split(","),
            uriPath = uriPath,
            itemCount = itemCount,
            sizeBytes = sizeBytes,
            dateAdded = dateAdded,
            dateModified = dateModified,
            durationMs = durationMs,
            width = width,
            height = height,
            lastViewedTimestamp = lastViewedTimestamp,
            viewCount = playCount,
            exposureCount = exposureCount,
            lastExposedTimestamp = lastExposedTimestamp,
            contentHash = contentHash,
            parentContentId = parentContentId,
            eloRating = eloRating,
            isDeleted = isDeleted,
            compatibilityStatus = compStatus,
            containerFormat = containerFormat,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            compatibilityReason = compatibilityReason,
            conversionStatus = convStatus,
            convertedUri = convertedUri.ifBlank { null },
            lastCompatibilityCheckTimestamp = lastCompatibilityCheckTimestamp,
            selectionReason = if (compStatus == CompatibilityStatus.ANALYSIS_FAILED) "Retry Analysis" else selectionReason,
            creatorId = creatorId,
            creatorName = creatorName,
            sourcePlatform = sourcePlatform,
            replacedByMediaId = replacedByMediaId
        )
    }

    suspend fun convertMediaItem(
        context: Context,
        itemId: String,
        deleteOriginalAfter: Boolean = false,
        onProgress: ((Int) -> Unit)? = null
    ): ConversionResult {
        val currentItem = _mediaItems.value.find { it.id == itemId }
            ?: return ConversionResult(false, null, null, "Media item not found")

        val result = AuraMediaConverter.convertToUniversalFormat(
            context = context,
            item = currentItem,
            deleteOriginalAfter = deleteOriginalAfter,
            onProgress = onProgress
        )

        if (result.isSuccess && result.updatedItem != null) {
            val updated = result.updatedItem
            _mediaItems.update { list ->
                list.map { if (it.id == itemId) updated else it }
            }
            database?.mediaDao()?.update(updated.toEntity())
        }

        return result
    }

    private suspend fun backfillSemantics() {
        val db = database ?: return
        val repo = semanticRepresentationRepository ?: return
        val allEntities = db.mediaDao().getAllMediaSync()
        
        val toBackfill = allEntities.filter { entity ->
            // Only backfill items that are already processed but missing embeddings
            val isProcessed = entity.compatibilityStatus == CompatibilityStatus.PLAYABLE.name
            if (!isProcessed) return@filter false
            
            val representations = repo.getForMedia(entity.id)
            representations.isEmpty()
        }

        if (toBackfill.isNotEmpty()) {
            Log.i("MediaRepository", "Backfilling semantics for ${toBackfill.size} items.")
            processSemanticsForBatch(toBackfill)
        }
    }

    private suspend fun processSemanticsForBatch(entities: List<MediaEntity>) {
        val provider = embeddingProvider ?: return
        val repo = semanticRepresentationRepository ?: return

        entities.forEach { entity ->
            try {
                // AURA P1 STABILITY: Failure isolation - semantic errors must not abort ingestion
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                
                // Idempotency & Update Detection
                val currentHash = entity.contentHash ?: "v1_${entity.sizeBytes}_${entity.title.hashCode()}"
                val existing = repo.getSpecificRepresentation(entity.id, provider.descriptor.primaryType, provider.descriptor)
                
                if (existing == null || existing.sourceDataHash != currentHash) {
                    Log.d("MediaRepository", "${if (existing == null) "Generating" else "Updating"} semantic embedding for: ${entity.title} (ID: ${entity.id})")
                    
                    val input = SemanticInput.Text(entity.title)
                    val result = provider.generateEmbedding(
                        mediaId = entity.id,
                        input = input,
                        sourceDataHash = currentHash
                    )

                    when (result) {
                        is EmbeddingResult.Success -> {
                            repo.saveRepresentation(result.representation)
                            semanticCandidateRetriever?.onRepresentationAdded(result.representation)
                            Log.i("MediaRepository", "Successfully persisted 384d embedding for: ${entity.title}")
                        }
                        is EmbeddingResult.Failure -> {
                            Log.e("MediaRepository", "Semantic embedding failure for ${entity.title}: [${result.errorCode}] ${result.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("MediaRepository", "Isolated semantic processing exception for ${entity.title}", e)
            }
        }
    }

    private suspend fun deleteSemanticDataForMedia(mediaId: String) {
        semanticRepresentationRepository?.deleteForMedia(mediaId)
        semanticCandidateRetriever?.onMediaRemoved(mediaId)
    }

    private fun matchesFilterType(item: MediaItem, filterType: String): Boolean {
        return when (filterType.uppercase()) {
            "PHOTO" -> item.mediaType.uppercase() in listOf("PHOTO", "IMAGE")
            "VIDEO" -> item.mediaType.uppercase() in listOf("VIDEO", "MOVIE")
            else -> true
        }
    }

    private fun isItemVisibleInLibrary(item: MediaItem): Boolean {
        // AURA P1 STABILITY: Authoritative Visibility Gate
        // Only verified playable terminal states are allowed in the Library Flow.
        val visibleStatuses = listOf(
            CompatibilityStatus.PLAYABLE,
            CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE,
            CompatibilityStatus.PLAYABLE_AFTER_CONVERSION,
            CompatibilityStatus.THUMBNAIL_FAILED,
            CompatibilityStatus.NEEDS_TRANSCODE
        )
        return !item.isDeleted && item.compatibilityStatus in visibleStatuses
    }

    private fun performLegacySearch(items: List<MediaItem>, query: String): List<MediaItem> {
        val q = query.trim().lowercase()
        return items.filter { item ->
            item.title.lowercase().contains(q) ||
            item.genre.lowercase().contains(q) ||
            item.moodTags.any { it.lowercase().contains(q) } ||
            item.year.toString().contains(q)
        }
    }

    /**
     * Production bridge between Aura's legacy metadata search and the Hybrid Search Engine.
     */
    private inner class ProductionLexicalRetriever : LexicalCandidateRetriever {
        override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> {
            val q = query.trim().lowercase()
            if (q.isBlank()) return emptyList()

            // Search across all locally known media items
            val allItems = _mediaItems.value
            
            return allItems.filter { item ->
                item.title.lowercase().contains(q) ||
                item.genre.lowercase().contains(q) ||
                item.moodTags.any { it.lowercase().contains(q) } ||
                item.year.toString().contains(q)
            }.mapIndexed { index, item ->
                RankedChannelItem(
                    mediaId = item.id,
                    rawScore = 1.0f, // Boolean match for legacy bridge
                    rank = index + 1
                )
            }.take(topK)
        }
    }

    private suspend fun rejectMedia(
        uri: String,
        title: String,
        mediaType: String,
        report: MediaCompatibilityReport
    ) {
        val db = database ?: return
        val id = "rejected_${uri.hashCode()}"
        db.rejectedMediaDao().insert(
            RejectedMediaEntity(
                id = id,
                uriPath = uri,
                title = title,
                mediaType = mediaType,
                reason = report.compatibilityReason,
                compatibilityStatus = report.status.name,
                containerFormat = report.containerFormat,
                videoCodec = report.videoCodec,
                audioCodec = report.audioCodec
            )
        )
    }

    /**
     * Performs a one-time reconciliation of existing media items to ensure all visible media 
     * is genuinely playable and eligible.
     */
    internal suspend fun reconcileExistingMedia(context: Context) {
        val db = database ?: return
        
        // Permission Check: Don't attempt to re-validate file access if permissions are missing.
        val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasPermissions) {
            Log.w("MediaRepository", "Reconciliation aborted: Permissions missing.")
            return
        }

        Log.d("MediaRepository", "Starting media reconciliation...")
        val allItems = db.mediaDao().getAllMediaSync()
        
        val mountedVolumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context)
        } else {
            setOf("external")
        }

        allItems.forEach { entity ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            
            // Re-validate if it was pending, untested, or known to be unplayable but still in the table
            val status = try { CompatibilityStatus.valueOf(entity.compatibilityStatus) } catch (e: Exception) { CompatibilityStatus.UNSUPPORTED }
            val isPending = status == CompatibilityStatus.ANALYSIS_PENDING
            val isUntested = status == CompatibilityStatus.UNTESTED
            val isUnplayable = !AuraMediaCompatibilityEngine.isEligibleForImport(status)

            if (isPending || isUntested || isUnplayable) {
                try {
                    val report = AuraMediaCompatibilityEngine.analyzeMedia(context, entity.uriPath, entity.mediaType)
                    if (AuraMediaCompatibilityEngine.isEligibleForImport(report.status)) {
                        db.mediaDao().update(entity.copy(
                            compatibilityStatus = report.status.name,
                            compatibilityReason = report.compatibilityReason,
                            containerFormat = report.containerFormat,
                            videoCodec = report.videoCodec,
                            audioCodec = report.audioCodec,
                            lastCompatibilityCheckTimestamp = System.currentTimeMillis()
                        ))
                    } else {
                        // ONLY reject if the status is explicitly ineligible (unsupported/corrupt)
                        val isPermanentFailure = report.status == CompatibilityStatus.UNSUPPORTED || 
                                                report.status == CompatibilityStatus.CORRUPT || 
                                                report.status == CompatibilityStatus.UNREADABLE
                                                
                        if (isPermanentFailure) {
                            // Safety: If it's UNREADABLE, verify the volume is actually mounted
                            val itemVolume = extractVolumeFromUri(entity.uriPath)
                            if (report.status == CompatibilityStatus.UNREADABLE && itemVolume != null && !mountedVolumes.contains(itemVolume)) {
                                Log.w("MediaRepository", "Reconciliation: Item ${entity.title} unreadable but volume $itemVolume not mounted. Skipping deletion.")
                                return@forEach
                            }

                            Log.w("MediaRepository", "Reconciliation: Permanently rejecting invalid existing item: ${entity.title}")
                            rejectMedia(entity.uriPath, entity.title, entity.mediaType, report)
                            db.mediaDao().deleteById(entity.id)
                            deleteSemanticDataForMedia(entity.id)
                        } else {
                            Log.w("MediaRepository", "Reconciliation: Transient failure for ${entity.title} (${report.status}). Retaining.")
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException || e is java.util.concurrent.CancellationException) throw e
                    Log.e("MediaRepository", "Failed to reconcile ${entity.title}: ${e.message}. Skipping rejection.")
                }
            }
        }

        // Trigger semantic regeneration for all valid library items (Phase 4 Migration)
        // Since we bumped modelVersion to 2, this will naturally replace deterministic v1 baseline vectors.
        val validItems = db.mediaDao().getAllMediaSync()
        processSemanticsForBatch(validItems)

        Log.d("MediaRepository", "Media reconciliation complete.")
    }
}

enum class SortCategory {
    STANDARD, INTELLIGENT
}

enum class StandardSortOption(val displayName: String, val description: String) {
    NEWEST_FIRST("Recently Added", "Your latest additions to the archive."),
    RECENTLY_PLAYED("Recently Played", "Media you've watched or viewed recently."),
    TITLE_ASC("Title A–Z", "Media sorted alphabetically by title."),
    TITLE_DESC("Title Z–A", "Media sorted in reverse alphabetical order."),
    SHORTEST_DURATION("Duration: Short → Long", "Your shortest clips and photos."),
    LONGEST_DURATION("Duration: Long → Short", "Your longest videos."),
    MOST_PLAYED("Most Played", "Media you watch and interact with most often."),
    LEAST_PLAYED("Least Played", "Media with the lowest play count."),
    RANDOM("Random", "Browse your library in a fresh randomized order.")
}

enum class IntelligentSortOption(val displayName: String, val description: String) {
    PERSONALIZED("Personalized", "AI thinks these are your best choices."),
    DISCOVER("Discover", "New content you haven't explored yet."),
    REDISCOVER("Rediscover", "Enjoy your favorites and past gems again."),
    HIDDEN_GEMS("Hidden Gems", "High quality items you might have missed."),
    FAVORITES("Favorites", "Everything you've liked and rated highly."),
    SURPRISE_ME("Surprise Me", "A fresh random selection from your library.")
}
