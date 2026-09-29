# AV Sync 診斷報告 - JvmJavaCvPlayer
# the report result is error
## 1. 現狀分析
目前 `JvmJavaCvPlayer` 在測試執行時出現頻繁的 `WAIT (Diff ~40ms)` 決策。這表示影像影格到達的時間點比音訊時鐘領先了約 40ms。

### 已觀察到的現象
- **音訊流暢**：表示 `AudioSink` 正常運作，且 `positionFrames` 有在更新。
- **頻繁 WAIT**：影像 Renderer 偵測到領先，正確地執行了等待。
- **40ms 偏差**：這剛好是 25fps 的一幀時長，可能是正常同步行為，但也可能暗示時鐘錨點 (Anchor) 有偏差。

## 2. 潛在問題與 discrepancies

### A. 時鐘切換跳變 (Clock Jump)
在 `KmpMediaClock` 中：
- **Fallback (System)** 使用 `_firstFrameTsUs` (通常是第一幀影片的 PTS)。
- **Master (Audio)** 使用 `_initialAudioTsUs` (第一幀音訊的 PTS)。
如果影片與音訊的第一幀 PTS 不一致，當播放器從「等待音訊啟動 (System)」切換到「音訊同步 (Hardware)」時，時鐘會發生跳變，導致同步決策瞬間偏移。

### B. 遞迴呼叫風險
在 `JvmJavaCvPlayer.doSomeWork` 中，`Decision.DROP` 採用了遞迴呼叫 `doSomeWork()`。在極端延遲情況下，這可能導致 `StackOverflowError`。

### C. WAIT 效率問題
目前 `WAIT` 固定每輪最多 `delay(10)`。若領先較多 (如 100ms)，會導致多次空轉。應參考 `shared` 播放器，允許單次較長的等待，以提高 CPU 效率。

### D. Audio/Video 耦合
由於音訊寫入與影片渲染在同一個 Coroutine Loop 中，若影片 `delay()`，音訊寫入也會暫停。雖然目前有 `audioBufferLimitUs` 緩衝，但若影片頻繁長等待，可能增加音訊欠載 (Underrun) 的風險。

## 3. 建議修復方案
1. **統一時鐘錨點**：在 `KmpMediaClock` 引入單一 `basePtsUs`，確保切換時無縫銜接。
2. **移除遞迴**：將 `Decision.DROP` 改為非遞迴處理。
3. **優化等待邏輯**：增加 `WAIT` 的單次等待上限，並確保 `yield()` 正確釋放資源。
4. **增強日誌**：輸出 `AudioTrackOffset` 與 `WallTime` 對比。
