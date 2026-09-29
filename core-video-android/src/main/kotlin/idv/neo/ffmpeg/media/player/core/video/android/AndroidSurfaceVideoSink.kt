package idv.neo.ffmpeg.media.player.core.video.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.Surface
import idv.neo.ffmpeg.media.player.core.video.VideoFrameData
import idv.neo.ffmpeg.media.player.core.video.VideoSink
import co.touchlab.kermit.Logger
import java.nio.ByteBuffer

/**
 * Android Surface 渲染接收器。
 * 透過 Surface.lockCanvas() 承接 JavaCV 影格像素。
 * 支援「偷天換日」模式：在 CPU 端完成 OpenCV 特效後直接壓入 Surface。
 */
class AndroidSurfaceVideoSink : VideoSink {
    private val logger = Logger.withTag("AndroidSurfaceVideoSink")
    private var surface: Surface? = null
    private var bufferBitmap: Bitmap? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private val drawRect = Rect()

    /**
     * 設定輸出 Surface。
     */
    fun setSurface(surface: Surface?) {
        this.surface = surface
    }

    override fun setVideoSize(width: Int, height: Int) {
        if (this.videoWidth != width || this.videoHeight != height) {
            this.videoWidth = width
            this.videoHeight = height
            
            // 預先配置 Bitmap 以重複使用，避免在 render 循環中分配內存
            // 配置為 ARGB_8888 以匹配 AV_PIX_FMT_RGBA
            bufferBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            logger.i { "Video size changed: ${width}x${height}, bitmap reallocated." }
        }
    }

    override fun render(frame: Any, timestampUs: Long) {
        val s = surface ?: return
        if (!s.isValid) return

        // 這裡預期接收的是已經過 VerifyError 隔離的 VideoFrameData
        val frameData = frame as? VideoFrameData ?: return
        val pixelBuffer = frameData.getByteBuffer() ?: return

        try {
            val bitmap = bufferBitmap ?: return
            
            // 1. 將 JavaCV 的像素數據 (RGBA) 快速拷貝到 Android Bitmap
            pixelBuffer.rewind()
            bitmap.copyPixelsFromBuffer(pixelBuffer)

            // 2. 鎖定 Surface Canvas 進行繪製
            // 使用 lockCanvas(null) 以獲得最佳相容性
            val canvas: Canvas? = s.lockCanvas(null)
            if (canvas != null) {
                try {
                    // 計算縮放比例以符合 Surface 大小 (Center Fit)
                    updateDrawRect(canvas.width, canvas.height)
                    canvas.drawBitmap(bitmap, null, drawRect, null)
                } finally {
                    s.unlockCanvasAndPost(canvas)
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Surface render failed" }
        }
    }

    override fun onFrameAvailable(frame: Any) {
        // 在目前的 JvmJavaCvPlayer 架構中，渲染邏輯由播放器時鐘觸發呼叫 render()
    }

    /**
     * 偏好 RGBA 格式 (AV_PIX_FMT_RGBA = 26 or symbolic)
     * 在 FFmpeg 中，RGBA 通常對應 Android 的 ARGB_8888
     */
    override val preferredPixelFormat: Int
        get() = 26 // AV_PIX_FMT_RGBA

    override fun flush() {
        // 清理殘留影格邏輯
    }

    override fun release() {
        surface = null
        bufferBitmap?.recycle()
        bufferBitmap = null
        logger.i { "AndroidSurfaceVideoSink released." }
    }

    private fun updateDrawRect(canvasWidth: Int, canvasHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0) return
        
        val screenRatio = canvasWidth.toFloat() / canvasHeight
        val videoRatio = videoWidth.toFloat() / videoHeight

        if (videoRatio > screenRatio) {
            val h = (canvasWidth / videoRatio).toInt()
            val top = (canvasHeight - h) / 2
            drawRect.set(0, top, canvasWidth, top + h)
        } else {
            val w = (canvasHeight * videoRatio).toInt()
            val left = (canvasWidth - w) / 2
            drawRect.set(left, 0, left + w, canvasHeight)
        }
    }
}
