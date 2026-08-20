package com.example

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DatabaseState
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.ui.components.AuraBottomNavigation
import com.example.ui.components.NavDestination
import com.example.ui.screens.BlueprintWorkspaceScreen
import com.example.ui.screens.CollectionsScreen
import com.example.ui.screens.CompareScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.EngagementDebuggerScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MediaDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.AuraBackground
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraTheme
import com.example.ui.theme.DiscoveryViolet
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository = MediaRepository.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository.initDatabase(applicationContext)
        enableEdgeToEdge()
        setContent {
            AuraTheme {
                AuraApp(repository = repository)
            }
        }
    }
}

@Composable
fun AuraApp(repository: MediaRepository) {
    val discoverViewModel: com.example.ui.screens.DiscoverViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return com.example.ui.screens.DiscoverViewModel(repository) as T
            }
        }
    )

    val cleanupViewModel: com.example.ui.screens.CleanupIntelligenceViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return com.example.ui.screens.CleanupIntelligenceViewModel(repository) as T
            }
        }
    )

    val cleanupReviewViewModel: com.example.ui.screens.CleanupReviewViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return com.example.ui.screens.CleanupReviewViewModel(repository) as T
            }
        }
    )

    val playbackDiagnosticsViewModel: com.example.ui.screens.PlaybackDiagnosticsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return com.example.ui.screens.PlaybackDiagnosticsViewModel(
                    repository.playbackErrorLogRepository!!,
                    repository.conversionQueueRepository
                ) as T
            }
        }
    )

    var showEngagementDebugger by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val mediaItems by repository.mediaItems.collectAsStateWithLifecycle()
    val compareSession by repository.compareSelectionSession.collectAsStateWithLifecycle()
    val mediaItemsMap by repository.mediaItemsMap.collectAsStateWithLifecycle()
    val pairwiseState by repository.pairwiseState.collectAsStateWithLifecycle()
    val compareMediaType by repository.compareMediaType.collectAsStateWithLifecycle()
    val compareStrategy by repository.compareStrategy.collectAsStateWithLifecycle()
    val compareSort by repository.compareSort.collectAsStateWithLifecycle()
    val consentState by repository.consentState.collectAsStateWithLifecycle()
    val importProgress by repository.importProgress.collectAsStateWithLifecycle()
    val scanProgress by repository.scanProgress.collectAsStateWithLifecycle()
    val activePlaylist by repository.activePlaylist.collectAsStateWithLifecycle()
    val isPlayerActive by repository.isPlayerActive.collectAsStateWithLifecycle()
    val databaseState by repository.databaseState.collectAsStateWithLifecycle()
    val currentSelectedItem = activePlaylist?.currentItem

    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var isPendingComparisonDelete by remember { mutableStateOf(false) }

    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Handle physical deletion result via unified manager
        repository.safeDeleteManager.handleDeletionResult(result.resultCode)
        
        // Handle legacy single-item delete result
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteId?.let { id ->
                if (isPendingComparisonDelete) {
                    repository.deleteComparisonMedia(id)
                } else {
                    repository.deleteMediaItem(id)
                }
            }
        }
        pendingDeleteId = null
        isPendingComparisonDelete = false
    }

    val requestDeletion: (String, Boolean) -> Unit = { id, isComparison ->
        val item = repository.getMediaItemById(id)
        if (item != null) {
            val uri = Uri.parse(item.uriPath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.toString().startsWith("content://media/")) {
                try {
                    // Save pending state for callback
                    pendingDeleteId = id
                    isPendingComparisonDelete = isComparison
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                    deleteRequestLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } catch (e: Exception) {
                    // Fallback to direct delete attempt
                    try {
                        val deleted = context.contentResolver.delete(uri, null, null)
                        if (deleted > 0) {
                            if (isComparison) repository.deleteComparisonMedia(id) else repository.deleteMediaItem(id)
                        } else if (isComparison) {
                            // If delete returns 0 but it's a comparison, we still might want to remove it from app
                            repository.deleteComparisonMedia(id)
                        } else {
                            repository.deleteMediaItem(id)
                        }
                    } catch (e2: Exception) {
                        // Force app-level removal if file is missing or unresolvable
                        if (isComparison) repository.deleteComparisonMedia(id) else repository.deleteMediaItem(id)
                    }
                }
            } else {
                // Legacy or non-MediaStore (e.g. app-owned private files)
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: Exception) {}
                // Always proceed with app-level deletion for legacy
                if (isComparison) repository.deleteComparisonMedia(id) else repository.deleteMediaItem(id)
            }
        }
    }

    var currentRoute by remember { mutableStateOf(NavDestination.LIBRARY.route) }
    var currentImprovementId by remember { mutableStateOf<String?>(null) }
    var currentFindingId by remember { mutableStateOf<String?>(null) }
    var showImprovementDetail by remember { mutableStateOf(false) }
    var showFindingDetail by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showAuraMomentsSelection by remember { mutableStateOf(false) }
    var activeMomentsMode by remember { mutableStateOf<com.example.data.MomentsMode?>(null) }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(
                    text = "Exit Aura?",
                    fontWeight = FontWeight.Bold,
                    color = AuraMidnight
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to exit?",
                    color = AuraMidnight
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    }
                ) {
                    Text(
                        text = "Exit",
                        fontWeight = FontWeight.Bold,
                        color = DiscoveryViolet
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = AuraMidnight
                    )
                }
            },
            containerColor = AuraCrispWhite
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!isPlayerActive && !showEngagementDebugger && !showAuraMomentsSelection && activeMomentsMode == null) {
                AuraBottomNavigation(
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        currentRoute = destination.route
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (activeMomentsMode != null) {
                val momentsItems = remember(activeMomentsMode, mediaItems) {
                    com.example.data.AuraMomentsEngine.generateSlideshow(mediaItems, activeMomentsMode!!)
                }
                com.example.ui.screens.AuraMomentsSlideshowScreen(
                    items = momentsItems,
                    mode = activeMomentsMode!!,
                    repository = repository,
                    onClose = { activeMomentsMode = null }
                )
            } else if (showAuraMomentsSelection) {
                com.example.ui.screens.AuraMomentsSelectionScreen(
                    onSelectMode = { mode ->
                        showAuraMomentsSelection = false
                        activeMomentsMode = mode
                    },
                    onBack = {
                        showAuraMomentsSelection = false
                    }
                )
            } else if (showEngagementDebugger && com.example.BuildConfig.ENABLE_DEVELOPER_TOOLS) {
                EngagementDebuggerScreen(
                    repository = repository,
                    onBack = { showEngagementDebugger = false },
                    onNavigateToCleanupDebug = {
                        showEngagementDebugger = false
                        currentRoute = NavDestination.CLEANUP_DASHBOARD.route
                    }
                )
            } else if (currentRoute == NavDestination.INTELLIGENCE.route && com.example.BuildConfig.ENABLE_DEVELOPER_TOOLS) {
                val intelligenceRepository = repository.intelligenceRepository
                if (intelligenceRepository == null || databaseState != DatabaseState.READY) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    if (showImprovementDetail && currentImprovementId != null) {
                        com.example.ui.screens.ImprovementDetailScreen(
                            improvementId = currentImprovementId!!,
                            repository = intelligenceRepository,
                            onBack = { showImprovementDetail = false }
                        )
                    } else if (showFindingDetail && currentFindingId != null) {
                        com.example.ui.screens.FindingDetailScreen(
                            findingId = currentFindingId!!,
                            repository = intelligenceRepository,
                            onBack = { showFindingDetail = false },
                            onViewImprovement = { id ->
                                currentImprovementId = id
                                showImprovementDetail = true
                            }
                        )
                    } else {
                        com.example.ui.screens.AuraIntelligenceScreen(
                            repository = intelligenceRepository,
                            onNavigateToImprovement = { id ->
                                currentImprovementId = id
                                showImprovementDetail = true
                            },
                            onNavigateToFinding = { id ->
                                currentFindingId = id
                                showFindingDetail = true
                            },
                            onNavigateToCleanupDebug = {
                                currentRoute = NavDestination.CLEANUP_DASHBOARD.route
                            }
                        )
                    }
                }
            } else if (isPlayerActive && currentSelectedItem != null) {
                MediaDetailScreen(
                    playlistState = activePlaylist,
                    item = currentSelectedItem,
                    repository = repository,
                    onBack = {
                        repository.clearPlaylist()
                    },
                    onFavoriteToggle = { id -> repository.toggleFavorite(id) },
                    onNext = { repository.nextPlaylistItem() },
                    onPrevious = { repository.previousPlaylistItem() },
                    onSelectIndex = { idx -> repository.selectPlaylistItem(idx) },
                    onUpdateRating = { id, rating -> repository.updateRating(id, rating) },
                    onDeleteMedia = { id ->
                        val isComparison = activePlaylist?.sourceTitle == "Compare Pair"
                        requestDeletion(id, isComparison)
                    },
                    onMicroMoment = { id, taps -> repository.recordMicroMoment(id, taps) },
                    onSeeSimilar = { targetItem ->
                        val similar = repository.getSimilarMedia(targetItem)
                        if (similar.isNotEmpty()) {
                            repository.setPlaylist(items = similar, initialIndex = 0, sourceTitle = "See Similar — ${targetItem.title}")
                            android.widget.Toast.makeText(context, "Loaded ${similar.size} similar items", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "No similar media found", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAISkipEvent = { mediaId, eventType, fromPos, toPos ->
                        repository.recordAISkipEvent(mediaId, eventType, fromPos, toPos)
                    }
                )
            } else {
                AnimatedContent(
                    targetState = currentRoute,
                    transitionSpec = {
                        val initialIndex = when (initialState) {
                            NavDestination.LIBRARY.route -> 0
                            NavDestination.DISCOVER.route -> 1
                            NavDestination.COMPARE.route -> 2
                            NavDestination.COLLECTIONS.route -> 3
                            NavDestination.PROFILE.route -> 4
                            NavDestination.PLAYBACK_DIAGNOSTICS.route -> 4 // Same as profile for logic
                            NavDestination.INTELLIGENCE.route -> 5
                            NavDestination.FAVORITES.route -> 6
                            NavDestination.CLEANUP_REVIEW.route -> 7
                            NavDestination.CLEANUP_DASHBOARD.route -> 8
                            else -> 0
                        }
                        val targetIndex = when (targetState) {
                            NavDestination.LIBRARY.route -> 0
                            NavDestination.DISCOVER.route -> 1
                            NavDestination.COMPARE.route -> 2
                            NavDestination.COLLECTIONS.route -> 3
                            NavDestination.PROFILE.route -> 4
                            NavDestination.PLAYBACK_DIAGNOSTICS.route -> 4
                            NavDestination.INTELLIGENCE.route -> 5
                            NavDestination.FAVORITES.route -> 6
                            NavDestination.CLEANUP_REVIEW.route -> 7
                            NavDestination.CLEANUP_DASHBOARD.route -> 8
                            else -> 0
                        }

                        if (targetIndex > initialIndex) {
                            (slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(300))).togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(300))
                            )
                        } else {
                            (slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(300))).togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(300))
                            )
                        }
                    },
                    label = "screen_transition"
                ) { route ->
                    when (route) {
                        NavDestination.LIBRARY.route -> {
                            LibraryScreen(
                                mediaItems = mediaItems,
                                repository = repository,
                                importProgress = importProgress,
                                scanProgress = scanProgress,
                                onMediaSelect = { _ -> /* Playlist handled internally by screen */ },
                                onCompareLaunch = { selectedIds ->
                                    repository.startCompareSelectionSession(selectedIds)
                                    currentRoute = NavDestination.COMPARE.route
                                },
                                onFavoriteToggle = { id -> repository.recordLike(id) },
                                onImportUris = { uris ->
                                    repository.importMediaFromUris(context, uris)
                                },
                                onScanDevice = {
                                    repository.scanLocalMedia(context)
                                },
                                deleteLauncher = deleteRequestLauncher
                            )
                        }
                        NavDestination.DISCOVER.route -> {
                            DiscoverScreen(
                                repository = repository,
                                viewModel = discoverViewModel,
                                onMediaSelect = { item ->
                                    // Handle direct media selection if needed
                                },
                                onObsessionSelect = { obsession ->
                                    // Obsession selection handled by DiscoverViewModel/Screen
                                },
                                onScanAndImport = {
                                    currentRoute = NavDestination.LIBRARY.route
                                }
                            )
                        }
                        NavDestination.COMPARE.route -> {
                            CompareScreen(
                                pairwiseState = pairwiseState,
                                session = compareSession,
                                mediaItemsMap = mediaItemsMap,
                                compareMediaType = compareMediaType,
                                compareStrategy = compareStrategy,
                                compareSort = compareSort,
                                onMediaTypeSelect = { filter -> repository.setCompareMediaType(filter) },
                                onStrategySelect = { strategy -> repository.setCompareStrategy(strategy) },
                                onSortSelect = { sort -> repository.setCompareSort(sort) },
                                onVote = { chosenId -> repository.recordComparisonVote(chosenId) },
                                onSkip = { repository.skipComparison() },
                                onDeleteMedia = { id -> requestDeletion(id, true) },
                                onFavoriteToggle = { id -> repository.addToFavorites(id) },
                                onExitSession = {
                                    repository.exitCompareSelectionSession()
                                    currentRoute = NavDestination.LIBRARY.route
                                },
                                onRestartSession = { repository.restartCompareSelectionSession() },
                                onMediaSelect = { item ->
                                    repository.setPlaylist(
                                        items = listOf(pairwiseState.optionA, pairwiseState.optionB),
                                        initialIndex = if (item.id == pairwiseState.optionA.id) 0 else 1,
                                        sourceTitle = "Compare Pair"
                                    )
                                }
                            )
                        }
                        NavDestination.COLLECTIONS.route -> {
                            CollectionsScreen(
                                mediaItems = mediaItems,
                                repository = repository,
                                onCollectionSelect = { collectionItem, itemsInCollection ->
                                    repository.setPlaylist(
                                        items = itemsInCollection,
                                        initialIndex = itemsInCollection.indexOfFirst { it.id == collectionItem.id }.coerceAtLeast(0),
                                        sourceTitle = "Collection"
                                    )
                                },
                                onFavoriteToggle = { id -> repository.addToFavorites(id) },
                                onLaunchAuraMoments = { showAuraMomentsSelection = true },
                                onSearch = { query ->
                                    repository.librarySearchQuery = query
                                    currentRoute = NavDestination.LIBRARY.route
                                }
                            )
                        }
                        NavDestination.FAVORITES.route -> {
                            FavoritesScreen(
                                mediaItems = mediaItems,
                                onMediaSelect = { selectedItem ->
                                    val favoriteItems = mediaItems.filter { it.isFavorite }
                                    val selectedIndex = favoriteItems.indexOfFirst { it.id == selectedItem.id }
                                    if (selectedIndex != -1) {
                                        repository.setPlaylist(
                                            items = favoriteItems,
                                            initialIndex = selectedIndex,
                                            sourceTitle = "Favorites"
                                        )
                                    }
                                },
                                onFavoriteToggle = { id -> repository.removeFromFavorites(id) },
                                onLike = { id ->
                                    repository.recordLike(id)
                                    repository.addToFavorites(id)
                                },
                                onBack = {
                                    currentRoute = NavDestination.PROFILE.route
                                }
                            )
                        }
                        NavDestination.PROFILE.route -> {
                            ProfileScreen(
                                repository = repository,
                                onNavigateToFavorites = {
                                    currentRoute = NavDestination.FAVORITES.route
                                },
                                onNavigateToCleanup = {
                                    currentRoute = NavDestination.CLEANUP_REVIEW.route
                                },
                                onNavigateToPrivacyPolicy = {
                                    currentRoute = NavDestination.PRIVACY_POLICY.route
                                },
                                onNavigateToDiagnostics = {
                                    currentRoute = NavDestination.PLAYBACK_DIAGNOSTICS.route
                                },
                                onLaunchAuraMoments = {
                                    showAuraMomentsSelection = true
                                }
                            )
                        }
                        NavDestination.PLAYBACK_DIAGNOSTICS.route -> {
                            com.example.ui.screens.PlaybackDiagnosticsScreen(
                                viewModel = playbackDiagnosticsViewModel,
                                onBack = {
                                    currentRoute = NavDestination.PROFILE.route
                                }
                            )
                        }
                        NavDestination.PRIVACY_POLICY.route -> {
                            com.example.ui.screens.PrivacyPolicyScreen(
                                onBack = {
                                    currentRoute = NavDestination.PROFILE.route
                                }
                            )
                        }
                        NavDestination.CLEANUP_REVIEW.route -> {
                            com.example.ui.screens.CleanupReviewScreen(
                                viewModel = cleanupReviewViewModel,
                                onBack = {
                                    currentRoute = NavDestination.PROFILE.route
                                },
                                onMediaDetail = { item ->
                                    // Navigate to detail
                                },
                                deleteLauncher = deleteRequestLauncher
                            )
                        }
                        NavDestination.CLEANUP_DASHBOARD.route -> {
                            com.example.ui.screens.CleanupIntelligenceDashboardScreen(
                                viewModel = cleanupViewModel,
                                onBack = {
                                    currentRoute = NavDestination.PROFILE.route
                                }
                            )
                        }
                    }
                }
            }

            // Global Emotional Intelligence Experience Overlay
            com.example.ui.components.AuraMomentOverlay(repository = repository)
        }
    }
}
