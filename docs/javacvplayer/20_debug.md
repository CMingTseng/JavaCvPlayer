# 播放器調試與 AV Sync 優化指南

當出現「聲音流暢、影像延遲、卡頓或影像幾秒後才出現」等症狀時，通常問題出在 **Video Render Loop**、**Timestamp Scheduling** 或 **Frame Queue** 的管理，而非解碼速度本身。

---

## 1. 核心設計考量

為了達到 Media3 等級的播放效能，架構應包含：
- **Player Abstraction**: 統一的播放器行為定義。
- **Clock**: 穩定且精準的媒體時鐘（通常以 Audio 為 Master）。
- **AV Sync**: 基於時鐘的影像呈現排程。
- **Buffer Queue**: 解耦「解碼」與「渲染」的緩衝區。
- **Renderer Separation**: 獨立的音訊與影片渲染執行緒。

---

## 2. 常見的錯誤優化

### 問題 A：解碼驅動渲染 (Decode-driven Rendering)
**錯誤做法**：
```kotlin
while (playing) {
    val frame = grabber.grab()
    render(frame)
}
```
**原因**：這會導致 `Decode Clock == Render Clock`。當解碼稍有抖動，畫面就會跟著卡頓。應將影像跟隨 Master Clock（AudioClock）進行呈現。

### 問題 B：錯誤的丟幀策略
**錯誤做法**：僅依據 Queue Size 丟棄舊影格（例如 `if (queue.size > 2) drop()`）。
**原因**：Media3 的丟幀是基於 **Clock Lateness**。如果影像相對於時鐘太晚（例如超過 30ms），才應該丟棄，而不是因為隊列滿了就丟。

### 問題 C：時間戳混淆
**錯誤做法**：使用 `System.currentTimeMillis()` 或 `grabber.getTimestamp()` 進行同步。
**原因**：應使用每個影格自帶的 `frame.timestamp` (PTS, Presentation TimeStamp)。這代表的是媒體時間軸上的絕對位置。

---

## 3. 正確的 Media3-like 架構

架構層級應為：
1. **Demux/Decode**: 獨立 Loader 負責將影格填入 `AudioQueue` 與 `VideoQueue`。
2. **Audio Renderer**: 從 `AudioQueue` 讀取並寫入 `AudioSink`，並更新 `PlayerClock`。
3. **Video Renderer**: 從 `VideoQueue` 讀取，比對 `PlayerClock` 決定渲染時機。

**渲染決策邏輯**：
- `diffUs = videoPtsUs - audioClockUs`
- `diffUs > 0`: 影片太早，執行 `delay(waitTime)`。
- `diffUs < -threshold`: 影片太晚，執行 **Drop Frame** 並處理下一幀。
- `else`: 準時，立刻渲染。

---

## 4. AV Sync 體檢工具 (Instrumentation)

透過詳細的 Log 可以快速定位同步問題。

### 步驟 1：確認 PTS 連續性
觀察解碼出的 PTS 增量是否穩定（例如 30fps 應約為 33ms）。
```kotlin
println("VIDEO pts=$pts delta=${pts - lastVideoPts}")
```

### 步驟 2：記錄渲染決策 (Decision Trace)
建立 `Decision` 列舉，並在 Render Loop 中記錄：
```kotlin
enum class Decision { RENDER, WAIT, DROP }

val diffUs = videoPts - audioClock
val decision = when {
    diffUs > 20_000 -> Decision.WAIT
    diffUs < -40_000 -> Decision.DROP
    else -> Decision.RENDER
}
println("AVSYNC v=$videoPts a=$audioClock diff=$diffUs q=${videoQueue.size} d=$decision")
```

---

## 5. 故障診斷表

| 症狀 | 可能原因 |
| :--- | :--- |
| **永遠處於 WAIT** | 時鐘計算錯誤或 PTS 基準點不對。 |
| **永遠處於 DROP** | 丟幀閾值太嚴苛或解碼效能嚴重不足。 |
| **Queue 持續暴增** | Scheduler 邏輯導致 Render 無法有效消耗影格。 |
| **Queue 永遠為 0** | 丟幀太過激進或 Loader 速度太慢。 |
| **Diff 劇烈跳動** | Master Clock 獲取方式不穩定（例如未做內插補點）。 |

---

## 6. AVSyncDebugger 模型
```kotlin
data class AvSyncSnapshot(
    val videoPtsUs: Long,
    val audioClockUs: Long,
    val diffUs: Long,
    val queueSize: Int,
    val decision: Decision
)
```
