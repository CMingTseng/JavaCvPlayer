# 現代直播特效：帶透明通道的影片疊加技術 (Alpha Video)

在直播應用中，華麗的送禮特效通常不再用 OpenGL 手寫，而是直接播放一段帶有透明背景的影片。本文件說明如何利用 JavaCV 實現這種疊加技術，並支援「一滑即隱藏」的功能。

---

## 1. 準備透明影片素材

要實現重疊且不擋住背景，你需要使用支援 **Alpha Channel** 的編碼格式：
- **WebM (VP8/VP9)**：網頁與 Android 常用的透明格式。
- **QuickTime (ProRes 4444)**：專業剪輯常用的透明格式。

---

## 2. 方案一：UI 圖層疊加 (UI Layering)

這是實現「滑動隱藏」最簡單且效能最高的方法。

### 構架設計：
1. **主播放器層**：渲染 `MainStream`。
2. **特效播放器層**：放在主播放器上方，背景設為透明。
3. **滑動控制**：
   - 使用 Compose 的 `Box` 或 JavaFX 的 `StackPane`。
   - 滑動時，調整特效層的 `translationX` 或 `alpha`。

### 虛擬代碼 (Compose Desktop)：
```kotlin
Box {
    // 底層：直播畫面
    VideoCanvas(player = mainPlayer)
    
    // 頂層：禮物特效 (可根據 UI 狀態隱藏)
    if (showEffects) {
        VideoCanvas(
            player = giftPlayer,
            modifier = Modifier.graphicsLayer(alpha = swipeAlpha)
        )
    }
}
```

---

## 3. 方案二：影像管線融合 (Buffer Blending)

如果你需要將特效「鎖」在影片流中（例如錄製帶特效的影片），則需在 `JavaCvPlayer` 內部處理。

### 實作步驟：
1. **雙 Grabber 啟動**：
   - `mainGrabber`: 負責直播流。
   - `giftGrabber`: 負責禮物素材 (格式設為 `AV_PIX_FMT_BGRA`)。

2. **OpenCV 融合邏輯**：
   ```java
   public void process(Frame mainFrame, Frame giftFrame) {
       Mat mainMat = converter.convert(mainFrame);
       Mat giftMat = converter.convert(giftFrame);
       
       // 分離 Alpha 頻道作為 Mask
       // giftMat 是 BGRA，第 4 個通道是 Alpha
       // 使用 OpenCV 的 copyTo 將禮物疊加到主畫面上
       giftMat.copyTo(mainMat, alphaMask); 
   }
   ```

---

## 4. 為什麼 JavaCV 比 ExoPlayer 適合做這個？

1. **格式相容性**：FFmpeg 對各種奇怪編碼的透明影片支援度遠高於 Android 原生解碼器。
2. **像素級操作**：你可以輕鬆使用 OpenCV 在疊加前對禮物特效做即時處理（如：改變禮物顏色、調整大小、加入發光邊緣）。
3. **多路同步**：你可以輕鬆在一個迴圈裡控制兩個 Grabber 的進度。

---

## 5. 總結：開發建議

- **單純顯示**：請用 **方案一 (UI 層)**。開發難度最低，且滑動隱藏的動畫最順暢。
- **需要錄製/推流**：請用 **方案二 (管線融合)**。這能確保錄下來的檔案直接帶有特效。

**關於滑動隱藏**：
只要將禮物特效放在一個獨立的 `VideoSink` 中，你就可以隨意操控這個 `View` 的顯示狀態，而不會對主播放器造成任何卡頓。
