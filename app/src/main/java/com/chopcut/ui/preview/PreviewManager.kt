package com.chopcut.ui.preview

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SeekParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.media3.common.util.UnstableApi
import com.chopcut.data.model.EditOperation
import com.chopcut.data.player.EffectFactory

/**
 * Manages ExoPlayer for video preview playback
 */
@UnstableApi
class PreviewManager(private val context: Context) {

    var exoPlayer: ExoPlayer? = null
        private set

    // ... (existing code)

    /**
     * Apply edit operations as video effects
     */
    fun applyEffects(operations: List<EditOperation>) {
        val player = exoPlayer ?: return

        try {
            val effects = EffectFactory.createEffects(operations)
            player.setVideoEffects(effects)

            // Extract audio settings
            baseVolume = operations.filterIsInstance<EditOperation.Volume>()
                .lastOrNull()?.volume ?: 1.0f

            val fadeOp = operations.filterIsInstance<EditOperation.Fade>().lastOrNull()
            fadeInDuration = fadeOp?.fadeInMs ?: 0L
            fadeOutDuration = fadeOp?.fadeOutMs ?: 0L

            // Calculate trim duration for fade calculation
            val trimOp = operations.filterIsInstance<EditOperation.Trim>().lastOrNull()
            trimDuration = if (trimOp != null) {
                trimOp.endTime - trimOp.startTime
            } else {
                player.duration.takeIf { it > 0 } ?: 0L
            }

            // Apply initial volume
            updateAudioFade(player.currentPosition)

            // Force visual update if paused
            if (!player.isPlaying) {
                 // Seeking to the exact same position often forces a redraw
                 player.seekTo(player.currentPosition)
            }

            Timber.d("Applied ${effects.size} video effects. Volume: $baseVolume, FadeIn: ${fadeInDuration}ms, FadeOut: ${fadeOutDuration}ms")
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply video effects")
        }
    }

    /**
     * Update audio fade based on current position
     */
    private fun updateAudioFade(currentPositionMs: Long) {
        val player = exoPlayer ?: return

        var fadeMultiplier = 1.0f

        // Apply fade in
        if (fadeInDuration > 0 && currentPositionMs < fadeInDuration) {
            fadeMultiplier = currentPositionMs.toFloat() / fadeInDuration.toFloat()
        }

        // Apply fade out
        if (fadeOutDuration > 0 && trimDuration > 0) {
            val fadeOutStart = trimDuration - fadeOutDuration
            if (currentPositionMs > fadeOutStart) {
                val remaining = trimDuration - currentPositionMs
                fadeMultiplier = remaining.toFloat() / fadeOutDuration.toFloat()
            }
        }

        // Clamp multiplier
        fadeMultiplier = fadeMultiplier.coerceIn(0f, 1f)

        // Apply to base volume
        val finalVolume = baseVolume * fadeMultiplier
        player.volume = finalVolume.coerceIn(0f, 1f)

        // Expose for UI
        _currentVolume.value = finalVolume
    }

    // Coroutine scope for position updates
    private val updateScope = CoroutineScope(Dispatchers.Main)
    private var updateJob: Job? = null
    private var currentUri: Uri? = null

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // Video dimensions for aspect ratio
    private val _videoWidth = MutableStateFlow(0)
    val videoWidth: StateFlow<Int> = _videoWidth.asStateFlow()

    private val _videoHeight = MutableStateFlow(0)
    val videoHeight: StateFlow<Int> = _videoHeight.asStateFlow()

    // Audio fade state
    private var baseVolume: Float = 1.0f
    private var fadeInDuration: Long = 0L
    private var fadeOutDuration: Long = 0L
    private var trimDuration: Long = 0L // Duration after trim for fade calculation

    private val _currentVolume = MutableStateFlow(1.0f)
    val currentVolume: StateFlow<Float> = _currentVolume.asStateFlow()

    /**
     * Prepare player with video URI - follows cropy's VideoPlayerController pattern
     * Creates player AND sets media item in one step to avoid race conditions
     */
    fun prepare(context: Context, uri: Uri?, scope: CoroutineScope) {
        if (uri == null) return

        // If player exists and URI is same, just ensure we are tracking
        if (exoPlayer != null && currentUri == uri) {
            startPositionUpdates()
            return
        }

        // Release previous if exists (and URI changed)
        if (exoPlayer != null) {
            release()
        }

        // Create new player with media item (like cropy)
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(createEventListener())
        }

        exoPlayer = player
        currentUri = uri
        startPositionUpdates()
        Timber.d("Player prepared with source: $uri")
    }

    /**
     * Play video
     */
    fun play() {
        val player = exoPlayer ?: throw IllegalStateException("PreviewManager not initialized")

        player.play()
        _isPlaying.value = true
        startPositionUpdates()
        Timber.d("Player started")
    }

    /**
     * Pause video
     */
    fun pause() {
        val player = exoPlayer ?: throw IllegalStateException("PreviewManager not initialized")

        player.pause()
        _isPlaying.value = false
        stopPositionUpdates()
        Timber.d("Player paused")
    }

    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    /**
     * Start periodic position updates
     */
    private fun startPositionUpdates() {
        stopPositionUpdates()
        updateJob = updateScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    _currentPosition.value = player.currentPosition
                    // Update audio fade in real-time
                    updateAudioFade(player.currentPosition)
                }
                delay(16) // Update ~60 times per second for smooth timeline
            }
        }
    }

    /**
     * Stop position updates
     */
    private fun stopPositionUpdates() {
        updateJob?.cancel()
        updateJob = null
    }

    /**
     * Seek to specific position
     * @param positionMs Position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: throw IllegalStateException("PreviewManager not initialized")

        Timber.v("PreviewManager: Seeking to ${positionMs}ms (isPlaying=${player.isPlaying})")
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        player.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    /**
     * Get current playback position
     */
    fun getCurrentPosition(): Long {
        val player = exoPlayer ?: return 0L
        return player.currentPosition
    }

    /**
     * Get video duration
     */
    fun getDuration(): Long {
        val player = exoPlayer ?: return 0L
        return player.duration
    }

    /**
     * Release player and cleanup resources
     */
    fun release() {
        exoPlayer?.let { player ->
            _isPlaying.value = false
            _isReady.value = false
            player.release()
            exoPlayer = null
            currentUri = null
            Timber.d("PreviewManager released")
        }
    }

    private fun createEventListener(): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> {
                        _isPlaying.value = false
                        Timber.d("Playback state: IDLE")
                    }
                    Player.STATE_BUFFERING -> {
                        Timber.d("Playback state: BUFFERING")
                    }
                    Player.STATE_READY -> {
                        _isReady.value = true
                        _duration.value = exoPlayer?.duration ?: 0L
                        // Update video dimensions
                        exoPlayer?.videoSize?.let { videoSize ->
                            _videoWidth.value = videoSize.width
                            _videoHeight.value = videoSize.height
                            Timber.d("Video size: ${videoSize.width}x${videoSize.height}")
                        }
                        Timber.d("Playback state: READY")
                    }
                    Player.STATE_ENDED -> {
                        _isPlaying.value = false
                        Timber.d("Playback state: ENDED")
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                Timber.d("Is playing changed: $isPlaying")
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.e(error, "Player error occurred")
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                _videoWidth.value = videoSize.width
                _videoHeight.value = videoSize.height
                Timber.d("Video size changed: ${videoSize.width}x${videoSize.height}")
            }
        }
    }
}
