package idv.neo.ffmpeg.media.player.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import idv.neo.ffmpeg.media.player.core.video.VideoSink

/**
 * 跨平台影片渲染畫布。
 * 根據不同平台與渲染模式自動切換底層實作。
 * 
 * @param player 播放器實例
 * @param modifier 佈局修飾符
 * @param renderMode 渲染模式 (Surface, Skia 等)
 * @param contentScale 縮放模式
 */
@Composable
expect fun VideoPlayerCanvas(
    player: Player,
    modifier: Modifier = Modifier,
    renderMode: VideoSink.RenderMode = VideoSink.RenderMode.SURFACE,
    contentScale: ContentScale = ContentScale.Fit
)
