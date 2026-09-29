# JVM 高性能影像渲染深度技術指南

在 JVM (Desktop) 環境下，影像渲染的效能瓶頸通常不在「解碼」，而是在「將解碼後的數據從 Native 記憶體傳送到 UI 元件」的過程中發生的**色彩空間轉換 (CSC)** 與 **記憶體拷貝 (Memory Copy)**。

---

## 1. 核心瓶頸：為什麼 Java2DFrameConverter 慢？
`Java2DFrameConverter` 內部執行了以下昂貴操作：
1. **CPU 色彩轉換**：將 YUV (420p) 轉換為 RGB (BGR24/ARGB)。
2. **中間緩衝區**：先轉成 `BufferedImage`，再透過 AWT 繪製。
3. **記憶體拷貝**：數據在 Native -> Heap -> GPU 之間多次搬運。

---

## 2. JavaFX：利用 PixelBuffer 實現準零拷貝 (Near Zero-copy)
自 JavaFX 13 起，`PixelBuffer` 是處理影片流的最佳方案，它允許直接操作 Native Memory。

### 最佳化設計路徑：
- **預分配直接記憶體**：
  建立一個 `java.nio.ByteBuffer.allocateDirect()`，大小為 `width * height * 4` (ARGB)。
- **Grabber 設定**：
  呼叫 `grabber.setPixelFormat(avutil.AV_PIX_FMT_BGRA)`。讓 FFmpeg 內部完成轉換，直接輸出 BGRA 到你的 `Direct ByteBuffer`。
- **UI 對接**：
  ```java
  // 建立 PixelBuffer 並與之關聯
  PixelBuffer<ByteBuffer> pixelBuffer = new PixelBuffer<>(width, height, directBuffer, PixelFormat.getByteBgraPreInstance());
  imageView.setImage(new WritableImage(pixelBuffer));
  
  // 渲染時：
  // 1. grabber.grabFrame() 到 directBuffer
  // 2. 呼叫更新
  pixelBuffer.updateBuffer(pb -> null); 
  ```
- **優點**：去除了 Java 堆記憶體 (Heap) 的中轉，數據直接從 FFmpeg 指標寫入顯存能存取的記憶體區域。

---

## 3. Compose Desktop：Skia 引擎的深度整合
Compose Desktop 底層使用 Skia。要達到最高效能，應避開 `BufferedImage` 橋接。

### 最佳化設計路徑：
- **Skia Bitmap 映射**：
  直接使用 `org.jetbrains.skia.Bitmap`。
- **內存共享**：
  ```kotlin
  val skiaBitmap = Bitmap()
  skiaBitmap.allocPixels(ImageInfo.makeN32Premul(width, height))
  val pointer = skiaBitmap.peekPixels()?.addr ?: 0
  ```
  你可以獲取 Skia Bitmap 的 Native 指標，並告訴 JavaCV：**「直接把解碼結果寫到這個地址」**。這才是真正的 **Zero-copy**。

---

## 4. GPU 加速方案：YUV -> RGB Shader (終極方案)
如果 CPU 佔用率仍然過高，是因為色彩轉換 (CSC) 佔用了太多運算量。

### 進階構架：
1. **傳輸 YUV**：Grabber 輸出原始 `AV_PIX_FMT_YUV420P` (數據量只有 RGB 的一半)。
2. **Texture 上傳**：將 Y、U、V 三個平面分別上傳為 3 個 OpenGL 紋理 (Textures)。
3. **GPU 轉換**：編寫一個簡單的 **GLSL Shader**，在顯示的瞬間進行轉換：
   ```glsl
   // 簡化示意
   color.r = y + 1.402 * (v - 0.5);
   color.g = y - 0.344 * (u - 0.5) - 0.714 * (v - 0.5);
   color.b = y + 1.772 * (u - 0.5);
   ```
4. **優點**：極致減輕 CPU 負擔，4K 60fps 也能輕鬆運作。

---

## 5. Swing / AWT：VolatileImage 與硬體表面
如果你必須在舊的 Swing 環境運行：
- **不要使用**：`BufferedImage` (它存儲在 System RAM)。
- **要使用**：`VolatileImage` (它存儲在 VRAM)。
- **策略**：
  使用 `FFmpegFrameFilter` 進行縮放。JavaCV 的 Filter 可以在 C 階層完成所有預處理，最後只呼叫一次 `Graphics2D.drawImage(volatileImage, ...)`。

---

## 6. 總結：高性能渲染 Checklist
1. **減少拷貝**：檢查數據是否進過 `byte[]` (Heap)，有的話就慢了，改用 `ByteBuffer.allocateDirect`。
2. **像素格式對齊**：確保 FFmpeg 輸出的格式與 UI 顯示格式一致 (通常是 BGRA 或 RGBA)，避免 UI 引擎二度轉換。
3. **垂直同步 (V-Sync)**：渲染頻率應與螢幕刷新率對齊，不要盲目 `while(true)`。
4. **Native 釋放**：JavaCV 的 `Frame` 在重用前，確保舊的 Native 指標已被正確處理，避免 OOM。
