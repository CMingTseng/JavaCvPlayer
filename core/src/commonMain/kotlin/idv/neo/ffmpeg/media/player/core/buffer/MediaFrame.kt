package idv.neo.ffmpeg.media.player.core.buffer

/**
 * Represents a generic media frame (audio or video).
 */
interface MediaFrame {
    /** 影格顯示時間 (微秒) */
    val timestampUs: Long

    /** 釋放影格資源 */
    fun release()
}
