package idv.neo.ffmpeg.media.player.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.*
import co.touchlab.kermit.Logger

/**
 * 基於 Java Sound API 的 JVM 音訊輸出實作。
 */
class JvmAudioSink : AudioSink {
    private val logger = Logger.withTag("JvmAudioSink")
    private var line: SourceDataLine? = null
    private var sonic: Sonic? = null
    private var channels: Int = 2
    private var speed: Float = 1.0f
    private var byteBuffer: ByteBuffer? = null
    private var reusableOutputBuffer: ShortArray? = null

    override var isRunning: Boolean = false
        get() = line?.isRunning ?: false
        private set

    override val isOpen: Boolean get() = line?.isOpen ?: false
    
    // 修正：使用硬體實際播放的影格位置
    override val positionFrames: Long
        get() = line?.longFramePosition ?: 0L

    override var sampleRate: Int = 44100
        private set

    override fun setup(sampleRate: Int, channels: Int) {
        if (line?.isOpen == true) {
            line?.stop()
            line?.close()
        }
        this.sampleRate = sampleRate
        this.channels = channels
        
        val format = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)
        val info = DataLine.Info(SourceDataLine::class.java, format)
        
        try {
            val calculatedBufferSize = (sampleRate * channels * 2 * 0.25).toInt()
            line = (AudioSystem.getLine(info) as SourceDataLine).apply {
                open(format, calculatedBufferSize)
            }
            sonic = Sonic(sampleRate, channels).apply {
                setSpeed(speed)
            }
            logger.i { "AudioSink setup success: $format, bufferSize=$calculatedBufferSize" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to open Audio line" }
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        this.speed = speed
        sonic?.setSpeed(speed)
    }

    override fun write(data: ShortArray, offset: Int, size: Int): Int {
        if (line == null || sonic == null) return 0

        if (speed == 1.0f) {
            return writeToLine(data, offset, size)
        }

        val s = sonic!!
        s.queueInput(data, offset, size)
        val outputSize = s.getOutputSize()
        var outputBuffer = reusableOutputBuffer
        if (outputBuffer == null || outputBuffer.size < outputSize) {
            outputBuffer = ShortArray(outputSize)
            reusableOutputBuffer = outputBuffer
        }
        val count = s.getOutput(outputBuffer)
        if (count > 0) {
            writeToLine(outputBuffer, 0, count)
        }
        return size 
    }

    private fun writeToLine(data: ShortArray, offset: Int, size: Int): Int {
        val l = line ?: return 0
        if (!l.isOpen) return 0
        
        val requiredBytes = size * 2
        var bb = byteBuffer
        if (bb == null || bb.capacity() < requiredBytes) {
            bb = ByteBuffer.allocate(requiredBytes).order(ByteOrder.LITTLE_ENDIAN)
            byteBuffer = bb
        }
        bb.clear()
        bb.asShortBuffer().put(data, offset, size)
        
        val writtenBytes = l.write(bb.array(), 0, requiredBytes)
        return writtenBytes / (2 * channels)
    }

    override fun getPlaybackHeadPositionUs(): Long {
        return (positionFrames * 1_000_000L) / sampleRate
    }

    override fun setVolume(volume: Float) {
        val l = line ?: return
        try {
            if (l.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gainControl = l.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                // gain (dB) = 20 * log10(volume)
                val dB = (Math.log10(maxOf(volume.toDouble(), 0.0001)) * 20.0).toFloat()
                gainControl.value = dB
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to set volume gain" }
        }
    }

    override fun play() {
        line?.start()
    }

    override fun pause() {
        line?.stop()
    }

    override fun flush() {
        line?.flush()
    }

    override fun stop() {
        line?.stop()
    }

    override fun release() {
        line?.close()
        line = null
    }
}
