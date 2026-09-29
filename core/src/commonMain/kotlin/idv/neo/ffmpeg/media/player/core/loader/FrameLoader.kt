package idv.neo.ffmpeg.media.player.core.loader

/**
 * 媒體加載器介面，負責從來源讀取資料並填充佇列。
 */
interface FrameLoader {
    fun start(url: String, onMetadataReady: (FrameMetadata) -> Unit)
    fun stop()
    fun seekTo(positionUs: Long)
    fun release()

    /** 是否已讀取到流的末尾 */
    val isEndOfStream: Boolean

    /** 取得當前媒體的 FrameMetadata */
    fun getMetadata(): FrameMetadata
}
