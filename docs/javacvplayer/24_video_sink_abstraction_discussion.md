# 影像渲染抽象化討論：介面驅動與 UI 組件封裝

本文件記錄了關於 `VideoSink` 架構設計的深度討論，特別是「介面注入」與「單一實作多環境支持」的權衡，以及如何將渲染細節隱藏於 UI 組件中。

---

## 1. 方案對比：介面驅動 vs. 單一類別條件分支

### 方案 A：介面驅動架構 (VideoSink 注入) - **最終採納**
*   **做法**：定義 `VideoSink` 介面，針對不同框架實作 `JavaFxPixelBufferVideoSink` 與 `JvmVideoSink` (Swing)。
*   **優點**：
    *   **解耦**：核心播放器模組不需同時依賴 JavaFX 與 AWT/Swing。
    *   **效能優化**：各平台可使用最原生的「極速路徑」（如 JavaFX 的 `PixelBuffer`）。
    *   **擴充性**：未來加入 Compose Desktop (Skia) 時，只需新增實作，不需更動核心。
*   **缺點**：需要一個工廠類別 (`VideoSinkFactory`) 來處理環境偵測。

### 方案 B：單一 JvmVideoSink 改寫 (內部分支)
*   **做法**：在一個類別中透過 `if (isJavaFx)` 來判斷要用 `PixelBuffer` 還是 `VolatileImage`。
*   **優點**：用戶只需面對一個類別。
*   **缺點**：
    *   **依賴混亂**：編譯時需要所有 UI 庫，且類別內容會變得臃腫。
    *   **維護困難**：不同平台的邏輯交織，容易產生副作用。
    *   **效能損失**：若嘗試在平台間轉換數據（如 PixelBuffer 轉 VolatileImage），會導致嚴重的記憶體拷貝開銷。

---

## 2. 核心技術準則：將渲染細節隱藏於 UI 組件中

為了讓播放器在各平台表現得像「原生組件」，我們定義了 `getView()` 介面：

1.  **JavaFX 環境**：
    *   `VideoSink` 返回 `javafx.scene.image.ImageView`。
    *   內部透過 `PixelBuffer` 與 FFmpeg 的 `BGRA` 數據直接對接，達成 **Near Zero-copy**。

2.  **Swing 環境**：
    *   `VideoSink` 返回 `javax.swing.JPanel`。
    *   內部封裝 `VolatileImage` 與 `paintComponent` 邏輯，利用硬體加速繪圖。

3.  **優勢**：
    *   **使用者視圖無感**：開發者只需呼叫 `player.getView()` 並將其加入容器，不需關心底層是用什麼繪製的。
    *   **生命週期管理**：`VideoSink` 負責管理自己的渲染資源（如 `release()` 時關閉 Buffer），UI 元件只需負責顯示。

---

## 3. 未來擴展性 (Roadmap)

基於此架構，我們可以輕易地橫向擴展：
- **Compose Desktop**：實作一個返回 `androidx.compose.ui.graphics.ImageBitmap` 的 Sink。
- **OpenGL/Vulkan**：實作一個返回 `GLCanvas` 的 Sink，直接在 GPU 進行 YUV->RGB 轉換。

---

## 4. 結論

採用 **「介面驅動 + UI 組件封裝」** 是達成「環境無感」播放器的最佳路徑。它平衡了核心代碼的簡潔性與各平台渲染的最優效能。
