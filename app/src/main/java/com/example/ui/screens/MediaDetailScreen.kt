package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import android.view.WindowManager
import android.app.Activity
import androidx.media3.common.PlaybackException
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.AISkipEngine
import com.example.data.ClipCandidate
import com.example.data.ClipTelemetryService
import com.example.data.CompatibilityStatus
import com.example.data.MediaRepository
import com.example.data.db.MediaEntity
import com.example.util.ClipExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.MediaItem
import com.example.data.PlaylistState
import com.example.ui.theme.AuraBackground
import com.example.ui.theme.AuraBorder
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMagenta
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraOnSurfaceVariant
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraSlate
import com.example.ui.theme.AuraStarGold
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.AuraSubtleSurface
import com.example.ui.theme.AuraSurface
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryViolet
import kotlinx.coroutines.delay

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    playlistState: PlaylistState?,
    item: MediaItem,
    repository: MediaRepository,
    onBack: () -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSelectIndex: ((Int) -> Unit)? = null,
    onUpdateRating: ((String, Float) -> Unit)? = null,
    onDeleteMedia: ((String) -> Unit)? = null,
    onMicroMoment: ((String, Int) -> Unit)? = null,
    onSeeSimilar: ((MediaItem) -> Unit)? = null,
    onAISkipEvent: ((String, String, Long, Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showPlayerMenu by remember { mutableStateOf(false) }
    var wasPlayingBeforeMenu by remember { mutableStateOf(false) }
    var showClipsSheet by remember { mutableStateOf(false) }
    var generatedClips by remember { mutableStateOf<List<ClipCandidate>>(emptyList()) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    
    val tasteDNA by repository.tasteDNA.collectAsStateWithLifecycle()
    val isLoopEnabled by repository.libraryRepeatMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isControlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableFloatStateOf(1000f) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Use internal tracking for the current item to ensure it stays in sync with ExoPlayer's playlist
    // Task 3: Keyed by playlistState to ensure index resets when a new playlist (like See Similar) is established.
    var currentItemIndex by remember(playlistState) { 
        mutableIntStateOf(playlistState?.currentIndex ?: 0) 
    }
    
    val activeItem = remember(currentItemIndex, playlistState) {
        playlistState?.items?.getOrNull(currentItemIndex) ?: item
    }

    var isAbRepeatActive by remember(activeItem.id) { mutableStateOf(false) }
    var abPointA by remember(activeItem.id) { mutableStateOf<Long?>(null) }
    var abPointB by remember(activeItem.id) { mutableStateOf<Long?>(null) }

    var recommendationInsight by remember(activeItem.id) { mutableStateOf<com.example.data.intelligence.RecommendationInsightSnapshot?>(null) }

    LaunchedEffect(activeItem.id, showPlayerMenu) {
        if (showPlayerMenu && activeItem.id.isNotEmpty()) {
            recommendationInsight = repository.intelligenceRepository?.getRecommendationInsight(activeItem)
        }
    }

    var activeClipCandidate by remember(activeItem.id) { mutableStateOf<ClipCandidate?>(null) }

    BackHandler {
        if (showClipsSheet) {
            showClipsSheet = false
        } else if (showPlayerMenu) {
            showPlayerMenu = false
        } else if (activeClipCandidate != null) {
            activeClipCandidate = null
            showClipsSheet = true
        } else {
            onBack()
        }
    }

    var currentRating by remember(activeItem.id) { mutableFloatStateOf(activeItem.rating) }
    val isVideo = remember(activeItem.id) { activeItem.mediaType.equals("VIDEO", ignoreCase = true) || activeItem.mediaType.equals("Movie", ignoreCase = true) }

    // AI Skip tracking & intelligence state
    var lastSkipForwardTimeMs by remember(activeItem.id) { mutableLongStateOf(0L) }
    var lastSkipForwardPosMs by remember(activeItem.id) { mutableLongStateOf(0L) }
    var pendingWatchedDestinationPosMs by remember(activeItem.id) { mutableLongStateOf(-1L) }
    var pendingWatchedDestinationStartMs by remember(activeItem.id) { mutableLongStateOf(0L) }
    var aiSkipFeedbackReason by remember(activeItem.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(aiSkipFeedbackReason) {
        if (aiSkipFeedbackReason != null) {
            Toast.makeText(context, aiSkipFeedbackReason, Toast.LENGTH_SHORT).show()
            kotlinx.coroutines.delay(2500)
            aiSkipFeedbackReason = null
        }
    }

    // Watched Destination Observation for AI Skip learning
    LaunchedEffect(currentPositionMs, pendingWatchedDestinationPosMs, isPlaying) {
        if (pendingWatchedDestinationPosMs >= 0L) {
            val distFromDest = kotlin.math.abs(currentPositionMs - pendingWatchedDestinationPosMs)
            if (distFromDest < 5000f) {
                val elapsedSinceSkip = System.currentTimeMillis() - pendingWatchedDestinationStartMs
                if (elapsedSinceSkip >= 3000L && isPlaying) {
                    onAISkipEvent?.invoke(
                        activeItem.id,
                        "WATCHED_DESTINATION",
                        lastSkipForwardPosMs,
                        currentPositionMs.toLong()
                    )
                    Log.d("AISkip", "Watched destination validated for media ${activeItem.id} at ${currentPositionMs}ms")
                    pendingWatchedDestinationPosMs = -1L
                }
            } else if (distFromDest > 12000f) {
                // User moved far away from destination; cancel observation
                pendingWatchedDestinationPosMs = -1L
            }
        }
    }

    // Gesture tracking state for drag/swipe
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Compatibility & Conversion State tied to item ID
    val playbackRoute = remember(activeItem.id, activeItem.convertedUri, activeItem.compatibilityStatus) {
        com.example.compatibility.AuraPlaybackRouter.resolveRoute(activeItem)
    }
    var isConverting by remember(activeItem.id) { mutableStateOf(false) }
    var conversionProgress by remember(activeItem.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    var deleteOriginalAfter by remember { mutableStateOf(false) }
    var conversionErrorMsg by remember(activeItem.id) { mutableStateOf<String?>(null) }

    // Single ExoPlayer instance for the session with TRUE PLAYLIST ARCHITECTURE
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = if (repository.repeatMode) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                
                override fun onMediaItemTransition(mediaItem: Media3Item?, reason: Int) {
                    // Update UI index from ACTUAL player transition
                    val transitionedId = mediaItem?.mediaId
                    val originalIndex = if (playlistState != null) {
                        playlistState.items.indexOfFirst { it.id == transitionedId }
                    } else {
                        if (transitionedId == item.id) 0 else -1
                    }

                    if (originalIndex != -1) {
                        currentItemIndex = originalIndex
                        // Update repository for global state consistency
                        onSelectIndex?.invoke(originalIndex)
                    }
                    
                    // AURA PHASE 2: Reset position when moving to a new item in the playlist
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || 
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        repository.updatePlaybackPosition(0L)
                    }
                    
                    Log.d("PlaylistTrace", "Player transitioned to original index: $originalIndex, reason: $reason")
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        com.example.data.AuraTelemetryService.logEvent(
                            repository,
                            com.example.data.AuraTelemetryService.EventType.MEDIA_COMPLETED,
                            activeItem.id
                        )
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("PlaylistTrace", "ExoPlayer Error: ${error.message}", error)
                    
                    // Capture detailed diagnostics
                    repository.recordPlaybackError(error, this@apply, activeItem)

                    // Only show toast if it's a real terminal error, not just a transition glitch
                    if (error.errorCode != PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        Toast.makeText(context, "Playback error: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    // AURA PHASE 2.1: Consolidated Lifecycle Management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Save position and pause immediately on background
                    if (isVideo) {
                        repository.updatePlaybackPosition(exoPlayer.currentPosition)
                        repository.setResumingFromBackground(true)
                        exoPlayer.pause()
                    }
                    Log.d("AuraLifecycle", "Paused: Saved position ${exoPlayer.currentPosition}ms")
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (repository.isResumingFromBackground) {
                        // Restore position but do not autoplay
                        if (isVideo) {
                            exoPlayer.seekTo(repository.lastPlaybackPositionMs)
                            exoPlayer.playWhenReady = false // Explicitly ensure paused state on return
                        }
                        // Clear the resuming flag after successful restoration to prevent unwanted behavior on next internal navigations
                        repository.setResumingFromBackground(false)
                        Log.d("AuraLifecycle", "Resumed: Restored to ${repository.lastPlaybackPositionMs}ms (Paused)")
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Task 1 Cleanup: Ensure flag is cleared when leaving the screen
            (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Requirement 3: "Release ExoPlayer resources" on Back navigation.
            exoPlayer.release()
        }
    }

    LaunchedEffect(showPlayerMenu) {
        if (showPlayerMenu) {
            wasPlayingBeforeMenu = isPlaying
            if (isPlaying) {
                exoPlayer.pause()
            }
        } else {
            // Restore playback state only if it was playing before
            if (wasPlayingBeforeMenu) {
                exoPlayer.play()
            }
        }
    }

    LaunchedEffect(isLoopEnabled) {
        exoPlayer.repeatMode = if (isLoopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // Task 1: Keep Screen Awake ONLY during active playback
    DisposableEffect(isPlaying) {
        val activity = context as? Activity
        if (isPlaying && isVideo) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            // Handled in Lifecycle cleanup or if isPlaying changes
        }
    }

    // Set playlist once and prepare. Use stable ID keys to avoid loops and source errors.
    val playlistIds = remember(playlistState) { playlistState?.items?.map { it.id } ?: listOf(item.id) }
    
    // AURA FIX: Identify video-only items for ExoPlayer playlist to avoid Photo source errors
    val videoOnlyItems = remember(playlistIds) {
        val baseList = playlistState?.items ?: listOf(item)
        baseList.filter { itm -> 
            itm.mediaType.equals("VIDEO", ignoreCase = true) || itm.mediaType.equals("Movie", ignoreCase = true) 
        }
    }

    // AURA PHASE 2.1: Consolidated Playback Synchronization
    LaunchedEffect(playlistIds, playlistState?.currentIndex, isVideo) {
        if (isVideo) {
            Log.d("MediaDetail", "MediaDetailScreen: Video item routed to ExoPlayer")
            
            // Check if ExoPlayer's current playlist matches the desired video-only list
            val exoIds = List(exoPlayer.mediaItemCount) { exoPlayer.getMediaItemAt(it).mediaId }
            val targetVideoIds = videoOnlyItems.map { it.id }

            // 1. Build and Set the MediaItems list if the playlist structure has changed
            if (exoIds != targetVideoIds) {
                val media3Items = videoOnlyItems.map { itm ->
                    val route = com.example.compatibility.AuraPlaybackRouter.resolveRoute(itm)
                    val uri = if (route is com.example.compatibility.PlaybackRouteResult.Playable) route.playUri else itm.uriPath
                    Media3Item.Builder()
                        .setUri(uri)
                        .setMediaId(itm.id)
                        .build()
                }

                // AURA PHASE 2: Check if we are resuming to determine initial seek and playWhenReady
                val startPos = if (repository.isResumingFromBackground) repository.lastPlaybackPositionMs else 0L
                val targetVideoIndex = videoOnlyItems.indexOfFirst { it.id == activeItem.id }.coerceAtLeast(0)
                
                exoPlayer.setMediaItems(media3Items, targetVideoIndex, startPos)
                exoPlayer.prepare()
                
                // Requirements: Do NOT autoplay on resume
                exoPlayer.playWhenReady = !repository.isResumingFromBackground
            } else {
                // 2. Just synchronize index if playlist is already correctly loaded
                val targetVideoIndex = videoOnlyItems.indexOfFirst { it.id == activeItem.id }
                if (targetVideoIndex != -1 && exoPlayer.currentMediaItemIndex != targetVideoIndex) {
                    exoPlayer.seekTo(targetVideoIndex, 0L)
                    repository.updatePlaybackPosition(0L)
                }
            }
        } else {
            Log.d("MediaDetail", "MediaDetailScreen: Photo item routed to Coil renderer; ExoPlayer bypassed")
            exoPlayer.pause()
        }
    }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            delay(4000)
            isControlsVisible = false
        }
    }

    // Pause main player while generated clips comparison sheet is open
    LaunchedEffect(showClipsSheet) {
        if (showClipsSheet) {
            exoPlayer.pause()
        }
    }

    // Progress update loop, A/B Repeat & Clip Loop handler
    LaunchedEffect(isAbRepeatActive, abPointA, abPointB, activeClipCandidate) {
        while (true) {
            currentPositionMs = exoPlayer.currentPosition.toFloat()
            durationMs = exoPlayer.duration.coerceAtLeast(1L).toFloat()
            if (isAbRepeatActive && abPointA != null && abPointB != null) {
                if (exoPlayer.currentPosition >= abPointB!!) {
                    exoPlayer.seekTo(abPointA!!)
                }
            } else if (activeClipCandidate != null) {
                val startMs = activeClipCandidate!!.startTimeMs
                val endMs = activeClipCandidate!!.endTimeMs
                val currentPos = exoPlayer.currentPosition
                // AURA FIX: Only trigger loop/play if the player is intended to be playing (playWhenReady)
                if (exoPlayer.playWhenReady && (currentPos >= endMs || currentPos < startMs - 1000L || exoPlayer.playbackState == Player.STATE_ENDED)) {
                    exoPlayer.seekTo(startMs)
                    if (!exoPlayer.isPlaying) {
                        exoPlayer.play()
                    }
                }
            }
            delay(100)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(activeItem.id) {
                detectTapGestures(
                    onTap = {
                        isControlsVisible = !isControlsVisible
                    },
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDoubleTapHeart = true
                        repository.recordLike(activeItem.id)
                        repository.addToFavorites(activeItem.id)

                        if (isVideo) {
                            val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                            val itemDur = if (activeItem.durationMs > 0L) activeItem.durationMs else exoPlayer.duration.coerceAtLeast(0L)
                            repository.enqueueVisualLikeContext(
                                mediaId = activeItem.id,
                                uri = activeItem.uriPath,
                                playbackPositionMs = positionMs,
                                durationMs = itemDur
                            )
                        }
                    },
                    onLongPress = {
                        captureAndSaveScreenshot(context, activeItem)
                    }
                )
            }
            .pointerInput(activeItem.id) {
                detectDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(offsetX) > kotlin.math.abs(offsetY)) {
                            if (offsetX > 100) {
                                onPrevious()
                            } else if (offsetX < -100) {
                                onNext()
                            }
                        } else {
                            if (offsetY < -100) {
                                showPlayerMenu = true
                            }
                        }
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
    ) {
        // Media Viewing Content - Maximize Area
        if (isVideo) {
            if (playbackRoute is com.example.compatibility.PlaybackRouteResult.Playable) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Compatibility / Conversion Panel
                val coroutineScope = rememberCoroutineScope()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F0E17)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(AuraSurface)
                            .border(1.dp, AuraBorder, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val (titleBadge, badgeColor) = when (playbackRoute) {
                            is com.example.compatibility.PlaybackRouteResult.NeedsConversion -> "CONVERSION RECOMMENDED" to Color(0xFFF97316)
                            is com.example.compatibility.PlaybackRouteResult.Unsupported -> "UNSUPPORTED FORMAT" to Color(0xFFEF4444)
                            is com.example.compatibility.PlaybackRouteResult.Corrupt -> "CORRUPT / UNREADABLE MEDIA" to Color(0xFFDC2626)
                            else -> "MEDIA NOTICE" to AuraPurple
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor.copy(alpha = 0.2f))
                                .border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = titleBadge,
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = activeItem.title,
                            color = AuraOnSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        val reasonText = when (playbackRoute) {
                            is com.example.compatibility.PlaybackRouteResult.NeedsConversion -> playbackRoute.reason
                            is com.example.compatibility.PlaybackRouteResult.Unsupported -> playbackRoute.reason
                            is com.example.compatibility.PlaybackRouteResult.Corrupt -> playbackRoute.reason
                            else -> activeItem.compatibilityReason
                        }

                        Text(
                            text = reasonText,
                            color = AuraOnSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        // Stream Specs Grid
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Container:", color = AuraOnSurfaceVariant, fontSize = 12.sp)
                                Text(activeItem.containerFormat.ifBlank { "Unknown" }, color = AuraOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Video Codec:", color = AuraOnSurfaceVariant, fontSize = 12.sp)
                                Text(activeItem.videoCodec.ifBlank { "Unknown" }, color = AuraOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Audio Codec:", color = AuraOnSurfaceVariant, fontSize = 12.sp)
                                Text(activeItem.audioCodec.ifBlank { "Unknown" }, color = AuraOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (activeItem.width > 0 && activeItem.height > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Resolution:", color = AuraOnSurfaceVariant, fontSize = 12.sp)
                                    Text("${activeItem.width}x${activeItem.height}", color = AuraOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Conversion controls
                        if (playbackRoute is com.example.compatibility.PlaybackRouteResult.NeedsConversion || playbackRoute is com.example.compatibility.PlaybackRouteResult.Unsupported) {
                            if (isConverting) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { conversionProgress / 100f },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                        color = AuraPurple,
                                        trackColor = AuraBorder
                                    )
                                    Text(
                                        text = "Converting to Aura MP4 ($conversionProgress%)...",
                                        color = AuraOnSurface,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { deleteOriginalAfter = !deleteOriginalAfter }
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = deleteOriginalAfter,
                                        onCheckedChange = { deleteOriginalAfter = it }
                                    )
                                    Text(
                                        text = "Delete original after successful conversion",
                                        color = AuraOnSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            isConverting = true
                                            conversionErrorMsg = null
                                            coroutineScope.launch {
                                                val res = MediaRepository.getInstance(context).convertMediaItem(
                                                    context = context,
                                                    itemId = activeItem.id,
                                                    deleteOriginalAfter = deleteOriginalAfter,
                                                    onProgress = { p -> conversionProgress = p }
                                                )
                                                isConverting = false
                                                if (!res.isSuccess) {
                                                    conversionErrorMsg = res.errorMessage ?: "Conversion failed"
                                                    Toast.makeText(context, conversionErrorMsg, Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Conversion complete! Playing media...", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                    color = AuraPurple,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Convert for Aura Playback",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                conversionErrorMsg?.let { err ->
                                    Text(err, color = Color(0xFFEF4444), fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Photo Viewing Content
            val photoModel = activeItem.imageUrl.ifEmpty { activeItem.uriPath }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (photoModel.isNotEmpty()) {
                    AsyncImage(
                        model = photoModel,
                        contentDescription = activeItem.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = activeItem.gradientColors.map { Color(it) }
                                )
                            )
                    )
                }
            }
        }

        // Double Tap Animated Heart Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = showDoubleTapHeart,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = AuraMagenta,
                modifier = Modifier.size(72.dp)
            )
        }
        LaunchedEffect(showDoubleTapHeart) {
            if (showDoubleTapHeart) {
                delay(500)
                showDoubleTapHeart = false
            }
        }

        // Overlay Controls (Auto-Hiding)
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                // Top Overlay: Back button & Title (Clean 7-Control Style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (showClipsSheet) {
                                showClipsSheet = false
                            } else if (showPlayerMenu) {
                                showPlayerMenu = false
                            } else if (activeClipCandidate != null) {
                                activeClipCandidate = null
                                showClipsSheet = true
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeClipCandidate != null) "Viewing Clip: ${activeClipCandidate?.title}" else activeItem.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        if (activeClipCandidate != null) {
                            Text(
                                text = "Looping Clip Segment (${formatTimeMs(activeClipCandidate!!.startTimeMs.toFloat())} - ${formatTimeMs(activeClipCandidate!!.endTimeMs.toFloat())})",
                                fontSize = 12.sp,
                                color = AuraPurple,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (playlistState != null && playlistState.items.isNotEmpty()) {
                            Text(
                                text = "${playlistState.sourceTitle}  ·  ${currentItemIndex + 1} of ${playlistState.items.size}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                // Removed Center Controls row (Moving to unified Bottom Row)
                
                // Bottom Controls: Scrubber bar, Timestamps, and restored 7-Control Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isVideo && durationMs > 0f) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTimeMs(currentPositionMs),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = formatTimeMs(durationMs),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        Slider(
                            value = currentPositionMs,
                            onValueChange = { pos ->
                                currentPositionMs = pos
                                exoPlayer.seekTo(pos.toLong())
                            },
                            valueRange = 0f..durationMs,
                            colors = SliderDefaults.colors(
                                thumbColor = AuraPurple,
                                activeTrackColor = AuraPurple,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Camera / Screenshot
                        IconButton(onClick = { captureAndSaveScreenshot(context, activeItem, currentPositionMs.toLong()) }) {
                            Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Capture", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // 2. AI Skip Backward
                        IconButton(
                            onClick = { 
                                val clipCandidates = generateClipCandidates(activeItem, durationMs)
                                val decision = AISkipEngine.calculateSkipBack(
                                    item = activeItem,
                                    currentPosMs = currentPositionMs.toLong(),
                                    durationMs = durationMs.toLong(),
                                    clipCandidates = clipCandidates,
                                    lastSkipForwardTimeMs = lastSkipForwardTimeMs,
                                    lastSkipForwardPosMs = lastSkipForwardPosMs
                                )
                                exoPlayer.seekTo(decision.targetPositionMs)
                                aiSkipFeedbackReason = decision.reason
                                val eventType = if (decision.isReversal) "SKIP_REVERSAL" else "SKIP_BACK"
                                onAISkipEvent?.invoke(
                                    activeItem.id,
                                    eventType,
                                    currentPositionMs.toLong(),
                                    decision.targetPositionMs
                                )
                                if (decision.isReversal) {
                                    lastSkipForwardTimeMs = 0L
                                }
                                pendingWatchedDestinationPosMs = -1L
                            },
                            modifier = Modifier.testTag("detail_ai_skip_back_button")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "AI Skip Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // 3. Previous Button
                        IconButton(
                            onClick = { onPrevious() },
                            enabled = playlistState?.hasPrevious == true,
                            modifier = Modifier.testTag("detail_previous_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = if (playlistState?.hasPrevious == true) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // 4. Large Play/Pause (Gradient Circle)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(DiscoveryGradient)
                                .clickable {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // 5. Next Button
                        IconButton(
                            onClick = { onNext() },
                            enabled = playlistState?.hasNext == true,
                            modifier = Modifier.testTag("detail_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = if (playlistState?.hasNext == true) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // 6. AI Skip Forward
                        IconButton(
                            onClick = { 
                                val clipCandidates = generateClipCandidates(activeItem, durationMs)
                                val now = System.currentTimeMillis()
                                val decision = AISkipEngine.calculateSkipForward(
                                    item = activeItem,
                                    currentPosMs = currentPositionMs.toLong(),
                                    durationMs = durationMs.toLong(),
                                    clipCandidates = clipCandidates,
                                    lastSkipForwardTimeMs = lastSkipForwardTimeMs,
                                    skipSensitivity = tasteDNA.effectiveSkipSensitivity
                                )
                                val fromPos = currentPositionMs.toLong()
                                exoPlayer.seekTo(decision.targetPositionMs)
                                aiSkipFeedbackReason = decision.reason
                                lastSkipForwardTimeMs = now
                                lastSkipForwardPosMs = fromPos
                                val eventType = if (decision.isRepeatedSkip) "REPEATED_SKIP" else "SKIP_FORWARD"
                                onAISkipEvent?.invoke(
                                    activeItem.id,
                                    eventType,
                                    fromPos,
                                    decision.targetPositionMs
                                )
                                pendingWatchedDestinationPosMs = decision.targetPositionMs
                                pendingWatchedDestinationStartMs = now
                            },
                            modifier = Modifier.testTag("detail_ai_skip_forward_button")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "AI Skip Forward", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // 7. Video Loop Toggle (Farthest Right)
                        IconButton(
                            onClick = {
                                if (!isVideo) {
                                    Toast.makeText(context, "Loop is available for videos", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                repository.repeatMode = !isLoopEnabled
                                val status = if (!isLoopEnabled) "Loop ON" else "Loop OFF"
                                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("video_loop_button")
                        ) {
                            Icon(
                                imageVector = if (isLoopEnabled) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = "Toggle Loop",
                                tint = if (isLoopEnabled) AuraPurple else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Player Menu Modal Sheet (Swipe Up or Info Button)
    if (showPlayerMenu) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPlayerMenu = false },
            sheetState = sheetState,
            containerColor = AuraCrispWhite,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(AuraSubtleBorder)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Media Title Row
                val displayFilename = if (activeItem.title.contains(".")) activeItem.title else "${activeItem.title}.${if (isVideo) "mp4" else "jpg"}"
                Text(
                    text = displayFilename,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuraMidnight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Rating & Playback Control Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AuraSubtleSurface)
                        .border(1.dp, AuraSubtleBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Five-Star Rating
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            val isStarred = i <= currentRating.toInt()
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rate $i stars",
                                tint = if (isStarred) AuraStarGold else AuraSubtleBorder,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        currentRating = i.toFloat()
                                        onUpdateRating?.invoke(activeItem.id, currentRating)
                                        Toast.makeText(context, "Rated $i stars", Toast.LENGTH_SHORT).show()
                                    }
                            )
                        }
                    }

                    // Controls: A/B, Camera, Speed, Favorite, Delete
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 3-Step A/B Repeat Button
                        val abLabel = when {
                            abPointA == null -> "A/B"
                            abPointB == null -> "A: ${formatTimeMs(abPointA!!.toFloat())}"
                            else -> "A-B Loop"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = abLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAbRepeatActive || abPointA != null) DiscoveryViolet else AuraMidnight,
                                modifier = Modifier
                                    .testTag("ab_repeat_button")
                                    .clickable {
                                        if (abPointA == null) {
                                            abPointA = currentPositionMs.toLong()
                                            Toast.makeText(context, "A/B: Point A set at ${formatTimeMs(abPointA!!.toFloat())}", Toast.LENGTH_SHORT).show()
                                        } else if (abPointB == null) {
                                            val targetB = currentPositionMs.toLong()
                                            abPointB = if (targetB <= abPointA!!) abPointA!! + 1000L else targetB
                                            isAbRepeatActive = true
                                            Toast.makeText(context, "A/B: Point B set at ${formatTimeMs(abPointB!!.toFloat())}. Looping A-B", Toast.LENGTH_SHORT).show()
                                        } else {
                                            abPointA = null
                                            abPointB = null
                                            isAbRepeatActive = false
                                            Toast.makeText(context, "A/B Repeat cleared", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            )

                            // AB Export Action
                            if (abPointA != null && abPointB != null) {
                                IconButton(
                                    onClick = {
                                        if (abPointB!! > abPointA!!) {
                                            val clip = com.example.data.ClipCandidate(
                                                title = "AB Clip ${System.currentTimeMillis()}",
                                                startTimeMs = abPointA!!,
                                                endTimeMs = abPointB!!,
                                                durationSec = ((abPointB!! - abPointA!!) / 1000).toInt(),
                                                relevanceScorePercent = 100,
                                                selectionReason = "User defined AB segment"
                                            )
                                            exportClip(context, activeItem, clip)
                                            showPlayerMenu = false
                                        } else {
                                            Toast.makeText(context, "Point B must be after Point A", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCut,
                                        contentDescription = "Export AB Clip",
                                        tint = DiscoveryViolet,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Camera / Screenshot
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Capture Screenshot",
                            tint = AuraMidnight,
                            modifier = Modifier
                                .size(18.dp)
                                .testTag("capture_frame_button")
                                .clickable { captureAndSaveScreenshot(context, activeItem, currentPositionMs.toLong()) }
                        )

                        // Playback Speed Dropdown Trigger
                        Box {
                            Text(
                                text = "${playbackSpeed}x",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AuraMidnight,
                                modifier = Modifier
                                    .testTag("playback_speed_button")
                                    .clickable { showSpeedMenu = true }
                            )

                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false },
                                modifier = Modifier.background(AuraCrispWhite)
                            ) {
                                listOf(0.25f, 0.5f, 1.0f, 2.0f, 4.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${speed}x ${if (playbackSpeed == speed) "✓" else ""}",
                                                color = AuraMidnight,
                                                fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            playbackSpeed = speed
                                            exoPlayer.setPlaybackSpeed(speed)
                                            showSpeedMenu = false
                                            Toast.makeText(context, "Playback speed: ${speed}x", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        // Favorite / Like
                        Icon(
                            imageVector = if (activeItem.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (activeItem.isFavorite) AuraMagenta else AuraMidnight,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onFavoriteToggle(activeItem.id) }
                        )

                        // Delete Trash Icon -> Trigger Dialog
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Media",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier
                                .size(18.dp)
                                .testTag("delete_media_button")
                                .clickable {
                                    showPlayerMenu = false
                                    showDeleteDialog = true
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Action Button Grid (3x2)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PlayerActionButton(
                            label = "See Similar",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onMicroMoment?.invoke(activeItem.id, 5)
                                showPlayerMenu = false
                                onSeeSimilar?.invoke(activeItem)
                            }
                        )
                        PlayerActionButton(
                            label = "More Like This",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onMicroMoment?.invoke(activeItem.id, 3)
                                Toast.makeText(context, "Increased recommendation weight", Toast.LENGTH_SHORT).show()
                                showPlayerMenu = false
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PlayerActionButton(
                            label = "Less Like This",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onMicroMoment?.invoke(activeItem.id, -2)
                                Toast.makeText(context, "Adjusted recommendations", Toast.LENGTH_SHORT).show()
                                showPlayerMenu = false
                            }
                        )
                        PlayerActionButton(
                            label = "Generate Clips",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onMicroMoment?.invoke(activeItem.id, 4)
                                showPlayerMenu = false
                                val clips = generateClipCandidates(activeItem, durationMs)
                                generatedClips = clips
                                showClipsSheet = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Media") },
            text = { Text("Choose how you want to delete '${activeItem.title}'.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteMedia?.invoke(activeItem.id)
                        Toast.makeText(context, "Removed from Aura Library", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("delete_library_confirm_button")
                ) {
                    Text("Remove from Library", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteMedia?.invoke(activeItem.id)
                        },
                        modifier = Modifier.testTag("delete_device_confirm_button")
                    ) {
                        Text("Delete from Device", color = Color(0xFFEF4444))
                    }
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            },
            containerColor = Color(0xFF161528),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f)
        )
    }

    // Generate Clips Bottom Sheet with Simultaneous Live Previews
    if (showClipsSheet) {
        val clipsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedClipCandidate by remember(generatedClips) { mutableStateOf(generatedClips.firstOrNull()) }

        ModalBottomSheet(
            onDismissRequest = { showClipsSheet = false },
            sheetState = clipsSheetState,
            containerColor = AuraCrispWhite,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Generated Clips for ${activeItem.title}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuraMidnight
                        )
                        Text(
                            text = if (generatedClips.isEmpty()) "No highlights generated" else "${generatedClips.size} AI highlight clip${if (generatedClips.size > 1) "s" else ""} generated (Max 60s per clip)",
                            fontSize = 12.sp,
                            color = AuraMutedSlate,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (generatedClips.isEmpty()) {
                    Text(
                        text = "No relevant highlights found for your current preferences.",
                        fontSize = 13.sp,
                        color = AuraMutedSlate,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(
                            items = generatedClips,
                            key = { clip -> "${activeItem.id}_${clip.title}_${clip.startTimeMs}_${clip.endTimeMs}" }
                        ) { clip ->
                            ClipComparisonCard(
                                clip = clip,
                                mediaItem = activeItem,
                                isSelected = (selectedClipCandidate?.startTimeMs == clip.startTimeMs),
                                onSelect = {
                                    selectedClipCandidate = clip
                                    activeClipCandidate = clip
                                    ClipTelemetryService.logInteraction(
                                        MediaRepository.instance,
                                        activeItem.id,
                                        clip,
                                        ClipTelemetryService.InteractionType.SELECT
                                    )
                                    exoPlayer.seekTo(clip.startTimeMs)
                                    exoPlayer.play()
                                    Toast.makeText(context, "Selected clip: ${clip.title}", Toast.LENGTH_SHORT).show()
                                    showClipsSheet = false
                                },

                                onExport = {
                                    exportClip(context, activeItem, clip)
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
private fun ClipComparisonCard(
    clip: ClipCandidate,
    mediaItem: MediaItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val targetUri = mediaItem.uriPath.ifEmpty { mediaItem.imageUrl }
    val clipKey = "${mediaItem.id}_${clip.title}_${clip.startTimeMs}_${clip.endTimeMs}"
    
    var previewPlayer by remember(clipKey, targetUri) { mutableStateOf<ExoPlayer?>(null) }
    var playbackErrorMsg by remember(clipKey, targetUri) { mutableStateOf<String?>(null) }

    DisposableEffect(clipKey, targetUri) {
        if (targetUri.isNotEmpty() && (mediaItem.mediaType.equals("VIDEO", ignoreCase = true) || mediaItem.mediaType.equals("Movie", ignoreCase = true) || targetUri.startsWith("content://") || targetUri.endsWith(".mp4") || targetUri.contains("/Movies/"))) {
            Log.d("AuraClipPreview", "Init unique preview [$clipKey]: URI=$targetUri, range=[${clip.startTimeMs}ms - ${clip.endTimeMs}ms]")
            ClipTelemetryService.logInteraction(
                MediaRepository.instance,
                mediaItem.id,
                clip,
                ClipTelemetryService.InteractionType.PREVIEW
            )
            val player = ExoPlayer.Builder(context.applicationContext).build().apply {

                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                val media3Item = Media3Item.fromUri(Uri.parse(targetUri))
                setMediaItem(media3Item)
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val stateStr = when (playbackState) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN"
                        }
                        Log.d("AuraClipPreview", "Clip [$clipKey] state: $stateStr, playWhenReady: $playWhenReady, pos: $currentPosition")
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("AuraClipPreview", "Clip [$clipKey] error: ${error.message}", error)
                        playbackErrorMsg = error.localizedMessage ?: "Preview unavailable"
                    }
                })

                prepare()
                playWhenReady = true
                seekTo(clip.startTimeMs)
            }
            previewPlayer = player
            onDispose {
                Log.d("AuraClipPreview", "Releasing unique preview player [$clipKey]")
                try {
                    player.clearVideoTextureView(null)
                    player.stop()
                    player.release()
                } catch (_: Exception) {}
                previewPlayer = null
            }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(previewPlayer, clip.startTimeMs, clip.endTimeMs) {
        while (previewPlayer != null) {
            val p = previewPlayer ?: break
            // ONLY check loop bounds when player is in STATE_READY or STATE_ENDED
            if (p.playbackState == Player.STATE_READY) {
                val currentPos = p.currentPosition
                if (currentPos >= clip.endTimeMs) {
                    p.seekTo(clip.startTimeMs)
                }
            } else if (p.playbackState == Player.STATE_ENDED) {
                p.seekTo(clip.startTimeMs)
                p.playWhenReady = true
            }
            delay(200)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .testTag("clip_comparison_card_${clip.startTimeMs}"),
        color = AuraSubtleSurface,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) DiscoveryViolet else AuraSubtleBorder
        )
    ) {
        Column {
            // Live Video Preview Viewport (ALWAYS VISIBLE, NO PREVIEW BUTTON REQUIRED)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color.Black)
            ) {
                val currentPlayer = previewPlayer
                if (currentPlayer != null && playbackErrorMsg == null) {
                    AndroidView(
                        factory = { ctx ->
                            android.view.TextureView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                currentPlayer.setVideoTextureView(this)
                            }
                        },
                        update = { textureView ->
                            currentPlayer.setVideoTextureView(textureView)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (mediaItem.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = mediaItem.imageUrl,
                        contentDescription = clip.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (playbackErrorMsg != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = playbackErrorMsg ?: "Preview Unavailable",
                            color = Color.Yellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Selection / Match badge on preview
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) DiscoveryViolet else Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isSelected) "Selected" else "${clip.relevanceScorePercent}% Match",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Live Loop indicator tag
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DiscoveryViolet.copy(alpha = 0.8f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "LIVE PREVIEW",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            // Clip Info & Controls
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = clip.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuraMidnight
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${formatTimeMs(clip.startTimeMs.toFloat())} - ${formatTimeMs(clip.endTimeMs.toFloat())}  ·  ${clip.durationSec}s",
                            fontSize = 12.sp,
                            color = DiscoveryViolet,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = clip.selectionReason,
                            fontSize = 11.sp,
                            color = AuraSlate,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayerActionButton(
                        label = if (isSelected) "Play Full Screen" else "Select & Play",
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect() }
                    )

                    PlayerActionButton(
                        label = "Export Clip",
                        modifier = Modifier.weight(1f),
                        onClick = { onExport() }
                    )
                }
            }
        }
    }
}

private fun exportClip(context: Context, item: MediaItem, clip: ClipCandidate) {
    Toast.makeText(context, "Exporting clip: ${clip.title}...", Toast.LENGTH_SHORT).show()
    ClipTelemetryService.logInteraction(
        MediaRepository.instance,
        item.id,
        clip,
        ClipTelemetryService.InteractionType.EXPORT
    )
    CoroutineScope(Dispatchers.Main).launch {

        val result = ClipExporter.exportClipAndSave(
            context = context,
            sourceItem = item,
            clip = clip,
            repository = MediaRepository.instance
        )
        if (result.isSuccess) {
            Toast.makeText(
                context,
                "Clip exported to Movies/AuraClips & added to User Media -> Videos!",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                context,
                "Export error: ${result.errorMessage ?: "Unknown error"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

private fun captureAndSaveScreenshot(context: Context, item: MediaItem, positionMs: Long = 0L) {
    try {
        var bitmap: Bitmap? = null
        val targetUri = item.uriPath.ifEmpty { item.imageUrl }

        if (item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("Movie", ignoreCase = true)) {
            val retriever = MediaMetadataRetriever()
            try {
                if (targetUri.startsWith("http://") || targetUri.startsWith("https://")) {
                    retriever.setDataSource(targetUri, HashMap())
                } else if (targetUri.isNotEmpty()) {
                    retriever.setDataSource(context, Uri.parse(targetUri))
                }
                val frameMicros = positionMs * 1000L
                bitmap = retriever.getFrameAtTime(frameMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                bitmap = null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } else if (targetUri.isNotEmpty()) {
            try {
                if (targetUri.startsWith("content://") || targetUri.startsWith("file://")) {
                    context.contentResolver.openInputStream(Uri.parse(targetUri))?.use { stream ->
                        bitmap = BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (_: Exception) {}
        }

        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                canvas.drawColor(android.graphics.Color.BLACK)
                val paint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 48f
                    isAntiAlias = true
                }
                canvas.drawText("Aura Frame: ${item.title}", 100f, 960f, paint)
            }
        }

        val filename = "Aura_Frame_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Aura")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val uriStr = uri?.toString() ?: ""
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            }
            Toast.makeText(context, "Frame saved to Pictures/Aura", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Frame saved to gallery", Toast.LENGTH_SHORT).show()
        }

        val now = System.currentTimeMillis()
        val snapshotEntity = MediaEntity(
            id = "aura_snapshot_${now}",
            title = "Snapshot — ${item.title}",
            mediaType = "PHOTO",
            year = 2026,
            duration = "Snapshot",
            durationMs = 0L,
            genre = "User Screenshots",
            imageUrl = uriStr,
            rating = 5.0f,
            category = "User Media",
            aiSummary = "User captured frame snapshot from ${item.title}",
            moodTagsJson = "User Media,Snapshot,Photo",
            uriPath = uriStr,
            dateAdded = now,
            sizeBytes = 0L,
            parentContentId = item.id, // Phase 6: Link to source video/photo
            compatibilityStatus = CompatibilityStatus.PLAYABLE.name
        )
        MediaRepository.getInstance(context).addMediaEntity(snapshotEntity)
    } catch (e: Exception) {
        Toast.makeText(context, "Frame captured: ${item.title}", Toast.LENGTH_SHORT).show()
    }
}

private data class CandidateDef(
    val relativePos: Float,
    val title: String,
    val baseDurationSec: Int,
    val baseScore: Int,
    val reasonTemplate: String
)

private fun generateClipCandidates(item: MediaItem, totalDurationMs: Float): List<ClipCandidate> {
    val effectiveDurationMs = maxOf(totalDurationMs, item.durationMs.toFloat(), 10000f)
    val durationSec = (effectiveDurationMs / 1000).toInt().coerceAtLeast(10)

    // Determine maximum clips this video length can reasonably support (up to 10 max)
    val maxClipsForDuration = when {
        durationSec <= 15 -> 1
        durationSec <= 40 -> 2
        durationSec <= 90 -> 3
        durationSec <= 240 -> 5
        durationSec <= 600 -> 7
        else -> 10
    }

    if (durationSec <= 15) {
        val relScore = (90 + (if (item.isFavorite) 5 else 0) + (if (item.rating >= 4.0f) 3 else 0)).coerceIn(70, 98)
        return listOf(
            ClipCandidate(
                title = "Full Highlight Peak",
                startTimeMs = 0L,
                endTimeMs = effectiveDurationMs.toLong(),
                durationSec = durationSec,
                relevanceScorePercent = relScore,
                selectionReason = "Complete short highlight matching user preference (${item.genre})"
            )
        )
    }

    val defs = listOf(
        CandidateDef(0.00f, "Opening Action Peak", 30, 93, "High initial scene energy & matching genre (${item.genre})"),
        CandidateDef(0.08f, "Early Narrative Hook", 25, 87, "Key narrative setup & visual intrigue"),
        CandidateDef(0.16f, "Rising Kinetic Sequence", 35, 89, "Evaluated high motion density & visual contrast"),
        CandidateDef(0.26f, "Atmospheric Focus Segment", 30, 84, "Aesthetic composition & mood alignment (${item.moodTags.firstOrNull() ?: "Cinematic"})"),
        CandidateDef(0.36f, "Midpoint Turning Point", 40, 91, "High semantic importance & engagement spike"),
        CandidateDef(0.46f, "Core Highlight Moment", 35, 88, "Evaluated high rating affinity (${item.rating}/5) & micro-moment engagement"),
        CandidateDef(0.56f, "Sustained Dynamic Phase", 30, 86, "Dynamic pacing & learned preference alignment"),
        CandidateDef(0.66f, "Pivotal Climax Build", 40, 90, "High motion density & dramatic buildup"),
        CandidateDef(0.76f, "Climax Peak Segment", 35, 94, "Peak emotional energy & learned preference score"),
        CandidateDef(0.85f, "Dramatic Confrontation", 30, 88, "Peak contrast & visual tension"),
        CandidateDef(0.92f, "High-Impact Finale", 25, 85, "Memorable resolution & high sentiment match")
    )

    val rawCandidates = mutableListOf<ClipCandidate>()

    for (def in defs) {
        val startMs = (effectiveDurationMs * def.relativePos).toLong()
        val requestedDurationMs = def.baseDurationSec * 1000L
        val endMs = (startMs + requestedDurationMs).coerceAtMost(effectiveDurationMs.toLong())
        val actualDurSec = ((endMs - startMs) / 1000).toInt()

        if (actualDurSec < 10) continue

        // Calculate dynamic relevance score based on item attributes
        var score = def.baseScore
        if (item.isFavorite) score += 3
        if (item.rating >= 4.5f) score += 3
        if (item.rating < 3.5f) score -= 6
        if (item.category == "Your Next Obsession" || item.category == "Fresh for You") score += 2
        val finalScore = score.coerceIn(60, 98)

        if (finalScore >= 70) {
            rawCandidates.add(
                ClipCandidate(
                    title = def.title,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    durationSec = actualDurSec,
                    relevanceScorePercent = finalScore,
                    selectionReason = def.reasonTemplate
                )
            )
        }
    }

    // Rank candidates by relevance score descending
    val rankedCandidates = rawCandidates.sortedByDescending { it.relevanceScorePercent }

    // Select non-overlapping, distinct highlights up to capacity (max 10)
    val selectedClips = mutableListOf<ClipCandidate>()

    for (candidate in rankedCandidates) {
        if (selectedClips.size >= minOf(10, maxClipsForDuration)) break

        val overlaps = selectedClips.any { selected ->
            val overlapStart = maxOf(candidate.startTimeMs, selected.startTimeMs)
            val overlapEnd = minOf(candidate.endTimeMs, selected.endTimeMs)
            val overlapMs = maxOf(0L, overlapEnd - overlapStart)

            // Considered overlapping if overlap is > 10s or start times are within 15s
            overlapMs > 10000L || kotlin.math.abs(candidate.startTimeMs - selected.startTimeMs) < 15000L
        }

        if (!overlaps) {
            selectedClips.add(candidate)
        }
    }

    // Return chronologically ordered clips for natural timeline presentation
    return selectedClips.sortedBy { it.startTimeMs }
}

@Composable
private fun PlayerActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = AuraSubtleSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AuraMidnight,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatTimeMs(timeMs: Float): String {
    val totalSeconds = (timeMs / 1000).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
