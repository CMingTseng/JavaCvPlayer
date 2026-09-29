# 深度解析：播放速度 (PlaybackParameters) vs. 時間特效 (SpeedChangeEffect)

在 Media3 中，改變影片「快慢」有兩套完全不同的機制。理解兩者的差異對於開發 JavaCvPlayer 至關重要。

---

## 1. 播放速度 (PlaybackParameters) - 運行時控制
這是使用者在 UI 點選「1.5x」或「2.0x」時使用的機制。

- **所屬模組**：`lib-common` (定義) 與 `lib-exoplayer` (實作)。
- **核心類別**：`androidx.media3.common.PlaybackParameters`。
- **運作原理**：
    - **不修改數據**：它不會改變影格原始的 `timestamp`。
    - **改變主時鐘 (MediaClock)**：它告訴時鐘「跑快一點」。例如在 2 倍速下，現實時間過了 1 秒，`MediaClock` 會跳進 2 秒。
    - **音訊處理**：這是最難的部分。Media3 使用 **Sonic 演算法** 在改變速度的同時**保持音調 (Pitch)** 不變，避免聲音變成「花栗鼠叫」。
- **JavaCV 建議**：在你的 `MediaClock` 中引入一個 `speed` 變數。
  `currentTimeUs = (System.nanoTime() - startTime) * speed;`

---

## 2. 時間特效 (SpeedChangeEffect) - 管線級修改
這是你在影片編輯或需要「物理性」改變影格順序時使用的機制。

- **所屬模組**：`lib-effect`。
- **核心類別**：`androidx.media3.effect.SpeedChangeEffect`。
- **運作原理**：
    - **修改數據**：它會物理性地改寫每一幀 `Frame` 的 `presentationTimeUs`。
    - **應用場景**：
        - 使用 `Transformer` 導出一個永久慢動作的影片。
        - 實作「抽幀」特效（如 `FrameDropEffect`）。
- **JavaCV 建議**：在你的 `EffectChain` 中實作。這通常用於「轉檔」或「特殊視覺效果」。

---

## 3. 兩者對比表

| 特性 | 播放速度 (PlaybackParameters) | 時間特效 (SpeedChangeEffect) |
| :--- | :--- | :--- |
| **主要目的** | 使用者即時觀影體驗。 | 影片編輯、轉檔、濾鏡效果。 |
| **數據變動** | 僅改變時鐘讀取速度。 | 永久修改影格時間戳。 |
| **音訊處理** | 自動處理音調補償 (Sonic)。 | 通常需要重新編碼音訊以匹配新長度。 |
| **性能開銷** | 極低 (僅為數學運算)。 | 較高 (涉及管線重排)。 |

---

## 4. JavaCvPlayer 實作指引

### A. 實作即時倍速 (1.5x, 2.0x)
1. 在 `JavaCvPlayer` 中維護一個 `PlaybackParameters` 物件。
2. 調整你的 `doSomeWork` 循環：
   ```kotlin
   // 取得考慮倍速後的目標時間
   long targetPositionUs = clock.getCurrentPositionUs(playbackParameters.speed);
   
   // 渲染器根據 targetPositionUs 決定是否顯示影格
   if (frame.timestamp <= targetPositionUs) { render(frame); }
   ```

### B. 實作變速特效 (慢動作濾鏡)
1. 實作一個繼承自 `Effect` 的 `JavaCvSpeedEffect`。
2. 在 `process(Frame frame)` 中，根據比例縮放 `frame.timestamp`。
3. 這將導致播放器「以為」這幀原本就該在那個時間點播。

**總結**：大多數情況下，你需要的只是 `PlaybackParameters`。只有當你要做影片剪輯器時，才需要深入研究 `lib-effect` 裡的時間特效。
