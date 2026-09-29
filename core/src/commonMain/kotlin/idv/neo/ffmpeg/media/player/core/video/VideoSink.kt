package idv.neo.ffmpeg.media.player.core.video

/**
 * 跨平台影像渲染接收器介面。
 * 模擬 Media3 exoplayer 模組中的 VideoSink，但針對 JavaCV 進行擴充。
 * 在 Android 端可封裝 Surface，在 JVM 端可封裝 BufferedImage 寫入或 Canvas 繪製
 */
interface VideoSink {
    /** 設置輸出的寬高。 */
    fun setVideoSize(width: Int, height: Int)

    /** 
     * 渲染影格。 
     * @param frame 影格物件 (JVM: org.bytedeco.javacv.Frame)
     * @param timestampUs 呈現時間戳
     */
    fun render(frame: Any, timestampUs: Long)

    /**
     * 當影格可用時呼叫。
     * @param frame 影格物件
     */
    fun onFrameAvailable(frame: Any)

    /**
     * 渲染模式。
     */
    enum class RenderMode {
        SURFACE, // Android: 性能優先 (Hardware Canvas)
        SKIA,    // Desktop/Android: 靈活性優先 (Bitmap/ImageBitmap)
        TEXTURE  // Android: GLES 紋理
    }

    /**
     * 該 Sink 偏好的 FFmpeg 像素格式 (AV_PIX_FMT_*)。
     */
    val preferredPixelFormat: Int get() = 3 // 預設 BGR24

    fun flush()
    fun release()
}
