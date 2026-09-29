package idv.neo.ffmpeg.media.player.core.video.skia

import idv.neo.ffmpeg.media.player.core.video.VideoFrameData
import idv.neo.ffmpeg.media.player.core.video.VideoSink
import org.jetbrains.skia.*
import co.touchlab.kermit.Logger
import org.bytedeco.ffmpeg.global.avutil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Skia 渲染接收器 (Desktop 專用)。
 * 透過發送 Long 脈衝訊號強制觸發 Compose 重繪，解決同一個 Bitmap 參照不觸發更新的問題。
 */
class SkiaVideoSink : VideoSink {
    private val logger = Logger.withTag("SkiaVideoSink")
    private val lock = Any()
    
    // 使用 StateFlow 發送脈衝訊號 (時間戳)。只要值變動，Compose 就會觸發 Recomposition
    private val _frameSignal = MutableStateFlow(0L)
    val frameSignal = _frameSignal.asStateFlow()
    
    private var videoWidth = 0
    private var videoHeight = 0
    
    /** 當前可供讀取的 Bitmap */
    var currentBitmap: Bitmap? = null
        private set

    private var pixelBuffer: ByteArray? = null

    override fun setVideoSize(width: Int, height: Int) {
        synchronized(lock) {
            if (this.videoWidth != width || this.videoHeight != height) {
                this.videoWidth = width
                this.videoHeight = height
                logger.i { "SkiaVideoSink size set to: ${width}x${height}" }
                
                currentBitmap?.close()
                val newBitmap = Bitmap()
                newBitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
                currentBitmap = newBitmap
                pixelBuffer = ByteArray(width * height * 4)
                
                _frameSignal.value = -1L // 初始重置
            }
        }
    }

    override fun render(frame: Any, timestampUs: Long) {
        val frameData = frame as? VideoFrameData ?: return
        val srcBuffer = frameData.getByteBuffer() ?: return

        synchronized(lock) {
            val bitmap = currentBitmap ?: return
            val dstBuffer = pixelBuffer ?: return

            try {
                val srcStride = frameData.stride
                val dstStride = videoWidth * 4

                // 修正：必須考慮 Stride 進行行拷貝，否則 4K 或非對齊寬度會產生斜影或崩潰
                if (srcStride == dstStride) {
                    srcBuffer.get(dstBuffer)
                } else {
                    for (y in 0 until videoHeight) {
                        srcBuffer.position(y * srcStride)
                        val length = dstStride.coerceAtMost(srcBuffer.remaining())
                        srcBuffer.get(dstBuffer, y * dstStride, length)
                    }
                }
                srcBuffer.rewind()

                val imageInfo = ImageInfo(
                    width = videoWidth,
                    height = videoHeight,
                    colorType = ColorType.RGBA_8888,
                    alphaType = ColorAlphaType.PREMUL
                )

                // 將 ByteArray 資料裝載進 Bitmap
                bitmap.installPixels(imageInfo, dstBuffer, dstStride)
                bitmap.notifyPixelsChanged()
                
                // 更新脈衝，觸發 UI 重繪
                _frameSignal.value = System.nanoTime()
            } catch (e: Exception) {
                logger.e(e) { "Skia render failed" }
            }
        }
    }

    /**
     * 線程安全地將當前畫面繪製至指定 Skia Canvas 上。
     */
    fun drawFrame(canvas: Canvas, dstWidth: Float, dstHeight: Float) {
        synchronized(lock) {
            val bitmap = currentBitmap ?: return
            if (bitmap.isClosed || bitmap.width <= 0 || bitmap.height <= 0) return
            try {
                val image = Image.makeFromBitmap(bitmap)
                try {
                    canvas.drawImageRect(
                        image,
                        Rect.makeWH(bitmap.width.toFloat(), bitmap.height.toFloat()),
                        Rect.makeWH(dstWidth, dstHeight),
                        SamplingMode.LINEAR,
                        null,
                        true
                    )
                } finally {
                    image.close()
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to draw frame in SkiaVideoSink" }
            }
        }
    }

    override fun onFrameAvailable(frame: Any) {}

    override val preferredPixelFormat: Int
        get() = avutil.AV_PIX_FMT_RGBA

    override fun flush() {}

    override fun release() {
        synchronized(lock) {
            currentBitmap?.close()
            currentBitmap = null
            pixelBuffer = null
        }
    }
}
