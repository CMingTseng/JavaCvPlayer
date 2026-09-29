package idv.neo.ffmpeg.media.player.core.video

/**
 * 影像幀輸出介面 (KMP Common)。
 * 用於解耦播放核心與具體的 UI 框架。
 * 核心層只負責將處理後的影格物件丟出，具體類型由實作者自行決定。
 */
fun interface VideoFrameOutput {
    /**
     * 當影格可用時呼叫。
     * @param frame 影格物件 (JVM: BufferedImage, Android: Bitmap, etc.)
     */
    fun onFrameAvailable(frame: Any?)
}
