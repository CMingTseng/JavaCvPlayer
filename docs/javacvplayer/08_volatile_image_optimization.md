# VolatileImage 硬件加速優化實踐指南

本文件記錄了將 `JvmVideoSink` 從純 `BufferedImage` (System RAM) 升級為 `VolatileImage` (VRAM) 的優化過程。這是實現高性能渲染的第一階段過渡方案。

---

## 1. 優化背景與目標

在 JVM Desktop 環境中，直接將解碼後的 `BufferedImage` 繪製到 Canvas 會造成頻繁的 CPU 運算與內存拷貝。根據 `05_high_performance_rendering.md` 的規劃，我們引入 AWT 的 `VolatileImage` 來利用顯卡加速。

### 核心優化點：
- **顯存存儲 (VRAM)**：將影像緩衝區放置於顯示卡記憶體中。
- **硬體位塊傳輸 (BitBLT)**：利用 GPU 進行縮放與繪製，減輕 CPU 負擔。
- **自動復原機制**：處理系統事件導致的顯存丟失。

---

## 2. 關鍵實作細節

### A. 建立與驗證 VolatileImage
使用 `GraphicsConfiguration` 建立與當前顯示設備相容的緩衝區：

```kotlin
private fun createVolatileImage(w: Int, h: Int): VolatileImage? {
    val config = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice.defaultConfiguration
    val vi = config.createCompatibleVolatileImage(w, h, Transparency.OPAQUE)
    
    // 驗證硬體加速狀態
    val caps = vi.capabilities
    logger.i { "VolatileImage created: ${w}x${h}, Accelerated: ${caps.isAccelerated}" }
    return vi
}
```

### B. 渲染循環與狀態校驗
在每一幀繪製前，必須驗證緩衝區是否依然可用：

```kotlin
val status = vi.validate(config)
when (status) {
    VolatileImage.IMAGE_INCOMPATIBLE -> {
        volatileImage = createVolatileImage(bi.width, bi.height)
    }
    VolatileImage.IMAGE_RESTORED -> {
        logger.i { "VolatileImage restored from VRAM loss" }
    }
}
```

### C. synchronized 範圍優化
為了提升併行能力，我們將 `synchronized(frameConverter)` 的範圍縮小到僅包含 `convert` 動作，避免在執行 AWT 繪製或 VRAM 操作時阻塞解碼執行緒。

---

## 3. 效能監測與基準測試 (Benchmark)

我們實作了 `RenderingBenchmark.kt` 來量化效能差異（以 4K 影像為例）：

| 測試項目 | 平均耗時 (ms/frame) | 備註 |
| :--- | :--- | :--- |
| **BufferedImage Deep Copy** | ~15.5 ms | 純 CPU 拷貝，極限約 60fps |
| **VolatileImage (Draw Only)** | **< 1.0 ms** | 純 VRAM 操作，極其迅速 |
| **VolatileImage (Draw + Snapshot)** | **~9.5 ms** | 受限於 VRAM -> RAM 回讀開銷 |

**結論**：`VolatileImage` 的繪製極快，但 `snapshot` 回讀是目前的效能殺手。這確認了下一階段的優化方向：**Skia 零拷貝 (Zero-copy)**。

---

## 4. 參考與優化限制 (Volcengine 觀點)

參考 **火山引擎 (Volcengine)** 的技術優化建議，目前的 `VolatileImage` 方案雖然提升了繪製效率，但仍存在一個**瓶頸**：

- **`vi.snapshot` 回讀開銷**：
  由於目前 Compose 端的 `VideoCanvas` 仍需要 `BufferedImage` 或 `ImageBitmap`，我們被迫呼叫 `vi.snapshot` 將數據從 VRAM 拷貝回 System RAM。這在 4K 環境下產生了約 8-12ms 的額外延遲。

---

## 5. 總結
本次優化成功建立了 JVM 端硬體加速渲染的基礎架構，並透過基準測試量化了權衡，為後續導入 4K 零拷貝技術鋪平了道路。
