package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MediaItem
import com.example.ui.components.AuraSquareMediaTile
import com.example.ui.components.AuraSectionHeader
import com.example.ui.components.AuraTopBar
import com.example.ui.theme.AuraBackground
import com.example.ui.theme.AuraBorder
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraOnSurfaceVariant
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.AuraSubtleSurface
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryViolet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.ui.components.AuraMediaThumbnail
import com.example.ui.components.TasteClusterCard
import com.example.data.intelligence.TasteClusterEvidence
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.AutoAwesome




data class SmartCollection(
    val id: String,
    val title: String,
    val description: String,
    val gradientColors: List<Long>,
    val items: List<MediaItem>,
    val section: String = "SMART_COLLECTIONS",
    val representativeItem: MediaItem? = null
)

@Composable
fun CollectionsScreen(
    mediaItems: List<MediaItem>,
    repository: com.example.data.MediaRepository,
    onCollectionSelect: (MediaItem, List<MediaItem>) -> Unit,
    onFavoriteToggle: ((String) -> Unit)? = null,
    onLaunchAuraMoments: (() -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedBrowserCollection by remember { mutableStateOf<SmartCollection?>(null) }
    var selectedCluster by remember { mutableStateOf<TasteClusterEvidence?>(null) }
    
    val watchHistory by repository.watchHistory.collectAsStateWithLifecycle()
    val recentSearches by repository.recentSearches.collectAsStateWithLifecycle()

    val dashboardViewModel: IntelligenceDashboardViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return IntelligenceDashboardViewModel(repository.intelligenceRepository!!) as T
            }
        }
    )
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val report = dashboardState.report


    if (selectedBrowserCollection != null) {
        val collection = selectedBrowserCollection!!
        BackHandler {
            selectedBrowserCollection = null
        }
        CollectionBrowserScreen(
            collection = collection,
            onBack = { selectedBrowserCollection = null },
            onMediaSelect = { selectedItem ->
                onCollectionSelect(selectedItem, collection.items)
            },
            onFavoriteToggle = onFavoriteToggle ?: {},
            repository = repository,
            modifier = modifier
        )
        return
    }

    val userMediaCollections = remember(mediaItems, watchHistory) {
        val userCollections = mutableListOf<SmartCollection>()

        fun List<MediaItem>.selectRepresentative(): MediaItem? {
            return this.sortedWith(
                compareByDescending<MediaItem> { it.isFavorite }
                    .thenByDescending { it.rating }
                    .thenByDescending { it.eloRating }
                    .thenByDescending { it.viewCount }
                    .thenBy { it.id }
            ).firstOrNull()
        }

        // 1. User Media -> Photos
        val userPhotos = mediaItems.filter {
            it.mediaType.equals("PHOTO", ignoreCase = true)
        }
        userCollections.add(
            SmartCollection(
                id = "user_media_photos",
                title = "Photos",
                description = "${userPhotos.size} user photos",
                gradientColors = listOf(0xFF10B981, 0xFF06B6D4),
                items = userPhotos,
                section = "USER_MEDIA",
                representativeItem = userPhotos.selectRepresentative()
            )
        )

        // 2. User Media -> Videos (excluding generated clips)
        val userVideos = mediaItems.filter {
            (it.mediaType.equals("VIDEO", ignoreCase = true) || it.mediaType.equals("Movie", ignoreCase = true)) &&
            !it.genre.contains("Aura Generated", ignoreCase = true) &&
            !it.id.startsWith("aura_clip_")
        }
        userCollections.add(
            SmartCollection(
                id = "user_media_videos",
                title = "Videos",
                description = "${userVideos.size} user videos",
                gradientColors = listOf(0xFF3B82F6, 0xFF8B5CF6),
                items = userVideos,
                section = "USER_MEDIA",
                representativeItem = userVideos.selectRepresentative()
            )
        )

        // 3. User Media -> User Screenshots
        val userScreenshots = mediaItems.filter {
            it.genre.equals("User Screenshots", ignoreCase = true) ||
            it.genre.equals("User Screenshot", ignoreCase = true) ||
            it.id.startsWith("aura_snapshot_") ||
            it.title.contains("Aura_Frame_", ignoreCase = true) ||
            it.title.startsWith("Snapshot") ||
            it.imageUrl.contains("Aura_Frame_", ignoreCase = true) ||
            it.uriPath.contains("Aura_Frame_", ignoreCase = true) ||
            it.moodTags.contains("Snapshot")
        }
        userCollections.add(
            SmartCollection(
                id = "user_media_screenshots",
                title = "User Screenshots",
                description = "${userScreenshots.size} captured frame snapshots",
                gradientColors = listOf(0xFFF59E0B, 0xFF10B981),
                items = userScreenshots,
                section = "USER_MEDIA",
                representativeItem = userScreenshots.selectRepresentative()
            )
        )

        // 4. User Media -> Aura Generated Clips
        val auraClips = mediaItems.filter {
            it.genre.contains("Aura Generated", ignoreCase = true) ||
                    it.id.startsWith("aura_clip_") ||
                    it.moodTags.contains("Aura Generated")
        }
        userCollections.add(
            SmartCollection(
                id = "user_media_aura_clips",
                title = "Aura Selected Clips",
                description = "${auraClips.size} exported highlight clips",
                gradientColors = listOf(0xFFEC4899, 0xFF8B5CF6),
                items = auraClips,
                section = "USER_MEDIA",
                representativeItem = auraClips.selectRepresentative()
            )
        )

        // 5. Watch History
        if (watchHistory.isNotEmpty()) {
            userCollections.add(
                SmartCollection(
                    id = "user_watch_history",
                    title = "Watch History",
                    description = "Recently viewed media",
                    gradientColors = listOf(0xFF6366F1, 0xFF4338CA),
                    items = watchHistory,
                    section = "USER_MEDIA",
                    representativeItem = watchHistory.firstOrNull() // Already sorted by recency
                )
            )
        }

        userCollections
    }



    val smartCollections = remember(mediaItems) {
        val collections = mutableListOf<SmartCollection>()

        fun List<MediaItem>.selectRepresentative(): MediaItem? {
            return this.sortedWith(
                compareByDescending<MediaItem> { it.isFavorite }
                    .thenByDescending { it.rating }
                    .thenByDescending { it.eloRating }
                    .thenByDescending { it.viewCount }
                    .thenBy { it.id }
            ).firstOrNull()
        }

        val favorites = mediaItems.filter { it.isFavorite }
        if (favorites.isNotEmpty()) {
            collections.add(
                SmartCollection(
                    id = "smart_favorites",
                    title = "Your Favorites",
                    description = "${favorites.size} saved favorites",
                    gradientColors = listOf(0xFF8B5CF6, 0xFFEC4899),
                    items = favorites,
                    representativeItem = favorites.selectRepresentative()
                )
            )
        }

        val topRated = mediaItems.filter { it.rating >= 4.0f }
        if (topRated.isNotEmpty()) {
            collections.add(
                SmartCollection(
                    id = "smart_top_rated",
                    title = "Top Rated",
                    description = "${topRated.size} highly rated items",
                    gradientColors = listOf(0xFFF59E0B, 0xFFEF4444),
                    items = topRated,
                    representativeItem = topRated.selectRepresentative()
                )
            )
        }

        // Group remaining items by Genre if available
        val byGenre = mediaItems.filter {
            it.genre.isNotBlank() &&
                    !it.genre.equals("Personal Media", ignoreCase = true) &&
                    !it.genre.contains("Aura Generated", ignoreCase = true)
        }.groupBy { it.genre }

        byGenre.forEach { (genreName, items) ->
            if (items.isNotEmpty()) {
                collections.add(
                    SmartCollection(
                        id = "genre_${genreName.hashCode()}",
                        title = genreName,
                        description = "${items.size} media items",
                        gradientColors = listOf(0xFF6366F1, 0xFFA855F7),
                        items = items,
                        representativeItem = items.selectRepresentative()
                    )
                )
            }
        }

        collections
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraCrispWhite)
    ) {
        AuraSectionHeader(
            title = "Collections",
            subtitle = "Smart groups and local archives"
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // SIGNATURE STYLES
            item(span = { GridItemSpan(maxLineSpan) }) {
                // FORCE SHOW FOR DEBUGGING if report is missing or empty
                val clusters = report?.tasteProfile?.tasteClusters ?: emptyList()
                
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = "SIGNATURE STYLES",
                        color = DiscoveryViolet,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (clusters.isEmpty()) {
                        Text(
                            text = "No clusters found in report. Report exists: ${report != null}",
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(clusters) { cluster ->
                                TasteClusterCard(
                                    evidence = cluster,
                                    onClick = { selectedCluster = cluster },
                                    modifier = Modifier.width(280.dp)
                                )
                            }
                        }
                    }
                }
            }

            // AURA MOMENTS HERO CARD
            item(span = { GridItemSpan(maxLineSpan) }) {
                AuraMomentsHeroCard(
                    onLaunch = { onLaunchAuraMoments?.invoke() }
                )
            }

            // USER MEDIA SECTION HEADER
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        text = "USER MEDIA",
                        color = DiscoveryViolet,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Your videos, photos and exported Aura clips",
                        color = AuraMutedSlate,
                        fontSize = 12.sp
                    )
                }
            }

            // USER MEDIA CARDS
            items(userMediaCollections, key = { it.id }) { collection ->
                CollectionTileCard(
                    collection = collection,
                    onClick = {
                        selectedBrowserCollection = collection
                    }
                )
            }

            // SMART COLLECTIONS SECTION HEADER
            if (smartCollections.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)) {
                        Text(
                            text = "SMART COLLECTIONS",
                            color = AuraMutedSlate,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Auto-organized categories based on themes",
                            color = AuraMutedSlate,
                            fontSize = 12.sp
                        )
                    }
                }

                // SMART COLLECTIONS CARDS
                items(smartCollections, key = { it.id }) { collection ->
                    CollectionTileCard(
                        collection = collection,
                        onClick = {
                            selectedBrowserCollection = collection
                        }
                    )
                }
            }

            // SEARCH HISTORY SECTION
            if (recentSearches.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "RECENT SEARCHES",
                                color = AuraMutedSlate,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Text(
                            text = "CLEAR ALL",
                            color = DiscoveryViolet,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { repository.clearSearchHistory() }
                                .padding(4.dp)
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    com.example.ui.screens.SearchHistoryList(
                        searches = recentSearches,
                        onSearchClick = { onSearch?.invoke(it) }
                    )
                }
            }
        }
    }

    if (selectedCluster != null) {
        TasteClusterDetailView(
            evidence = selectedCluster!!,
            onDismiss = { selectedCluster = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchHistoryList(
    searches: List<String>,
    onSearchClick: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        searches.forEach { query ->
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSearchClick(query) },
                color = AuraSubtleSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
            ) {
                Text(
                    text = query,
                    color = AuraMidnight,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}


@Composable
private fun CollectionBrowserScreen(
    collection: SmartCollection,
    onBack: () -> Unit,
    onMediaSelect: (MediaItem) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    repository: com.example.data.MediaRepository,
    modifier: Modifier = Modifier
) {
    var currentPage by remember(collection.id) { mutableIntStateOf(1) }
    val pageSize = 20
    val totalItems = collection.items.size
    val totalPages = maxOf(1, (totalItems + pageSize - 1) / pageSize)

    val currentItems = remember(collection.items, currentPage) {
        val startIndex = (currentPage - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, totalItems)
        if (startIndex < totalItems) {
            collection.items.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraCrispWhite)
            .testTag("collection_browser_${collection.id}")
    ) {
        AuraTopBar(
            title = collection.title,
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("collection_browser_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AuraMidnight
                    )
                }
            }
        )

        // Sub-header info
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AuraSubtleSurface,
            border = androidx.compose.foundation.BorderStroke(width = 0.dp, color = Color.Transparent)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${collection.items.size} Total Items",
                    fontSize = 13.sp,
                    color = AuraMutedSlate,
                    fontWeight = FontWeight.Medium
                )

                if (totalPages > 1) {
                    Text(
                        text = "Page $currentPage of $totalPages",
                        fontSize = 13.sp,
                        color = DiscoveryViolet,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (currentItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No items in this collection yet",
                    color = AuraMutedSlate,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(currentItems, key = { it.id }) { item ->
                    AuraSquareMediaTile(
                        item = item,
                        onClick = { onMediaSelect(item) },
                        onLike = {
                            repository.recordLike(item.id)
                            repository.addToFavorites(item.id)
                        },
                        onFavoriteToggle = { onFavoriteToggle(item.id) }
                    )
                }
            }

            // Pagination Controls Bar at Bottom if > 1 page
            if (totalPages > 1) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AuraCrispWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { if (currentPage > 1) currentPage-- },
                            enabled = currentPage > 1,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = DiscoveryViolet,
                                disabledContentColor = Color.Gray
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
                            modifier = Modifier.testTag("browser_prev_page")
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prev")
                        }

                        Text(
                            text = "$currentPage / $totalPages",
                            color = AuraMidnight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = { if (currentPage < totalPages) currentPage++ },
                            enabled = currentPage < totalPages,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = DiscoveryViolet,
                                disabledContentColor = Color.Gray
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
                            modifier = Modifier.testTag("browser_next_page")
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionTileCard(
    collection: SmartCollection,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("collection_card_${collection.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(AuraSubtleSurface)
                .border(1.dp, AuraSubtleBorder, RoundedCornerShape(12.dp))
        ) {
            if (collection.representativeItem != null) {
                AuraMediaThumbnail(
                    itemId = collection.representativeItem.id,
                    mediaType = collection.representativeItem.mediaType,
                    imageUrl = collection.representativeItem.imageUrl,
                    uriPath = collection.representativeItem.uriPath,
                    title = collection.representativeItem.title,
                    modifier = Modifier.fillMaxSize(),
                    locationTag = "collection_thumbnail"
                )
                
                // Readability Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)),
                                startY = 100f
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${collection.items.size} items",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }


        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = collection.title,
            color = AuraMidnight,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = collection.description,
            color = AuraMutedSlate,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AuraMomentsHeroCard(
    onLaunch: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, AuraSubtleBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onLaunch)
            .testTag("aura_moments_hero_card"),
        color = AuraSubtleSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(DiscoveryGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "Aura Slideshow",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AURA SLIDESHOW",
                    color = DiscoveryViolet,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Your Visual Taste",
                    color = AuraMidnight,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Intelligent slideshows curated from your library",
                    color = AuraMutedSlate,
                    fontSize = 12.sp
                )
            }
        }
    }
}
