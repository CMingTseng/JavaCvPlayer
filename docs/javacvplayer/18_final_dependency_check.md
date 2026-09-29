# JavaCvPlayer 最終依賴過濾清單

這份文件總結了 Media3 的哪些模組對 `JavaCvPlayer` 是有價值的，哪些是冗餘的。

---

## 1. 核心結論：瑞士刀不需要樂高零件

`FFmpegFrameGrabber` 本身就是一個「全能引擎」，它在內部已經實現了 Media3 許多獨立模組的功能。

| Media3 模組 | JavaCvPlayer 是否需要？ | 替代方案 / 理由 |
| :--- | :---: | :--- |
| **`lib-common`** | **必須** | 用於定義 `Player` 介面與 `MediaItem` 數據結構，達成相容性。 |
| **`lib-effect`** | **建議參考** | 參考其濾鏡設計邏輯。具體實作可改用 OpenCV 或 FFmpeg Filter。 |
| **`lib-datasource`** | **不需要** | FFmpeg 內建協議棧 (HTTP/RTSP...) 比 Android 內建的更強。 |
| **`lib-container`** | **不需要** | FFmpeg 的 `avformat` 已處理所有容器解析。 |
| **`lib-extractor`** | **不需要** | FFmpeg 已內建所有主流格式的 Extractor。 |
| **`lib-decoder`** | **不需要** | FFmpeg 已內建所有主流格式的 Decoder。 |
| **`lib-inspector`** | **不需要** | FFmpeg 的元數據提取能力已達專業級，無需額外診斷。 |
| **`lib-transformer`** | **視需求** | 僅在你需要實作「影片導出/剪輯」功能時參考其管線設計。 |

---

## 2. 關於 LottieOverlay 的 JavaCV 實現建議

雖然你不直接使用 `lib-effect-lottie`，但其實作思路對你很有幫助：

1. **引入 Lottie-Java**：這是一個純 Java 實作的 Lottie 渲染器。
2. **渲染流程**：
   - 每一個影片影格，計算對應的 Lottie 進度。
   - 將 Lottie 渲染至一個透明的 `BufferedImage`。
   - 將 `BufferedImage` 轉為 OpenCV `Mat`。
   - 使用 OpenCV 的覆蓋邏輯疊加至影片。
3. **優勢**：你可以實現跟 Media3 完全一樣的「向量動態浮水印」效果，且能跑在 JVM Desktop 上。

---

## 3. 最終架構建議

你的 `JavaCvPlayer` 應該是一個 **「瘦身版 Media3」**：

1. **包裝層**：實現 `androidx.media3.common.Player`。
2. **數據源**：僅依賴 `org.bytedeco:javacv` (FFmpeg)。
3. **橋接層**：建立 `MediaItem` -> `FFmpegFrameGrabber` 的參數轉換器。
4. **渲染層**：根據環境自動切換 `VideoSink` (Skia / PixelBuffer)。

**一句話總結**：
你只需要 Media3 的 **「名份 (lib-common)」** 和 **「靈感 (lib-effect)」**，不需要它的 **「肌肉 (datasource/decoder/extractor)」**，因為 JavaCV 本身的肌肉已經足夠強壯了。
