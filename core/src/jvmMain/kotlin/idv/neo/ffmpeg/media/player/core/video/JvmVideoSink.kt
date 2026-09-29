package idv.neo.ffmpeg.media.player.core.video

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import idv.neo.ffmpeg.media.player.core.buffer.JvmVideoFrame
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.awt.GraphicsEnvironment
import java.awt.image.VolatileImage
import java.awt.Transparency
import co.touchlab.kermit.Logger

/**
 * JVM 端的 VideoSink 實作。
 * 升級優化：使用 VolatileImage (VRAM) 替代 BufferedImage (System RAM) 的深拷貝，
 * 作為邁向 Skia/PixelBuffer 零拷貝技術的過渡步驟。
 */
class JvmVideoSink : VideoSink {
    private val logger = Logger.withTag("JvmVideoSink")
    private val frameConverter = Java2DFrameConverter()
    
    /** 供 UI 層讀取的影像狀態 (目前仍透過 BufferedImage 快照傳遞給 Compose) */
    val videoBitmap: MutableState<BufferedImage?> = mutableStateOf(null, neverEqualPolicy())

    private var width: Int = 0
    private var height: Int = 0
    private var volatileImage: VolatileImage? = null

    override fun setVideoSize(width: Int, height: Int) {
        if (this.width != width || this.height != height) {
            this.width = width
            this.height = height
            releaseVolatileImage()
            logger.i { "Video size set to ${width}x${height}, VolatileImage reset" }
        }
    }

    private fun createVolatileImage(w: Int, h: Int): VolatileImage? {
        val config = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration
        return try {
            val vi = config.createCompatibleVolatileImage(w, h, Transparency.OPAQUE)
            val caps = vi.capabilities
            logger.i { "VolatileImage created: ${w}x${h}, Accelerated: ${caps.isAccelerated}, TrueVolatile: ${caps.isTrueVolatile}" }
            vi
        } catch (e: Exception) {
            logger.e(e) { "Failed to create VolatileImage" }
            null
        }
    }

    override fun render(frame: Any, timestampUs: Long) {
        if (frame is JvmVideoFrame) {
            val bi = synchronized(frameConverter) {
                frameConverter.convert(frame.internalFrame)
            }

            if (bi != null) {
                try {
                    if (volatileImage == null || volatileImage?.width != bi.width || volatileImage?.height != bi.height) {
                        volatileImage = createVolatileImage(bi.width, bi.height)
                    }

                    val vi = volatileImage
                    if (vi != null) {
                        val config = GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .defaultScreenDevice.defaultConfiguration

                        val status = vi.validate(config)
                        if (status == VolatileImage.IMAGE_INCOMPATIBLE) {
                            logger.w { "VolatileImage incompatible, recreating..." }
                            volatileImage = createVolatileImage(bi.width, bi.height)
                        } else if (status == VolatileImage.IMAGE_RESTORED) {
                            logger.i { "VolatileImage restored" }
                        }

                        val g = vi.createGraphics()
                        try {
                            g.drawImage(bi, 0, 0, null)
                        } finally {
                            g.dispose()
                        }

                        // 目前 Step: 透過 snapshot 取得 BufferedImage 給 Compose 使用
                        // 雖然仍有拷貝，但繪製過程已可利用 AWT 硬體加速優化。
                        videoBitmap.value = vi.snapshot
                    } else {
                        // Fallback to traditional deep copy
                        val copy = BufferedImage(
                            bi.width,
                            bi.height,
                            if (bi.type == 0) BufferedImage.TYPE_3BYTE_BGR else bi.type
                        )
                        val g = copy.createGraphics()
                        g.drawImage(bi, 0, 0, null)
                        g.dispose()
                        videoBitmap.value = copy
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Error during video frame rendering with VolatileImage" }
                }
            }
        }
    }

    override fun onFrameAvailable(frame: Any) {}

    override fun flush() {
        videoBitmap.value = null
    }

    private fun releaseVolatileImage() {
        volatileImage?.flush()
        volatileImage = null
    }

    override fun release() {
        videoBitmap.value = null
        releaseVolatileImage()
        synchronized(frameConverter) {
            frameConverter.close()
        }
    }
}
