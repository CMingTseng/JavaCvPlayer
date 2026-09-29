# 跨平台 Media3 相容性策略 (JVM Desktop & Android)

這份文件探討如何建立一個既能運行在 Android (使用原生 ExoPlayer) 又能運行在 JVM Desktop (使用 JavaCV 封裝) 的統一播放器構架。

## 1. 核心思想：依賴 lib-common
Media3 的 `lib-common` 模組是關鍵。它定義了 `Player` 介面，且不依賴 Android SDK 的核心播放邏輯。
- **Android 端**：直接使用 `androidx.media3.exoplayer.ExoPlayer`。
- **JVM/Desktop 端**：開發 `JavaCvPlayer`，使其實現 `androidx.media3.common.Player`。

## 2. 構架拓撲

```text
[ UI / Business Logic (Shared) ]
          |
    [ Player Interface (lib-common) ]
          |
    ---------------------
    |                   |
[ ExoPlayer ]    [ JavaCvPlayer ]
 (Android)         (JVM Desktop)
```

## 3. JavaCvPlayer 實現重點

### A. 繼承 `BasePlayer`
`BasePlayer` 幫你處理了大部分繁瑣的 Listener 管理與狀態檢查。你只需要實現核心的異步操作（如 `prepare()`, `seekTo()`, `setPlayWhenReady()`）。

### B. 狀態映射 (State Mapping)
將 JavaCV 的執行緒狀態映射到 Media3 標準：
- `grabber.start()` -> `STATE_BUFFERING`
- 緩衝足夠 -> `STATE_READY`
- `grabPacket()` 回傳 null -> `STATE_ENDED`

### C. 渲染表面抽象 (Surface Abstraction)
這是最困難的部分。Media3 的 `setVideoSurface` 接收的是 `android.view.Surface`。
- **解決方案**：在 Desktop 端，你需要一個「橋接器」。
- 定義一個 `VideoSink` 介面。在 Android 封裝 `Surface`；在 Desktop 封裝 JavaFX 的 `Canvas` 或 Compose 的 `DrawScope`。

## 4. 未來擴展 (iOS / Web)
若要支援 iOS，則需要將 `JavaCvPlayer` 的邏輯用 Kotlin Multiplatform (KMP) 重寫，並在 Native 側呼叫 AVFoundation 或同樣使用 FFmpeg 的 C API。

## 5. 為什麼要這樣做？
- **代碼重用**：你的播放清單管理、播放順序、UI 控制邏輯只需要寫一次。
- **一致性**：無論在什麼平台上，你的 App 行為（如自動下一首、錯誤處理）都是一致的。
