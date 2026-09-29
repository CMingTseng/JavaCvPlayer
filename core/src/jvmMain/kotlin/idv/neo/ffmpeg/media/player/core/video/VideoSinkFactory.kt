package idv.neo.ffmpeg.media.player.core.video

import co.touchlab.kermit.Logger

/**
 * VideoSink 工廠。
 * 在重構為多模組架構後，此類別不再直接持有 JavaFX 或 Swing 的引用。
 * 建議由應用層根據環境選擇合適的實作並注入。
 */
object VideoSinkFactory {
    private val logger = Logger.withTag("VideoSinkFactory")

    /**
     * 根據 Sink 實作類別名稱偵測其建議的像素格式。
     * 避免直接使用 'is JavaFxPixelBufferVideoSink' 以解除編譯時依賴。
     */
    fun getPreferredPixelFormat(sink: VideoSink): Int {
        val className = sink.javaClass.simpleName
        return when {
            className.contains("JavaFx", ignoreCase = true) -> {
                // AV_PIX_FMT_BGRA = 28
                28 
            }
            else -> {
                // AV_PIX_FMT_BGR24 = 3
                3
            }
        }
    }
}
