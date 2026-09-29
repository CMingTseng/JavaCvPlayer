package idv.neo.ffmpeg.media.player.ui.compose

import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    // 保持通用解耦：透過反射或 VideoSink 存取
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
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        setZOrderMediaOverlay(true)
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                if (videoSink is AndroidSurfaceVideoSink) {
                                    videoSink.setSurface(holder.surface)
                                } else {
                                    setPlayerVideoSurface(player, holder.surface, this@apply)
                                }
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
                                videoSink?.setVideoSize(w, h)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
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
            val sink = videoSink as? AndroidBitmapVideoSink
            if (sink != null) {
                val bitmap by sink.bitmapState.collectAsState()

                bitmap?.let { b ->
                    if (b.isRecycled) return@let

                    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val imageBitmap = b.asImageBitmap()
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val videoWidth = b.width.toFloat()
                            val videoHeight = b.height.toFloat()

                            if (videoWidth > 0 && videoHeight > 0) {
                                val scale = (canvasWidth / videoWidth).coerceAtMost(canvasHeight / videoHeight)
                                val drawWidth = (videoWidth * scale).toInt()
                                val drawHeight = (videoHeight * scale).toInt()
                                val offsetX = ((canvasWidth - drawWidth) / 2).toInt()
                                val offsetY = ((canvasHeight - drawHeight) / 2).toInt()

                                drawImage(
                                    image = imageBitmap,
                                    dstSize = IntSize(drawWidth, drawHeight),
                                    dstOffset = IntOffset(offsetX, offsetY),
                                    filterQuality = FilterQuality.None // 高效採樣
                                )
                            }
                        }
                    }
                }
            }
        }
        else -> { /* 預留其他渲染路徑 */ }
    }
}

private fun setPlayerVideoSurface(player: Player, surface: Any?, surfaceView: SurfaceView) {
    try {
        val method = player.javaClass.getMethod("setVideoSurfaceView", SurfaceView::class.java)
        method.invoke(player, surfaceView)
        return
    } catch (_: Exception) {}

    try {
        val method = player.javaClass.getMethod("setVideoSurface", Surface::class.java)
        method.invoke(player, surface)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to attach surface to player", e)
    }
}

private fun clearPlayerVideoSurface(player: Player, surfaceView: SurfaceView) {
    try {
        val method = player.javaClass.getMethod("clearVideoSurfaceView", SurfaceView::class.java)
        method.invoke(player, surfaceView)
        return
    } catch (_: Exception) {}

    try {
        val method = player.javaClass.getMethod("clearVideoSurface", Surface::class.java)
        method.invoke(player, null)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to clear surface from player", e)
    }
}