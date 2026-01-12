package com.chopcut.ui.preview

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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

/**
 * Manages ExoPlayer for video preview playback
 */
class PreviewManager(private val context: Context) {

    var exoPlayer: ExoPlayer? = null
        private set

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
                }
                delay(100) // Update 10 times per second
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

        player.seekTo(positionMs)
        _currentPosition.value = positionMs
        Timber.d("Seeked to ${positionMs}ms")
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
