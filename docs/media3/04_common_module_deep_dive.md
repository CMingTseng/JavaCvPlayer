# lib-common 深度解析：Media3 的邏輯基石

`lib-common` 模組不包含任何播放邏輯，但它包含了所有媒體組件必須遵循的「通用語言」。理解這層設計，是掌握 Media3 架構的關鍵。

## 1. Player 介面：單一事實來源 (Source of Truth)
`Player` 介面是 UI 層與播放引擎之間的「契約」。
- **命令模式 (Commands)**：Media3 引入了 `Player.Commands` 系統。UI 可以透過 `player.isCommandAvailable(COMMAND_SEEK_FORWARD)` 來判斷目前是否允許前進，這在處理廣告或直播流時非常有用。
- **異步監聽器**：所有的狀態變化都透過 `onEvents` 回調。這確保了 UI 能夠在單一個循環中處理多個狀態變更，避免頻繁刷新的效能問題。

## 2. Timeline：ExoPlayer 的時空導航
這是最讓新手困惑的部分。Media3 將播放時間線拆分為兩層：
- **Window (播放視窗)**：
    - 代表「播放清單中的一項」。
    - 使用者看到的 `currentPosition` 是相對於 Window 的。
    - 包含了是否可以快進、是否為直播、播放清單中的總時長等資訊。
- **Period (時段)**：
    - 代表「物理上的媒體段落」。
    - 一個 Window 內可以包含多個 Period（例如：`[廣告 Period] -> [正片 Period] -> [後貼片 Period]`）。
    - 這種結構讓 ExoPlayer 可以在同一個 Window 內實現廣告插入 (Ad Insertion)，而不會導致播放進度條出現跳動。

## 3. MediaItem 與 MediaMetadata
- **MediaItem**：不再僅僅是一個 URL。它現在是一個包含 DRM 配置、廣告標籤、自定義屬性的複合體。
- **MediaMetadata**：這是一個純淨的數據類，專門用於顯示 UI。它包含了標題、封面 URL、藝術家名稱等。Media3 會自動從 `MediaItem` 或是 ID3/MP4 中解析這些資訊並填充到這裡。

## 4. PlaybackParameters：播放性能的調節閥
這是控制播放行為的核心數據類：
- **Speed (倍速)**：控制播放快慢 (0.1x - 8.0x)。
- **Pitch (音調)**：控制聲音高低。
- **Sonic 演算法**：Media3 內建了 Sonic 處理器，確保在改變速度時，聲音不會變尖或變厚。這是在 `lib-exoplayer` 內部自動完成的。

## 5. Format：媒體的指紋
`Format` 描述了一個軌道 (Track) 的一切特徵。
- **Codec 資訊**：例如 `video/avc` 或 `audio/opus`。
- **硬體需求**：包含解析度、幀率、HDR 資訊。
- **語言標籤**：讓 `TrackSelector` 可以根據使用者的系統語言自動選擇對應的音軌或字幕。

## 5. C 類別與常量定義
`androidx.media3.common.C` 是整個專案的「大辭典」。
- **時間單位**：ExoPlayer 內部統一使用 **Microseconds (微秒, us)** 以保證精度，但在對外 API 會轉換為 **Milliseconds (毫秒, ms)**。
- **無效值定義**：例如 `TIME_UNSET` 代表該數值不存在，開發者必須學會處理這些邊界值。

## 6. Bundleable 跨進程通訊 (IPC)
這是 Media3 與舊版 ExoPlayer 最大的不同點之一。
- 為了支援 `MediaSession` 在不同 App 或進程間傳遞數據，`lib-common` 內的許多類別都實現了 `Bundleable` 介面。
- 相比 Android 原生的 `Parcelable`，`Bundleable` 提供了更靈活的版本相容性處理，確保舊版的 Controller 能與新版的 Session 正常溝通。

---

## 總結
`lib-common` 的設計哲學是 **「定義結構，但不定義行為」**。它確保了無論是本地播放、遠端控制還是影片編輯，都能夠使用同一套數據結構進行溝通，這正是 Media3 能實現高度統一化的秘密。
