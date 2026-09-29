package idv.neo.ffmpeg.media.player.core.time

import idv.neo.ffmpeg.media.player.core.audio.AudioSink
import co.touchlab.kermit.Logger

/**
 * 媒體時鐘實作，參考 ExoPlayer 的 MediaClock 機制。
 * 管理播放器的當前媒體時間 (Absolute Media Timestamp)。
 */
class KmpMediaClock(
    private val clockSource: () -> Long = { getCurrentTimeNanos() }
) {
    private val logger = Logger.withTag("KmpMediaClock")

    private var _firstFrameTsUs: Long = -1L
    val firstFrameTsUs: Long get() = _firstFrameTsUs

    private var _initialAudioTsUs: Long = -1L
    private var systemTimeAnchorNanos: Long = -1L
    private var totalPausedTimeNanos: Long = 0L
    private var lastPauseTimestampNanos: Long = -1L
    private var isInitialized: Boolean = false
    private var isPlaybackStarted: Boolean = false

    private var audioTrackOffsetFrames: Long = 0L
    private var audioBytesWritten: Long = 0L

    private var lastAudioPositionUs: Long = 0L
    private var lastAudioPositionUpdateNanos: Long = -1L
    private var _playbackSpeed: Float = 1.0f

    fun setPlaybackSpeed(speed: Float) {
        this._playbackSpeed = speed
    }

    fun init(firstFrameTimestampUs: Long, currentAudioPositionFrames: Long = 0) {
        val now = clockSource()
        this._firstFrameTsUs = firstFrameTimestampUs
        this.systemTimeAnchorNanos = now
        this.totalPausedTimeNanos = 0L
        this.audioBytesWritten = 0L
        this.audioTrackOffsetFrames = currentAudioPositionFrames
        this.lastPauseTimestampNanos = if (!isPlaybackStarted) now else -1L
        this.lastAudioPositionUs = 0L
        this.lastAudioPositionUpdateNanos = now
        this.isInitialized = true
        logger.i { "MediaClock Initialized: FirstFrameTS=$firstFrameTimestampUs us, AudioOffset=$currentAudioPositionFrames" }
    }

    /** 設定寫入 AudioSink 的第一幀音訊時間戳 */
    fun setInitialAudioTimestamp(tsUs: Long) {
        if (_initialAudioTsUs == -1L) {
            _initialAudioTsUs = tsUs
            logger.i { "MediaClock: Initial Audio TS set to $tsUs us" }
        }
    }

    fun onPlaybackStateChanged(playing: Boolean) {
        val now = clockSource()
        if (playing) {
            if (lastPauseTimestampNanos != -1L) {
                totalPausedTimeNanos += (now - lastPauseTimestampNanos)
                lastPauseTimestampNanos = -1L
            }
        } else {
            if (lastPauseTimestampNanos == -1L) {
                lastPauseTimestampNanos = now
            }
        }
        isPlaybackStarted = playing
    }

    fun updateAudioProgress(bytesWritten: Long) {
        this.audioBytesWritten = bytesWritten
    }

    /**
     * 取得當前媒體時間 (微秒)。
     * 回傳值為絕對時間。
     */
    fun getPositionMicros(audioSink: AudioSink? = null): Long {
        if (!isInitialized) return 0L

        val nowNanos = if (lastPauseTimestampNanos != -1L) lastPauseTimestampNanos else clockSource()

        // 1. 優先使用音訊硬體時鐘
        if (audioSink != null && audioSink.isOpen && audioBytesWritten > 0 && _initialAudioTsUs != -1L) {
            val sampleRate = audioSink.sampleRate
            val currentFrames = audioSink.positionFrames
            val framePos = currentFrames - audioTrackOffsetFrames
            
            if (framePos >= 0) {
                val elapsedMediaUs = (framePos.toDouble() / sampleRate.toDouble() * 1_000_000.0 * _playbackSpeed).toLong()
                
                if (elapsedMediaUs != lastAudioPositionUs) {
                    lastAudioPositionUs = elapsedMediaUs
                    lastAudioPositionUpdateNanos = nowNanos
                }

                val elapsedSinceUpdateUs = if (lastAudioPositionUpdateNanos != -1L && lastPauseTimestampNanos == -1L) {
                    (nowNanos - lastAudioPositionUpdateNanos) / 1000L
                } else 0L
                
                val interpolatedMediaUs = elapsedMediaUs + (elapsedSinceUpdateUs * _playbackSpeed).toLong()
                val safeInterpolatedUs = minOf(interpolatedMediaUs, elapsedMediaUs + 50_000L)
                
                // 核心修復：確保音訊基準時鐘與視訊初始點對齊，防止跳變
                return _initialAudioTsUs + safeInterpolatedUs
            }
        }

        // 2. 回退至系統時鐘
        val elapsedNanos = nowNanos - systemTimeAnchorNanos - totalPausedTimeNanos
        val elapsedUs = (elapsedNanos / 1000L * _playbackSpeed).toLong()
        
        // 系統時鐘基準
        return _firstFrameTsUs + elapsedUs
    }

    fun reset() {
        isInitialized = false
        _firstFrameTsUs = -1L
        _initialAudioTsUs = -1L
        systemTimeAnchorNanos = -1L
        totalPausedTimeNanos = 0L
        lastPauseTimestampNanos = -1L
        audioBytesWritten = 0L
        audioTrackOffsetFrames = 0L
        isPlaybackStarted = false
        logger.i { "MediaClock Reset" }
    }

    val isStarted: Boolean get() = isInitialized
}
