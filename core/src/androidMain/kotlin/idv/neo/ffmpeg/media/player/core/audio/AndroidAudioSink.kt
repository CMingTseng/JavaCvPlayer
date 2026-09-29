package idv.neo.ffmpeg.media.player.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import androidx.media3.common.audio.Sonic
import co.touchlab.kermit.Logger

/**
 * Android 端的 [AudioSink] 實作，基於 Android [AudioTrack] 與 [Sonic] 音訊處理器。
 */
class AndroidAudioSink : AudioSink {
    private val logger = Logger.withTag("AndroidAudioSink")
    private var audioTrack: AudioTrack? = null
    private var sonic: Sonic? = null
    private var channels: Int = 2
    private var speed: Float = 1.0f

    override val isRunning: Boolean
        get() = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    override val isOpen: Boolean
        get() = audioTrack != null

    override val positionFrames: Long
        get() = audioTrack?.playbackHeadPosition?.toLong() ?: 0L

    override var sampleRate: Int = 0
        private set

    override fun setup(sampleRate: Int, channels: Int) {
        release()
        this.sampleRate = sampleRate
        this.channels = channels

        val channelConfig = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> AudioFormat.CHANNEL_OUT_STEREO
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = Math.max(minBufferSize, 16384)

        audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        }

        sonic = Sonic(sampleRate, channels).apply {
            setSpeed(speed)
        }
        
        audioTrack?.play()
    }

    override fun write(buffer: ShortArray, offset: Int, size: Int): Int {
        val track = audioTrack ?: return 0
        if (size <= 0) return 0

        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            track.play()
        }

        if (speed == 1.0f) {
            val written = track.write(buffer, offset, size)
            return if (written >= 0) written / channels else 0
        }

        val s = sonic ?: return 0
        s.queueInput(buffer, offset, size)
        val outputSize = s.getOutputSize()
        val outputBuffer = ShortArray(outputSize)
        val count = s.getOutput(outputBuffer)
        if (count > 0) {
            track.write(outputBuffer, 0, count)
            return size / channels 
        }
        return 0
    }

    override fun getPlaybackHeadPositionUs(): Long {
        if (sampleRate <= 0) return 0L
        return (positionFrames * 1_000_000L) / sampleRate
    }

    override fun setPlaybackSpeed(speed: Float) {
        this.speed = speed
        sonic?.setSpeed(speed)
    }

    override fun setVolume(volume: Float) {
        audioTrack?.setVolume(volume)
    }

    override fun play() {
        audioTrack?.play()
    }

    override fun pause() {
        audioTrack?.pause()
    }

    override fun flush() {
        audioTrack?.flush()
        sonic?.flush()
    }

    override fun stop() {
        audioTrack?.stop()
        audioTrack?.flush()
    }

    override fun release() {
        audioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                logger.e(e) { "Error releasing AudioTrack" }
            }
        }
        audioTrack = null
    }
}
