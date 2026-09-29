package idv.neo.ffmpeg.media.player.core.loader

data class FrameMetadata(
    val hasVideo: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val hasAudio: Boolean = false,
    val durationUs: Long = 0,
    val sampleRate: Int = 44100,
    val audioChannels: Int = 2
)
