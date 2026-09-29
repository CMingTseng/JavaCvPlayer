# JavaCV (FFmpegFrameGrabber) 仿 ExoPlayer 構架映射

要將簡單的 JavaCV 範例封裝成類似 ExoPlayer/Media3 的專業播放器，首先需要理解兩者在組件上的對應關係。JavaCV 的 `FFmpegFrameGrabber` 是一個高度整合的組件，它同時承擔了 ExoPlayer 中多個模組的角色。

## 組件映射表

| ExoPlayer / Media3 概念 | JavaCV / FFmpeg 對應實現 | 說明 |
| :--- | :--- | :--- |
| **DataSource** | `FFmpegFrameGrabber(InputStream/String)` | Grabber 內建了協議處理 (File, HTTP, RTSP)。 |
| **Extractor** | `grabber.grabPacket()` | 負責從容器中提取壓縮的數據包。 |
| **Decoder (Renderer)** | `grabber.decodeV/A()` 或 `grabber.grabFrame()` | Grabber 封裝了 FFmpeg 的 `avcodec`。 |
| **SampleQueue** | 需要自定義 `LinkedBlockingQueue<Frame>` | JavaCV 範例通常是抓一幀播一幀，但 ExoPlayer 會緩衝。 |
| **MediaClock** | `System.nanoTime()` + `frame.timestamp` | 需要自行實作邏輯來比對系統時間與影幀時間。 |
| **ExoPlayerImplInternal** | `while(running) { ... }` 執行緒 | 需要一個獨立的背景執行緒來驅動 Grabber 與 Renderer。 |

## 架構設計建議

---
~~### 1. 拆解 FFmpegFrameGrabber~~
~~在簡單範例中，Grabber 同時負責「抓取」與「解碼」。為了達到 ExoPlayer 的性能，建議將其拆分為兩個層次：~~
~~- **IO/Demux 層 (Loader 線程)**：僅呼叫 `grabPacket()`，將數據包放入緩衝隊列。~~
~~- **解碼/渲染層 (Playback 線程)**：從隊列取出數據包進行解碼並輸出。~~

~~### 2. 引入 "Buffer" 概念~~
~~JavaCV 範例最常見的問題是「網路一抖動就卡頓」。~~
~~- **解決方案**：仿照 `LoadControl`。建立一個 `PriorityBlockingQueue`，預先抓取並解碼 50-100 幀存放在記憶體中。~~

~~### 3. 統一介面 (Media3 哲學)~~
~~不要直接在 UI 層調用 `FFmpegFrameGrabber`。~~
~~- 定義一個 `GenericPlayer` 介面。~~
~~- 讓 `JavaCVPlayer` 實現這個介面。~~
---

### [進階版] 跨平台 Media3 抽象層設計 (JVM/Desktop/Android)

為了實現與 ExoPlayer/Media3 的完全相容並具備跨平台潛力（JVM Desktop 或未來可能的跨平台框架），建議直接**實現或包裝 Media3 的 `Player` 介面**，而非自創 `GenericPlayer`。

#### 1. 介面層：`androidx.media3.common.Player`
`lib-common` 是一個純 Java 模組，理論上它可以運行在任何 JVM 環境。你的 JavaCV 實現應該直接繼承 `BasePlayer` (來自 `lib-common`)。

#### 2. 實作層：`JavaCvPlayer` (位於 JVM 側)
- **職責**：使用 JavaCV 的 `FFmpegFrameGrabber` 實現 `BasePlayer` 定義的所有行為。
- **好處**：當你在 Android 上時，你注入 `ExoPlayer`；在 Desktop 上時，你注入 `JavaCvPlayer`。UI 層 (甚至可能是 Compose Multiplatform) 看到的都是同一個 `Player` 介面。

#### 3. 構架對應：
- **`MediaItem` (Media3)** -> 傳遞給 JavaCV 作為路徑。
* **`Player.Listener` (Media3)** -> JavaCV 內部的狀態變更（如 `onPlaybackStateChanged`）需手動觸發回傳給 Listener。
* **`Surface` 映射**：
    - **Android**: 渲染到 `android.view.Surface`。
    - **JVM/JavaFX**: 將 JavaCV 的 `Frame` 轉換為 `WritableImage` 並在 `ImageView` 顯示。
