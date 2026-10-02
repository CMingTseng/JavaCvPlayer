package idv.neo.ffmpeg.media.player.core.video.skia

import idv.neo.ffmpeg.media.player.core.video.VideoFrameData
import idv.neo.ffmpeg.media.player.core.video.VideoSink
import org.jetbrains.skia.*
import co.touchlab.kermit.Logger
import org.bytedeco.ffmpeg.global.avutil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 實驗性質：透過 Skia BackendTexture 與 OpenGL Texture 進行 GPU 零拷貝渲染的 VideoSink (暫時建立規畫，尚未啟用)。
 */
class SkiaGpuVideoSink : VideoSink {
    private val logger = Logger.withTag("SkiaGpuVideoSink")
    private val lock = Any()

    private val _frameSignal = MutableStateFlow(0L)
    val frameSignal = _frameSignal.asStateFlow()

    private var videoWidth = 0
    private var videoHeight = 0

    private var cachedImage: Image? = null
    private var directContext: DirectContext? = null

    /**
     * 設定 Skia DirectContext (OpenGL Context)
     */
    fun setDirectContext(context: DirectContext) {
        synchronized(lock) {
            this.directContext = context
        }
    }

    override fun setVideoSize(width: Int, height: Int) {
        synchronized(lock) {
            if (this.videoWidth != width || this.videoHeight != height) {
                this.videoWidth = width
                this.videoHeight = height
                logger.i { "SkiaGpuVideoSink size set to: ${width}x${height}" }
                cachedImage?.close()
                cachedImage = null
                _frameSignal.value = -1L
            }
        }
    }

    override fun render(frame: Any, timestampUs: Long) {
        // 示範：在此處可以將 Native Pointers / Direct ByteBuffer 上傳至 OpenGL Texture ID
        // 然後利用 BackendTexture 建立 Skia Image
        synchronized(lock) {
            val context = directContext ?: return
            val frameData = frame as? VideoFrameData ?: return
            val byteBuffer = frameData.getByteBuffer() ?: return

            try {
                // 範例程式碼邏輯：
                // val glInfo = GlTextureInfo(0x0DE1, glTextureId, 0x8058) // GL_TEXTURE_2D, GL_RGBA8
                // val backendTexture = BackendTexture.makeGL(videoWidth, videoHeight, false, glInfo)
                // cachedImage = Image.makeFromTexture(context, backendTexture, SurfaceOrigin.TOP_LEFT, ColorType.RGBA_8888, ColorAlphaType.PREMUL)

                _frameSignal.value = System.nanoTime()
            } catch (e: Exception) {
                logger.e(e) { "SkiaGpuVideoSink render failed" }
            }
        }
    }

    fun drawFrame(canvas: Canvas, dstWidth: Float, dstHeight: Float) {
        synchronized(lock) {
            val image = cachedImage ?: return
            if (image.isClosed || image.width <= 0 || image.height <= 0) return
            try {
                canvas.drawImageRect(
                    image,
                    Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                    Rect.makeWH(dstWidth, dstHeight),
                    SamplingMode.LINEAR,
                    null,
                    true
                )
            } catch (e: Exception) {
                logger.e(e) { "Failed to draw frame in SkiaGpuVideoSink" }
            }
        }
    }

    override fun onFrameAvailable(frame: Any) {}

    override val preferredPixelFormat: Int
        get() = avutil.AV_PIX_FMT_RGBA

    override fun flush() {}

    override fun release() {
        synchronized(lock) {
            cachedImage?.close()
            cachedImage = null
            directContext = null
        }
    }
}
