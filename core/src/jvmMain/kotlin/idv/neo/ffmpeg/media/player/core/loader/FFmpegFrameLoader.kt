package idv.neo.ffmpeg.media.player.core.loader

import idv.neo.ffmpeg.media.player.core.buffer.JvmAudioFrame
import idv.neo.ffmpeg.media.player.core.buffer.JvmVideoFrame
import idv.neo.ffmpeg.media.player.core.buffer.MediaFrameQueue
import idv.neo.ffmpeg.media.player.core.video.VideoFrameProcessor
import kotlinx.coroutines.*
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import co.touchlab.kermit.Logger

/**
 * 負責使用 JavaCV 解碼媒體並填充影格佇列。
 * 相當於 Media3 中的 Loader / SampleStream 角色。
 */
class FFmpegFrameLoader(
    private val videoQueue: MediaFrameQueue<JvmVideoFrame>,
    private val audioQueue: MediaFrameQueue<JvmAudioFrame>,
    private var videoFrameProcessor: VideoFrameProcessor? = null
) : FrameLoader {
    private val logger = Logger.withTag("FFmpegFrameLoader")
    private val loaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("FrameLoader"))
    private var loaderJob: Job? = null
    private var grabber: FFmpegFrameGrabber? = null

    @Volatile
    private var isReleased = false

    private var _isEndOfStream = false
    override val isEndOfStream: Boolean get() = _isEndOfStream

    private var lastVideoPts = -1L
    private var lastAudioPts = -1L
    private var loopIteration = 0L

    private var currentMetadata = FrameLoader.Metadata()
    override fun getMetadata(): FrameLoader.Metadata = currentMetadata

    private var preferredPixelFormat: Int = avutil.AV_PIX_FMT_BGR24

    /** 
     * 設定偏好的影像格式。
     * JavaFX 建議使用 AV_PIX_FMT_BGRA 以配合 PixelBuffer (Near Zero-copy)。
     * Swing 則建議使用 AV_PIX_FMT_BGR24。
     */
    fun setPreferredPixelFormat(format: Int) {
        preferredPixelFormat = format
    }

    /**
     * 設定影像處理器（如 OpenCV 濾鏡、旋轉等）。
     */
    fun setVideoFrameProcessor(processor: VideoFrameProcessor?) {
        this.videoFrameProcessor = processor
    }

    override fun start(url: String, onMetadataReady: (FrameLoader.Metadata) -> Unit) {
        isReleased = false // 修正：重置釋放標記，否則迴圈會直接跳出
        _isEndOfStream = false
        loaderJob?.cancel()
        loaderJob = loaderScope.launch {
            try {
                initGrabber(url)
                val g = grabber!!
                
                // 設定解碼器輸出格式
                g.pixelFormat = preferredPixelFormat
                
                g.start()

                currentMetadata = extractMetadata()
                onMetadataReady(currentMetadata)
                
                logger.i { "Loader loop started for $url (Format: $preferredPixelFormat)" }
                
                while (isActive && !isReleased) {
                    // 控制佇列大小，防止過度緩衝導致 CPU 巔峰負載
                    if (videoQueue.size >= 60 || audioQueue.size >= 120) {
                        delay(10)
                        continue
                    }

                    val frame = try {
                        grabber?.grab()
                    } catch (e: Exception) {
                        logger.e(e) { "Grab failed" }
                        null
                    } ?: run {
                        logger.i { "End of stream reached" }
                        _isEndOfStream = true
                        break
                    }
                    
                    when {
                        frame.image != null -> {
                            // 執行影像處理（如 OpenCV 特效）
                            val processedFrame = videoFrameProcessor?.process(frame) ?: frame
                            
                            // 確保入隊的是 org.bytedeco.javacv.Frame 的克隆，以獨立生命週期
                            val finalFrame = if (processedFrame is org.bytedeco.javacv.Frame) processedFrame else frame
                            videoQueue.enqueue(JvmVideoFrame(finalFrame.clone()))
                        }
                        frame.samples != null -> {
                            audioQueue.enqueue(JvmAudioFrame(frame.clone()))
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e(e) { "Loader encountered a fatal error" }
            } finally {
                releaseGrabber()
                logger.i { "Loader loop finished" }
            }
        }
    }

    override fun seekTo(positionUs: Long) {
        loaderScope.launch {
            try {
                grabber?.setTimestamp(positionUs)
                videoQueue.clear()
                audioQueue.clear()
            } catch (e: Exception) {
                logger.w(e) { "Seek failed" }
            }
        }
    }

    override fun stop() {
        isReleased = true
        loaderJob?.cancel()
    }

    override fun release() {
        stop()
        releaseGrabber()
    }

    private fun initGrabber(url: String) {
        grabber = FFmpegFrameGrabber(url).apply {
            // 優化參數 (參考 shared 模組經驗)
            setOption("rtsp_transport", "tcp")
            setOption("stimeout", "10000000")
            // 不要在這裡設定 pixelFormat，這可能導致 start() 失敗
            // pixelFormat = avutil.AV_PIX_FMT_BGR24
        }
    }

    private fun extractMetadata(): FrameLoader.Metadata {
        val g = grabber ?: return FrameLoader.Metadata()
        return FrameLoader.Metadata(
            hasVideo = g.videoStream >= 0,
            width = g.imageWidth,
            height = g.imageHeight,
            hasAudio = g.audioStream >= 0,
            durationUs = g.lengthInTime,
            sampleRate = if (g.sampleRate > 0) g.sampleRate else 44100,
            audioChannels = if (g.audioChannels > 0) g.audioChannels else 2
        )
    }


    private fun releaseGrabber() {
        try {
            grabber?.stop()
            grabber?.release()
        } catch (e: Exception) {
            logger.w(e) { "Error releasing grabber" }
        }
        grabber = null
    }
}
