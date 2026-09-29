package idv.neo.ffmpeg.media.player.core.video

import java.awt.image.BufferedImage

/**
 * 影格包裝器，用於強制觸發 Compose 重繪。
 */
data class VideoFrame(
    val image: BufferedImage?,
    val version: Long
)