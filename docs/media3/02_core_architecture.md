# ExoPlayer 核心設計架構深度探索

ExoPlayer 的核心設計理念是 **Pipeline (管線)** 與 **Event Loop (事件迴圈)**。它將媒體播放拆解為多個可替換的組件，並透過一個背景執行緒來調度這些組件。

## 核心設計圖 (簡化)

```text
[ MediaItem ] -> [ MediaSource ] -> [ MediaPeriod ] -> [ SampleStream ]
                                                               |
                                                        [ Renderer ] -> [ Video/Audio Output ]
                                                               ^
                                                        [ Internal Thread ] (ExoPlayerImplInternal)
                                                               |
                                                        [ MediaClock ] (Synchronization Master)
```

## Step-by-Step 播放流程說明

### 1. 初始化與資源準備 (Initialization)
當開發者呼叫 `ExoPlayer.Builder.build()` 時：
- 建立 `ExoPlayerImpl`：運行在主執行緒，負責與開發者 API 互動，維持 `Player` 狀態。
- 建立 `ExoPlayerImplInternal`：運行在背景 `HandlerThread`，這是播放器的「大腦」，負責所有的組件調度。
- 初始化預設組件：`DefaultTrackSelector` (選軌), `DefaultLoadControl` (緩衝控制), `DefaultRenderersFactory` (建立解碼器)。

### 2. 媒體項轉換 (MediaItem to MediaSource)
當呼叫 `setMediaItem(item)` 時：
- `MediaSource.Factory` 會將 `MediaItem` 轉換為對應的 `MediaSource` (例如 `DashMediaSource`, `ProgressiveMediaSource`)。
- `MediaSource` 定義了媒體的結構 (`Timeline`)，負責管理多個播放清單項目的切換，但不負責直接讀取二進制數據。

### 3. 加載與解析 (Loading & Extracting)
在背景執行緒中：
- `MediaSource` 建立 `MediaPeriod` (代表一個連續的播放時段)。
- `MediaPeriod` 內部啟動 `Loader` 執行緒（IO 線程）。
- `DataSource` (如 `DefaultHttpDataSource`) 開始讀取數據。
- `Extractor` (如 `Mp4Extractor`) 解析容器格式，並將數據寫入多個 `SampleQueue` (每個軌道一個隊列)。

### 4. 軌道選擇與渲染器綁定 (Track Selection)
- 當 `MediaPeriod` 準備就緒，會觸發 `TrackSelector`。
- `TrackSelector` 根據設備能力與使用者偏好，決定哪些軌道要被啟動。
- `ExoPlayerImplInternal` 建立對應的 `Renderer`，並透過 `SampleStream` 將其與 `SampleQueue` 連接。

### 5. 播放迴圈 (The Playback Loop: doSomeWork)
這是 ExoPlayer 的核心動能，位於 `ExoPlayerImplInternal.doSomeWork()`。
- **運作機制**：背景執行緒透過 `Handler` 不斷發送 `MSG_DO_SOME_WORK` 訊息，形成一個循環。
- **每次循環執行**：
    1. **更新進度**：調用 `updatePlaybackPositions()`，從 `MediaClock` 取得最新時間。
    2. **驅動渲染**：呼叫每個啟動中的 `Renderer.render(positionUs, elapsedRealtimeUs)`。
    3. **緩衝判斷**：詢問 `LoadControl` 是否需要加載更多數據，或是否因為緩衝不足需要暫停。

---

## 影音同步機制 (AV Synchronization) 深度解析

影音同步是播放器的靈魂。ExoPlayer 採用「**主從同步 (Master-Slave Sync)**」機制。

### 1. 主時鐘 (Master Clock / MediaClock)
- 在大多數情況下，**音訊軌 (Audio)** 是 Master。
- **原因**：人類對音訊的微小中斷或頻率偏移極其敏感，但對影像跳格 (Drop frame) 容忍度較高。
- `AudioRenderer` 會根據 `AudioTrack` 實際輸出的硬體進度，回報精確的時間給 `DefaultMediaClock`。

### 2. 影像追趕與等待 (Video Sync Logic)
`VideoRenderer` 在執行 `render()` 時，會執行以下邏輯：
- **計算偏移 (Offset)**：`frame_pts - master_clock_time`。
- **影像太慢 (Late)**：如果偏移大於門檻值（例如 30ms），代表影像跟不上音訊。`VideoRenderer` 會通知 `MediaCodec` 直接跳過該緩衝區，不進行渲染。
- **影像太快 (Early)**：如果影像還沒到播放時間，渲染器會記住這個影格，等到下一次 `doSomeWork` 迴圈再檢查，直到時間點吻合才送入 `Surface`。

### 3. 無音訊時的同步
如果沒有音訊軌，ExoPlayer 會切換到 `StandaloneMediaClock`，這是一個基於系統 `SystemClock.elapsedRealtime()` 的時鐘，以確保純影片播放也能維持正常倍速。

---

## 為什麼這樣設計？

1.  **高度可客製化 (Customizability)**:
    - 所有的 IO、解析、解碼、渲染都被介面化，可以輕易替換（如 FFmpeg 軟解）。
2.  **效能與響應性 (Performance & Responsiveness)**:
    - 所有的耗時操作都在背景執行緒，保證 UI 永遠不會因為網路卡頓或解碼壓力而掉幀。
3.  **Media3 統一介面 (Common Interface)**:
    - 透過 `Player` 介面，讓開發者只需關心控制邏輯，而不用處理底層複雜的時鐘同步。
