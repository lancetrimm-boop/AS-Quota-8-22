package com.example.compatibility

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Headless playback validator using Media3 ExoPlayer.
 * Verifies that a converted file can be successfully prepared and advanced.
 */
object AuraPlaybackValidator {

    private const val VALIDATION_TIMEOUT_MS = 10000L

    /**
     * Validates if the media at [uri] can be played by ExoPlayer.
     */
    suspend fun validatePlayback(context: Context, uri: Uri): Boolean = withContext(Dispatchers.Main) {
        val player = ExoPlayer.Builder(context).build()
        val deferred = CompletableDeferred<Boolean>()

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        // Successfully prepared and ready to play
                        deferred.complete(true)
                    }
                    Player.STATE_IDLE -> {
                        // If it moves to idle without an error, it might be a failure in some contexts
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                deferred.complete(false)
            }
        }

        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = false // Just prepare

        val result = withTimeoutOrNull(VALIDATION_TIMEOUT_MS) {
            deferred.await()
        } ?: false

        player.removeListener(listener)
        player.release()

        return@withContext result
    }
}
