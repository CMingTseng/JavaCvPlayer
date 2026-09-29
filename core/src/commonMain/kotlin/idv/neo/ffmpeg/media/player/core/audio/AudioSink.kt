package idv.neo.ffmpeg.media.player.core.audio

/**
 * 音訊輸出接收器介面。
 */
interface AudioSink {
    /** 是否已啟動 */
    val isRunning: Boolean
    /** 是否已開啟 */
    val isOpen: Boolean
    /** 目前播放位置 (Frame 數) */
    val positionFrames: Long
    /** 取樣率 */
    val sampleRate: Int

    /** 獲取目前播放位置 (微秒) */
    fun getPlaybackHeadPositionUs(): Long

    /**
     * 寫入音頻數據
     * @param buffer 支援音頻採樣的 ShortArray較符合多數平台底層
     * @param offset 起始位置
     * @param size 數據長度
     * @return 實際寫入的影格數 (frames)
     */
    fun write(buffer: ShortArray, offset: Int, size: Int): Int

    /** 設置播放速度 (1.0f 為正常速度) */
    fun setPlaybackSpeed(speed: Float)

    /** 設置音量 (0.0f ~ 1.0f) */
    fun setVolume(volume: Float)

    /** 初始化設定 */
    fun setup(sampleRate: Int, channels: Int)

    /** 暫停音訊輸出 */
    fun pause()

    /** 恢復音訊輸出 */
    fun play()

    /** 清空緩衝並停止 */
    fun stop()

    /** 清空緩衝 */
    fun flush()

    /** 釋放資源 */
    fun release()
}