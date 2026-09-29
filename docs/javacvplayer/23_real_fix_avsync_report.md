# JavaCvPlayer 影音同步與渲染失效檢討修正報告

## 1. 問題概述
在開發基於 JavaCV 的跨平台播放器時，發現桌面端（Compose Desktop）出現以下嚴重問題：
1. **黑屏或畫面閃爍**：雖然有聲音，但影像無法顯示，或顯示不穩定。
2. **影音不同步**：聲音與畫面時間偏差過大，甚至導致播放卡死。
3. **切換媒體失效**：執行「下一首」或「重新播放」時，播放器進入永久載入或無畫面狀態。

---

## 2. 核心技術根因分析

### A. 渲染層：影像資料生命週期管理錯誤
*   **錯誤現象**：`JvmVideoSink` 直接將 `Java2DFrameConverter` 產生的 `BufferedImage` 傳遞給 Compose UI。
*   **技術細節**：JavaCV 的 `Java2DFrameConverter.convert()` 返回的 `BufferedImage` 通常僅是底層 `AVFrame` 記憶體的封裝（Wrapper）。當 `videoRenderLoop` 呼叫 `frame.release()` 釋放 C 記憶體時，該 `BufferedImage` 的內容隨即失效或被回收，導致 UI 繪製時讀到空資料或產生黑屏。
*   **UI 刷新機制**：Compose Desktop 的 `MutableState` 預設使用 `structuralEqualityPolicy`。如果 `BufferedImage` 的物件引用 (Reference) 沒變，即使內容更新，UI 也可能不會觸發重繪。

### B. 同步層：初始化死結 (Deadlock)
*   **錯誤現象**：播放器啟動後卡在第一幀，不播放。
*   **技術細節**：
    1.  `videoRenderLoop` 在有音軌的情況下，會等待 `MediaClock.isStarted`。
    2.  `MediaClock` 通常由 `audioRenderLoop` 寫入第一幀音訊時初始化。
    3.  若影片的影像隊列 (`videoQueue`) 優先被填滿（預設 30-60 幀），`FFmpegFrameLoader` 會因為隊列阻塞而停止解碼。
    4.  這導致後續的音訊影格無法進入 `audioQueue`，音訊循環無法啟動時鐘，影像循環也因此永遠等待，形成死結。

### C. 生命週期：Loader 的競態條件 (Race Condition)
*   **錯誤現象**：切換影片時，畫面無法恢復。
*   **技術細節**：當切換媒體時，舊的 `loaderJob` 可能尚未完全 `cancel()` 並執行 `releaseGrabber()`。新的 `loaderJob` 啟動並建立了新的 `grabber`，隨後舊任務的 `finally` 塊執行，誤將新建立的 `grabber` 釋放並設為 `null`。

---

## 3. 修正方案與實作細節

### A. 實作影像深拷貝 (Deep Copy) 與 強制重繪
*   **修改點**：`JvmVideoSink.kt`
*   **做法**：在 `render()` 方法中，使用 `Graphics2D` 將 `BufferedImage` 的內容繪製到一個全新的實例中。
*   **狀態更新**：將 `videoBitmap` 宣告為 `neverEqualPolicy()`，確保每次指派新影像時，Compose 都會無條件重繪。

```kotlin
// 影像深拷貝與強制刷新
val copy = BufferedImage(bi.width, bi.height, type)
val g: Graphics2D = copy.createGraphics()
g.drawImage(bi, 0, 0, null)
g.dispose()
videoBitmap.value = copy // 觸發 neverEqualPolicy
```

### B. 引入死結預防機制 (Deadlock Prevention)
*   **修改點**：`JvmJavaCvPlayer.kt`
*   **做法**：調整 `videoRenderLoop` 的取用邏輯。
    1.  **優先取出 (Dequeue)**：不論時鐘是否啟動，優先從隊列取出影格。這能確保隊列有空位，避免阻塞 `Loader`。
    2.  **主動啟動時鐘**：如果影像隊列積壓超過 5 幀，或者媒體本身無音軌，則強制由影片的第一幀 PTS 初始化 `MediaClock`。這打破了「等待音訊啟動時鐘」的死結循環。

```kotlin
// 影像循環強制啟動邏輯
if (!clock.isStarted) {
    if (!metadata.hasAudio || videoQueue.size > 5) {
        clock.init(frame.timestampUs, audioSink.positionFrames)
    } else {
        delay(10) // 繼續等待音訊
    }
}
```

### C. 健全的 Loader 生命週期管理
*   **修改點**：`FFmpegFrameLoader.kt`
*   **做法**：
    1.  **重置標記**：在 `start()` 內明確將 `isReleased` 設為 `false`。
    2.  **原子化 Grabber 操作**：確保在啟動新 Grabber 前，舊的 Grabber 已經完全釋放，避免 Race Condition。
    3.  **預先設定格式**：在 `grabber.start()` 前設定 `pixelFormat`，優化解碼效能。

---

## 4. 總結
經過本次修正，`JavaCvPlayer` 在 JVM 環境下的穩定性顯著提升：
*   **渲染穩定性**：解決了 Frame Release 導致的黑屏問題，確保畫面顯示完整性。
*   **同步精準度**：透過影像/音訊雙啟動機制，消除了啟動死結，並讓 AVSync 誤差穩定在可接受範圍內。
*   **架構對齊**：此模式更接近 Media3 的 `Renderers` 概念，方便後續擴展至其他平台。

**建議後續優化**：
*   實作 4K 影片的 Pre-fill 邏輯（音訊緩衝 200ms 後再啟動）。
*   加入影像 Drop Frame 閾值的動態調整機制。
