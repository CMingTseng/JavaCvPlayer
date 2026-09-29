# 深度解讀：播放器核心循環邏輯 (Internal Loop)

仿照 ExoPlayer 的 `ExoPlayerImplInternal`，一個專業的 JavaCV 播放器需要一個「心臟」循環。

## 虛擬代碼結構 (核心邏輯)

```java
public void run() {
    while (running) {
        // 1. 檢查緩衝是否充足
        if (videoQueue.size() < min_buffer && !isEndOfStream) {
            setState(BUFFERING);
            continue; // 等待抓取線程填充數據
        }
        setState(READY);

        // 2. 獲取當前主時鐘時間 (以音訊為準)
        long playPositionUs = audioOutput.getTimestampUs();

        // 3. 處理影像渲染
        Frame videoFrame = videoQueue.peek();
        if (videoFrame != null) {
            if (videoFrame.timestamp <= playPositionUs) {
                // 時間到了，或是落後了
                videoQueue.poll();
                if (videoFrame.timestamp >= playPositionUs - late_threshold) {
                    renderToSurface(videoFrame);
                } else {
                    // 太慢了，丟棄這幀 (Drop frame)
                }
            } else {
                // 影像超前，不操作，等待下一個循環
            }
        }

        // 4. 精確休眠
        // 不要休眠太久，否則會錯過渲染時機。建議 1-5ms 或使用 LockSupport.parkNanos
        sleep(2); 
    }
}
```

## JavaCV 實作中的致命陷阱

### 1. 記憶體管理 (Off-heap Memory)
JavaCV 的 `Frame` 物件包含 Native 記憶體。
- **解決方案**：在 `videoQueue.poll()` 並渲染完畢後，務必檢查是否需要回收或重用。頻繁建立 `Frame` 會導致 JVM 沒滿但系統記憶體耗盡。

### 2. Seek 的效能
`grabber.setTimestamp()` 在 FFmpeg 中通常是搜尋最近的關鍵影格。
- **對應 ExoPlayer**：這就是 `SeekParameters.CLOSEST_SYNC`。如果你想要精準 Seek，必須在 `setTimestamp` 後，不斷 `grabFrame` 並檢查時間戳，直到到達目標點為止。

### 3. 解碼器阻塞
某些 4K 影片解碼非常慢。
- **ExoPlayer 做法**：將解碼器放在獨立執行緒。
- **JavaCV 建議**：如果效能不足，可以建立多個 `FFmpegFrameGrabber` 分別處理音軌與影軌（雖然會增加 IO 開銷，但能利用多核解碼）。

### 4. 變速播放
- **ExoPlayer**：`PlaybackParameters`。
- **JavaCV**：你需要手動調整 `AudioTrack` 的採樣率 (Playback rate) 或使用 `Sonic` 庫處理音訊，並讓 `MediaClock` 跑得比現實時間快。
