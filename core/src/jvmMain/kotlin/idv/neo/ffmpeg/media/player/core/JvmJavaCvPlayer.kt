package idv.neo.ffmpeg.media.player.core

import idv.neo.ffmpeg.media.player.core.audio.JvmAudioSink
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

/**
 * JVM 端的播放器具體實作。
 * 採用並行渲染循環架構，並解決可能導致黑屏的 Queue 阻塞死結問題。
 */
class JvmJavaCvPlayer private constructor(
    vQueue: DefaultMediaFrameQueue<JvmVideoFrame>,
    aQueue: DefaultMediaFrameQueue<JvmAudioFrame>,
    aSink: JvmAudioSink,
    vSink: VideoSink,
    loader: FFmpegFrameLoader
) : BaseJavaCvPlayer<JvmVideoFrame, JvmAudioFrame>(vQueue, aQueue, aSink, vSink, loader) {

    companion object {
        /**
         * 建立播放器實例。
         * @param videoSink 必須由外部注入（例如來自 :core-video-javafx, :core-video-swing 或 :core-video-compose）
         */
        fun create(videoSink: VideoSink): JvmJavaCvPlayer {
            val vQueue = DefaultMediaFrameQueue<JvmVideoFrame>(240)
            val aQueue = DefaultMediaFrameQueue<JvmAudioFrame>(480)
            val aSink = JvmAudioSink()
            
            val loader = FFmpegFrameLoader(vQueue, aQueue)
            
            // 根據 Sink 自動調整解碼格式
            loader.setPreferredPixelFormat(videoSink.preferredPixelFormat)

            return JvmJavaCvPlayer(vQueue, aQueue, aSink, videoSink, loader)
        }
    }

    private val logger = Logger.withTag("JvmJavaCvPlayer")
    private var totalAudioFramesWritten = 0L
    private val videoCatchUpDropThresholdUs = 60_000L
    private var loopIteration = 0L

    private var videoJob: Job? = null
    private var audioJob: Job? = null

    /** 影像幀輸出管道 (保留回呼彈性) */
    var videoFrameOutput: VideoFrameOutput? = null

    /** 取得影像 Sink (可轉型為具體環境的實作以獲取 View) */
    fun getPlayerVideoSink(): VideoSink = videoSink as VideoSink

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
        // 印出當前狀態，診斷為何不播
        if (loopIteration % 50L == 0L) {
            logger.d { "Watchdog: state=$playbackState, ready=$playWhenReady, loops=${videoJob?.isActive == true}" }
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
            val audioBufferLimitUs = 200_000L // Increased buffer for smoother playback
            logger.i { "audioRenderLoop started" }
            
            while (coroutineContext.isActive) {
                val frame = audioQueue.peek()
                if (frame == null) {
                    delay(10)
                    continue
                }

                val speed = playbackParameters.speed
                val currentClockUs = clock.getPositionMicros(audioSink)
                val audioRelTs = frame.timestampUs

                // Always write if clock isn't started or we haven't set initial audio TS yet
                val shouldWrite = if (!clock.isStarted) {
                    true
                } else {
                    // Feed audio if we haven't reached the buffer limit ahead of the clock
                    audioRelTs <= currentClockUs + (audioBufferLimitUs * speed).toLong()
                }

                if (shouldWrite) {
                    val dequeuedFrame = audioQueue.dequeue() ?: continue
                    writeAudioToSink(dequeuedFrame)
                } else {
                    // We have enough audio buffered, wait a bit
                    delay(20)
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) logger.e(e) { "audioRenderLoop crashed" }
        } finally {
            logger.i { "audioRenderLoop finished" }
        }
    }

    private suspend fun videoRenderLoop() {
        try {
            val clock = getClock()
            logger.i { "videoRenderLoop started" }
            
            while (coroutineContext.isActive) {
                loopIteration++
                val frame = videoQueue.dequeue() ?: run {
                    if (loopIteration % 100L == 0L) {
                        logger.d { "videoRenderLoop: waiting for frame... q_v=${videoQueue.size} q_a=${audioQueue.size}" }
                    }
                    delay(10)
                    continue
                }

                try {
                    if (!clock.isStarted) {
                        clock.init(frame.timestampUs, audioSink.positionFrames)
                        logger.i { "Starting clock via VideoFrame. TS: ${frame.timestampUs}" }
                    }

                    val videoPtsUs = frame.timestampUs
                    val currentClockUs = clock.getPositionMicros(audioSink)
                    val diffUs = videoPtsUs - currentClockUs

                    // AVSync decision logic
                    if (diffUs > 5_000) { // 降低閾值到 5ms
                        val waitMs = (diffUs / 1000).coerceAtMost(50)
                        if (waitMs > 1) delay(waitMs)
                    }

                    // Re-check clock after potential delay
                    val currentClockUsAfterWait = clock.getPositionMicros(audioSink)
                    val finalDiffUs = videoPtsUs - currentClockUsAfterWait

                    // 如果太遲（超過 60ms），則丟棄
                    if (finalDiffUs < -videoCatchUpDropThresholdUs) {
                        if (loopIteration % 100L == 0L) {
                            logger.w { "Video too late (${-finalDiffUs / 1000}ms), dropping frame. Clock=${currentClockUsAfterWait} Frame=${videoPtsUs}" }
                        }
                    } else {
                        videoSink.render(frame, videoPtsUs)
                    }

                    if (loopIteration % 60L == 0L) { // 減少日誌頻率
                        logger.v { "AVSync: vPts=$videoPtsUs, clock=$currentClockUsAfterWait, diff=$finalDiffUs, qV=${videoQueue.size}" }
                    }
                    loopIteration++
                } finally {
                    frame.release()
                }

                yield()
            }
        } catch (e: Exception) {
            if (e !is CancellationException) logger.e(e) { "videoRenderLoop crashed" }
        } finally {
            logger.i { "videoRenderLoop finished" }
        }
    }

    private fun writeAudioToSink(frame: JvmAudioFrame) {
        val clock = getClock()
        
        // Ensure initial audio timestamp is set (used by MediaClock as anchor)
        clock.setInitialAudioTimestamp(frame.timestampUs)

        // If clock isn't started yet, use this audio frame to initialize it
        if (!clock.isStarted) {
            clock.init(frame.timestampUs, audioSink.positionFrames)
            logger.i { "Starting clock via AudioFrame. TS: ${frame.timestampUs}" }
        }
        
        // Explicitly start audio sink if it's not running
        if (!audioSink.isRunning) {
            audioSink.play()
        }

        val samples = frame.internalFrame.samples[0] as java.nio.ShortBuffer
        val data = ShortArray(samples.remaining())
        samples.get(data)
        
        val writtenFrames = audioSink.write(data, 0, data.size)
        if (writtenFrames > 0) {
            totalAudioFramesWritten += writtenFrames
            clock.updateAudioProgress(totalAudioFramesWritten)
        }
        frame.release()
    }
}
