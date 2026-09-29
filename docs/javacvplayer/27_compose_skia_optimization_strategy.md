# Compose 與 Skia 渲染優化技術設計文件 (最終討論版)

## 1. 概述
針對 JetBrains Compose (Desktop) 與 Android Compose 平台，提供高效能的影像渲染路徑。本設計核心在於「介面隔離」與「動態切換渲染載體」。

## 2. Android 端「偷天換日」渲染策略 (The "Switching" Strategy)
參考 Media3/ExoPlayer 的內部機制，實作動態渲染路徑切換。

### 2.1 核心機制：透過 Surface 承接 OpenCV 影格
*   **偷天換日做法**: 
    *   在 Android 端優先使用 `AndroidSurfaceVideoSink`。
    *   當 UI 使用 `SurfaceView` 時，將其 `Surface` 綁定至 Sink。
    *   **特效處理鏈**: 影格在進入 Sink 之前，由 `VideoFrameProcessor` (OpenCV) 進行全處理（旋轉、濾鏡、模糊等）。
    *   **渲染壓入**: 處理後的 `Frame` 透過 `surface.lockHardwareCanvas()` 繪製。這允許我們在擁有系統級 Surface 合成加速的同時，保有 OpenCV 的靈活特效能力。
*   **預留擴展路徑**:
    *   **ImageBitmap / AndroidBitmapVideoSink**: 當需要與 Compose UI 深度集成（如 Modifier 特效）時切換此路徑。
    *   **AHardwareBuffer**: 預留給未來真正的 Zero-copy 硬體加速實現。
    *   **GLES Texture**: 預留給未來需要 GPU Shader 處理的極致性能場景。

## 3. Desktop 端：Skia Zero-copy 優化
*   **機制**: 利用 Skiko 提供的 `org.jetbrains.skia.Bitmap`。
*   **流程**: `FFmpeg` (RGBA) -> `Direct ByteBuffer` -> `skBitmap.installPixels()` -> `Canvas.drawBitmap()`.
*   **優點**: 避免了大量的像素拷貝與 GC 壓力，與 JetBrains Compose 渲染循環完美融合。

## 4. 跨平台 CMP 整合方案
*   **UI 層**: 在 `ui-compose` 模閱定義 `expect fun VideoPlayerCanvas`。
*   **Android 實現**: 使用 `AndroidView` 封裝 `SurfaceView`，並根據 `Player` 狀態動態切換 Sink 類型。
*   **Desktop 實現**: 使用 `androidx.compose.foundation.Canvas` 配合 `SkiaVideoSink`。

---
## 5. 術語分析與結論
*   **Android 無 Skia.Bitmap?**: Android 雖然底層用 Skia，但 Java 層為 `android.graphics.Bitmap`。我們透過 `copyPixelsFromBuffer` 達成高效能映射。
*   **特效相容性**: 透過「解碼 -> OpenCV 處理 -> Sink 渲染」的 pipeline，確保了無論是 Surface 還是 Bitmap 模式，都能享有一致的特效能力。
