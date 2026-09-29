# 開發流程指引：從 Grabber 到 專業播放器

要開發一個基於 JavaCV 的強大播放器，建議遵循以下 Step-by-Step 流程，這能保證你在每一階段都能解決對應的複雜度。

## 第一階段：建立「時間主軸」 (The MediaClock)
這是最底層的基礎。
- **任務**：不要使用 `Thread.sleep()` 來控制進度。
- **做法**：實作一個 `Clock` 類別，記錄 `startTime`。
- **關鍵**：播放器的當前時間 `currentTime = (System.nanoTime() - startTime) / 1000`。所有的影音同步都必須對齊這個 `currentTime`。

## 第二階段：生產者與消費者模型 (Decoupling)
解決範例中「抓取阻塞渲染」的問題。
- **Producer (抓取線程)**：不斷執行 `grabber.grabFrame()`，並將 `Frame` 物件存入兩個隊列：`videoQueue` 與 `audioQueue`。
- **Consumer (渲染線程)**：從隊列中取出 `Frame`，根據其 `timestamp` 決定何時顯示。

## 第三階段：實現 AV 同步 (AV Sync)
參考 ExoPlayer 的 Master-Slave 模式。
- **音訊驅動**：音訊一旦寫入 `AudioTrack` (或 JavaSound)，獲取其播放位置作為 Master Clock。
- **影像對齊**：
    ```java
    if (frame.timestamp > masterClock.getTime()) {
        wait(); // 影像太快
    } else if (frame.timestamp < masterClock.getTime() - threshold) {
        drop(); // 影像太慢，直接跳過不畫
    } else {
        render(); // 繪製到畫面上
    }
    ```

## 第四階段：異常處理與恢復 (Robustness)
- **斷線重連**：當 `grabFrame` 回傳 null 或拋出異常時，重啟 Grabber 並 Seek 到最後記錄的時間點。
- **資源釋放**：確保在 `release()` 時關閉執行緒、關閉 Grabber、清理 Native 記憶體 (非常重要，JavaCV 容易記憶體洩漏)。

## 第五階段：封裝 API (Media3 Style)
- **MediaItem**：建立一個包含 URL 的數據類。
- **Listener**：提供 `onStateChanged(int state)` 回調（IDLE, BUFFERING, READY, ENDED）。
- **Seek 實現**：呼叫 `grabber.setTimestamp(us)`，並清空目前的緩衝隊列。
