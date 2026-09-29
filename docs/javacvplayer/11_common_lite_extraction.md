# lib-common 精簡抽出指引 (Lite Version for JVM)

為了讓 JavaCV 播放器具備 Media3 的靈魂而不引入過重的 Android 依賴，建議抽出以下核心類別。

---

## 1. 核心類別清單與路徑建議

建議保持原有的 package 名稱 `androidx.media3.common`，這能最大化代碼的互換性。

### A. 基礎定義
- **`C.java`**：這是最重要的常數表。包含 `TIME_UNSET`, `STATE_IDLE`, `STATE_BUFFERING`, `STATE_READY`, `STATE_ENDED`。
- **`PlaybackException.java`**：定義統一的錯誤回傳格式。

### B. 介面與骨架
- **`Player.java` (Interface)**：這是你的播放器必須實現的最高契約。
- **`BasePlayer.java` (Abstract Class)**：**強烈建議抽出**。它實現了 `Player` 的大部分方法（如 `play()` 轉為 `setPlayWhenReady(true)`），能節省你大量時間。

### C. 數據描述
- **`MediaItem.java`**：用於傳遞 URL 與元數據。
- **`PlaybackParameters.java`**：用於處理播放速度。
- **`VideoSize.java`**：簡單的寬高封裝。

---

## 2. 關於 Kotlin 化的建議

如果你決定使用 Kotlin 重寫這些類別：
1. **優點**：程式碼更精簡，可以使用 Kotlin 的 `data class` 簡化 `MediaItem` 與 `VideoSize`。
2. **互操作性**：確保在 Kotlin 中使用 `@JvmField` 或 `@JvmStatic`，這樣在 Java 撰寫的 `JavaCvPlayer` 中呼叫時，語法會跟官方 Media3 完全一致。

---

## 3. 解決 Android 依賴陷阱 (Stubbing)

官方 `lib-common` 最大的問題是依賴 `android.os.Bundle` 與 `android.os.Handler`。

### 抽出時的處理策略：
- **移除 Bundleable**：在 JVM Lite 版本中，如果你不需要跨進程通訊，可以直接移除 `Bundleable` 介面及其相關方法。
- **Handler 抽離**：
    - 在 `BasePlayer` 中，官方使用 `Handler` 來發送事件。
    - **建議**：改為定義一個簡單的 `Clock` 或 `Executor` 介面，在 JVM 上使用 `ScheduledExecutorService` 替代。

---

## 4. 總結：你的 JavaCvPlayer 結構

```java
package your.package;

import androidx.media3.common.BasePlayer;
import androidx.media3.common.MediaItem;
import org.bytedeco.javacv.FFmpegFrameGrabber;

public class JavaCvPlayer extends BasePlayer {
    private FFmpegFrameGrabber grabber;
    
    // 實作必要的介面方法
    @Override
    public void prepare() {
        // 在背景執行緒啟動 grabber
    }
    
    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        // 切換內部循環狀態
    }
}
```

透過這種方式，你建立了一個「輕量級」但「標準化」的播放器核心，它能與任何針對 Media3 Player 介面設計的 UI 元件完美對接。
