package idv.neo.ffmpeg.media.player.core.video.compose

import idv.neo.ffmpeg.media.player.core.video.VideoSink
import idv.neo.ffmpeg.media.player.core.video.VideoFrameOutput
import idv.neo.ffmpeg.media.player.core.video.VideoFrameData
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import co.touchlab.kermit.Logger

/**
 * 專為 JVM Desktop Compose 設計的 VideoSink。
 * 使用 Java2DFrameConverter 將 Frame 轉換為 BufferedImage。
 */
class JvmBufferedImageVideoSink : VideoSink {
    override val preferredPixelFormat: Int get() = 3 // AV_PIX_FMT_BGR24

    private val logger = Logger.withTag("JvmBufferedImageVideoSink")
    private val frameConverter = Java2DFrameConverter()
    
    /** 影像幀輸出管道，通常由 UI 層 (如 VideoCanvas) 注入 */
    var videoFrameOutput: VideoFrameOutput? = null

    override fun setVideoSize(width: Int, height: Int) {
        logger.i { "Compose JVM VideoSink size set to ${width}x${height}" }
    }

    override fun render(frame: Any, timestampUs: Long) {
        onFrameAvailable(frame)
    }

    override fun onFrameAvailable(frame: Any) {
        val frameData = frame as? VideoFrameData ?: return
        val rawFrame = frameData.getRawFrame() as? Frame ?: return
        
        // 轉換為 BufferedImage
        val bi = synchronized(frameConverter) {
            frameConverter.convert(rawFrame)
        }

        if (bi != null) {
            // 這裡傳出 BufferedImage，由 ComposeVideoFrameOutput 接收
            videoFrameOutput?.onFrameAvailable(bi)
        }
    }

    override fun flush() {
        videoFrameOutput?.onFrameAvailable(null)
    }

    override fun release() {
        videoFrameOutput?.onFrameAvailable(null)
        synchronized(frameConverter) {
            frameConverter.close()
        }
    }
}
