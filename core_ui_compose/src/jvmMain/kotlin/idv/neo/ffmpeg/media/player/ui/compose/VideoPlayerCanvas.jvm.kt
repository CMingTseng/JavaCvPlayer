package idv.neo.ffmpeg.media.player.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import idv.neo.ffmpeg.media.player.core.video.VideoSink
import idv.neo.ffmpeg.media.player.core.video.skia.SkiaVideoSink

@Composable
actual fun VideoPlayerCanvas(
    player: Player,
    modifier: Modifier,
    renderMode: VideoSink.RenderMode,
    contentScale: ContentScale
) {
    // 透過反射獲取 VideoSink (與 Android 端一致，確保介面解耦)
    val videoSink = remember(player) {
        try {
            val method = player.javaClass.getMethod("getPlayerVideoSink")
            method.invoke(player) as? VideoSink
        } catch (e: Exception) {
            null
        }
    }

    // 針對 Desktop 優先使用 Skia 路徑
    val skiaSink = videoSink as? SkiaVideoSink
    if (skiaSink != null) {
        // 監聽 Long 脈衝訊號。每次 render 發出新時間戳，都會觸發 pulse 變動進而引發重繪
        val pulse by skiaSink.frameSignal.collectAsState(initial = 0L)
        
        Canvas(modifier = modifier) {
            // 讀取 pulse 以確保 Compose 追蹤此狀態
            val trigger = pulse
            if (trigger != 0L) {
                drawIntoCanvas { canvas ->
                    skiaSink.drawFrame(canvas.nativeCanvas, size.width, size.height)
                }
            }
        }
    } else {
        // 備援方案：顯示背景或佔位符
    }
}
