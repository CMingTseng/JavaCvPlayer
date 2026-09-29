# JavaCvPlayer Libraries

本目錄包含 **JavaCvPlayer 跨平台影音播放器核心**與 **Media3 ExoPlayer Compose Multiplatform 介面適配套件**。

---

## 📐 架構總覽 (Architecture Overview)

專案採用分層與模組化設計，將媒體解碼、音訊處理、時間時鐘同步、各平台渲染接收器 (VideoSink) 與 Compose UI 畫布完全解耦。

```
+-----------------------------------------------------------------------+
|                    UI 層 (Compose / Material3)                         |
|  :core_ui_compose (VideoPlayerCanvas) / :media3_exoplyaer:ui_compose  |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                    核心介面層 (Media3 Player API)                      |
|                   androidx.media3.common.Player                       |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                   播放器引擎層 (BaseJavaCvPlayer)                      |
|  JvmJavaCvPlayer + KmpMediaClock + DefaultMediaFrameQueue (音視訊佇列)   |
+-----------------------------------------------------------------------+
                |                                      |
                v                                      v
+-------------------------------+      +-------------------------------+
|     解碼與音訊處理 (Core)      |      |     平台渲染接收器 (VideoSink)   |
| - FFmpegFrameLoader (JavaCV)  |      | - AndroidSurfaceVideoSink     |
| - JvmAudioSink + Sonic (變速) |      | - SkiaVideoSink (Skiko)       |
| - OpenCVVideoFrameProcessor   |      | - JavaFxPixelBufferVideoSink  |
+-------------------------------+      | - JvmBufferedImageVideoSink   |
                                       | - JvmVideoSink (Swing)        |
                                       +-------------------------------+
```

---

## 📦 模組詳細說明 (Submodules)

### 1. 核心與播放器引擎 (Core & Player Engine)

* **`:core`** (Kotlin Multiplatform 核心庫)
  * `commonMain`:
    * `BaseJavaCvPlayer`：繼承/實作 AndroidX Media3 `Player` 介面，統一管理播放器狀態 (`STATE_IDLE`, `STATE_READY`, `STATE_ENDED`)、播放/暫停、Seek、音量、播放速度、重複模式與 Playlist。
    * `KmpMediaClock`：影音同步核心時鐘，計算音訊/影像 PTS，控制渲染時間點並處理追格/丟幀。
    * `DefaultMediaFrameQueue`：多線程影音 Frame 緩衝佇列，提供解碼與渲染之間的同步隔離。
    * `VideoFrameData`：影格像素抽象包 (ByteBuffer, width, height, stride, pixelFormat)，解耦原生 JavaCV 類別。
  * `jvmMain`:
    * `JvmJavaCvPlayer`：JVM 端的播放器具體實作，採用並行渲染循環 (`videoRenderLoop` 與 `audioRenderLoop`)，避免佇列阻塞。
    * `FFmpegFrameLoader`：基於 FFmpeg / JavaCV `FFmpegFrameGrabber` 負責實體檔與串流之解碼。
    * `JvmAudioSink`：基於 Java Sound (`SourceDataLine`) 播放 PCM 音訊，內建 `Sonic` 演算法支援變速/變調。
    * `OpenCVVideoFrameProcessor`：支援透過 OpenCV 對影像進行即時處理解析與濾鏡處理。

---

### 2. 各平台專用 VideoSink 模組 (Rendering Sinks)

* **`:core-video-android`** (Android 平台)
  * `AndroidSurfaceVideoSink`：使用 Android `Surface.lockCanvas()` 繪製 RGBA 像素，預先配置與重用 Bitmap 以提升性能並減少 GC。
  * `AndroidBitmapVideoSink`：基於 Bitmap 的渲染接收器。

* **`:core-video-skia`** (Compose Desktop / Skiko 平台)
  * `SkiaVideoSink`：使用 Skia (Skiko) `Bitmap` 進行像素拷貝，並透過 `StateFlow` 時間戳脈衝觸發 Compose UI Recomposition。

* **`:core-video-javafx`** (JavaFX 平台)
  * `JavaFxPixelBufferVideoSink`：利用 JavaFX `PixelBuffer` 與 `WritableImage` 實現 Near Zero-copy 渲染，避免主 UI 線程阻塞。

* **`:core-video-compose-jvm`** (Compose JVM 平台)
  * `JvmBufferedImageVideoSink`：轉換影格為 `BufferedImage` / `ImageBitmap` 供 Compose Canvas 繪製。

* **`:core-video-swing`** (Java Swing 平台)
  * `JvmVideoSink`：提供 Swing UI 組件繪製能力。

---

### 3. UI 控制與介面庫 (UI Components)

* **`:core_ui_compose`** (跨平台 Compose 畫布)
  * `VideoPlayerCanvas`：跨平台 `expect / actual` 畫布組件。在 Android 端自動綁定 `SurfaceView` / `AndroidSurfaceVideoSink`；在 JVM 端依據 `RenderMode`（如 SKIA、SURFACE）綁定對應的 VideoSink 與 Compose 渲染層。

* **`:media3_exoplyaer`** (Media3 ExoPlayer Multiplatform 子專案 / Git Submodule)
  * `common_lite`：KMP 純 Kotlin 版 Media3 基礎介面（如 `Player`, `MediaItem`, `VideoSize`, `Timeline` 等）。
  * `common_ktx`：Android Media3 的 Kotlin 擴充與 `PlayerPool` 播放器池化管理。
  * `ui_compose`：Media3 Compose 控制組件（如 `PlayerSurface`, `PlayPauseButton`, `ProgressIndicator` 等）與 State 管理（如 `PresentationState`, `PlayPauseButtonState`）。
  * `ui_compose_material3`：基於 Material 3 設計規範的控制元件（如 `Player`, `MiniController`, `ArtworkLoader` 等）。

---

## 🚀 JitPack 發布與 Group ID 命名規則說明

### 1. Group ID 與快取路徑差異
- **JitPack 遠端依賴 (`jitpack.io`)**：
  - **Group ID 命名規則**：JitPack 會強制依據 GitHub 帳號與儲存庫名稱為 Group ID 命名，即 `com.github.CMingTseng.JavaCvPlayer`。
  - **引用範例**：`implementation("com.github.CMingTseng.JavaCvPlayer:core:v1.0.2")`
  - **Gradle 下載快取目錄**：`~/.gradle/caches/modules-2/files-2.1/com.github.CMingTseng.JavaCvPlayer/`
- **本地發布 (`publishToMavenLocal`)**：
  - **Group ID**：`idv.neo.ffmpeg.media.player`
  - **本地 Maven 倉庫目錄**：`~/.m2/repository/idv/neo/ffmpeg/media/player/`

### 2. 開發與部署模式切換
- **切換為本地源碼開發**：
  1. 取消 `settings.gradle.kts` 中 `includeExternalProject` 相關行的註解。
  2. 在各應用模組的 `build.gradle.kts` 中解開 `project(":core")` 等本地工程依賴。
- **切換為 JitPack 遠端依賴**：
  1. 註解 `settings.gradle.kts` 中的 `includeExternalProject` 行。
  2. 在各應用模組的 `build.gradle.kts` 中開啟 `com.github.CMingTseng.JavaCvPlayer:*:v1.0.2` 依賴。

---

## 🔗 Media3 ExoPlayer 的載入與整合形式

`media3_exoplyaer` 與本專案的整合採用了**「 Git 子模組 (Git Submodule) + Maven 遠端/預編譯構件 (Remote Artifact)」**雙軌機制：

1. **Git Submodule 形式**：
   * `libraries/media3_exoplyaer` 以 Git Submodule 形式關聯至 repository `git@github.com:cybernhl/media.git`。
   * 此方式使得開發者可以在本機保留完整 Media3 與 UI 擴充組件的原始碼，便於即時除錯與維護。

2. **Gradle 依賴載入形式**：
   * 在專案 `settings.gradle.kts` 中配置了 GitHub Maven 倉庫：
     `https://raw.githubusercontent.com/cybernhl/maven-repository/master/` 及 JitPack。
   * 在模組（如 `:core`, `:core_ui_compose`）的 `build.gradle.kts` 中，以 Maven Artifact 形式引入基礎層：
     ```kotlin
     compileOnly("com.github.cybernhl.media:lib-common-lite:727538c430")
     ```
   * 當需要調用本機源碼時，亦可透過 `settings.gradle.kts` 中的 `includeExternalProject` 動態將子模組資料夾掛載為 Gradle 本機 Project 進行編譯。

3. **介面層無縫適配 (Interface Adaptation)**：
   * 由於 `BaseJavaCvPlayer` 繼承並實現了 `androidx.media3.common.Player` 介面，因此 `libraries/media3_exoplyaer/libraries/ui_compose` 與 `ui_compose_material3` 中的所有 Compose 元件（如 `PlayPauseButton`, `ProgressIndicator` 等），均可直接無縫傳入 `JavaCvPlayer` 實例進行全功能 UI 操控。
