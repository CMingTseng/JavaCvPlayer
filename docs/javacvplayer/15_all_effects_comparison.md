# Media3 lib-effect 全清單與 JavaCV/OpenCV 對比指南

這份文件詳細列出了 Media3 `lib-effect` 模組中的所有主要濾鏡，並提供在 JavaCV (OpenCV/FFmpeg) 中實現對等效果的關鍵字與範例。

---

## 1. 色彩與色調調整 (Color & Tone)

| Media3 Effect | 說明 | JavaCV / OpenCV 關鍵字 | 簡易範例 (JavaCV/OpenCV) |
| :--- | :--- | :--- | :--- |
| **`Brightness`** | 調整亮度 | `Mat.convertTo`, `beta` | `mat.convertTo(dst, -1, 1.0, brightnessValue);` |
| **`Contrast`** | 調整對比 | `Mat.convertTo`, `alpha` | `mat.convertTo(dst, -1, contrastValue, 0);` |
| **`HslAdjustment`** | HSL 空間調整 | `cvtColor`, `COLOR_BGR2HSL` | `cvtColor(src, hsl, COLOR_BGR2HSL);` <br> `// 調整通道後 cvtColor 回 BGR` |
| **`RgbAdjustment`** | RGB 分量縮放 | `split`, `multiply`, `merge` | `split(src, planes);` <br> `multiply(planes.get(0), scale, planes.get(0));` |
| **`RgbFilter`** | 預設 RGB 濾鏡 | `LUT` (Lookup Table) | `Core.LUT(src, lutMat, dst);` |
| **`ColorLut`** | 3D/2D LUT 濾鏡 | `FFmpegFrameFilter`, `lut3d` | `filter.setFilters("lut3d=file.cube");` |
| **`SingleColorLut`** | 單一顏色查找表 | `applyColorMap` | `applyColorMap(src, dst, COLORMAP_JET);` |

---

## 2. 幾何變換與裁剪 (Geometry & Transformation)

| Media3 Effect | 說明 | JavaCV / OpenCV 關鍵字 | 簡易範例 (JavaCV/OpenCV) |
| :--- | :--- | :--- | :--- |
| **`Crop`** | 畫面裁切 | `Mat(Rect)` (ROI) | `Mat cropped = new Mat(src, new Rect(x, y, w, h));` |
| **`Presentation`** | 比例與縮放 | `resize`, `copyMakeBorder` | `resize(src, dst, new Size(w, h));` |
| **`ScaleAndRotate`** | 縮放與旋轉 | `getRotationMatrix2D`, `warpAffine` | `Mat m = getRotationMatrix2D(center, angle, scale);` <br> `warpAffine(src, dst, m, src.size());` |
| **`MatrixTransformation`** | 自定義 4x4 矩陣變換 | `warpPerspective` | `warpPerspective(src, dst, matrix, size);` |
| **`LanczosResample`** | 高品質重採樣 | `resize`, `INTER_LANCZOS4` | `resize(src, dst, size, 0, 0, INTER_LANCZOS4);` |

---

## 3. 疊加效果 (Overlays)

| Media3 Effect | 說明 | JavaCV / OpenCV 關鍵字 | 簡易範例 (JavaCV/OpenCV) |
| :--- | :--- | :--- | :--- |
| **`TextOverlay`** | 疊加文字 | `putText` | `putText(src, "Text", point, FONT_HERSHEY_SIMPLEX, 1.0, color);` |
| **`BitmapOverlay`** | 疊加圖片 | `copyTo` with Mask | `logo.copyTo(src.rowRange(y, y+h).colRange(x, x+w), mask);` |
| **`CanvasOverlay`** | 畫布繪製 | `Java2D` 整合 | `Graphics2D g = bi.createGraphics(); g.draw(...);` |
| **`OverlayEffect`** | 綜合疊加管理 | `FFmpegFrameFilter`, `overlay` | `filter.setFilters("overlay=x=10:y=10");` |

---

## 4. 卷積與模糊 (Blur & Convolution)

| Media3 Effect | 說明 | JavaCV / OpenCV 關鍵字 | 簡易範例 (JavaCV/OpenCV) |
| :--- | :--- | :--- | :--- |
| **`GaussianBlur`** | 高斯模糊 | `GaussianBlur` | `GaussianBlur(src, dst, new Size(15, 15), 0);` |
| **`SeparableConvolution`** | 可分離卷積 | `sepFilter2D` | `sepFilter2D(src, dst, ddepth, kernelX, kernelY);` |

---

## 5. 時間與速度 (Temporal & Speed)

| Media3 Effect | 說明 | JavaCV / OpenCV 關鍵字 | 簡易範例 (JavaCV/OpenCV) |
| :--- | :--- | :--- | :--- |
| **`SpeedChangeEffect`** | 改變播放速度 | `grabber.setFrameRate` | `// 調整讀取循環中的延時或 FrameRate` |
| **`FrameDropEffect`** | 丟幀處理 | `// 邏輯控制` | `if (count % 2 == 0) continue; // 跳過影格` |
| **`TimestampAdjustment`** | 時間戳微調 | `frame.timestamp` | `frame.timestamp = frame.timestamp + offset;` |

---

## 6. JavaCV 專屬強化：FFmpegFrameFilter

對於 Media3 中許多複雜的 GL 效果，JavaCV 使用者有一個「終極武器」：**`FFmpegFrameFilter`**。許多 Media3 需要寫 Shader 的效果，在 FFmpeg 中只需一行字串：

- **美顏/磨皮**：`filter.setFilters("smartblur")` 或 `filter.setFilters("delogo")`
- **畫中畫**：`filter.setFilters("[0:v][1:v]overlay=x=10:y=10")`
- **顏色平衡**：`filter.setFilters("colorbalance=rs=0.5")`

### 總結開發建議
1. **簡單調整**：直接用 OpenCV 的 `Mat` 運算（如 `convertTo`, `putText`），效能最直接。
2. **高品質縮放**：使用 OpenCV 的 `resize` 並指定 `INTER_LANCZOS4`。
3. **複雜濾鏡組合**：優先考慮 `FFmpegFrameFilter`，它比你自己寫 OpenCV 循環快得多，且支援硬體加速。
