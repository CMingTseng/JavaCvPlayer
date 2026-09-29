# AVSYNC 對比分析報告：JavaFx vs. JvmJavaCvPlayer
# the report result is error
本報告針對 `JavaFxSwingComposeFFmpegPlayer` (正常播放) 與 `JvmJavaCvPlayer` (卡在第一幀) 的 AVSYNC 日誌與實作進行深入比對，分析導致卡頓的關鍵原因。

---

## 1. AVSYNC 日誌數據對比

| 指標 | JavaFx Player (Working) | JvmJavaCvPlayer (Broken) |
| :--- | :--- | :--- |
| **起始 `v_rel`** | **0 us** (準確對齊第一幀) | **467,133 us** (存在 ~0.5s 的偏移) |
| **起始 `a_rel`** | ~1,482 us | ~434,829 us |
| **初始 `diff`** | -1,482 us (RENDER) | **+32,304 us (WAIT)** |
| **循環頻率** | 低頻 (每 20 次迭代 Log 一次，約 1s 數次) | **極高頻 (每秒數千次)** |
| **決策行為** | RENDER -> 正常渲染 | **永久處於 WAIT** |
| **隊列狀態** | 穩定在 `q=9` ~ `q=10` | **持續飽和 `q=30`** |

---

## 2. 關鍵差異與問題診斷

### A. 忙碌等待 (Busy-Waiting) 的副作用
*   **JavaFx Player**: 遇到 `Decision.WAIT` 時，會計算 `sleepMs` 並執行 `delay(sleepMs)`。這會讓出執行緒並掛起協程，直到時間到達。
*   **JvmJavaCvPlayer**: 之前的修正將 `delay` 改為立刻 `return`。雖然 `BaseJavaCvPlayer` 會 `yield()`，但它隨即又進入下一次 `doSomeWork`。
*   **後果**: CPU 陷入 100% 的死循環。雖然時鐘（Wall Clock）在走，但每循環一次時鐘才增加 10us，要抵消 32ms 的 `diff` 需要循環 3200 次。這種「瘋狂輪詢」搶佔了解碼器（Loader）和 UI 繪製的資源，導致畫面看起來完全沒動。

### B. 時鐘初始化邏輯 (First Frame Sync)
*   **JavaFx Player**: 在 `handleMetadataAndClock` 中，只要收到第一個帶有有效 TS 的影格（無論音影），就呼叫 `initializeMediaClock`。
*   **JvmJavaCvPlayer**: 實作中強制等待 `totalAudioFramesWritten > 0` 且 `hasAudio` 為 true 才初始化時鐘。
*   **後果**: 如果音訊寫入 `AudioSink` 慢了幾百毫秒（例如 Buffer 未滿），影片渲染器就已經在拿影格對比「尚未對齊」的時鐘，產生了巨大的 `v_rel` 偏移，進而觸發 `WAIT`。

### C. 隊列阻塞與飢餓
*   **JvmJavaCvPlayer**: 因為 `WAIT` 決策不消耗影格，且循環速度極快，導致 `videoQueue` 迅速填滿（`q=30`）。而 Loader 可能因為 CPU 被 Render Loop 佔滿而解碼變慢，或者因為隊列滿了而停止讀取。

---

## 3. 建議修正方案

1.  **恢復計算型 Delay**: 在 `Decision.WAIT` 時，根據 `diffUs / 1000` 進行適當的 `delay`，確保不會造成 CPU 100% 輪詢。
2.  **優化初始化時機**: 參考 JavaFx 版本，只要 `videoQueue.peek()` 拿到第一幀且 Metadata 準備好，就應初始化時鐘基準點，不應死等音訊寫入。
3.  **對齊相對時間基準**: 確保 `v_rel` 從 0 開始，避免一開始就產生 400ms+ 的誤差。

---
報告完成日期：2024-05-22
報告狀態：等待指示修正
