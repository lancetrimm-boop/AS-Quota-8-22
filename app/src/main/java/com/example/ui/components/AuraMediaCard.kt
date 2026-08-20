package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.util.MediaThumbnailFetcher
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.MediaItem
import com.example.ui.models.LibraryItemUi
import com.example.ui.theme.AuraBackground
import com.example.ui.theme.AuraBorder
import com.example.ui.theme.AuraBrandGradient
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMagenta
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraOnSurfaceVariant
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraStarGold
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.AuraSurface
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryViolet

@Composable
fun AuraMediaThumbnail(
    itemId: String,
    mediaType: String,
    imageUrl: String,
    uriPath: String,
    title: String,
    modifier: Modifier = Modifier,
    locationTag: String = "generic"
) {
    val isVideo = mediaType.equals("VIDEO", ignoreCase = true) || mediaType.equals("Movie", ignoreCase = true)
    val imageModel = if (imageUrl.isNotEmpty()) imageUrl else uriPath
    val context = LocalContext.current
    var thumbnailBitmap by remember(itemId, uriPath, imageUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(itemId, uriPath, imageUrl) {
        val targetUri = if (imageUrl.isNotEmpty()) imageUrl else uriPath
        if (targetUri.isNotEmpty() && isVideo) {
            thumbnailBitmap = MediaThumbnailFetcher.getThumbnail(context, targetUri)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AuraCrispWhite)
            .border(1.dp, AuraSubtleBorder, RoundedCornerShape(12.dp))
    ) {
        if (thumbnailBitmap != null || imageModel.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Single Blurred Background (Optimization: reduced quality for blur)
                val backgroundModifier = Modifier
                    .fillMaxSize()
                    .blur(30.dp)
                    .graphicsLayer(alpha = 0.5f)

                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = backgroundModifier,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageModel)
                            .crossfade(false)
                            .size(100) // Much lower res for blurred layer
                            .build(),
                        contentDescription = null,
                        modifier = backgroundModifier,
                        contentScale = ContentScale.Crop
                    )
                }

                // Single Aspect-Fit Foreground
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageModel)
                            .crossfade(false)
                            .build(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        if (isVideo) {
            VideoTilePreview(
                itemId = itemId,
                videoUri = uriPath,
                imageUrl = imageUrl,
                locationTag = locationTag,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .clipToBounds()
            )
        }
    }
}

@Composable
fun LibraryGalleryMediaTile(
    item: LibraryItemUi,
    onClick: () -> Unit,
    onLike: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "gallery_tile_scale"
    )

    var showDoubleTapHeart by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDoubleTapHeart = true
                        onLike()
                    },
                    onLongPress = {
                        onLongClick?.invoke()
                    }
                )
            }
            .testTag("library_gallery_tile_${item.id}")
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            AuraMediaThumbnail(
                itemId = item.id,
                mediaType = item.mediaType,
                imageUrl = item.imageUrl,
                uriPath = item.uriPath,
                title = item.title,
                modifier = Modifier.fillMaxSize(),
                locationTag = "library_gallery"
            )

            // Selection Overlay (Phase 4 — Updated for Phase 3 Visuals)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.5.dp, DiscoveryGradient, RoundedCornerShape(0.dp))
                        .background(DiscoveryViolet.copy(alpha = 0.05f))
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = DiscoveryViolet,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp)
                )
            }

            // Duration Badge for Videos (Retained)
            val isVideo = item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("Movie", ignoreCase = true)
            
            if (isVideo && item.duration.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = item.duration,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Double tap heart pop animation (Essential feedback for gesture)
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
                    modifier = Modifier.size(48.dp)
                )
            }
            LaunchedEffect(showDoubleTapHeart) {
                if (showDoubleTapHeart) {
                    delay(500)
                    showDoubleTapHeart = false
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = item.title,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = AuraMidnight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun AuraSquareMediaTile(
    item: MediaItem,
    onClick: () -> Unit,
    onLike: () -> Unit = {},
    onFavoriteToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "tile_scale"
    )

    var showDoubleTapHeart by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = {
                        onClick()
                    },
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDoubleTapHeart = true
                        onLike()
                    }
                )
            }
            .testTag("media_tile_${item.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            AuraMediaThumbnail(
                itemId = item.id,
                mediaType = item.mediaType,
                imageUrl = item.imageUrl,
                uriPath = item.uriPath,
                title = item.title,
                modifier = Modifier.fillMaxSize(),
                locationTag = "grid"
            )

            // Top Right: Favorite heart button (if supported on this surface)
            if (onFavoriteToggle != null) {
                val heartColor by animateColorAsState(
                    targetValue = if (item.isFavorite) AuraMagenta else Color.White.copy(alpha = 0.85f),
                    label = "heart_color"
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(48.dp) // 48dp touch target
                        .clip(CircleShape)
                        .clickable { onFavoriteToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = heartColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Double tap heart pop animation
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
                    modifier = Modifier.size(48.dp)
                )
            }
            LaunchedEffect(showDoubleTapHeart) {
                if (showDoubleTapHeart) {
                    delay(500)
                    showDoubleTapHeart = false
                }
            }
        }
    }
}

@Composable
fun AuraFeaturedMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onLike: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var thumbnailBitmap by remember(item.id, item.uriPath, item.imageUrl) { mutableStateOf<Bitmap?>(null) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }

    LaunchedEffect(item.id, item.uriPath, item.imageUrl) {
        val targetUri = if (item.imageUrl.isNotEmpty()) item.imageUrl else item.uriPath
        val isVideo = item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("Movie", ignoreCase = true)
        if (targetUri.isNotEmpty() && isVideo) {
            thumbnailBitmap = MediaThumbnailFetcher.getThumbnail(context, targetUri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = item.gradientColors.map { Color(it) }
                )
            )
            .border(1.dp, AuraSubtleBorder, RoundedCornerShape(20.dp))
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDoubleTapHeart = true
                        onLike?.invoke()
                    }
                )
            }
            .testTag("featured_card_${item.id}")
    ) {
        val imageModel = if (item.imageUrl.isNotEmpty()) item.imageUrl else item.uriPath

        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (imageModel.isNotEmpty()) {
            AsyncImage(
                model = imageModel,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        // Scrim gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        // Double tap heart pop animation
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
                modifier = Modifier.size(56.dp)
            )
        }
        LaunchedEffect(showDoubleTapHeart) {
            if (showDoubleTapHeart) {
                delay(500)
                showDoubleTapHeart = false
            }
        }

        // Title and Metadata Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = "FEATURED",
                color = AuraMagenta,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${item.year}  ·  ${item.duration}  ·  ${item.genre}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Play Button Overlay
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(DiscoveryGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Featured",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun AuraContinueWatchingCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("continue_watching_card"),
        color = AuraCrispWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumb
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DiscoveryGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AuraMidnight
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.progressText.ifEmpty { "In progress" },
                    fontSize = 12.sp,
                    color = AuraMutedSlate
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = DiscoveryViolet,
                    trackColor = AuraSubtleBorder
                )
            }
        }
    }
}

object VideoPreviewPool {
    private const val MAX_ACTIVE_PREVIEWS = 6
    
    // Key is "itemId_locationTag" to prevent player stealing between different UI contexts
    private val activePlayers = mutableMapOf<String, ExoPlayer>()
    private val accessOrder = mutableListOf<String>()

    @Synchronized
    fun acquirePlayer(
        context: Context, 
        itemId: String, 
        locationTag: String,
        uriString: String, 
        imageUrl: String = ""
    ): ExoPlayer? {
        val poolKey = "${itemId}_$locationTag"
        
        if (activePlayers.containsKey(poolKey)) {
            accessOrder.remove(poolKey)
            accessOrder.add(poolKey)
            val existing = activePlayers[poolKey]
            existing?.apply {
                repeatMode = Player.REPEAT_MODE_ONE
                if (playbackState == Player.STATE_ENDED) {
                    seekTo(0)
                    playWhenReady = true
                } else if (!isPlaying && playWhenReady) {
                    playWhenReady = true
                }
            }
            return existing
        }

        if (activePlayers.size >= MAX_ACTIVE_PREVIEWS && accessOrder.isNotEmpty()) {
            val oldestKey = accessOrder.removeAt(0)
            val oldestPlayer = activePlayers.remove(oldestKey)
            oldestPlayer?.apply {
                try {
                    clearVideoTextureView(null)
                    stop()
                    release()
                } catch (_: Exception) {}
            }
        }
        
        return try {
            val exoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
                val uri = when {
                    uriString.isNotEmpty() && (uriString.startsWith("http") || uriString.startsWith("content") || uriString.startsWith("file") || uriString.startsWith("android.resource")) -> Uri.parse(uriString)
                    imageUrl.isNotEmpty() && (imageUrl.startsWith("http") || imageUrl.startsWith("content") || imageUrl.startsWith("file") || imageUrl.startsWith("android.resource")) -> Uri.parse(imageUrl)
                    else -> Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                }
                setMediaItem(Media3Item.fromUri(uri))
                volume = 0f // Muted preview
                repeatMode = Player.REPEAT_MODE_ONE // Looping preview
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            seekTo(0)
                            playWhenReady = true
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("VideoPreviewPool", "Preview error for $itemId: ${error.message}", error)
                        // Capture diagnostics for preview failures
                        val repo = com.example.data.MediaRepository.instance
                        val mediaItem = repo.getMediaItemById(itemId)
                        repo.recordPlaybackError(error, this@apply, mediaItem)
                    }
                })
                prepare()
                playWhenReady = true
            }
            activePlayers[poolKey] = exoPlayer
            accessOrder.add(poolKey)
            exoPlayer
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun releasePlayer(itemId: String, locationTag: String) {
        val poolKey = "${itemId}_$locationTag"
        accessOrder.remove(poolKey)
        val player = activePlayers.remove(poolKey)
        player?.apply {
            try {
                clearVideoTextureView(null)
                stop()
                release()
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun releaseAll() {
        accessOrder.clear()
        activePlayers.values.forEach { player ->
            try {
                player.clearVideoTextureView(null)
                player.stop()
                player.release()
            } catch (_: Exception) {}
        }
        activePlayers.clear()
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoTilePreview(
    itemId: String,
    videoUri: String,
    imageUrl: String,
    modifier: Modifier = Modifier,
    locationTag: String = "generic"
) {
    val context = LocalContext.current
    var playerState by remember(itemId, locationTag) { mutableStateOf<ExoPlayer?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(itemId, locationTag) {
        playerState = VideoPreviewPool.acquirePlayer(context, itemId, locationTag, videoUri, imageUrl)
        onDispose {
            playerViewRef?.player = null
            VideoPreviewPool.releasePlayer(itemId, locationTag)
            playerState = null
        }
    }

    val player = playerState
    if (player != null) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    playerViewRef = this
                }
            },
            update = { playerView ->
                playerViewRef = playerView
                if (playerView.player != player) {
                    playerView.player = player
                }
            },
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .clipToBounds()
        )
    }
}
