package idv.neo.ffmpeg.media.player.core

import idv.neo.ffmpeg.media.player.core.audio.AudioSink
import idv.neo.ffmpeg.media.player.core.video.VideoSink
import idv.neo.ffmpeg.media.player.core.video.VideoFrameOutput
import idv.neo.ffmpeg.media.player.core.buffer.DefaultMediaFrameQueue
import idv.neo.ffmpeg.media.player.core.buffer.JvmAudioFrame
import idv.neo.ffmpeg.media.player.core.buffer.JvmVideoFrame
import idv.neo.ffmpeg.media.player.core.loader.FFmpegFrameLoader
import kotlinx.coroutines.*
import co.touchlab.kermit.Logger
import androidx.media3.common.Player
import kotlin.coroutines.coroutineContext

typealias JvmJavaCvPlayer = JavaCvPlayer

/**
 * 通用的 JavaCvPlayer 實作。
 * 僅依賴 [AudioSink] 與 [VideoSink] 介面，適用於 JVM 桌面端（Swing, JavaFX, Compose AWT/Skia）以及 Android 端。
 */
class JavaCvPlayer private constructor(
    vQueue: DefaultMediaFrameQueue<JvmVideoFrame>,
    aQueue: DefaultMediaFrameQueue<JvmAudioFrame>,
    aSink: AudioSink,
    vSink: VideoSink,
    loader: FFmpegFrameLoader
) : BaseJavaCvPlayer<JvmVideoFrame, JvmAudioFrame>(vQueue, aQueue, aSink, vSink, loader) {
    companion object {
        @JvmOverloads
        fun create(
            videoSink: VideoSink,
            audioSink: AudioSink,
            options: Map<String, String> = emptyMap()
        ): JavaCvPlayer {
            return Builder()
                .setVideoSink(videoSink)
                .setAudioSink(audioSink)
                .setFFmpegOptions(options)
                .build()
        }
    }

    /**
     * 播放器組裝器。
     */
    class Builder {
        private var videoSink: VideoSink? = null
        private var audioSink: AudioSink? = null
        private var maxVideoBuffer: Int = 240
        private var maxAudioBuffer: Int = 480
        private var preferredPixelFormat: Int? = null
        private val ffmpegOptions = mutableMapOf<String, String>()

        fun setVideoSink(videoSink: VideoSink) = apply { this.videoSink = videoSink }
        fun setAudioSink(audioSink: AudioSink) = apply { this.audioSink = audioSink }

        /** 設定影片緩衝區最大影格數 (預設 240) */
        fun setMaxVideoBuffer(size: Int) = apply { this.maxVideoBuffer = size }

        /** 設定音訊緩衝區最大影格數 (預設 480) */
        fun setMaxAudioBuffer(size: Int) = apply { this.maxAudioBuffer = size }

        /** 設定解碼輸出的像素格式 (若未設定，則使用 VideoSink 的預設值) */
        fun setPreferredPixelFormat(format: Int) = apply { this.preferredPixelFormat = format }

        /** 設定 FFmpeg 選項 (如 probesize, analyzeduration, rtsp_transport 等) */
        fun setFFmpegOptions(options: Map<String, String>) = apply {
            this.ffmpegOptions.putAll(options)
        }

        /** 新增單一 FFmpeg 選項 */
        fun addFFmpegOption(key: String, value: String) = apply {
            this.ffmpegOptions[key] = value
        }

        fun build(): JavaCvPlayer {
            val vs = videoSink ?: throw IllegalStateException("VideoSink must be set")
            val asink = audioSink ?: throw IllegalStateException("AudioSink must be set")

            val vQueue = DefaultMediaFrameQueue<JvmVideoFrame>(maxVideoBuffer)
            val aQueue = DefaultMediaFrameQueue<JvmAudioFrame>(maxAudioBuffer)
            val loader = FFmpegFrameLoader(vQueue, aQueue)

            // 設定自定義 FFmpeg 選項
            loader.setFFmpegOptions(ffmpegOptions)

            // 優先使用 Builder 設定的格式，否則由 Sink 決定
            loader.setPreferredPixelFormat(preferredPixelFormat ?: vs.preferredPixelFormat)

            return JavaCvPlayer(vQueue, aQueue, asink, vs, loader)
        }
    }

    private val logger = Logger.withTag("JavaCvPlayer")
    private var totalAudioFramesWritten = 0L
    private val videoCatchUpDropThresholdUs = 60_000L
    private var loopIteration = 0L
    private var reusableAudioBuffer: ShortArray? = null

    private var videoJob: Job? = null
    private var audioJob: Job? = null

    var videoFrameOutput: VideoFrameOutput? = null

    fun getPlayerVideoSink(): VideoSink = videoSink

    override fun onReset() {
        videoFrameOutput?.onFrameAvailable(null)
        totalAudioFramesWritten = 0L
        loopIteration = 0L
        stopRenderLoops()
    }

    override suspend fun doSomeWork() {
        if (playbackState == Player.STATE_READY && playWhenReady) {
            ensureRenderLoopsStarted()
            if (loader.getMetadata().hasAudio && !audioSink.isRunning) {
                audioSink.play()
            }
        } else {
            if (!playWhenReady || playbackState != Player.STATE_READY) {
                stopRenderLoops()
            }
            if (audioSink.isRunning) {
                audioSink.pause()
            }
        }
        delay(30)
    }

    private fun ensureRenderLoopsStarted() {
        if (videoJob == null || videoJob?.isActive == false) {
            videoJob = mainScope.launch(Dispatchers.Default) { videoRenderLoop() }
        }
        if (audioJob == null || audioJob?.isActive == false) {
            audioJob = mainScope.launch(Dispatchers.Default) { audioRenderLoop() }
        }
    }

    private fun stopRenderLoops() {
        videoJob?.cancel()
        audioJob?.cancel()
        videoJob = null
        audioJob = null
    }

    private suspend fun audioRenderLoop() {
        try {
            val clock = getClock()
            val audioBufferLimitUs = 200_000L
            while (coroutineContext.isActive) {
                val frame = audioQueue.peek()
                if (frame == null) {
                    delay(10)
                    continue
                }
                val speed = playbackParameters.speed
                val currentClockUs = clock.getPositionMicros(audioSink)
                val audioRelTs = frame.timestampUs
                val shouldWrite = if (!clock.isStarted) true
                else audioRelTs <= currentClockUs + (audioBufferLimitUs * speed).toLong()

                if (shouldWrite) {
                    val dequeuedFrame = audioQueue.dequeue() ?: continue
                    writeAudioToSink(dequeuedFrame)
                } else {
                    delay(20)
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) logger.e(e) { "audioRenderLoop crashed" }
        }
    }

    private suspend fun videoRenderLoop() {
        try {
            val clock = getClock()
            while (coroutineContext.isActive) {
                loopIteration++
                val frame = videoQueue.dequeue() ?: run {
                    delay(10)
                    continue
                }
                try {
                    if (!clock.isStarted) {
                        clock.init(frame.timestampUs, audioSink.positionFrames)
                    }
                    val videoPtsUs = frame.timestampUs
                    val currentClockUs = clock.getPositionMicros(audioSink)
                    val diffUs = videoPtsUs - currentClockUs

                    if (diffUs > 5_000) {
                        val waitMs = (diffUs / 1000).coerceAtMost(50)
                        if (waitMs > 1) delay(waitMs)
                    }

                    val currentClockUsAfterWait = clock.getPositionMicros(audioSink)
                    val finalDiffUs = videoPtsUs - currentClockUsAfterWait

                    if (finalDiffUs < -videoCatchUpDropThresholdUs) {
                        // Drop frame
                    } else {
                        videoSink.render(frame, videoPtsUs)
                    }
                } finally {
                    frame.release()
                }
                yield()
            }
        } catch (e: Exception) {
            if (e !is CancellationException) logger.e(e) { "videoRenderLoop crashed" }
        }
    }

    private fun writeAudioToSink(frame: JvmAudioFrame) {
        val clock = getClock()
        clock.setInitialAudioTimestamp(frame.timestampUs)
        if (!clock.isStarted) {
            clock.init(frame.timestampUs, audioSink.positionFrames)
        }
        if (!audioSink.isRunning) {
            audioSink.play()
        }
        val samples = frame.internalFrame.samples[0] as java.nio.ShortBuffer
        val remaining = samples.remaining()
        var data = reusableAudioBuffer
        if (data == null || data.size < remaining) {
            data = ShortArray(remaining)
            reusableAudioBuffer = data
        }
        samples.get(data, 0, remaining)
        val writtenFrames = audioSink.write(data, 0, remaining)
        if (writtenFrames > 0) {
            totalAudioFramesWritten += writtenFrames
            clock.updateAudioProgress(totalAudioFramesWritten)
        }
        frame.release()
    }
}