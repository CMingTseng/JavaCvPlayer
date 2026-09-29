# Media3 VideoEffects 深度解析：HSL 與 RGB 影像處理

Media3 引入了強大的 `lib-effect` 模組（通常配合 `Transformer` 或 `ExoPlayer` 使用），讓開發者能透過 GPU 高效處理影片影格。

---

## 1. 所屬庫位
`VideoEffects` 相關 API 位於 **`androidx.media3:media3-effect`** 模組中。
核心介面為 `Effect` 與其子介面 `GlEffect`，處理中心則是 `DefaultVideoFrameProcessor`。

---

## 2. HSL vs. RGB：色彩調整的差異

### A. 什麼是 HSL？
**HSL** 代表 **Hue (色相)**、**Saturation (飽和度)**、**Lightness (亮度)**。
- **Hue (色相)**：顏色的種類（如紅、黃、藍），以 0-360 度圓環表示。
- **Saturation (飽和度)**：顏色的鮮豔程度。0 是灰色，100 是最鮮艷。
- **Lightness (亮度)**：顏色的明暗。0 是全黑，100 是全白，50 是正常色彩。

**為什麼要用 HSL 調整亮度？**
當你只想讓影片「變亮」而不改變顏色原本的比例時，調整 HSL 的 **Lightness** 效果最自然，類似於人眼的視覺感受。

### B. RGB 調整 (RgbAdjustment / Matrix)
**RGB** 代表 **Red (紅)**、**Green (綠)**、**Blue (藍)**。
- 透過調整 R、G、B 分量的乘積 (Scale) 來改變顏色。
- **對比度 (Contrast)** 與 **色彩偏移 (Color Offset)** 通常在 RGB 空間處理效果更好。

**兩者差異總結**：
- **HSL** 適合做**「直覺式」**的調整（如：讓天空更藍一點、整體畫面亮一點）。
- **RGB** 適合做**「精確控制」**或**「濾鏡效果」**（如：增加紅色調營造溫暖感、透過矩陣運算實現黑白濾鏡）。

---

## 3. Media3 內建的 VideoEffects 清單

以下是 `lib-effect` 中可用的主要效果：

| 效果名稱 | 功能說明 |
| :--- | :--- |
| **`HslAdjustment`** | 旋轉色相、增減飽和度與亮度。 |
| **`RgbAdjustment`** | 縮放 R、G、B 通道，用於簡單調色。 |
| **`Contrast`** | 調整畫面亮部與暗部的對比程度。 |
| **`Brightness`** | 調整畫面整體亮度（基於 RGB 偏移）。 |
| **`Crop`** | 裁切畫面的特定區域。 |
| **`Presentation`** | 處理畫面的解析度縮放與顯示比例。 |
| **`OverlayEffect`** | 在影片上疊加 **Bitmap**、**Text** 或 **Canvas**（可用於浮水印、字幕）。 |
| **`GaussianBlur`** | 高斯模糊，讓畫面變模糊。 |
| **`ColorLut`** | 使用 Lookup Table (LUT) 實現專業級濾鏡（如：電影感濾鏡）。 |
| **`ScaleAndRotate`** | 縮放與旋轉畫面。 |
| **`SpeedChangeEffect`** | 改變影格播放速度（慢動作或快進）。 |

---

## 4. 開發者參照建議

如果你正在 JavaCV 中實作類似功能：
1. **濾鏡順序**：Media3 採用 **Chaining (鏈式)** 處理。例如：先 `Crop` -> 再 `HslAdjustment` -> 最後 `Overlay`。這樣能減少不必要的像素計算。
2. **GPU 處理**：Media3 的這些效果底層全是 **OpenGL Shader (GLSL)**。
   - `HslAdjustment` 內部會執行 `RGB -> HSL` 轉換，調整後再轉回 `RGB` 輸出。
   - `RgbAdjustment` 則是直接套用 `4x4 Matrix` 運算。
3. **性能注意**：盡量合併多個 RGB 矩陣運算為一個，以減少 GPU 的 Pass 次數。
