# 依賴分析：以 lib-common 為核心的跨平台開發

要建立一個與 Media3 生態相容的 JavaCV 播放器，選擇正確的依賴範圍至關重要。

---

## 1. lib-common：不可或缺的依賴

如果你希望你的播放器能做到「大幅度相容」，你必須實作 `lib-common` 中的 `Player` 介面。

### 優點：
- **UI 邏輯重用**：你可以直接使用為 Media3 寫的播放清單管理、自動跳過片段、進度條更新等邏輯。
- **數據結構統一**：使用 `MediaItem` 定義影片資訊，使用 `Format` 定義解析度與編碼。這讓你的 `JavaCvPlayer` 在代碼層面看起來跟 `ExoPlayer` 一模一樣。

### 挑戰與對策：
- **Android 依賴問題**：`lib-common` 雖然位於 `common` 模組，但仍依賴於 `android.os.Bundle` (用於狀態序列化)。
- **對策**：
    1. **直接依賴**：如果你的 JVM 環境允許（例如使用特定的 Android-on-JVM 橋接庫），直接依賴它是最完美的。
    2. **介面抽離**：若無法直接依賴，則需在你的 JVM 專案中定義一套「完全相同」的介面與常量（如 `STATE_READY`, `COMMAND_SEEK_TO`）。

---

## 2. lib-session：視需求而定

`lib-session` 的核心是 `MediaSession` 與 `MediaController`，主要用於解決「遠端控制」問題。

### 什麼時候需要？
- 如果你的播放器需要**被其他進程控制**（例如一個 Service 跑播放，另一個 Activity 跑 UI）。
- 如果你希望實作一套支援跨平台的遠端控制協議。

### 為什麼 Desktop 開發可以先跳過它？
- **系統差異大**：Windows, macOS, Linux 的媒體控制中心（通知欄控制）實作機制完全不同。`lib-session` 是專為 Android 的 `MediaSessionCompat` 設計的。
- **替代方案**：在 JVM Desktop，建議直接使用 JNA 或特定平台的庫（如 `jnativehook`）來監聽媒體鍵，然後直接呼叫 `player.play()` 或 `player.pause()`。

---

## 3. 推薦的跨平台架構建議

1. **核心層 (Shared)**：
   - 使用 `lib-common` 定義的數據結構。
   - 定義一個 `CrossPlatformPlayer` 介面，完全對齊 `androidx.media3.common.Player`。

2. **Android 實現層**：
   - 封裝 `ExoPlayer` 實作 `CrossPlatformPlayer`。

3. **JVM Desktop 實現層**：
   - 封裝 `JavaCvPlayer` 實作 `CrossPlatformPlayer`。

### 最終開發路徑：
**不要去動 `lib-session`**。專注於讓 `JavaCvPlayer` 完美模擬 `lib-common` 中的 `Player` 行為。一旦你做到了這一點，你的 JavaCV 播放器就已經具備了「Media3 靈魂」，足以應對 99% 的跨平台播放應用。
