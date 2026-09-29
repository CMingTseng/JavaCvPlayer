package idv.neo.ffmpeg.media.player.core.buffer

/**
 * 影片影格封裝 (通用介面)
 */
interface VideoFrame {
    /** 影格顯示時間 (微秒) */
    val timestampUs: Long
    
    /** 釋放影格資源 */
    fun release()
}
