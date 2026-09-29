# 跨平台統一渲染架構：從 Android Surface 到 JVM PixelBuffer

這份文件解析了 Android ExoPlayer 的渲染本質，並指導如何在 JVM 環境下實作一套同樣「視圖無感」的渲染切換系統。

---

## 1. Android 的渲染真相：Surface vs. Image
在 Android 中，即使使用 Compose，Media3 依然偏好 `Surface`。

- **為何不用 Image/Bitmap?**
  如果在 Compose 中使用 `Image(bitmap)`，每一幀影像都要經過：`MediaCodec -> Bitmap -> UI Thread Canvas -> RenderNode -> GPU`。這會產生大量的垃圾回收 (GC) 與記憶體拷貝，導致 4K 播放時掉幀。
- **Media3 的做法**：
  `ExoPlayer` 拿到的是一個 `Surface` 指標。它直接將解碼後的數據「投射」到這個緩衝區，這屬於 **System-level Overlay**，不佔用 UI 執行緒資源。

---

## 2. JVM 的挑戰：缺乏統一的 Surface
JVM (Desktop) 沒像 Android 系統級別的 `Surface`。Compose Desktop、JavaFX 與 Swing 各自有不同的畫布實現。

### 優雅切換設計：`VideoSink` 模式
為了達成像 Media3 只要 `setPlayer(player)` 就能顯示的效果，我們需要一個抽象層：

```java
public interface VideoSink {
    // 當 Player 準備好一幀影像時呼叫
    void onFrameAvailable(Frame frame);
    
    // 取得適合該環境的 UI 元件 (如 Compose 的 MutableState 或 JavaFX 的 Node)
    Object getView(); 
}
```

---

## 3. 各平台的優雅實現路徑

### A. Compose Multiplatform (Desktop)
使用一個 `MutableState<ImageBitmap?>` 作為橋接。
- **優雅點**：Player 只管更新 State，Compose 會自動重繪。
- **高效點**：利用 `Skia` 的 `Bitmap.installPixels` 讓 JavaCV 直接寫入 Skia 記憶體。

### B. JavaFX
實作一個 `JavaFxVideoSink` 包裝 `ImageView` 與 `PixelBuffer`。
- **優雅點**：UI 層只需要 `pane.getChildren().add(sink.getView())`。
- **高效點**：使用 `PixelBuffer` 的 `directBuffer`。

### C. Android (相容層)
如果你希望 JavaCV 也能跑在 Android：
- 實作 `AndroidSurfaceSink`。
- 呼叫 `Surface.lockHardwareCanvas()`，利用 JavaCV 的 `AndroidFrameConverter` 進行渲染。

---

## 4. 總結：如何跟隨環境自動切換？
你可以實作一個 **`VideoSinkFactory`**：

1. **偵測環境**：檢查 `System.getProperty("os.name")` 或類別路徑（如 `androidx.compose.ui.ImageBitmap` 是否存在）。
2. **自動注入**：
   ```java
   // 範例：在 Compose Desktop 中
   VideoSink sink = VideoSinkFactory.createComposeSink();
   player.setVideoSink(sink);
   
   // UI 顯示
   VideoCanvas(sink); 
   ```

這樣一來，你的 `JavaCvPlayer` 核心邏輯完全不需要改動，就能在各種 UI 環境中「優雅地」運行，且能使用各平台最極致的渲染技術（如 `PixelBuffer` 或 `Skia Zero-copy`）。
