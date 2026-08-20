package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextAlign
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    mediaItems: List<MediaItem>,
    repository: MediaRepository,
    importProgress: ImportProgressState,
    scanProgress: ScanProgressState = ScanProgressState(),
    onMediaSelect: (MediaItem) -> Unit,
    onCompareLaunch: (Set<String>) -> Unit,
    onFavoriteToggle: ((String) -> Unit)? = null,
    onImportUris: ((List<Uri>) -> Unit)? = null,
    onScanDevice: (suspend () -> Unit)? = null,
    deleteLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>? = null,
    modifier: Modifier = Modifier
) {
    val selectedFilter by repository.libraryFilterFlow.collectAsStateWithLifecycle()
    val activeCategory by repository.activeSortCategory.collectAsStateWithLifecycle()
    val standardSort by repository.selectedStandardSort.collectAsStateWithLifecycle()
    val intelligentSort by repository.selectedIntelligentSort.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf(repository.librarySearchQuery) }
    var isSearchActive by remember { mutableStateOf(false) }

    // Multi-select state (Phase 4)
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = repository.libraryScrollIndex,
        initialFirstVisibleItemScrollOffset = repository.libraryScrollOffset
    )

    // Automatically scroll to top when filters or sorts change
    var isFirstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(selectedFilter, activeCategory, standardSort, intelligentSort, searchQuery) {
        if (isFirstLoad) {
            isFirstLoad = false
        } else {
            gridState.scrollToItem(0)
        }
    }

    // Clear selection mode when deletion finishes (Phase 4)
    val deletionState by repository.safeDeleteManager.deletionState.collectAsStateWithLifecycle()
    LaunchedEffect(deletionState) {
        if (deletionState == com.example.data.cleanup.DeletionState.CONFIRMED ||
            deletionState == com.example.data.cleanup.DeletionState.CANCELLED ||
            deletionState == com.example.data.cleanup.DeletionState.FAILED) {
            isSelectionMode = false
            selectedIds = emptySet()
        }
    }

    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 8 }
    }

    val latestSortedItems by repository.latestAiSortRecommendation.collectAsStateWithLifecycle()
    val mediaItemsMap by repository.mediaItemsMap.collectAsStateWithLifecycle()
    val dbState by repository.databaseState.collectAsStateWithLifecycle()

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && searchQuery.length >= 2) {
            delay(1500) // Only record if user stops typing for 1.5s
            repository.recordSearch(searchQuery)
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                repository.libraryScrollIndex = index
                repository.libraryScrollOffset = offset
            }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty() && onImportUris != null) {
            onImportUris(uris)
        }
    }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val scanPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        coroutineScope.launch {
            onScanDevice?.invoke()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = this.maxWidth > this.maxHeight
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AuraCrispWhite)
        ) {
            // Selection/Search/Library Header
            if (isSelectionMode) {
                SelectionHeader(
                    selectedCount = selectedIds.size,
                    onDelete = {
                        if (deleteLauncher != null) {
                            val itemsToDelete = selectedIds.mapNotNull { mediaItemsMap[it] }
                            repository.safeDeleteManager.requestDeletion(
                                context = context,
                                items = itemsToDelete,
                                recommendations = emptyList(),
                                launcher = deleteLauncher
                            )
                        }
                    },
                    onCompare = {
                        if (selectedIds.size >= 4) {
                            onCompareLaunch(selectedIds)
                        }
                    },
                    onCancel = {
                        isSelectionMode = false
                        selectedIds = emptySet()
                    }
                )
            } else if (isSearchActive) {
                SearchHeader(
                    searchQuery = searchQuery,
                    onQueryChange = { 
                        searchQuery = it
                        repository.librarySearchQuery = it
                    },
                    onExit = { 
                        isSearchActive = false
                        searchQuery = ""
                        repository.librarySearchQuery = ""
                    }
                )
            } else {
                if (!isLandscape) {
                    AuraSectionHeader(
                        title = "Library",
                        subtitle = "Your complete media collection"
                    )
                } else {
                    // Compact Landscape Header for Library
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.titleSmall,
                            color = DiscoveryViolet,
                            fontWeight = FontWeight.Bold
                        )
                        UtilityControlsRow(
                            selectedFilter = selectedFilter,
                            onFilterChange = { repository.libraryFilter = it },
                            onSearchClick = { isSearchActive = true },
                            onImportClick = {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                            onSyncClick = {
                                if (dbState == DatabaseState.READY) {
                                    scanPermissionLauncher.launch(permissionsToRequest)
                                } else {
                                    repository.initDatabase(context)
                                }
                            },
                            onScrollToTop = {
                                coroutineScope.launch { gridState.scrollToItem(0) }
                            },
                            showScrollToTop = showScrollToTop,
                            dbState = dbState,
                            isCompact = true
                        )
                    }
                }
            }

            if (!isSelectionMode && !isSearchActive && !isLandscape) {
                UtilityControlsRow(
                    selectedFilter = selectedFilter,
                    onFilterChange = { repository.libraryFilter = it },
                    onSearchClick = { isSearchActive = true },
                    onImportClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onSyncClick = {
                        if (dbState == DatabaseState.READY) {
                            scanPermissionLauncher.launch(permissionsToRequest)
                        } else {
                            repository.initDatabase(context)
                        }
                    },
                    onScrollToTop = {
                        coroutineScope.launch { gridState.scrollToItem(0) }
                    },
                    showScrollToTop = showScrollToTop,
                    dbState = dbState
                )
            }

            // Compact Controls: Filters and Intelligence Modes
            CompactControlsRow(
                activeCategory = activeCategory,
                onCategoryChange = { repository.sortCategory = it },
                standardSort = standardSort,
                onStandardSortChange = { repository.standardSort = it },
                intelligentSort = intelligentSort,
                onIntelligentSortChange = { repository.intelligentSort = it }
            )

            // Loading Progress Overlays
            if (scanProgress.isScanning || importProgress.isImporting) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
                        color = DiscoveryViolet,
                        trackColor = AuraSubtleBorder
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = { 
                    // Refresh the sort (triggers session seed refresh for Random)
                    if (activeCategory == SortCategory.STANDARD) {
                        repository.standardSort = standardSort
                    } else {
                        repository.intelligentSort = intelligentSort
                    }

                    if (dbState == com.example.data.DatabaseState.READY) {
                        scanPermissionLauncher.launch(permissionsToRequest)
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (latestSortedItems.isEmpty()) {
                    EmptyLibraryView(
                        onImportClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        onScanClick = { scanPermissionLauncher.launch(permissionsToRequest) }
                    )
                } else {
                    // Infinite Scrolling Grid with adaptive columns for responsive landscape support
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        itemsIndexed(
                            items = latestSortedItems,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            LibraryGalleryMediaTile(
                                item = item,
                                isSelected = selectedIds.contains(item.id),
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (selectedIds.contains(item.id)) {
                                            selectedIds - item.id
                                        } else {
                                            selectedIds + item.id
                                        }
                                        if (selectedIds.isEmpty()) isSelectionMode = false
                                    } else {
                                        // AURA PHASE 1: Use stable media identity and current visible list
                                        val currentMediaItems = latestSortedItems.mapNotNull { mediaItemsMap[it.id] }
                                        val clickedItemIndex = currentMediaItems.indexOfFirst { it.id == item.id }
                                        
                                        if (clickedItemIndex != -1) {
                                            repository.setLibraryPlaylist(
                                                items = currentMediaItems,
                                                initialIndex = clickedItemIndex
                                            )
                                            onMediaSelect(currentMediaItems[clickedItemIndex])
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds = setOf(item.id)
                                    }
                                },
                                onLike = {
                                    repository.recordLike(item.id)
                                    repository.addToFavorites(item.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    onDelete: () -> Unit,
    onCompare: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DiscoveryGradient)
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$selectedCount Selected",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            // COMPARE ACTION
            Surface(
                onClick = onCompare,
                enabled = selectedCount >= 4,
                shape = CircleShape,
                color = if (selectedCount >= 4) Color.White else Color.White.copy(alpha = 0.2f),
                modifier = Modifier.height(36.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "COMPARE ($selectedCount)",
                        color = if (selectedCount >= 4) DiscoveryViolet else Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }
    }
}

@Composable
private fun SearchHeader(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .background(AuraCrispWhite)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.Default.Close, contentDescription = "Exit Search", tint = AuraMidnight)
        }
        
        TextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search your library...", color = AuraMutedSlate) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = DiscoveryViolet
            ),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AuraMidnight)
        )
    }
}

@Composable
private fun UtilityControlsRow(
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit,
    onSyncClick: () -> Unit,
    onScrollToTop: () -> Unit,
    showScrollToTop: Boolean,
    dbState: DatabaseState,
    isCompact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (isCompact) 0.dp else 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Filters
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            AuraFilterChip(
                label = "All",
                isSelected = selectedFilter == "ALL",
                onClick = { onFilterChange("ALL") }
            )
            AuraFilterChip(
                label = "Photos",
                isSelected = selectedFilter == "PHOTO",
                onClick = { onFilterChange("PHOTO") }
            )
            AuraFilterChip(
                label = "Videos",
                isSelected = selectedFilter == "VIDEO",
                onClick = { onFilterChange("VIDEO") }
            )
        }

        // Right Side: Action Icons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showScrollToTop && !isCompact) {
                IconButton(onClick = onScrollToTop, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll to top", tint = AuraMutedSlate)
                }
            }
            
            IconButton(onClick = onSearchClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = AuraMidnight)
            }
            
            IconButton(onClick = onImportClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Import", tint = AuraMidnight)
            }
            
            IconButton(onClick = onSyncClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Sync, 
                    contentDescription = "Sync", 
                    tint = if (dbState == DatabaseState.READY) DiscoveryViolet else AuraMutedSlate
                )
            }
        }
    }
}

@Composable
private fun CompactControlsRow(
    activeCategory: SortCategory,
    onCategoryChange: (SortCategory) -> Unit,
    standardSort: StandardSortOption,
    onStandardSortChange: (StandardSortOption) -> Unit,
    intelligentSort: IntelligentSortOption,
    onIntelligentSortChange: (IntelligentSortOption) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AuraSortSelector(
            label = "Sort Mode",
            currentOption = if (activeCategory == SortCategory.STANDARD) "Standard" else "Aura AI",
            isSelected = true,
            options = SortCategory.entries,
            onOptionSelected = onCategoryChange,
            onPillClick = { /* No-op for main pill */ },
            getDisplayName = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } },
            selectedColor = if (activeCategory == SortCategory.STANDARD) AuraMutedSlate else DiscoveryViolet
        )

        Spacer(modifier = Modifier.width(4.dp))

        if (activeCategory == SortCategory.STANDARD) {
            AuraSortSelector(
                label = "Criteria",
                currentOption = when (standardSort) {
                    StandardSortOption.NEWEST_FIRST -> "Newest"
                    StandardSortOption.TITLE_ASC -> "Name A-Z"
                    StandardSortOption.TITLE_DESC -> "Name Z-A"
                    StandardSortOption.MOST_PLAYED -> "Popular"
                    StandardSortOption.RECENTLY_PLAYED -> "Recent"
                    StandardSortOption.SHORTEST_DURATION -> "Shortest"
                    StandardSortOption.LONGEST_DURATION -> "Longest"
                    StandardSortOption.RANDOM -> "Shuffle"
                    StandardSortOption.LEAST_PLAYED -> "Unplayed"
                },
                isSelected = false,
                options = StandardSortOption.entries,
                onOptionSelected = onStandardSortChange,
                onPillClick = { /* Toggle menu */ },
                getDisplayName = { it.name.replace("_", " ").lowercase().replaceFirstChar { char -> char.uppercase() } }
            )
        } else {
            AuraSortSelector(
                label = "Intelligence",
                currentOption = when (intelligentSort) {
                    IntelligentSortOption.PERSONALIZED -> "Personalized"
                    IntelligentSortOption.REDISCOVER -> "Rediscover"
                    IntelligentSortOption.LEAST_INTERACTED -> "Unseen"
                    IntelligentSortOption.EXPLORE -> "Experimental"
                },
                isSelected = false,
                options = IntelligentSortOption.entries,
                onOptionSelected = onIntelligentSortChange,
                onPillClick = { /* Toggle menu */ },
                getDisplayName = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } }
            )
        }
    }
}

@Composable
private fun EmptyLibraryView(
    onImportClick: () -> Unit,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            tint = AuraMutedSlate.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your library is empty",
            style = MaterialTheme.typography.titleLarge,
            color = AuraMidnight
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Import media from your device or scan for local content to start building your intelligence profile.",
            style = MaterialTheme.typography.bodyMedium,
            color = AuraSlate,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onImportClick) {
                Text("IMPORT")
            }
            OutlinedButton(onClick = onScanClick) {
                Text("SCAN DEVICE")
            }
        }
    }
}
