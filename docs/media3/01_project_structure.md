# Media3 專案結構與模組化設計哲學

Media3 是一個模組化程度極高的專案。這種設計並非為了增加複雜度，而是為了解決 Android 媒體開發中的兩大痛點：**二進制體積 (Binary Size)** 與 **功能解耦 (Decoupling)**。

## 核心模組用途

| 模組名稱 | 角色 | 核心功能與職責 |
| :--- | :--- | :--- |
| `lib-common` | **基礎協議** | 定義 `Player` 介面、`MediaItem` 格式，讓所有模組有共同通訊基礎。 |
| `lib-exoplayer` | **執行引擎** | 實現複雜的播放調度、AV 同步、緩衝管理與選軌邏輯。 |
| `lib-session` | **通訊介面** | 負責跨進程 (IPC) 控制，讓外部 App 或系統通知欄能操作播放器。 |
| `lib-extractor` | **解析專家** | 深入了解 MP4, MKV, TS, FLV 等容器結構，負責「拆包」。 |
| `lib-datasource` | **傳輸層** | 處理 HTTP/S, UDP, File, Asset, ContentProvider 等多樣化的數據來源。 |
| `lib-decoder` | **解碼框架** | 提供硬體 (`MediaCodec`) 與軟體 (FFmpeg, Av1) 解碼器的統一封裝。 |
| `lib-ui` | **呈現層** | 提供高性能的 `SurfaceView` / `TextureView` 整合與播放控制器 UI。 |

## 為什麼要分這麼細？ (設計優勢)

### 1. 最小化二進制體積 (Minimizing APK Size)
如果你的 App 只需要播放本地 MP4，你不需要包含 `exoplayer-dash` 或 `exoplayer-hls` 的程式碼。Media3 允許開發者「按需引入」，避免載入不需要的解碼器或網路協議。

### 2. 介面與實現完全分離
- **UI 與 Player 分離**：你的自定義 View 只依賴於 `lib-common` 的 `Player` 介面。這意味著未來你可以把底層從 ExoPlayer 換成另一個播放器，而 UI 完全不需要修改。
- **Session 與 Player 分離**：透過 `lib-session`，播放器可以運行在 `Service` 中。即使 Activity 被銷毀，播放仍可持續，且 Controller 與 Session 之間的通訊邏輯被完美隱藏。

### 3. 強大的擴展性 (Extension Point)
Media3 在各處都留下了 Hook：
- 想換網路庫？替換 `DataSource`。
- 想自研解碼器？替換 `Renderer`。
- 想處理特殊封裝？替換 `Extractor`。

## 依賴層級 (Dependency Graph)

1.  **Level 0: Common** (無依賴)
2.  **Level 1: Functional Base** (依賴 Common) - 如 `extractor`, `datasource`, `decoder`。
3.  **Level 2: Core Engine** (依賴 Level 1) - `lib-exoplayer` 整合所有基礎組件。
4.  **Level 3: Feature Modules** - `exoplayer-dash`, `exoplayer-hls` 等特定協議支援。
5.  **Level 4: Wrapper & UI** - `lib-ui`, `lib-session` 以及各類軟體解碼器擴展 (如 `ffmpeg`)。
