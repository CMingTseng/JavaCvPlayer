# Media3 進階模組解析：Transformer, Lottie Effect 與 Inspector

除了核心播放功能，Media3 還包含了一些針對「媒體編輯」、「視覺增強」與「除錯分析」的專業模組。

---

## 1. lib-transformer：媒體轉碼與編輯引擎

這是 Media3 中最重頭戲的新模組，旨在替代過時且難以使用的 `MediaMuxer` 與手動 `MediaCodec` 拼接。

- **核心用途**：
    - **轉碼 (Transcoding)**：改變影片的解析度、位元率或編碼格式（如將 4K H.264 轉為 1080p HEVC）。
    - **剪輯 (Trimming)**：快速截取影片的某個時間段。
    - **合成 (Composition)**：將多個影片、音軌、圖片串接或疊加在一起。
    - **導出 (Exporting)**：將 `ExoPlayer` 支援的播放列表直接輸出成一個實體檔案。
- **對 JavaCV 的啟發**：
    - 如果你的 JavaCV 應用不只是要「播放」，還要能「存檔」或「剪輯」，你應該參考 `Transformer` 的設計架構，它將 `Grabber` -> `Effects` -> `Recorder` (FFmpegFrameRecorder) 串聯成一條高效管線。

---

## 2. lib-effect-lottie：動畫向量特效

這是一個非常酷的擴充模組，它將著名的 **Lottie (Airbnb 開源的動畫庫)** 整合進了影片處理管線。

- **核心用途**：
    - **動態貼圖**：在影片中加入 JSON 格式的向量動畫。
    - **動態浮水印**：傳統浮水印是靜態圖片，使用 Lottie 可以輕鬆加入精美且輕量化的動態 Logo。
    - **場景裝飾**：例如在影片特定時間點噴出「愛心」或「煙火」特效。
- **核心類別**：`LottieOverlay`。它本質上是將 Lottie 的每一幀渲染到一個 `Canvas` 上，然後作為 `GlEffect` 疊加到影片影格中。

---

## 3. lib-inspector：媒體檔案的診斷與提取

這是一個工具類模組，主要用於「診斷」與「深度解析」媒體檔案。

- **核心用途**：
    - **元數據分析**：比 `MediaExtractor` 更強大，能提取更詳細的編碼參數、HDR 資訊與封裝格式細節。
    - **MediaExtractor 的替代品**：在某些不穩定或受限的 Android 裝置上，它提供了更一致的數據提取行為。
    - **除錯分析**：開發者可以用它來檢查為什麼某個特定的影片檔案在播放時會出現問題。
- **子模組 `lib-inspector-frame`**：
    - 專門用於「逐幀檢查」解碼後的數據，對於需要開發精準定位或影格分析的應用非常有用。

---

## 4. 總結：開發者的選擇建議

| 模組 | 什麼時候該用它？ | 在 JavaCV 中的對應 |
| :--- | :--- | :--- |
| **lib-transformer** | 當你需要實現「導出影片」、「影片剪輯」或「格式轉換」功能時。 | `FFmpegFrameRecorder` 的高級封裝。 |
| **lib-effect-lottie** | 當你想要在影片中加入炫酷的動態向量動畫、動態 Logo 時。 | 需要整合 Lottie-Java 與 OpenCV Canvas 繪製。 |
| **lib-inspector** | 當你需要深入了解影片內容（如檢查 HDR 參數、編碼層級）時。 | `grabber.getMetadata()` 與 `avformat` 原始 API。 |

這些模組展示了 Media3 如何從一個「播放器」演變成一個完整的「媒體處理平台」。對於 `JavaCvPlayer` 的開發者，`lib-transformer` 的管線設計是最值得借鑑的部分。
