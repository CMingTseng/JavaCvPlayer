package idv.neo.ffmpeg.media.player.core.video

/**
 * 影像影格處理器。
 * 允許在渲染前對影格進行變換（例如：旋轉、濾鏡、浮水印）。
 */
interface VideoFrameProcessor {
    /**
     * 處理影格。
     * @param frame 原始影格 (JVM: org.bytedeco.javacv.Frame)
     * @return 處理後的影格
     */
    fun process(frame: Any): Any

    /**
     * 重置處理器狀態。
     */
    fun reset()

    /**
     * 釋放資源。
     */
    fun release()
}
