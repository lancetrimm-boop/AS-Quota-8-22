package com.example.ui.screens

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.EmotionalRole
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.ObsessionContentBatch
import com.example.data.ObsessionRecommendation
import com.example.data.RecommendationExplanation
import com.example.data.SystemDiscoveryState
import com.example.data.TasteReveal
import com.example.ui.components.AuraButton
import com.example.ui.components.AuraEngagementTunerCard
import com.example.ui.components.AuraSectionHeader
import com.example.ui.components.AuraTopBar
import com.example.ui.components.DiscoveryPolicyControl
import com.example.ui.components.VideoTilePreview
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraSlate
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.AuraSubtleSurface
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryViolet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    repository: MediaRepository,
    viewModel: DiscoverViewModel,
    onMediaSelect: (MediaItem) -> Unit,
    onObsessionSelect: (ObsessionRecommendation) -> Unit,
    onScanAndImport: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(enabled = detailState is ObsessionDetailState.Active) {
        com.example.data.AuraTelemetryService.logEvent(
            repository, 
            com.example.data.AuraTelemetryService.EventType.NAVIGATED_BACK
        )
        viewModel.deselectObsession()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = this.maxWidth > this.maxHeight
        val horizontalPadding = if (isLandscape) (this.maxWidth * 0.15f) else 0.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AuraCrispWhite)
                .padding(horizontal = horizontalPadding)
        ) {
            when (val dState = detailState) {
                is ObsessionDetailState.Active -> {
                    ObsessionDetailView(
                        obsession = dState.obsession,
                        batch = dState.batch,
                        isLoading = dState.isLoading,
                        onBack = { viewModel.deselectObsession() },
                        onMediaSelect = onMediaSelect,
                        onFavoriteToggle = { id -> repository.addToFavorites(id) },
                        onExpand = { viewModel.expandCurrentObsession() },
                        onTrySomethingNew = { viewModel.trySomethingNew() },
                        repository = repository
                    )
                }
                else -> {
                    AuraSectionHeader(
                        title = "Discover",
                        subtitle = "Your personal media intelligence engine",
                        actions = {
                            IconButton(onClick = { /* Search Placeholder */ }) {
                                Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search", tint = AuraMidnight)
                            }
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(imageVector = Icons.Outlined.Sync, contentDescription = "Refresh", tint = AuraMidnight)
                            }
                        }
                    )

                    PullToRefreshBox(
                        isRefreshing = feedState is DiscoverFeedState.Loading,
                        onRefresh = { viewModel.refresh(forceNewSession = true) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (val state = feedState) {
                            is DiscoverFeedState.Loading -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    item {
                                        com.example.ui.components.AuraSkeletonTile(
                                            modifier = Modifier.fillMaxWidth().height(300.dp)
                                        )
                                    }
                                    items(3) {
                                        com.example.ui.components.AuraSkeletonTile(
                                            modifier = Modifier.fillMaxWidth().height(250.dp)
                                        )
                                    }
                                }
                            }
                            is DiscoverFeedState.Error -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = state.message, color = Color.Red)
                                }
                            }
                            is DiscoverFeedState.Success -> {
                                if (state.tasteReveal != null) {
                                    TasteRevealScreen(
                                        reveal = state.tasteReveal,
                                        onConfirm = { viewModel.markTasteRevealSeen() }
                                    )
                                } else if (state.snapshot.obsessions.isEmpty()) {
                                    EmptyDiscoverView(
                                        onScanAndImport = onScanAndImport
                                    )
                                } else {
                                    DiscoveryFeed(
                                        obsessions = state.snapshot.obsessions,
                                        systemState = state.snapshot.systemState,
                                        onObsessionSelect = { obsession ->
                                            viewModel.selectObsession(obsession)
                                            onObsessionSelect(obsession)
                                        },
                                        repository = repository
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObsessionDetailView(
    obsession: ObsessionRecommendation,
    batch: ObsessionContentBatch,
    isLoading: Boolean,
    onBack: () -> Unit,
    onMediaSelect: (MediaItem) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onExpand: () -> Unit,
    onTrySomethingNew: () -> Unit,
    repository: MediaRepository
) {
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize().background(AuraCrispWhite)) {
        AuraTopBar(
            title = obsession.title,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AuraMidnight
                    )
                }
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = obsession.subtitle,
                        fontSize = 14.sp,
                        color = AuraSlate
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${batch.items.size} DISCOVERIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DiscoveryViolet,
                        letterSpacing = 1.sp
                    )
                }
            }

            items(
                items = batch.items,
                key = { it.id }
            ) { item ->
                ImmersiveMediaCard(
                    item = item,
                    onClick = {
                        val allItems = batch.items
                        val selectedIndex = allItems.indexOf(item)
                        val truncatedPlaylist = if (selectedIndex != -1) {
                            allItems.subList(selectedIndex, allItems.size)
                        } else {
                            listOf(item)
                        }

                        repository.setPlaylist(
                            items = truncatedPlaylist,
                            initialIndex = 0,
                            sourceTitle = obsession.title
                        )
                        onMediaSelect(item)
                    },
                    onFavoriteToggle = { onFavoriteToggle(item.id) },
                    repository = repository
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = DiscoveryViolet)
                    }
                }
            } else if (batch.items.isNotEmpty()) {
                item {
                    EndOfBatchView(
                        canExpand = batch.canExpand,
                        onExpand = onExpand,
                        onTrySomethingNew = onTrySomethingNew,
                        onBackToObsessions = onBack,
                        repository = repository
                    )
                }
            }
        }
    }

    // Record exposures for the batch items as they appear
    LaunchedEffect(batch.items) {
        if (batch.items.isNotEmpty()) {
            repository.recordExposures(batch.items.map { it.id })
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            repository.forceFlushExposures()
        }
    }
}

@Composable
fun EndOfBatchView(
    canExpand: Boolean,
    onExpand: () -> Unit,
    onTrySomethingNew: () -> Unit,
    onBackToObsessions: () -> Unit,
    repository: MediaRepository
) {
    LaunchedEffect(Unit) {
        com.example.data.AuraTelemetryService.logEvent(
            repository, 
            com.example.data.AuraTelemetryService.EventType.END_OF_BATCH_REACHED
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = DiscoveryViolet.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "End of current batch",
            style = MaterialTheme.typography.titleMedium,
            color = AuraMidnight
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Aura has analyzed all candidates in this obsession. Would you like more or something different?",
            style = MaterialTheme.typography.bodySmall,
            color = AuraSlate,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (canExpand) {
            AuraButton(
                text = "SEE MORE IN THIS STYLE",
                onClick = onExpand,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        AuraButton(
            text = "TRY SOMETHING NEW",
            onClick = onTrySomethingNew,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        androidx.compose.material3.TextButton(onClick = onBackToObsessions) {
            Text("BACK TO DISCOVER", color = AuraSlate, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ImmersiveMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    repository: MediaRepository
) {
    val isVideo = item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("Movie", ignoreCase = true)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AuraSubtleSurface)
            .clickable { onClick() }
            .border(1.dp, AuraSubtleBorder, RoundedCornerShape(24.dp))
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(if (isLandscape) 2.5f else 1.2f)) {
            val imageModel = if (item.imageUrl.isNotEmpty()) item.imageUrl else item.uriPath
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (isVideo) {
                VideoTilePreview(
                    itemId = item.id,
                    videoUri = item.uriPath,
                    imageUrl = item.imageUrl,
                    locationTag = "immersive_card",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    onClick = onFavoriteToggle,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.3f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) Color.Red else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AuraMidnight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (item.aiSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.aiSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = AuraSlate,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DiscoveryFeed(
    obsessions: List<ObsessionRecommendation>,
    systemState: SystemDiscoveryState,
    onObsessionSelect: (ObsessionRecommendation) -> Unit,
    repository: MediaRepository
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = repository.discoverScrollIndex,
        initialFirstVisibleItemScrollOffset = repository.discoverScrollOffset
    )

    val tasteDNA by repository.tasteDNA.collectAsStateWithLifecycle()
    val preferenceProfile by repository.preferenceProfile.collectAsStateWithLifecycle()
    val stats by repository.intelligenceStats.collectAsStateWithLifecycle()
    val discoveryPolicy by repository.discoveryPolicy.collectAsStateWithLifecycle()

    val dashboardViewModel: com.example.ui.screens.IntelligenceDashboardViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return com.example.ui.screens.IntelligenceDashboardViewModel(repository.intelligenceRepository!!) as T
            }
        }
    )
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val report = dashboardState.report

    // Save scroll position for restoration
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                repository.discoverScrollIndex = index
                repository.discoverScrollOffset = offset
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        items(
            items = obsessions,
            key = { it.id }
        ) { obsession ->
            ObsessionCard(
                obsession = obsession,
                onClick = { onObsessionSelect(obsession) },
                repository = repository
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Intelligence Tuning",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AuraMidnight,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column {
                    DiscoveryPolicyControl(
                        policy = discoveryPolicy,
                        onPolicyChange = { repository.updateDiscoveryPolicy(it) }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    AuraEngagementTunerCard(
                        tasteDNA = tasteDNA,
                        preferenceProfile = preferenceProfile,
                        onTasteDnaUpdate = { updatedDna -> 
                            repository.updateTasteDNA(updatedDna, isUserGenerated = true, evidenceCategory = "Discover Manual Tuning") 
                        },
                        onPreferenceProfileUpdate = { updatedProfile -> 
                            repository.updatePreferenceProfile(updatedProfile) 
                        },
                        aiDescription = report?.tasteProfile?.description,
                        showWeightsAtTop = true,
                        collapsibleSliders = true,
                        initialSlidersExpanded = false,
                        isDiscoverContext = true
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Phase 7: Record exposures for the visible preview items
    LaunchedEffect(obsessions) {
        val allPreviewIds = obsessions.flatMap { it.previewItems.map { item -> item.id } }
        repository.recordExposures(allPreviewIds)
    }

    DisposableEffect(Unit) {
        onDispose {
            repository.forceFlushExposures()
        }
    }
}

@Composable
fun ObsessionCard(
    obsession: ObsessionRecommendation,
    onClick: () -> Unit,
    repository: MediaRepository
) {
    val mainItem = obsession.previewItems.firstOrNull() ?: return
    val isVideo = mainItem.mediaType.equals("VIDEO", ignoreCase = true) || mainItem.mediaType.equals("Movie", ignoreCase = true)

    LaunchedEffect(obsession.id) {
        com.example.data.AuraTelemetryService.logEvent(
            repository, 
            com.example.data.AuraTelemetryService.EventType.OBSESSION_EXPOSURE, 
            metadata = mapOf("obsessionId" to obsession.id)
        )
    }

    // Entry Animation
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700),
        label = "alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.98f,
        animationSpec = tween(700),
        label = "scale"
    )
    LaunchedEffect(Unit) { visible = true }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardAspectRatio = if (isLandscape) 2.2f else 1.1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer { 
                this.alpha = alpha 
                this.scaleX = scale
                this.scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(AuraCrispWhite)
            .clickable(
                onClickLabel = "Open ${obsession.title}",
                onClick = onClick
            )
            .border(1.dp, AuraSubtleBorder, RoundedCornerShape(24.dp))
    ) {
        // Large Media Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cardAspectRatio)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            val imageModel = if (mainItem.imageUrl.isNotEmpty()) mainItem.imageUrl else mainItem.uriPath
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            if (isVideo) {
                VideoTilePreview(
                    itemId = mainItem.id,
                    videoUri = mainItem.uriPath,
                    imageUrl = mainItem.imageUrl,
                    locationTag = "discover_feed",
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                        )
                    )
            )
        }

        // Details
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = obsession.title.uppercase(),
                color = DiscoveryViolet,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = obsession.subtitle,
                color = AuraMidnight,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary Previews
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    obsession.previewItems.drop(1).take(3).forEach { item ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AuraSubtleBorder)
                        ) {
                            AsyncImage(
                                model = if (item.imageUrl.isNotEmpty()) item.imageUrl else item.uriPath,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                
                AuraButton(
                    text = "EXPLORE",
                    onClick = onClick,
                    modifier = Modifier.height(40.dp).padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyDiscoverView(onScanAndImport: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = AuraMutedSlate.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your Intelligence Awaits",
            style = MaterialTheme.typography.headlineSmall,
            color = AuraMidnight,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Aura needs more local content to generate obsessions. Scan your device or import media to start discovering.",
            style = MaterialTheme.typography.bodyMedium,
            color = AuraSlate,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        AuraButton(
            text = "SCAN FOR MEDIA",
            onClick = { onScanAndImport?.invoke() }
        )
    }
}

@Composable
fun RecommendationExplanationView(
    explanation: RecommendationExplanation,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AuraSubtleSurface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Why this match?",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = DiscoveryViolet
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = explanation.primaryReason,
            fontSize = 13.sp,
            color = AuraMidnight,
            lineHeight = 18.sp
        )
        
        if (explanation.detailPoints.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            explanation.detailPoints.forEach { point ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(DiscoveryViolet))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = point, fontSize = 11.sp, color = AuraSlate)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
        
        explanation.confidenceLabel?.let { label ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = DiscoveryViolet.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp
            )
        }
    }
}
