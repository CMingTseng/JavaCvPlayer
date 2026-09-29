# JavaCvPlayer 與 Media3 (ExoPlayer) 結構對比報告

## 1. 概覽 (Overview)

JavaCvPlayer 旨在為 JVM/Compose 環境提供一個「類 Media3」的 API 介面。雖然在類別名稱與套件路徑 (package `androidx.media3.common`) 上儘可能對齊，但由於底層渲染引擎 (FFmpeg vs Android MediaCodec) 與平台顯示機制 (Compose/Swing vs Android Surface) 的本質差異，實作上存在不同程度的差異。

## 2. 組件相容性矩陣 (Compatibility Matrix)

| 組件名稱 | 相容程度 | 說明 |
| :--- | :--- | :--- |
| **Player (Interface)** | 🟡 部分相容 | 核心狀態與播放控制對齊，但缺乏 `Timeline` 與 `Tracks` 等進階功能。 |
| **MediaItem** | 🟡 部分相容 | 僅保留 `uri` 與 `mediaId`，不支援 DRM、Ads、Clipping 等進階配置。 |
| **AudioSink** | 🟢 功能相容 | 介面語義一致，但 JavaCvPlayer 實作為 JVM `SourceDataLine`。 |
| **VideoSink** | 🔴 結構差異 | Media3 依賴 Android `Surface`，JavaCvPlayer 則使用 `BufferedImage` 供 Compose 消費。 |
| **PlaybackParameters** | 🟢 完全相容 | `speed` 與 `pitch` 邏輯與 Media3 完全一致。 |
| **VideoSize** | 🟢 完全相容 | Width/Height 資料結構對齊。 |

---

## 3. 詳細分析 (Detailed Analysis)

### 3.1 Player 介面 (`Player.kt`)
*   **完全相容部分**:
    *   播放狀態常數 (`STATE_IDLE`, `STATE_READY`, 等)。
    *   控制介面 (`play()`, `pause()`, `seekTo()`, `setMediaItem()`)。
    *   監聽器模式 (`Player.Listener`)。
*   **內部修改影響**:
    *   **無 Looper 機制**: Media3 強制所有呼叫在 Application Looper。JavaCvPlayer 使用 **Kotlin 協程 (Main Scope)** 來確保 UI 執行緒安全性，更符合 KMP/Compose 習慣。
    *   **簡化事件機制**: 僅實作了最常用的 `onPlaybackStateChanged` 與 `onVideoSizeChanged`，並未實作 Media3 龐大的 `AnalyticsListener`。

### 3.2 媒體項目 (`MediaItem.kt`)
*   **差異點**: Media3 的 `MediaItem` 是一個巨大的配置對象。JavaCvPlayer 為了輕量化，僅保留了 `LocalConfiguration` 結構來存放播放路徑。
*   **外部影響**: 在 Android 端產生的複雜 `MediaItem` (含 DRM Token) 無法直接傳給 JavaCvPlayer。開發者應僅提取 `uri` 進行傳遞。

### 3.3 影像渲染 (`VideoSink.kt`)
*   **根本差異**:
    *   **Media3**: 採「推播式」。Decoder 將資料推到 `Surface`，由 Android 系統完成渲染。
    *   **JavaCvPlayer**: 採「拉取式」。`JvmVideoSink` 將 `VideoFrame` 轉換為 `BufferedImage` 並存放在 `MutableState` 中，觸發 Compose UI 重繪。
*   **結論**: 這兩者在渲染器層級是不可互換的。JavaCvPlayer 的 UI 必須使用專屬的 `VideoCanvas` 組件，而非 Android 的 `PlayerView`。

### 3.4 音訊渲染 (`AudioSink.kt`)
*   **內部差異**: Media3 的 `DefaultAudioSink` 处理了复杂的音訊軌道切換與越界處理。JavaCvPlayer 的 `JvmAudioSink` 專注於 **FFmpeg 浮點 PCM 到 JVM 音訊行的轉換**，並內建了 `Sonic` 演算法支援變速播放。
*   **功能表現**: 從外部看，兩者皆能達到變速播放、音量調整的功能。

---

## 4. 遷移與整合建議 (Integration Guide)

1.  **UI 層適配**: 
    *   不要試圖在 JVM 端使用 `androidx.media3.ui.PlayerView`。
    *   應使用 `JavaCvPlayer` 提供的 `VideoCanvas(player)`，它會自動訂閱 `JvmVideoSink` 的影像狀態。
    
2.  **播放控制**:
    *   邏輯層可以共用相同介面。例如，一個 ViewModel 可以持有一個 `Player` 引用，在 Android 端注入 `ExoPlayer`，在 JVM 端注入 `JvmJavaCvPlayer`。

3.  **依賴管理**:
    *   `common-lite` 模組可用於 KMP 專案，讓 Common 層程式碼能夠引用 `Player` 與 `MediaItem` 而不產生編譯錯誤。
