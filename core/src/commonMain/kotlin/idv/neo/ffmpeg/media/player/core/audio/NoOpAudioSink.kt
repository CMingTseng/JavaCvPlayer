package idv.neo.ffmpeg.media.player.core.audio

/**
 * An [AudioSink] implementation that does nothing (No-Op).
 * Useful for pure audio processing, silent playback, or testing without audio hardware output.
 */
class NoOpAudioSink : AudioSink {
    override fun setup(sampleRate: Int, channels: Int) {}
    override fun write(buffer: ShortArray, offset: Int, size: Int): Int = size
    override fun play() {}
    override fun pause() {}
    override fun flush() {}
    override fun stop() {}
    override fun release() {}
    
    override val isRunning: Boolean = false
    override val isOpen: Boolean = false
    override val positionFrames: Long = 0L
    override val sampleRate: Int = 0
    
    override fun setPlaybackSpeed(speed: Float) {}
    override fun setVolume(volume: Float) {}
    override fun getPlaybackHeadPositionUs(): Long = 0L
}
