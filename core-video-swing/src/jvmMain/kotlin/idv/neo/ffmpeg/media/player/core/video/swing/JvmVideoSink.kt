package idv.neo.ffmpeg.media.player.core.video.swing

import idv.neo.ffmpeg.media.player.core.video.VideoSink
import idv.neo.ffmpeg.media.player.core.video.VideoFrameOutput
import idv.neo.ffmpeg.media.player.core.video.VideoFrameData
import androidx.media3.common.ViewProvider
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.image.VolatileImage
import java.awt.Transparency
import javax.swing.JPanel
import co.touchlab.kermit.Logger
import org.bytedeco.javacv.Frame

/**
 * JVM 端的 VideoSink 實作 (Swing/AWT)。
 * 移至 :core-video-swing 模組，確保 :core 不再受 AWT 污染。
 */
class JvmVideoSink : VideoSink, ViewProvider {
    override val preferredPixelFormat: Int get() = 3 // AV_PIX_FMT_BGR24

    private val logger = Logger.withTag("JvmVideoSink")
    private val frameConverter = Java2DFrameConverter()
    
    /** 影像幀輸出管道 (保留回呼彈性) */
    var videoFrameOutput: VideoFrameOutput? = null

    private var width: Int = 0
    private var height: Int = 0
    private var volatileImage: VolatileImage? = null

    private val canvas = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val vi = volatileImage
            if (vi != null) {
                g.drawImage(vi, 0, 0, this.width, this.height, null)
            }
        }
    }

    override fun setVideoSize(width: Int, height: Int) {
        if (this.width != width || this.height != height) {
            this.width = width
            this.height = height
            releaseVolatileImage()
            logger.i { "Swing VideoSink size set to ${width}x${height}" }
        }
    }

    private fun createVolatileImage(w: Int, h: Int): VolatileImage? {
        val config = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration
        return try {
            config.createCompatibleVolatileImage(w, h, Transparency.OPAQUE)
        } catch (e: Exception) {
            logger.e(e) { "Failed to create VolatileImage" }
            null
        }
    }

    override fun render(frame: Any, timestampUs: Long) {
        onFrameAvailable(frame)
    }

    override fun onFrameAvailable(frame: Any) {
        // 1. 取得 VideoFrameData 介面
        val frameData = frame as? VideoFrameData ?: return
        val rawFrame = frameData.getRawFrame() as? Frame ?: return
        
        val bi = synchronized(frameConverter) {
            frameConverter.convert(rawFrame)
        }

        if (bi != null) {
            try {
                val bw = bi.width
                val bh = bi.height
                
                if (volatileImage == null || volatileImage?.width != bw || volatileImage?.height != bh) {
                    volatileImage = createVolatileImage(bw, bh)
                }

                val vi = volatileImage
                if (vi != null) {
                    val config = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .defaultScreenDevice.defaultConfiguration

                    if (vi.validate(config) == VolatileImage.IMAGE_INCOMPATIBLE) {
                        volatileImage = createVolatileImage(bw, bh)
                    }

                    val g = vi.createGraphics()
                    try {
                        g.drawImage(bi, 0, 0, null)
                    } finally {
                        g.dispose()
                    }

                    canvas.repaint()
                    // 這裡的 snapshot 是 BufferedImage，符合 VideoFrameOutput(Any?) 的簽名
                    videoFrameOutput?.onFrameAvailable(vi.snapshot)
                } else {
                    videoFrameOutput?.onFrameAvailable(bi)
                }
            } catch (e: Exception) {
                logger.e(e) { "Error during Swing video rendering" }
            }
        }
    }

    override fun getView(viewGroup: Any?): Any = canvas

    override fun flush() {
        videoFrameOutput?.onFrameAvailable(null)
    }

    private fun releaseVolatileImage() {
        volatileImage?.flush()
        volatileImage = null
    }

    override fun release() {
        videoFrameOutput?.onFrameAvailable(null)
        releaseVolatileImage()
        synchronized(frameConverter) {
            frameConverter.close()
        }
    }
}
