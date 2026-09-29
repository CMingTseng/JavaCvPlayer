# 實作範例：VideoSinkFactory 與 Skia 零拷貝技術

本文件提供具體的代碼實作思路，幫助你建立跨平台的渲染抽像層，並在 Compose Desktop 上實現極致的零拷貝 (Zero-copy) 渲染。

---

## 1. VideoSinkFactory：環境自適應與優雅切換

透過工廠模式，讓播放器核心不需要依賴具體的 UI 框架。

### 核心介面
```java
public interface VideoSink {
    /** 接收從 Grabber/Decoder 產生的原生 Frame */
    void onFrameAvailable(Frame frame);
    
    /** 釋放渲染資源 */
    void release();
}
```

### 工廠實作邏輯
```java
public class VideoSinkFactory {
    public static VideoSink createBestSink() {
        if (isComposeDesktop()) {
            return new SkiaZeroCopyVideoSink();
        } else if (isJavaFX()) {
            return new JavaFxPixelBufferVideoSink();
        } else {
            return new SwingVolatileVideoSink();
        }
    }

    private static boolean isComposeDesktop() {
        try {
            Class.forName("org.jetbrains.skia.Bitmap");
            return true;
        } catch (ClassNotFoundException e) { return false; }
    }
    // ... 其他環境偵測
}
```

---

## 2. Compose Desktop (Skia) 零拷貝實作深度解析

這是 JVM 環境下最高效的渲染方式。我們直接將 JavaCV 的 Native 記憶體位址「借給」Skia Bitmap。

### 核心實作 (Kotlin/Java 混合思路)
```kotlin
class SkiaZeroCopyVideoSink(val width: Int, val height: Int) : VideoSink {
    // 1. 建立 Skia Bitmap
    private val skiaBitmap = Bitmap().apply {
        allocPixels(ImageInfo.makeN32Premul(width, height))
    }
    
    // 2. 獲取 Skia 的內存地址指標
    private val pixelAddress: Long = skiaBitmap.peekPixels()?.addr ?: 0

    // 3. 建立一個指向該地址的 JavaCV 緩衝區 (或使用 FFmpeg 的 sws_scale)
    // 這樣 Grabber 解碼後的數據會直接寫入 Skia 的臉部緩衝區
    
    override fun onFrameAvailable(frame: Frame) {
        // 使用 JavaCV 的 FFmpegFrameFilter 或 sws_scale 
        // 將原始 YUV 轉換為 BGRA 並直接輸出到 pixelAddress 指定的位址
        
        // 渲染完成後，觸發 Compose 重繪
        triggerComposeRedraw()
    }
    
    fun asImageBitmap(): ImageBitmap = skiaBitmap.asImageBitmap()
}
```

---

## 3. JavaFX PixelBuffer 實作範例

針對 JavaFX 13+，利用 `PixelBuffer` 直接操作 `Direct ByteBuffer`。

```java
public class JavaFxPixelBufferVideoSink implements VideoSink {
    private final ByteBuffer directBuffer;
    private final PixelBuffer<ByteBuffer> pixelBuffer;
    private final WritableImage writableImage;

    public JavaFxPixelBufferVideoSink(int width, int height) {
        // 分配不受 JVM GC 影響的直接記憶體
        this.directBuffer = ByteBuffer.allocateDirect(width * height * 4);
        
        pixelBuffer = new PixelBuffer<>(
            width, height, directBuffer, 
            PixelFormat.getByteBgraPreInstance()
        );
        this.writableImage = new WritableImage(pixelBuffer);
    }

    @Override
    public void onFrameAvailable(Frame frame) {
        // 讓 Grabber 直接將 BGRA 數據寫入 directBuffer
        // ... grabber.grabFrame() to directBuffer ...
        
        // 僅通知 UI 該區域已更新，不進行數據拷貝
        pixelBuffer.updateBuffer(pb -> null);
    }

    public Image getFinalImage() { return writableImage; }
}
```

---

## 4. 效能對比總結

| 技術方案 | 拷貝次數 | CPU 負擔 | 適用場景 |
| :--- | :---: | :---: | :--- |
| **Java2DFrameConverter** | 2-3 次 | 極高 | 快速原型、簡單小影片 |
| **JavaFX PixelBuffer** | 1 次 (DMA) | 中 | JavaFX 桌面應用 |
| **Skia Zero-copy** | **0-1 次** | **極低** | Compose Desktop 專業播放器 |
| **OpenGL Shader** | 0 次 | 最低 | 4K 60fps, 複雜濾鏡需求 |

---

## 5. 漸進式優化策略 (避免過度工程)

在實作 4K 播放器時，建議遵循以下優化階段。**不要在第一階段就跳入 Shader 開發**，以免陷入複雜的驅動程式與 OpenGL 綁定問題而導致開發延宕。

### 第一階段：Skia 零拷貝 (解決「帶寬」問題) - **優先推薦**
*   **目標**：消除 `Native -> Heap -> VRAM` 的冗餘拷貝。
*   **預期效果**：即使是 4K 30fps，CPU 佔用率應能控制在合理範圍內 (15-25%)。
*   **開發成本**：低。僅需操作 Skia 指標映射。

### 第二階段：RuntimeShader / GPU 加速 (解決「算力」問題)
*   **觸發條件**：只有在「零拷貝」實作後，CPU 佔用率依然居高不下，或遇到以下情況：
    *   需要支援 4K 60fps 以上的極高幀率。
    *   需要實作即時影音濾鏡 (如美顏、銳化)。
    *   處理 10-bit HDR 影片的色彩空間轉換。
*   **開發成本**：高。需要處理 GPU 上下文管理與 GLSL 編寫。

**總結原則**：先解決「數據怎麼搬運」的帶寬瓶頸，再解決「數據怎麼計算」的算力瓶頸。

### 開發建議
如果你追求的是 Media3 等級的體驗，請務必跳過 `Java2DFrameConverter`，直接根據 `VideoSinkFactory` 的偵測結果，實作上述的 `PixelBuffer` 或 `Skia` 映射。這能讓你的 JavaCV 播放器在桌面端擁有與原生應用無異的流暢度。
