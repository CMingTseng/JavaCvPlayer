# 設計回顧：核心解耦與環境無感架構的演進

在開發 JavaCvPlayer 的過程中，我們經歷了一次重大的架構重構。本次重構的核心目標是將播放核心與 UI 框架（如 Compose）解耦，以達成真正的跨平台與多環境適配性。

## 1. 發現問題：強耦合的代價

最初的實作中，`JvmJavaCvPlayer` 直接持有 Compose 的 `MutableState<BufferedImage?>`。這導致了以下問題：
- **環境限制**：`core` 模組被迫依賴 `androidx.compose.runtime`，使得該模組無法在純 Swing 或 JavaFX 環境下運行。
- **測試困難**：核心邏輯與 UI 狀態綁定，難以進行純粹的單元測試。
- **渲染限制**：硬編碼的 Compose State 限制了影像輸出的靈活性，難以引入更高效的渲染路徑（如 VolatileImage 或 Skia 零拷貝）。

## 2. 改進方案：引入 `VideoFrameOutput` 抽象層

我們決定建立一個環境無關的介面來處理影像幀的輸出。

### 介面定義
```kotlin
fun interface VideoFrameOutput {
    fun onFrameAvailable(image: BufferedImage?)
}
```

### 重構策略
1.  **核心純淨化**：從 `JvmVideoSink` 與 `JvmJavaCvPlayer` 中移除所有對 Compose 的引用。
2.  **依賴反轉**：核心只負責產生影像並透過 `VideoFrameOutput` 輸出，而不關心誰在接收。
3.  **橋接模式**：針對不同的 UI 環境提供專用的橋接器：
    - **Swing**: 直接在回調中呼叫 `JPanel.repaint()`。
    - **Compose**: 提供 `ComposeVideoFrameOutput` 將影像導流至 `MutableState`。

## 3. 處理方式的改善思考

### A. 優先考慮「純 JVM」依賴
核心模組應盡可能保持純粹。在 JVM 平台上，使用 `java.awt.image.BufferedImage` 作為標準交換格式，能確保最大的相容性。

### B. 介面勝過狀態
與其讓核心持有 UI 狀態，不如讓核心暴露回調介面。這樣 UI 層可以自由決定如何「觀察」這些數據。

### C. 渲染路徑的階梯式演進
透過 `VideoFrameOutput`，我們現在可以無縫切換渲染技術：
- **Level 1 (Compatibility)**: `BufferedImage` 拷貝（當前穩定方案）。
- **Level 2 (Hardware Acceleration)**: `VolatileImage` 繪製（Swing 方案）。
- **Level 3 (Zero-Copy)**: Skia 指標映射（Compose Desktop 未來方案）。

### D. 技術辯證：為什麼不用 `MutableStateFlow`？

在考慮解耦時，一個常見的疑問是：既然 `StateFlow` 屬於 Kotlin Coroutines 而非 Compose，是否可以用 `MutableStateFlow<BufferedImage?>` 代替介面？經過分析，我們堅持選擇 **`VideoFrameOutput` (介面回調)**，主要是基於以下四個深層考量：

#### 1. 效能與開銷 (Performance Overhead)
*   **VideoFrameOutput (介面回調)**：這是一個直接的函數調用（Direct Call）。在影片播放（每秒 30-60 幀）的高頻率場景下，這種「推（Push）」模式的開銷極低，幾乎等同於一個 C 指標的回調。
*   **MutableStateFlow**：雖然它也是推模式，但 `StateFlow` 內部包含大量的狀態檢查、執行緒安全鎖（ReentrantLock）以及協程掛起邏輯。對於每秒 60 次的影像更新，`StateFlow` 的封裝成本較高，且會產生不必要的協程排程 (Dispatching) 開銷。

#### 2. Swing 環境的複雜度
*   **介面回調**：在 Swing 中，你只需要在回調裡寫 `panel.repaint()`。這是在 AWT 執行緒中非常直覺的處理方式。
*   **StateFlow**：如果你在 core 中使用 `StateFlow`，Swing 端必須啟動一個 `CoroutineScope` 去 `collect` 這個 Flow，然後再更新 UI。這在純 Swing 專案中引入了額外的協程維護成本，讓原本簡單的 AWT 邏輯變得複雜。

#### 3. 影像幀的「連續性」vs「狀態性」
*   **StateFlow 的本質是「狀態（State）」**：它會「合併（Conflate）」更新。如果生產者（播放器）發送太快，而消費者（UI）處理太慢，`StateFlow` 會自動跳過中間的幀。這雖然有助於 AVSync，但有時我們需要更精確的渲染控制（例如：逐幀分析或錄製），這時「狀態」就不如「事件流」精確。
*   **VideoFrameOutput 是「接收器（Sink）」**：它代表的是一個數據的去向。這符合 Media3 的 `VideoSink` 或 FFmpeg 的 `appsink` 設計哲學——核心只管把解碼好的數據「推出去」，接收端決定要「立即顯示（Swing）」還是「轉為狀態（Compose）」。

#### 4. 未來的「零拷貝」擴充性
在重構計畫的 Step 5 (Skia 零拷貝) 中，我們可能不再傳遞 `BufferedImage`，而是傳遞一個 `Native Pointer` 或 `Hardware Texture ID`：
*   **介面回調**：可以輕易更改參數（如增加 Metadata），且不產生額外物件，直接傳遞 Long 指標。
*   **StateFlow**：必須不斷建立新的資料類別 (Data Class) 物件來傳遞參數，這在高頻播放時會產生顯著的 GC 壓力。

#### 綜合對照表

| 特性 | VideoFrameOutput (Interface) | MutableStateFlow |
| :--- | :--- | :--- |
| **依賴性** | 零依賴 (Pure Java/Kotlin) | 需依賴 `kotlinx-coroutines-core` |
| **效能開銷** | **極低** (直接函數調用) | 中 (包含狀態檢查與協程排程) |
| **Swing 整合** | **極簡** (Listener 模式) | 較難 (需維護協程作用域以 collect) |
| **語意精確度** | **事件流** (確保每一幀的觸發) | **狀態性** (高頻下可能合併/跳過幀) |
| **擴充性** | **優** (可輕鬆增加參數如 Metadata) | 差 (需不斷建立包裝物件) |

**結論**：介面回調提供了最極致的效能與最廣泛的適配性，讓 `core` 模組達成了「非同步框架中立」的目標。

## 4. 總結

這次重構不僅修復了 Swing Demo 無法運行的錯誤，更為專案打下了堅實的架構基礎。未來在擴展 JavaFX 或 Android 相容層時，我們將不再需要修改播放器的核心邏輯。
