package idv.neo.ffmpeg.media.player.core.buffer

import idv.neo.ffmpeg.media.player.core.video.VideoFrameData
import org.bytedeco.javacv.Frame
import org.bytedeco.javacpp.Pointer
import java.nio.ByteBuffer

/**
 * JVM 端的影格實作，包裝 JavaCV 的 Frame。
 * 實作 VideoFrameData 以便在不依賴 JavaCV 的情況下傳輸數據。
 */
class JvmVideoFrame(
    val internalFrame: Frame
) : MediaFrame, VideoFrameData {

    override val timestampUs: Long = internalFrame.timestamp
    override val width: Int get() = internalFrame.imageWidth
    override val height: Int get() = internalFrame.imageHeight
    override val stride: Int get() = getStrideInternal()

    override fun release() {
        internalFrame.close()
    }

    override fun getByteBuffer(): ByteBuffer? {
        val image = internalFrame.image ?: return null
        if (image.isEmpty()) return null
        val data: Any = image[0] ?: return null

        return when (data) {
            is Pointer -> data.asByteBuffer()
            is ByteBuffer -> data.duplicate().apply { rewind() }
            is java.nio.Buffer -> {
                // 強制轉型為 ByteBuffer (JavaCV 的 Frame image buffer 通常是 ByteBuffer)
                (data as? ByteBuffer)?.duplicate()?.apply { rewind() }
            }
            else -> null
        }
    }

    override fun getRawFrame(): Any? = internalFrame

    private fun getStrideInternal(): Int {
        val s: Any = internalFrame.imageStride
        return when (s) {
            is IntArray -> if (s.isNotEmpty()) s[0] else internalFrame.imageWidth * 4
            is Int -> s
            is Number -> s.toInt()
            else -> internalFrame.imageWidth * 4
        }
    }

    /** 取得複製的影格，用於放入佇列 */
    fun clone(): JvmVideoFrame {
        return JvmVideoFrame(internalFrame.clone())
    }
}
