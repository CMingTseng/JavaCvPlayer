package idv.neo.ffmpeg.media.player.core.video.javafx

import idv.neo.ffmpeg.media.player.core.video.VideoSink
import idv.neo.ffmpeg.media.player.core.video.VideoFrameData
import androidx.media3.common.ViewProvider
import javafx.application.Platform
import javafx.scene.image.ImageView
import javafx.scene.image.PixelBuffer
import javafx.scene.image.PixelFormat
import javafx.scene.image.WritableImage
import javafx.scene.layout.StackPane
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import co.touchlab.kermit.Logger

/**
 * 實作高性能 JavaFX PixelBuffer 渲染器。
 * 使用 PixelBuffer 達成 Near Zero-copy 渲染。
 */
class JavaFxPixelBufferVideoSink : VideoSink, ViewProvider {
    private val logger = Logger.withTag("JavaFxPixelBufferVideoSink")
    
    private val imageView = ImageView().apply {
        isPreserveRatio = true
    }
    
    private val container = StackPane(imageView).apply {
        setStyle("-fx-background-color: black;")
    }
    
    private var pixelBuffer: PixelBuffer<ByteBuffer>? = null
    @Volatile
    private var directBuffer: ByteBuffer? = null
    private var width = 0
    private var height = 0
    
    private val isUpdatePending = AtomicBoolean(false)

    // AV_PIX_FMT_BGRA = 28
    override val preferredPixelFormat: Int get() = 28

    override fun setVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        
        if (this.width != width || this.height != height) {
            logger.i { "Setting video size to ${width}x${height}" }
            this.width = width
            this.height = height
            
            Platform.runLater {
                try {
                    val capacity = width * height * 4
                    val buffer = ByteBuffer.allocateDirect(capacity)
                    directBuffer = buffer
                    
                    val format = PixelFormat.getByteBgraPreInstance()
                    val pb = PixelBuffer(width, height, buffer, format)
                    pixelBuffer = pb
                    imageView.image = WritableImage(pb)
                    
                    imageView.fitWidthProperty().bind(container.widthProperty())
                    imageView.fitHeightProperty().bind(container.heightProperty())
                    
                    logger.i { "JavaFX PixelBuffer initialized: ${width}x${height}" }
                } catch (e: Exception) {
                    logger.e(e) { "Failed to setup JavaFX PixelBuffer" }
                }
            }
        }
    }

    override fun onFrameAvailable(frame: Any) {
        val targetBuffer = directBuffer ?: return
        
        // 1. 取得 VideoFrameData 介面，完全避開 JavaCV Frame 符號
        val frameData = frame as? VideoFrameData ?: return

        // 2. 利用 VideoFrameData 提供的數據進行操作
        val nioBuf = frameData.getByteBuffer() ?: return
        val stride = frameData.stride
        
        val frameWidth = frameData.width
        val frameHeight = frameData.height
        
        if (frameWidth <= 0 || frameHeight <= 0) return

        // 如果尺寸不符，則跳過此影格 (等待 setVideoSize 生效)
        if (frameWidth != width || frameHeight != height) {
            return
        }

        synchronized(targetBuffer) {
            targetBuffer.clear()
            val rowSize = frameWidth * 4
            
            try {
                if (stride == rowSize) {
                    // 完美對齊，直接複製整個 Buffer
                    val bytesToCopy = nioBuf.remaining().coerceAtMost(targetBuffer.remaining())
                    if (bytesToCopy > 0) {
                        val oldLimit = nioBuf.limit()
                        val newLimit = nioBuf.position() + bytesToCopy
                        nioBuf.limit(newLimit)
                        targetBuffer.put(nioBuf)
                        nioBuf.limit(oldLimit)
                    }
                } else {
                    // 處理 Stride 不一致的情況 (逐行複製)
                    for (y in 0 until frameHeight) {
                        nioBuf.position(y * stride)
                        val oldLimit = nioBuf.limit()
                        val newLimit = nioBuf.position() + rowSize
                        if (newLimit <= nioBuf.capacity()) {
                            nioBuf.limit(newLimit)
                            targetBuffer.put(nioBuf)
                        }
                        nioBuf.limit(oldLimit)
                    }
                }
                // 重要：複製完成後必須重置 Position，否則 JavaFX 渲染引擎讀不到數據
                targetBuffer.rewind()
            } catch (e: Exception) {
                logger.e(e) { "Error copying frame data: frame=${frameWidth}x${frameHeight}, stride=$stride, rowSize=$rowSize" }
            }
        }
        
        // 請求 JavaFX UI 執行緒更新畫面
        if (isUpdatePending.compareAndSet(false, true)) {
            Platform.runLater {
                try {
                    pixelBuffer?.updateBuffer { null }
                } catch (e: Exception) {
                    logger.e(e) { "Error updating JavaFX PixelBuffer" }
                } finally {
                    isUpdatePending.set(false)
                }
            }
        }
    }

    override fun render(frame: Any, timestampUs: Long) {
        onFrameAvailable(frame)
    }

    override fun getView(viewGroup: Any?): Any = container

    override fun flush() {
        // 可在此清空畫面或重置狀態
    }

    override fun release() {
        Platform.runLater {
            imageView.image = null
            directBuffer = null
            pixelBuffer = null
            container.children.clear()
            logger.i { "JavaFX PixelBufferVideoSink released" }
        }
    }
}
