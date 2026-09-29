# VideoSink 模組化與平台解耦重構計劃

## 1. 目標
將影像渲染實作從 `:core` 模組移出，建立獨立的渲染器模組，解決 Android 端的依賴污染問題，並落實「核心一份，渲染隨插即用」的架構。

## 2. 新模組架構圖
```text
      [:common-lite] (Media3 介面定義)
            ^
            |
         [:core] (KMP: FFmpeg 解碼、時鐘同步、JvmJavaCvPlayer)
            ^
            +---------------------------------------+
            |                   |                   |
    [:core-video-javafx] [:core-video-swing] [:core-video-android]
    (PixelBuffer 渲染)   (VolatileImage 渲染) (Surface 渲染)
```

## 3. 開發步驟與稽核清單 (Checklist)

### 第一階段：清理核心模組 (:core)
- [ ] **Step 1.1**: 在 `:core` 的 `commonMain` 中定義 `VideoSink` 介面（確保不依賴任何 AWT/JavaFX 類別）。
- [ ] **Step 1.2**: 移除 `:core` 中 `jvmMain` 對 JavaFX 與 Swing 的實作（`JavaFxPixelBufferVideoSink.kt`, `JvmVideoSink.kt`）。
- [ ] **Step 1.3**: 移除 `libraries/core/build.gradle.kts` 中所有的 `org.openjfx` 依賴。
- [ ] **Step 1.4**: 更新 `VideoSinkFactory` 為「抽象工廠」或「動態注入模式」，不再靜態引用具體 Sink。

### 第二階段：建立渲染器子模組
- [ ] **Step 2.1**: 建立 `libraries/core-video-javafx`：
    - 遷移 `JavaFxPixelBufferVideoSink.kt`。
    - 配置 JavaFX 運行環境依賴。
- [ ] **Step 2.2**: 建立 `libraries/core-video-swing`：
    - 遷移 `JvmVideoSink.kt`。
    - 確保僅依賴 AWT/Swing，不混入 JavaFX。
- [ ] **Step 2.3**: (預留) 建立 `libraries/core-video-android` 殼模組。

### 第三階段：Demo 專案與注入邏輯重構
- [ ] **Step 3.1**: 更新 `JavaFxDemo`：手動注入 `JavaFxPixelBufferVideoSink` 給 `JvmJavaCvPlayer`。
- [ ] **Step 3.2**: 更新 `SwingDemo`：手動注入 `JvmVideoSink`。
- [ ] **Step 3.3**: 驗證 Android 模組編譯，確保不再因 JavaFX/AWT 符號缺失或污染而報錯。

## 4. 關鍵技術細節：VideoSink 與 ViewProvider 介面定義 (KMP)
~~為了讓 `:core` 保持純淨，`VideoSink` 在 `commonMain` 或 `jvmMain` (若目前僅考慮 JVM) 的定義應如下：~~

~~```kotlin~~
~~interface VideoSink : androidx.media3.common.VideoSink {~~
~~    fun onFrameAvailable(frame: org.bytedeco.javacv.Frame)~~
~~    fun getView(): Any? // 返回平台原生組件，核心層不感知具體類型~~
~~}~~
~~```~~

**[2024-05-23 重大修正：Media3 兼容性校正]**
根據對 Media3 `libraries/common` 的稽核，`VideoSink` 不應存在於 common 模組。
1.  **`common-lite` 職責**：僅保留 `MediaItem`, `Player`, `ViewProvider` 等基礎介面。
2.  **`VideoSink` 遷移**：自 `androidx.media3.common` 移除，改為由 `:core` 自行定義（模擬 `exoplayer` 內部介面）。
3.  **UI 取得方式**：改用 `ViewProvider` 模式，避免 `VideoSink` 與 UI 組件強耦合。

```kotlin
// common-lite: androidx.media3.common.ViewProvider
interface ViewProvider {
    fun getView(): Any? 
}

// core: idv.neo.ffmpeg.media.player.core.video.VideoSink
interface VideoSink {
    fun setVideoSize(width: Int, height: Int)
    fun render(frame: Any, timestampUs: Long)
    fun onFrameAvailable(frame: Any)
    val preferredPixelFormat: Int
    fun flush()
    fun release()
}

**[2024-05-23 修正：ViewProvider 簽名對齊]**
~~interface ViewProvider {~~
~~    fun getView(): Any? ~~
~~}~~

為了相容 Media3 2025 `ListenableFuture<View> getView(ViewGroup viewGroup)` 的設計：
```kotlin
// common-lite: androidx.media3.common.ViewProvider
@UnstableApi
interface ViewProvider {
    /**
     * 回傳包含視圖的物件。
     * @param container 容器 (Android: ViewGroup, JVM: 暫不使用或傳入父組件)
     * @return 視圖物件 (Android: ListenableFuture<View>, JVM: 直接回傳 Node/Component)
     */
    fun getView(container: Any?): Any?
}
```
```

## 5. 稽核紀錄
- 計劃建立日期：2023-10-XX
- 當前進度：準備開始第一階段。
