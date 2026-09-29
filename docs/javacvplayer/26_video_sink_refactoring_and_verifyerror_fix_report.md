# VideoSink 模組化重構與 VerifyError 深度解法報告

## 1. 背景與起因
原始架構中，影像渲染邏輯（VideoSink）高度耦合於 `:core` 模組中，且存在以下問題：
*   **效能瓶頸**：JavaFX 渲染原先採用 `SwingNode` 或標準 `WritableImage`，在高解析度下 CPU 負載過高。
*   **依賴污染**：`:core` 模組強依賴 JavaFX 與 AWT，導致 Android 模組編譯困難，且違反 KMP 乾淨核心原則。
*   **擴充性差**：更換渲染框架（如 Swing 轉 JavaFX）需要修改核心代碼。

## 2. 核心挑戰：VerifyError 的出現
在嘗試將 JavaFX 邏輯移至獨立模組 `:core-video-javafx` 後，運行時出現了嚴重的 `java.lang.VerifyError`。

### 病灶分析
*   **問題描述**：錯誤指向 `JavaFxPixelBufferVideoSink` 訪問 `org.bytedeco.javacv.Frame.imageStride` 屬性。
*   **底層原因**：Kotlin 編譯器在處理 `imageStride` 時（JavaCV 中該欄位可能在不同版本間於 `Int` 與 `IntArray` 間變化），於跨模組引用時產生的 **StackMapTable (堆疊映射表)** 不一致。這是一種深層的位元碼衝突。
*   **結論**：不能在非 `:core` 模組直接訪問 JavaCV `Frame` 的具體欄位。

## 3. 解決方案：數據隔離架構 (Binary Isolation)
為了徹底解決 `VerifyError` 並實現架構解耦，我們實施了以下重構：

### 3.1 定義 VideoFrameData 介面
在 `:core` (commonMain) 定義了一個純淨的數據介面，不包含任何外部庫符號：
```kotlin
interface VideoFrameData {
    val width: Int
    val height: Int
    val stride: Int
    val timestampUs: Long
    fun getByteBuffer(): ByteBuffer?
    fun getRawFrame(): Any? // 返回原始物件供特殊轉換使用
}
```

### 3.2 實現 JvmVideoFrame
在 `:core` (jvmMain) 實現該介面，封裝所有對 JavaCV `Frame` 的危險操作：
*   **Stride 魯棒性**：自動判斷 `imageStride` 是 `Int` 還是 `IntArray` 並返回正確數值。
*   **Buffer 安全性**：使用 `duplicate().rewind()` 確保 Sink 端讀取數據時 position 正確。

### 3.3 建立專用渲染模組
*   **`:core-video-javafx`**：專注於 `PixelBuffer` 渲染。
*   **`:core-video-swing`**：專注於 `VolatileImage` (硬體加速) 渲染。
*   **`:core-video-compose-jvm`**：專注於 `BufferedImage` 轉換。

## 4. JavaFX PixelBuffer 深度優化
針對 JavaFX 渲染器進行了以下關鍵修復：
1.  **PixelFormat 校正**：將 `BYTE_BGRA` 改為 `getByteBgraPreInstance()`，解決 `Unsupported PixelFormat` 報錯。
2.  **Near Zero-copy 複製**：實作了 Stride 感知的逐行複製邏輯，能正確處理有 Padding 的影格數據。
3.  **Buffer Position 修復**：在數據填充後強制執行 `targetBuffer.rewind()`，解決了 JavaFX 渲染引擎拋出的 `0 elements remain in buffer` 異常。

## 5. UI 耦合解決：ViewProvider 模式
為了符合 Media3 設計規範並保持解耦，所有的 `VideoSink` 現在都實作了 `ViewProvider` 介面：
```kotlin
interface ViewProvider {
    fun getView(container: Any?): Any?
}
```
這使得播放器核心（JvmJavaCvPlayer）只需持有 `VideoSink`，而 UI 層（如 JavaFX 的 Stage 或 Swing 的 JFrame）可以透過 `getView()` 獲取平台特定的組件（Node 或 JPanel），實現了「核心一份，渲染隨插即用」。

## 6. 最終成果
*   ✅ **VerifyError 徹底消除**：透過 `VideoFrameData` 介面實現二進位隔離。
*   ✅ **渲染效能大幅提升**：JavaFX 使用 `PixelBuffer` 達成高效能渲染。
*   ✅ **架構清晰**：`:core` 模組不再受 AWT/JavaFX 污染，Android 模組編譯正常。
*   ✅ **平台一致性**：Swing、JavaFX、Compose 三大桌面渲染分支均已同步此架構並運作順暢。

---
**文件紀錄日期**：2024-05-23
**狀態**：重構已完成並驗證通過。
