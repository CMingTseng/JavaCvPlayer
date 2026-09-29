# Media3 濾鏡架構與 OpenCV (JavaCV) 整合指引

在開發 `JavaCvPlayer` 時，如何將 **Media3 的 GL 濾鏡** 與 **OpenCV 的 CPU 濾鏡** 整合在同一個管線中，是實現專業播放器的關鍵。

---

## 1. Media3 Effect 核心類別補充

除了 `HslAdjustment` 與 `RgbAdjustment`，Media3 還有許多功能強大的類別：

- **`OverlayEffect`**: 最常用的濾鏡，可疊加 `TextOverlay` (文字)、`BitmapOverlay` (圖層) 或 `DrawableOverlay`。
- **`GaussianBlur`**: 高品質的高斯模糊。
- **`Contrast` / `Brightness`**: 專門的對比與亮度調整。
- **`SingleColorLut`**: 實現「一鍵濾鏡」效果的核心類別。
- **`Crop` / `Presentation`**: 處理物理變換（裁切、解析度調整）。

---

## 2. 整合 OpenCV：Media3 的橋接設計

Media3 是一個以 **GPU (OpenGL)** 為主的框架，而 OpenCV (JavaCV) 通常運作在 **CPU (Mat 物件)**。要在 Media3 的 `Effect` 鏈中插入 OpenCV，你可以參考 `ByteBufferGlEffect` 的設計。

### 建議的整合介面：`OpenCvEffect`
我們需要定義一個介面，讓 OpenCV 的 `Mat` 處理邏輯能「掛」在播放器的濾鏡鏈中。

```kotlin
/** 基於 Media3 Effect 概念的 OpenCV 濾鏡介面 */
interface OpenCvEffect : Effect {
    /** 
     * 處理影格。
     * @param mat OpenCV 的 Mat 物件 (通常是 RGBA)
     * @param presentationTimeUs 當前影格的時間戳
     */
    fun process(mat: Mat, presentationTimeUs: Long)
}
```

---

## 3. 混合管線設計：GL 與 CPU 的取捨

在 `JavaCvPlayer` 中，你有兩條路徑可以實現濾鏡：

### 方案 A：純 CPU 管線 (OpenCV 主導)
如果你的播放器主要處理 OpenCV 運算（如人臉偵測、特徵提取）：
1. **Grabber** 抓取 `Frame`。
2. 轉換為 **OpenCV `Mat`**。
3. 遍歷 `List<OpenCvEffect>` 進行處理。
4. 將處理後的 `Mat` 轉換為 UI 顯示的 `Image` (如我們之前討論的 Skia 或 PixelBuffer)。
- **優點**：邏輯簡單，OpenCV 函數庫豐富。
- **缺點**：4K 播放時 CPU 負擔重。

### 方案 B：混合/GL 管線 (Media3 方式)
如果你想利用 Media3 的 GPU 加速：
1. **影像上傳 GPU**。
2. 執行 `GlEffect` (如亮度、色彩調整)。
3. **回讀 (Read-back)**：如果必須執行 OpenCV 運算，使用 `ByteBufferGlEffect` 將 GPU 數據下載回 CPU，轉成 `Mat` 處理。
4. **重新上傳**：處理完再上傳回 GPU 渲染。
- **優點**：可利用 Media3 強大的 GPU 濾鏡。
- **缺點**：GPU/CPU 切換會產生性能損耗 (Memory Copy)。

---

## 4. 實戰範例：實作 OpenCvEffect 濾鏡管線

為了讓 `JavaCvPlayer` 具備擴展性，我們需要建立一個能處理 `OpenCvEffect` 的管線。這通常發生在 `Grabber` 抓取到影格後，渲染到 UI 前。

### A. 定義 OpenCV 效果介面
```kotlin
package androidx.media3.common

/** 仿 Media3 設計的 OpenCV 濾鏡介面 */
interface OpenCvEffect : Effect {
    /** 處理影像。mat 通常為 RGBA 格式 */
    fun process(mat: Mat, presentationTimeUs: Long)
}
```

### B. 實作一個具體的 OpenCV 效果 (例如：灰階濾鏡)
```kotlin
class GrayScaleEffect : OpenCvEffect {
    override fun process(mat: Mat, presentationTimeUs: Long) {
        // 使用 JavaCV 的 OpenCV 繫結進行處理
        opencv_imgproc.cvtColor(mat, mat, opencv_imgproc.COLOR_RGBA2GRAY)
        opencv_imgproc.cvtColor(mat, mat, opencv_imgproc.COLOR_GRAY2RGBA)
    }
}
```

### C. 在 Player 內部建立處理管線
這段邏輯應放在播放器的核心循環執行緒中。

```kotlin
class JavaCvPlayer : BasePlayer() {
    private val effects = mutableListOf<Effect>()
    private val converter = OpenCVFrameConverter.ToMat()

    // 模擬核心循環中的處理環節
    private fun processFrameEffects(frame: Frame): Frame {
        if (effects.isEmpty()) return frame

        var currentFrame = frame
        
        // 1. 遍歷濾鏡鏈
        for (effect in effects) {
            when (effect) {
                is OpenCvEffect -> {
                    // 轉換為 Mat 進行處理
                    val mat = converter.convert(currentFrame)
                    effect.process(mat, currentFrame.timestamp)
                    // 轉回 Frame 供下一個濾鏡使用
                    currentFrame = converter.convert(mat)
                }
                is RgbAdjustment -> {
                    // 這裡可以選擇使用 FFmpegFrameFilter 或是手動處理矩陣
                    currentFrame = applyRgbAdjustment(currentFrame, effect)
                }
            }
        }
        return currentFrame
    }

    private fun applyRgbAdjustment(frame: Frame, effect: RgbAdjustment): Frame {
        // 實作略：可調用 FFmpeg 內建濾鏡或使用自定義 Shader
        return frame
    }
}
```

---

## 5. 性能優化建議 (Performance Tips)

1. **物件重用**：`OpenCVFrameConverter.ToMat()` 會產生暫時性的 `Mat` 物件。在高幀率播放時，建議手動管理 `Mat` 緩衝區，避免頻繁的 GC (Garbage Collection)。
2. **色彩空間一致性**：盡量讓 `Grabber` 輸出、`OpenCV` 處理、以及最後的 `VideoSink` 渲染都維持在相同的格式 (如 `AV_PIX_FMT_RGBA` 或 `BGRA`)。頻繁的 `cvtColor` 是效能殺手。
3. **異步處理**：如果某個 `OpenCvEffect` 非常耗時（如 AI 物件偵測），應考慮將該濾鏡放入獨立的 Worker Thread，並在 `onFrameAvailable` 中回傳處理結果，以避免阻塞播放主循環。

### 結論
對於 `JavaCvPlayer` 來說，**「相容共用」** 的關鍵在於：
1. **維持 `MediaItem` 傳遞參數**。
2. **實作一個統一的 `Effect` 容器**。
3. **根據效能需求選擇處理路徑**：能用矩陣/Shader 算的（調色、亮度）交給 FFmpeg/GPU；需要影像分析的（偵測、標記）交給 OpenCV。
