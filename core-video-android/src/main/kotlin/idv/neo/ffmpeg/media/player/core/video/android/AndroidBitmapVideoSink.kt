package idv.neo.ffmpeg.media.player.core.video.android

import android.graphics.Bitmap
import idv.neo.ffmpeg.media.player.core.video.VideoFrameData
import idv.neo.ffmpeg.media.player.core.video.VideoSink
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android Bitmap 渲染接收器。
 * 將影格寫入 android.graphics.Bitmap，供 Compose Image 使用。
 * 這是在 Android 端實現「靈活性優先」的路徑。
 */
class AndroidBitmapVideoSink : VideoSink {
    private val logger = Logger.withTag("AndroidBitmapVideoSink")
    private var videoWidth = 0
    private var videoHeight = 0
    
    // 提供給 Compose UI 觀察的 StateFlow
    private val _bitmapState = MutableStateFlow<Bitmap?>(null)
    val bitmapState = _bitmapState.asStateFlow()

    override fun setVideoSize(width: Int, height: Int) {
        if (videoWidth != width || videoHeight != height) {
            videoWidth = width
            videoHeight = height
            // 預先配置 Bitmap
            _bitmapState.value = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            logger.i { "BitmapVideoSink: Allocated ${width}x${height} bitmap" }
        }
    }

    override fun render(frame: Any, timestampUs: Long) {
        val frameData = frame as? VideoFrameData ?: return
        val pixelBuffer = frameData.getByteBuffer() ?: return
        val currentBitmap = _bitmapState.value ?: return

        try {
            pixelBuffer.rewind()
            // 極速 memcpy 到 Android Bitmap
            currentBitmap.copyPixelsFromBuffer(pixelBuffer)
            
            // 觸發 Compose 重新繪製 (如果使用的是同一個 Bitmap 物件，可能需要手動觸發通知)
            // 在某些情況下，我們可能需要輪替兩個 Bitmap (Double Buffering)
        } catch (e: Exception) {
            logger.e(e) { "Bitmap render failed" }
        }
    }

    override fun onFrameAvailable(frame: Any) {}

    override val preferredPixelFormat: Int
        get() = 26 // AV_PIX_FMT_RGBA

    override fun flush() {}

    override fun release() {
        _bitmapState.value?.recycle()
        _bitmapState.value = null
    }
}
