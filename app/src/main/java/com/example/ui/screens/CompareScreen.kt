package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.example.util.MediaThumbnailFetcher
import com.example.ui.components.VideoTilePreview
import coil.compose.AsyncImage
import com.example.data.MediaItem
import com.example.data.PairwiseComparison
import androidx.compose.material.icons.filled.CheckCircle
import com.example.ui.components.AuraButton
import com.example.ui.components.AuraOutlinedButton
import com.example.ui.components.AuraTopBar
import com.example.ui.components.AuraSortSelector
import com.example.ui.components.AuraSectionHeader
import com.example.ui.theme.AuraBackground
import com.example.ui.theme.AuraBorder
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMagenta
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraOnSurfaceVariant
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraPurpleContainer
import com.example.ui.theme.AuraSlate
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.AuraSubtleSurface
import com.example.ui.theme.AuraSurface
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryMagenta
import com.example.ui.theme.DiscoveryViolet
import com.example.data.CompareResultsHelper
import kotlinx.coroutines.delay

import androidx.compose.runtime.key
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import coil.request.ImageRequest
import com.example.compatibility.AuraPlaybackRouter
import com.example.compatibility.PlaybackRouteResult
import com.example.data.CompareMediaTypeFilter
import com.example.data.CompareSelectionSession
import com.example.data.CompareSortOption
import com.example.data.CompareStrategy

private enum class CompareFilterGroup {
    MEDIA_TYPE, LEARNING, STANDARD
}

@Composable
fun CompareScreen(
    pairwiseState: PairwiseComparison,
    session: CompareSelectionSession = CompareSelectionSession(),
    mediaItemsMap: Map<String, MediaItem> = emptyMap(),
    compareMediaType: CompareMediaTypeFilter = CompareMediaTypeFilter.PHOTOS,
    compareStrategy: CompareStrategy = CompareStrategy.PERSONALIZED,
    compareSort: CompareSortOption = CompareSortOption.NEWEST,
    onMediaTypeSelect: (CompareMediaTypeFilter) -> Unit = {},
    onStrategySelect: (CompareStrategy) -> Unit = {},
    onSortSelect: (CompareSortOption) -> Unit = {},
    onVote: (String) -> Unit,
    onSkip: () -> Unit,
    onDeleteMedia: (String) -> Unit,
    onExitSession: () -> Unit = {},
    onRestartSession: () -> Unit = {},
    onMediaSelect: ((MediaItem) -> Unit)? = null,
    onFavoriteToggle: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var itemToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showResultsOverlay by remember { mutableStateOf(false) }
    var activeFilterGroup by remember { mutableStateOf(CompareFilterGroup.LEARNING) } // Default to Learning as it's the core intelligence

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("About Pairwise Comparisons", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Pairwise comparisons are the foundation of your personalized media intelligence.",
                        fontSize = 14.sp
                    )
                    Text(
                        "How it works:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "• You are presented with two items from your library.\n" +
                        "• Choose the one you prefer, or skip if they are equal.\n" +
                        "• Each choice updates the 'Elo rating' of the items—a mathematical representation of your relative preference.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Text(
                        "This data trains Aura's recommendation engine to understand your unique visual and emotional taste, allowing it to discover more content you'll love.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it", color = DiscoveryViolet, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = AuraCrispWhite,
            titleContentColor = AuraMidnight,
            textContentColor = AuraSlate,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Media?") },
            text = { Text("This will permanently remove this media from your device. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    itemToDelete?.let { onDeleteMedia(it.id) }
                    itemToDelete = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = AuraSurface,
            titleContentColor = AuraOnSurface,
            textContentColor = AuraOnSurfaceVariant
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = this.maxWidth > this.maxHeight
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AuraCrispWhite)
        ) {
            if (!isLandscape) {
                AuraSectionHeader(
                    title = if (session.isActive) "Compare Selection" else "Compare",
                    subtitle = if (session.isActive) "Session Active • Round ${session.roundNumber}" else "Teach Aura what you like",
                    actions = {
                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(imageVector = Icons.Outlined.Info, contentDescription = "Info", tint = AuraMidnight)
                        }
                    }
                )
            } else {
                // Compact Landscape Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (session.isActive) "Compare Selection — Round ${session.roundNumber}" else "Compare",
                        style = MaterialTheme.typography.titleSmall,
                        color = DiscoveryViolet,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Outlined.Info, contentDescription = "Info", tint = AuraMidnight, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (session.isActive && !isLandscape) {
                CompareSessionBanner(
                    session = session,
                    onExit = onExitSession
                )
            }

            // UNIFIED COMPARE CONTROLS (Standard/Global)
            CompareControlRow(
                mediaType = compareMediaType,
                strategy = compareStrategy,
                sort = compareSort,
                activeGroup = activeFilterGroup,
                onGroupClick = { activeFilterGroup = it },
                onMediaTypeSelect = onMediaTypeSelect,
                onStrategySelect = onStrategySelect,
                onSortSelect = onSortSelect
            )

            if (session.isComplete) {
                val topItems = remember(session.selectedIds, mediaItemsMap) {
                    CompareResultsHelper.getRankedResults(session.selectedIds, mediaItemsMap)
                }
                CompareSelectionCompleteView(
                    session = session,
                    topItems = topItems,
                    onRestart = onRestartSession,
                    onViewResults = { showResultsOverlay = true },
                    onExit = onExitSession
                )
            } else if (pairwiseState.optionA.id.isEmpty() || pairwiseState.optionB.id.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                            contentDescription = null,
                            tint = AuraMutedSlate,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        val effectiveFilter = compareMediaType.name
                        val emptyTitle = when (effectiveFilter.uppercase()) {
                            "PHOTO", "PHOTOS" -> "Need At Least 2 Photos"
                            "VIDEO", "VIDEOS" -> "Need At Least 2 Videos"
                            else -> "Need At Least 2 Media Items"
                        }
                        val emptyMessage = when (effectiveFilter.uppercase()) {
                            "PHOTO", "PHOTOS" -> if (session.isActive) "Your selection doesn't contain at least 2 photos. Change the filter or selection to continue." else "Import or scan at least 2 photos into your Library to compare them."
                            "VIDEO", "VIDEOS" -> if (session.isActive) "Your selection doesn't contain at least 2 videos. Change the filter or selection to continue." else "Import or scan at least 2 videos into your Library to compare them."
                            else -> if (session.isActive) "Your selection doesn't contain enough media. Change the filter or selection to continue." else "Import or scan at least 2 photos or videos into your Library to compare them."
                        }
                        Text(
                            text = emptyTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuraMidnight
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = emptyMessage,
                            fontSize = 13.sp,
                            color = AuraSlate,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val traitContrast = remember(pairwiseState.optionA.id, pairwiseState.optionB.id) {
                    TraitContrastHelper.deriveContrast(pairwiseState.optionA, pairwiseState.optionB)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    if (traitContrast != null) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AuraSubtleSurface)
                                .border(1.dp, AuraSubtleBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                                .testTag("trait_contrast_label")
                        ) {
                            Text(
                                text = "Trait Contrast: ${traitContrast.contrastLabel}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = AuraMutedSlate
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        key(pairwiseState.optionA.id) {
                            Column(modifier = Modifier.weight(1f)) {
                                IconButton(
                                    onClick = { itemToDelete = pairwiseState.optionA },
                                    modifier = Modifier.align(Alignment.End).size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete, 
                                        contentDescription = "Delete", 
                                        tint = AuraMutedSlate.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                CompareMediaTile(
                                    item = pairwiseState.optionA,
                                    label = "Option A",
                                    locationTag = "compare_opt_a",
                                    traitBadge = traitContrast?.badgeA,
                                    onSelect = { onMediaSelect?.invoke(pairwiseState.optionA) },
                                    onFavoriteToggle = { onFavoriteToggle?.invoke(pairwiseState.optionA.id) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "choose_option_a"
                                )
                            }
                        }

                        key(pairwiseState.optionB.id) {
                            Column(modifier = Modifier.weight(1f)) {
                                IconButton(
                                    onClick = { itemToDelete = pairwiseState.optionB },
                                    modifier = Modifier.align(Alignment.End).size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete, 
                                        contentDescription = "Delete", 
                                        tint = AuraMutedSlate.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                CompareMediaTile(
                                    item = pairwiseState.optionB,
                                    label = "Option B",
                                    locationTag = "compare_opt_b",
                                    traitBadge = traitContrast?.badgeB,
                                    onSelect = { onMediaSelect?.invoke(pairwiseState.optionB) },
                                    onFavoriteToggle = { onFavoriteToggle?.invoke(pairwiseState.optionB.id) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "choose_option_b"
                                )
                            }
                        }
                    }

                    // Bottom Explicit Voting Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { onVote(pairwiseState.optionA.id) }
                                .testTag("prefer_left_button"),
                            color = Color.Transparent,
                            contentColor = Color.White
                        ) {
                            Box(
                                modifier = Modifier.background(DiscoveryGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Prefer Left",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(0.8f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(1.dp, AuraSubtleBorder, RoundedCornerShape(22.dp))
                                .clickable { onSkip() }
                                .testTag("skip_button"),
                            color = AuraCrispWhite,
                            contentColor = AuraMidnight
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Equal / Skip",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { onVote(pairwiseState.optionB.id) }
                                .testTag("prefer_right_button"),
                            color = Color.Transparent,
                            contentColor = Color.White
                        ) {
                            Box(
                                modifier = Modifier.background(DiscoveryGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Prefer Right",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Results Overlay (Phase 5)
    AnimatedVisibility(
        visible = showResultsOverlay,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f)
    ) {
        CompareSelectionResultsOverlay(
            session = session,
            mediaItemsMap = mediaItemsMap,
            onDismiss = { showResultsOverlay = false }
        )
    }
}

@Composable
private fun CompareMediaTile(
    item: MediaItem,
    label: String,
    traitBadge: String?,
    onSelect: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    locationTag: String = "compare_opt",
    testTag: String = ""
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isVideo = remember(item.id, item.mediaType) {
        item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("Movie", ignoreCase = true)
    }

    val playableUri = remember(item.id) {
        if (isVideo) {
            when (val route = AuraPlaybackRouter.resolveRoute(item)) {
                is PlaybackRouteResult.Playable -> route.playUri
                else -> null
            }
        } else {
            null
        }
    }

    val imageModel = remember(item.id, item.imageUrl, item.uriPath) {
        item.imageUrl.ifEmpty { item.uriPath }
    }

    var thumbnail by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(item.id) {
        val targetUri = item.uriPath.ifEmpty { item.imageUrl }
        if (targetUri.isNotEmpty()) {
            thumbnail = MediaThumbnailFetcher.getThumbnail(context, targetUri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(AuraMidnight)
            .border(1.dp, AuraSubtleBorder, RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .testTag(testTag)
    ) {
        // LAYER 0: Ambient Blurred Background to fill non-matching aspect ratios
        Box(modifier = Modifier.fillMaxSize()) {
            val backgroundModifier = Modifier
                .fillMaxSize()
                .blur(24.dp)
                .graphicsLayer(alpha = 0.55f)

            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = backgroundModifier,
                    contentScale = ContentScale.Crop
                )
            } else if (imageModel.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(false)
                        .size(100)
                        .build(),
                    contentDescription = null,
                    modifier = backgroundModifier,
                    contentScale = ContentScale.Crop
                )
            }

            // Darkening scrim over blurred background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }

        // LAYER 1: Aspect-Fit Foreground (Photo or Video)
        if (isVideo && !playableUri.isNullOrEmpty()) {
            // First-frame / Static fallback layer underneath PlayerView
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else if (imageModel.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(false)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // Live video preview
            VideoTilePreview(
                itemId = item.id,
                videoUri = playableUri,
                imageUrl = item.imageUrl,
                locationTag = locationTag,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .clipToBounds()
            )
        } else {
            // PHOTO (or unplayable video fallback)
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else if (imageModel.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(false)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Top-right Favorite
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.4f),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onFavoriteToggle()
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = if (item.isFavorite) Color.Red else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Bottom label and badge
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(12.dp)
        ) {
            if (traitBadge != null) {
                Surface(
                    color = DiscoveryViolet,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = traitBadge,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Text(
                text = item.title,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompareSessionBanner(
    session: CompareSelectionSession,
    onExit: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = DiscoveryViolet.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DiscoveryViolet.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SESSION ACTIVE: ${session.originalCount} ITEMS",
                    style = MaterialTheme.typography.labelSmall,
                    color = DiscoveryViolet,
                    fontWeight = FontWeight.Black
                )
                val progress = session.roundNumber.toFloat() / session.maxRounds.toFloat()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(4.dp)
                        .clip(CircleShape),
                    color = DiscoveryViolet,
                    trackColor = DiscoveryViolet.copy(alpha = 0.1f)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(
                onClick = onExit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit Session", tint = DiscoveryViolet)
            }
        }
    }
}

@Composable
private fun CompareControlRow(
    mediaType: CompareMediaTypeFilter,
    strategy: CompareStrategy,
    sort: CompareSortOption,
    activeGroup: CompareFilterGroup,
    onGroupClick: (CompareFilterGroup) -> Unit,
    onMediaTypeSelect: (CompareMediaTypeFilter) -> Unit,
    onStrategySelect: (CompareStrategy) -> Unit,
    onSortSelect: (CompareSortOption) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            AuraSortSelector(
                label = "Media Type",
                currentOption = when (mediaType) {
                    CompareMediaTypeFilter.PHOTOS -> "Photos"
                    CompareMediaTypeFilter.VIDEOS -> "Videos"
                },
                isSelected = activeGroup == CompareFilterGroup.MEDIA_TYPE,
                options = CompareMediaTypeFilter.entries,
                onOptionSelected = {
                    onMediaTypeSelect(it)
                    onGroupClick(CompareFilterGroup.MEDIA_TYPE)
                },
                onPillClick = { onGroupClick(CompareFilterGroup.MEDIA_TYPE) },
                getDisplayName = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } },
                selectedColor = DiscoveryViolet
            )
        }

        item {
            AuraSortSelector(
                label = "Intelligence",
                currentOption = when (strategy) {
                    CompareStrategy.PERSONALIZED -> "Personalized"
                    CompareStrategy.REDISCOVER -> "Rediscover"
                    CompareStrategy.LEAST_INTERACTED -> "Least Compared"
                    CompareStrategy.EXPLORE -> "Experimental"
                },
                isSelected = activeGroup == CompareFilterGroup.LEARNING,
                options = CompareStrategy.entries,
                onOptionSelected = {
                    onStrategySelect(it)
                    onGroupClick(CompareFilterGroup.LEARNING)
                },
                onPillClick = { onGroupClick(CompareFilterGroup.LEARNING) },
                getDisplayName = { 
                    when (it) {
                        CompareStrategy.PERSONALIZED -> "Personalized"
                        CompareStrategy.REDISCOVER -> "Rediscover"
                        CompareStrategy.LEAST_INTERACTED -> "Least Compared"
                        CompareStrategy.EXPLORE -> "Experimental"
                    }
                },
                selectedColor = DiscoveryMagenta
            )
        }

        item {
            AuraSortSelector(
                label = "Priority",
                currentOption = when (sort) {
                    CompareSortOption.RECOMMENDED -> "Recommended"
                    CompareSortOption.NEWEST -> "Newest First"
                    CompareSortOption.OLDEST -> "Oldest First"
                    CompareSortOption.LARGEST_FILES -> "Largest Files"
                    CompareSortOption.SMALLEST_FILES -> "Smallest Files"
                },
                isSelected = activeGroup == CompareFilterGroup.STANDARD,
                options = CompareSortOption.entries,
                onOptionSelected = {
                    onSortSelect(it)
                    onGroupClick(CompareFilterGroup.STANDARD)
                },
                onPillClick = { onGroupClick(CompareFilterGroup.STANDARD) },
                getDisplayName = { 
                    when (it) {
                        CompareSortOption.RECOMMENDED -> "Recommended"
                        CompareSortOption.NEWEST -> "Newest"
                        CompareSortOption.OLDEST -> "Oldest"
                        CompareSortOption.LARGEST_FILES -> "Largest"
                        CompareSortOption.SMALLEST_FILES -> "Smallest"
                    }
                },
                selectedColor = AuraPurple
            )
        }
    }
}

@Composable
private fun CompareSelectionCompleteView(
    session: CompareSelectionSession,
    topItems: List<MediaItem>,
    onRestart: () -> Unit,
    onViewResults: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = DiscoveryViolet,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Selection Complete",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AuraMidnight
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Aura has analyzed your preferences across ${session.maxRounds} comparison rounds.",
            style = MaterialTheme.typography.bodyMedium,
            color = AuraSlate,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Session Stats Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatItem(label = "Items", value = session.originalCount.toString())
            StatItem(label = "Rounds", value = (session.roundNumber - 1).toString())
            StatItem(label = "Top Pick", value = topItems.firstOrNull()?.title ?: "N/A")
        }

        Spacer(modifier = Modifier.height(48.dp))

        AuraButton(
            text = "VIEW RESULTS",
            onClick = onViewResults,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        AuraOutlinedButton(
            text = "COMPARE AGAIN",
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        androidx.compose.material3.TextButton(onClick = onExit) {
            Text("RETURN TO LIBRARY", color = AuraSlate, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompareSelectionResultsOverlay(
    session: CompareSelectionSession,
    mediaItemsMap: Map<String, MediaItem>,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AuraCrispWhite
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AuraTopBar(
                title = "Comparison Results",
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AuraMidnight)
                    }
                }
            )

            val rankedItems = remember(session.selectedIds, mediaItemsMap) {
                CompareResultsHelper.getRankedResults(session.selectedIds, mediaItemsMap)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(rankedItems) { index, item ->
                    val isFirst = index == 0
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isFirst) DiscoveryViolet.copy(alpha = 0.05f) else AuraSubtleSurface)
                            .border(
                                width = if (isFirst) 2.dp else 1.dp,
                                brush = if (isFirst) DiscoveryGradient else SolidColor(AuraSubtleBorder),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(if (isFirst) 16.dp else 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${index + 1}",
                                fontWeight = FontWeight.Black,
                                color = if (isFirst) DiscoveryViolet else AuraSlate,
                                fontSize = if (isFirst) 20.sp else 16.sp,
                                modifier = Modifier.width(if (isFirst) 44.dp else 36.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(if (isFirst) 80.dp else 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AuraMutedSlate)
                            ) {
                                AsyncImage(
                                    model = if (item.imageUrl.isNotEmpty()) item.imageUrl else item.uriPath,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Bold,
                                    style = if (isFirst) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                    color = AuraMidnight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ELO SCORE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = AuraSlate.copy(alpha = 0.7f),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = item.eloRating.toInt().toString(),
                                    fontSize = if (isFirst) 18.sp else 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DiscoveryViolet
                                )
                            }
                            
                            if (isFirst) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Winner",
                                    tint = DiscoveryViolet,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        Text(text = value, fontWeight = FontWeight.Black, fontSize = 20.sp, color = AuraMidnight)
        Text(text = label.uppercase(), fontSize = 10.sp, color = AuraSlate, fontWeight = FontWeight.Bold)
    }
}

internal object TraitContrastHelper {
    data class TraitPoles(val negativeLabel: String, val positiveLabel: String)

    val dimensionPoles = mapOf(
        "vibrancy" to TraitPoles("Muted", "Vibrant"),
        "contrast" to TraitPoles("Flat", "High Contrast"),
        "sharpness" to TraitPoles("Soft", "Sharp"),
        "symmetry" to TraitPoles("Asymmetric", "Symmetric"),
        "complexity" to TraitPoles("Simple", "Intricate"),
        "naturalism" to TraitPoles("Stylized", "Natural"),
        "novelty" to TraitPoles("Familiar", "Unique"),
        "lighting" to TraitPoles("Dark", "Bright"),
        "colorTemperature" to TraitPoles("Cool", "Warm"),
        "texture" to TraitPoles("Smooth", "Textured"),
        "motion" to TraitPoles("Static", "Kinetic"),
        "dynamicRange" to TraitPoles("Low DR", "High DR"),
        "framing" to TraitPoles("Loose", "Tight"),
        "depth" to TraitPoles("Shallow", "Deep"),
        "warmth" to TraitPoles("Cold", "Warm"),
        "saturation" to TraitPoles("Subtle", "Saturated"),
        "elegance" to TraitPoles("Raw", "Elegant"),
        "minimalism" to TraitPoles("Busy", "Minimal"),
        "grain" to TraitPoles("Clean", "Grainy"),
        "focus" to TraitPoles("Diffuse", "Focused"),
        "density" to TraitPoles("Sparse", "Dense"),
        "rhythm" to TraitPoles("Steady", "Rhythmic"),
        "mood" to TraitPoles("Somber", "Bright"),
        "harmony" to TraitPoles("Chaotic", "Harmonious")
    )

    data class TraitContrast(
        val dimensionName: String,
        val badgeA: String,
        val badgeB: String,
        val contrastLabel: String
    )

    fun deriveContrast(itemA: MediaItem, itemB: MediaItem): TraitContrast? {
        val traitsA = com.example.data.PersonalizationTraitMapper.getTraitAdjustments(itemA.moodTags)
        val traitsB = com.example.data.PersonalizationTraitMapper.getTraitAdjustments(itemB.moodTags)

        var maxDelta = 0.0
        var bestDim: String? = null

        traitsA.forEach { (dim, valA) ->
            val valB = traitsB[dim] ?: 0.0
            val delta = Math.abs(valA - valB)
            if (delta > maxDelta) {
                maxDelta = delta
                bestDim = dim
            }
        }

        if (bestDim != null && maxDelta >= 0.2) {
            val poles = dimensionPoles[bestDim] ?: return null
            val valA = traitsA[bestDim!!] ?: 0.0
            val valB = traitsB[bestDim!!] ?: 0.0
            
            return TraitContrast(
                dimensionName = bestDim!!,
                badgeA = if (valA > valB) poles.positiveLabel else poles.negativeLabel,
                badgeB = if (valB > valA) poles.positiveLabel else poles.negativeLabel,
                contrastLabel = bestDim!!.capitalizeWords()
            )
        }
        return null
    }

    private fun String.capitalizeWords(): String = 
        this.replace(Regex("([a-z])([A-Z])")) { 
            "${it.groupValues[1]} ${it.groupValues[2]}" 
        }.replaceFirstChar { it.uppercase() }
}
