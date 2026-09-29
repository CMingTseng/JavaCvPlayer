# JavaCvPlayer 逐步開發計畫

本計畫旨在參考 Media3 架構，重新建立一個基於 JavaCV (FFmpeg) 的 Kotlin/JVM 播放器，解決早期 `shared` 模組規劃不佳與同步邏輯混亂的問題。

## 第一階段：基礎介面定義 (common-lite)
**目標**：定義播放器行為的核心介面與資料結構，不涉及具體實作。

- [x] **Step 1.1**: 定義 `PlayerState` 與 `PlayerEvent` (基於 Media3 概念)。
- [x] **Step 1.2**: 定義 `Player` 介面 (精簡版)。
- [x] **Step 1.3**: 定義 `VideoSize` 與其他輔助物件。

## 第二階段：核心播放引擎實作 (core)
**目標**：使用 JavaCV `FFmpegFrameGrabber` 實作播放邏輯。

- [ ] **Step 2.1**: 實作基礎 `VideoFrame` 與 `MediaFrameQueue`。
- [ ] **Step 2.2**: 實作 `FFmpegFrameLoader` (負責 Grabber 的初始化與封裝)。
- [ ] **Step 2.3**: 實作 `doSomeWork` 迴圈 (參考 Media3 Event Loop)。
- [ ] **Step 2.4**: 音視頻同步 (AV Sync) 機制。

## 第三階段：UI 元件 (ui-leanback)
**目標**：建立基於 Jetpack Compose Material3 的 Leanback 風格介面。

- [ ] **Step 3.1**: 建立 `VideoCanvas` 元件 (支援 ResizeMode)。
- [ ] **Step 3.2**: 實作 `PlayerControlView` (Leanback 風格)。

## 第四階段：Demo 與整合
- [ ] **Step 4.1**: 建立桌面端/Android 端展示 App。

---

## 當前進度
- **狀態**: 開始 第一階段 Step 1.1
- **交接內容**: [HANDOVER.json](HANDOVER.json)
