# Compose Multiplatform 渲染優化整合報告

## 1. 已完成項目
- **核心介面擴展**: 在 `:core` 模組定義了 `VideoFrameProcessor` (OpenCV 特效鏈) 與 `VideoSink.RenderMode`。
- **Android 雙軌渲染**: 
    - `AndroidSurfaceVideoSink`: 利用 `lockHardwareCanvas` 直接操作 Surface，適合 OpenCV 特效後的快速渲染。
    - `AndroidBitmapVideoSink`: 提供 Bitmap 狀態流，整合 Compose `Image` 組件。
- **Desktop Skia 優化**:
    - `SkiaVideoSink`: 透過 Skiko `installPixels` 與 Compose `Canvas` 的 Native Skia Canvas 整合，達成高效渲染。
- **跨平台 UI 組件**:
    - `VideoPlayerCanvas`: 透過 `expect/actual` 封裝，自動根據平台與 `RenderMode` 切換渲染路徑。
    - 解決了 `ui-compose` 模組在 Android 與 JVM 端的編譯錯誤與相依性問題。

## 2. 關鍵實作細節
### Android "偷天換日" (SurfaceView)
在 `VideoPlayerCanvas.android.kt` 中，透過 `AndroidView` 嵌入 `SurfaceView`，並在 `surfaceCreated` 時將 `Surface` 注入到 `AndroidSurfaceVideoSink`。這使得解碼後的影格在經過 OpenCV 處理後，可以直接繪製到硬體 Surface，跳過 Compose 的層層包裝。

### Desktop Skia 映射
在 `VideoPlayerCanvas.jvm.kt` 中，直接存取 `drawIntoCanvas { canvas -> canvas.nativeCanvas }` (Skia Canvas)，並繪製由 `SkiaVideoSink` 維護的 `org.jetbrains.skia.Bitmap`。

## 3. 待驗證項目
- [ ] 驗證 OpenCV 特效處理鏈 (OpenCVVideoFrameProcessor) 的旋轉與灰階濾鏡在 Android 端 Surface 上的表現。
- [ ] 測試 `SkiaVideoSink` 的 `installPixels` 性能，必要時進一步優化為零拷貝 (Native Pointer) 模式。
- [ ] 確保 `FFmpegFrameLoader` 的 `pixelFormat` 自動協商邏輯在所有平台正確運作。
