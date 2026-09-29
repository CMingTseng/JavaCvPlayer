# 技術分析：VolatileImage 直接寫入的可能性與 Skia 零拷貝優勢

本文件深入探討為什麼我們無法讓 JavaCV 的 `Frame` 直接寫入 `VolatileImage`，以及為什麼邁向 Skia `installPixels` (Zero-copy) 是 Compose Desktop 渲染的終極解決方案。

---

## 1. 為什麼 Java2DFrameConverter 沒有 VolatileImage 版本？

在開發過程中，一個直覺的想法是：「既然 `VolatileImage` 在 VRAM 中很快，為什麼不讓 JavaCV 直接把解碼後的數據丟進去？」

### 技術限制：
1. **設備相關性 (Device Dependency)**：
   `VolatileImage` 是與特定的顯卡硬體和螢幕配置 (`GraphicsConfiguration`) 綁定的。`Java2DFrameConverter` 是一個通用的數據處理工具，它產生的 `BufferedImage` 是設備無關的 (Device-independent)，這使得它能在任何環境下運作，但代價就是存在於 System RAM。

2. **封裝與存取權限**：
   AWT 的 `VolatileImage` 嚴格封裝了底層的 VRAM 指標。Java/Native 代碼**無法直接取得**該 VRAM 地址並進行寫入。
   *   **唯一寫入途徑**：必須透過 `vi.createGraphics()` 取得畫筆，然後呼叫 `g.drawImage(source, ...)`。
   *   **強迫中轉**：這個 `source` 必須是一個 `Image` (通常是 `BufferedImage`)。這意味著你永遠繞不開「先在 RAM 建立 BufferedImage，再上傳到 VRAM」這個動作。

---

## 2. VolatileImage 方案的效能瓶頸真相

目前的 `JvmVideoSink` 優化路徑如下：
`Native Frame` -> `BufferedImage` (RAM) -> **`g.drawImage` (Upload)** -> `VolatileImage` (VRAM) -> **`vi.snapshot` (Download)** -> `Compose Image`

雖然 AWT 的硬體加速處理了繪製，但產生了兩個嚴重的搬運損耗：
1.  **Upload (RAM -> VRAM)**：將解碼後的像素點上傳至顯示卡。
2.  **Download (VRAM -> RAM)**：為了給 Compose 使用，`vi.snapshot` 會強制 GPU 將數據回傳給 CPU。在 4K 測試中，這一步產生了約 **9.5ms** 的延遲，是效能的主要殺手。

---

## 3. 為什麼 Skia `installPixels` 是正確路徑？

Compose Desktop 底層使用 Skia 引擎，它並不使用 AWT 的渲染管線。使用 `VolatileImage` 其實是跨錯了場景。

### Skia 零拷貝 (Zero-copy) 的運作原理：
1.  **內存地址共享**：
    我們在 Native 層（或使用 `ByteBuffer.allocateDirect`）申請一塊「直接記憶體」。
2.  **像素映射**：
    呼叫 Skia 的 `Bitmap.installPixels(address)`。這會告訴 Skia：**「這塊記憶體地址就是我的像素數據來源，你直接去這裡讀取。」**
3.  **直接寫入**：
    讓 FFmpeg 的 `sws_scale` 工具直接將 YUV 轉換後的 RGB 數據寫入該 `address`。

### 優勢對比：
- **無需 BufferedImage 中轉**：數據從 Native 轉換後直接到位。
- **無需回讀 (No Snapshot)**：Skia 在 GPU 渲染時直接存取該地址，徹底消除 VRAM -> RAM 的 9.5ms 開銷。
- **極低 CPU 負擔**：省去了所有 AWT 物件的新增與管理，大幅降低 GC 頻率。

---

## 4. 結論與下一步

`VolatileImage` 的嘗試為我們量化了硬體加速的潛力，但也揭露了 AWT 與 Compose 整合時的天然瓶頸。

**下一步行動：**
- 捨棄 `Java2DFrameConverter`。
- 引入 `org.bytedeco.ffmpeg.global.swscale` 進行 Native 級別的色彩轉換。
- 實作 `SkiaVideoSink`，利用 `installPixels` 達成真正的 4K 零拷貝渲染。
