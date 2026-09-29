package idv.neo.ffmpeg.media.player.core.buffer

import org.bytedeco.javacv.Frame

/**
 * JVM 端的音訊影格實作。
 */
class JvmAudioFrame(
    val internalFrame: Frame
) : VideoFrame { // 這裡暫時實作 VideoFrame 介面以便共用 Queue，未來可抽換為更通用的 MediaFrame
    
    override val timestampUs: Long = internalFrame.timestamp

    override fun release() {
        internalFrame.close()
    }

    fun clone(): JvmAudioFrame {
        return JvmAudioFrame(internalFrame.clone())
    }
}
