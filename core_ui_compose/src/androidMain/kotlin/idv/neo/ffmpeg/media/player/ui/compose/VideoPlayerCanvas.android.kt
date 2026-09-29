package idv.neo.ffmpeg.media.player.ui.compose

import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import idv.neo.ffmpeg.media.player.core.video.VideoSink
import idv.neo.ffmpeg.media.player.core.video.android.AndroidBitmapVideoSink
import idv.neo.ffmpeg.media.player.core.video.android.AndroidSurfaceVideoSink

private const val TAG = "VideoPlayerCanvas"

@Composable
actual fun VideoPlayerCanvas(
    player: Player,
    modifier: Modifier,
    renderMode: VideoSink.RenderMode,
    contentScale: ContentScale
) {
    // 透過反射或型別檢查獲取 VideoSink，這是「偷天換日」的起點
    val videoSink = remember(player) {
        try {
            val method = player.javaClass.getMethod("getPlayerVideoSink")
            method.invoke(player) as? VideoSink
        } catch (e: Exception) {
            null
        }
    }

    when (renderMode) {
        VideoSink.RenderMode.SURFACE -> {
            // Android 核心路徑：透過 SurfaceView 承接經過處理的影格
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        setZOrderMediaOverlay(true)
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                Log.d(TAG, "surfaceCreated: videoSink=$videoSink, player=${player.javaClass.name}")
                                if (videoSink is AndroidSurfaceVideoSink) {
                                    videoSink.setSurface(holder.surface)
                                } else {
                                    setPlayerVideoSurface(player, holder.surface, this@apply)
                                }
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
                                Log.d(TAG, "surfaceChanged: w=$w, h=$h")
                                videoSink?.setVideoSize(w, h)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                Log.d(TAG, "surfaceDestroyed")
                                if (videoSink is AndroidSurfaceVideoSink) {
                                    videoSink.setSurface(null)
                                } else {
                                    clearPlayerVideoSurface(player, this@apply)
                                }
                            }
                        })
                    }
                },
                update = { surfaceView ->
                    if (videoSink == null) {
                        setPlayerVideoSurface(player, surfaceView.holder.surface, surfaceView)
                    }
                },
                modifier = modifier
            )
        }
        VideoSink.RenderMode.SKIA -> {
            // 備援/特效路徑：使用 Android Bitmap 配合 Compose Image
            val sink = videoSink as? AndroidBitmapVideoSink
            if (sink != null) {
                val bitmap by sink.bitmapState.collectAsState()
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Video Frame",
                        modifier = modifier,
                        contentScale = contentScale
                    )
                }
            }
        }
        else -> { /* 預留 AHardwareBuffer / GLES Texture 路徑 */ }
    }
}

private fun setPlayerVideoSurface(player: Player, surface: Any?, surfaceView: SurfaceView) {
    try {
        val method = player.javaClass.getMethod("setVideoSurfaceView", SurfaceView::class.java)
        method.invoke(player, surfaceView)
        Log.i(TAG, "Successfully attached ExoPlayer setVideoSurfaceView")
        return
    } catch (e: Exception) {
        // Fallback
    }

    try {
        val method = player.javaClass.getMethod("setVideoSurface", Surface::class.java)
        method.invoke(player, surface)
        Log.i(TAG, "Successfully attached ExoPlayer setVideoSurface")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to attach surface to player", e)
    }
}

private fun clearPlayerVideoSurface(player: Player, surfaceView: SurfaceView) {
    try {
        val method = player.javaClass.getMethod("clearVideoSurfaceView", SurfaceView::class.java)
        method.invoke(player, surfaceView)
        Log.i(TAG, "Successfully cleared ExoPlayer videoSurfaceView")
        return
    } catch (e: Exception) {
        // Fallback
    }

    try {
        val method = player.javaClass.getMethod("clearVideoSurface", Surface::class.java)
        method.invoke(player, null)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to clear surface from player", e)
    }
}
