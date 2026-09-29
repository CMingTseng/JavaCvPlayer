# 架構對比：一體化 (JavaCV) vs. 模組化 (ExoPlayer)

這份文件解析了 JavaCV 的 `FFmpegFrameGrabber` 與 AndroidX Media3/ExoPlayer 在功能組件上的對等關係，幫助開發者理解兩者的設計差異。

---

## 1. 組件功能對等表

| 功能階段 | JavaCV / FFmpeg 實現 | Media3 / ExoPlayer 實現 |
| :--- | :--- | :--- |
| **數據讀取 (IO)** | `FFmpegFrameGrabber` (內建 `libavformat` 協議層) | `lib-datasource` (OkHttp, Cronet, File...) |
| **容器解析 (Demux)** | `FFmpegFrameGrabber` (內建 `libavformat`) | `lib-extractor` (Mp4Extractor, TsExtractor...) |
| **解碼 (Decode)** | `FFmpegFrameGrabber` (內建 `libavcodec`) | `lib-decoder` (MediaCodec, FFmpeg擴展...) |
| **核心調度 (Control)** | **你需要實作的部分** | `lib-exoplayer` (ExoPlayerImplInternal) |
| **基礎定義 (Spec)** | `JavaCV` 內部對象 | `lib-common` (Player, MediaItem, Format) |

---

## 2. JavaCV 播放器的開發重心
正如你觀察到的，使用 JavaCV 開發時，你省去了組合 `extractor` 與 `datasource` 的麻煩，但這也意味著你的開發重心會與 ExoPlayer 不同：

1. **不需要處理底層協議**：你不需要寫 `HttpDataSource`，因為 FFmpeg 幫你寫好了。
2. **重心在「調度」與「同步」**：因為 `FFmpegFrameGrabber` 太過封裝，你的主要工作是實作類似 `lib-exoplayer` 的 **Event Loop (事件循環)**，處理影音同步、緩衝控制與 Seek 邏輯。
3. **相容性工作**：你的主要任務是將 `FFmpegFrameGrabber` 的輸出，轉化為 `lib-common` 定義的標準格式，以便與 Media3 生態相容。

---

## 3. 為什麼 ExoPlayer 要拆得這麼散？
雖然 JavaCV 的「一體化」很方便，但 ExoPlayer 的「模組化」有其必要性：
- **二進制體積**：如果你只需要播 MP4，ExoPlayer 可以不包含 MKV 的解析程式碼。而 FFmpeg 通常是整塊包進去。
- **硬體加速**：ExoPlayer 的 `lib-decoder` 能完美對接 Android 的 `MediaCodec` 硬體解碼。JavaCV 雖然支援硬體加速，但在 Android 上的整合複雜度較高。
- **可擴展性**：如果你想自定義一種全新的加密網路協議，在 ExoPlayer 中你只需要寫一個 `DataSource`；但在 JavaCV 中，你可能需要重新編譯整個 FFmpeg。

---

## 4. 總結
你的理解是完全正確的。
- **JavaCV** 提供的是一個**現成的播放引擎核心**。
- **ExoPlayer** 提供的是一個**建立播放引擎的工具箱**。

這就是為什麼在跨平台 (JVM Desktop) 應用中，使用 JavaCV 是更明智的選擇——因為它用一個 `FFmpegFrameGrabber` 就取代了 Media3 在 Android 上賴以生存的多個複雜模組。
