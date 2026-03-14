package com.chopcut.ui.components.timeline

import android.view.LayoutInflater
import android.widget.ImageView
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.chopcut.R
import androidx.media3.ui.PlayerView

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.unit.dp

import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import android.view.View

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import android.graphics.Bitmap
import com.chopcut.data.thumbnail.v3.ThumbnailExtractorV3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

@Composable
fun TimelineV3(
    modifier: Modifier = Modifier,
    exoPlayer: ExoPlayer,
    videoUri: Uri
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var maxPerformanceEnabled by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // State for video duration
    var durationMs by remember { mutableStateOf(0L) }
    
    // Observer to get duration when player is ready
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
            
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        // Initial check in case it's already ready
        if (exoPlayer.playbackState == androidx.media3.common.Player.STATE_READY) {
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
        }
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Map of index to bitmap for thumbnails
    val thumbnails = remember(videoUri) { mutableStateMapOf<Int, Bitmap>() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // MAX_PERFORMANCE Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MAX_PERFORMANCE Mode",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = maxPerformanceEnabled,
                onCheckedChange = { maxPerformanceEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        // Video Player
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // Hide controller for minimal UI
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Horizontal Thumbnail Scroll
        val totalSeconds = (durationMs / 1000).toInt().coerceAtLeast(0)
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
        ) {
            items(totalSeconds) { index ->
                val timeUs = index * 1000000L // 1 second intervals
                
                LaunchedEffect(videoUri, index) {
                    if (!thumbnails.containsKey(index)) {
                        val bitmap = ThumbnailExtractorV3.extractFrame(
                            context = context,
                            videoUri = videoUri,
                            timeUs = timeUs,
                            width = 160,
                            height = 90
                        )
                        if (bitmap != null) {
                            thumbnails[index] = bitmap
                        }
                    }
                }

                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "T ${index}s",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 160.dp, height = 90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                LayoutInflater.from(ctx).inflate(R.layout.item_timeline_thumbnail, null)
                            },
                            update = { view ->
                                val imageView = view.findViewById<ImageView>(R.id.thumbnailImage)
                                val bitmap = thumbnails[index]
                                if (bitmap != null) {
                                    imageView?.setImageBitmap(bitmap)
                                } else {
                                    imageView?.setImageResource(R.drawable.ic_launcher_foreground)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
