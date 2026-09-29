package idv.neo.ffmpeg.media.player.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import idv.neo.ffmpeg.media.player.core.JvmJavaCvPlayer
import idv.neo.ffmpeg.media.player.core.video.compose.JvmBufferedImageVideoSink
import kotlin.math.max
import kotlin.math.min

/**
 * JVM 端的影像畫布，依賴於 JvmJavaCvPlayer 與 BufferedImage。
 */
@Composable
fun VideoCanvas(
    player: JvmJavaCvPlayer,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val jvmSink = player.getPlayerVideoSink() as? JvmBufferedImageVideoSink
    val composeVideoOutput = remember(player) { ComposeVideoFrameOutput() }

    DisposableEffect(player, jvmSink, composeVideoOutput) {
        if (jvmSink != null) {
            val originalOutput = jvmSink.videoFrameOutput
            jvmSink.videoFrameOutput = composeVideoOutput
            onDispose {
                if (jvmSink.videoFrameOutput === composeVideoOutput) {
                    jvmSink.videoFrameOutput = originalOutput
                }
            }
        } else {
            onDispose { }
        }
    }

    val frameWrapper by composeVideoOutput.videoFrame
    val resizeMode = player.resizeMode

    Canvas(modifier = modifier) {
        frameWrapper.image?.let { bImage ->
            val imageBitmap = bImage.toComposeImageBitmap()
            val canvasWidth = size.width
            val canvasHeight = size.height
            val imageWidth = imageBitmap.width.toFloat()
            val imageHeight = imageBitmap.height.toFloat()

            if (imageWidth <= 0 || imageHeight <= 0) return@let

            when (resizeMode) {
                1 -> { // FILL
                    drawImage(
                        image = imageBitmap,
                        dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt())
                    )
                }
                2 -> { // ZOOM
                    val scale = max(canvasWidth / imageWidth, canvasHeight / imageHeight)
                    val drawWidth = imageWidth * scale
                    val drawHeight = imageHeight * scale
                    val offsetX = (canvasWidth - drawWidth) / 2
                    val offsetY = (canvasHeight - drawHeight) / 2
                    drawImage(
                        image = imageBitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                        dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
                    )
                }
                else -> { // 0: FIT
                    val scale = min(canvasWidth / imageWidth, canvasHeight / imageHeight)
                    val drawWidth = imageWidth * scale
                    val drawHeight = imageHeight * scale
                    val offsetX = (canvasWidth - drawWidth) / 2
                    val offsetY = (canvasHeight - drawHeight) / 2
                    drawImage(
                        image = imageBitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                        dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
                    )
                }
            }
        }
    }
}
