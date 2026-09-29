# 重要次要組件與擴展模組說明

除了核心播放功能，Media3 還提供了豐富的次要組件來處理 UI、跨進程通訊以及格式擴展。

## 1. MediaSession 模組 (`lib-session`)
MediaSession 是 Media3 的一大重點，它將原本複雜的 MediaSessionCompat 簡化並與 Player 介面深度整合。

- **MediaSession**: 將 `Player` 實例包裝起來，對外提供通訊介面。
- **MediaController**: 客戶端 (如 UI 執行緒或另一個 App) 用來控制播放的工具。
- **設計理念**: 讓播放邏輯可以運行在背景 Service 中，而 UI 透過 Controller 進行操作。即使 UI 進程被殺死，播放仍可繼續。

## 2. UI 組件 (`lib-ui`)
提供開箱即用的播放控制介面：
- **PlayerView**: 整合了視訊渲染層 (Surface) 與控制層 (PlayerControlView)。
- **StyledPlayerView**: 提供更現代、可自定義 CSS 樣式的 UI (在某些版本中與 PlayerView 合併或替代)。
- **設計優點**: 自動處理字幕顯示、畫中畫 (PiP) 邏輯與多種比例縮放。

## 3. 解碼器擴展 (以 `lib-decoder-ffmpeg` 為例)
當 Android 系統內建的硬體解碼器 (`MediaCodec`) 無法支援某些特殊格式 (如 Opus, FLAC, 或特定的 AC3) 時，開發者可以使用軟體解碼擴展。

- **FFmpeg 擴展**: 透過 JNI 呼叫 C/C++ 的 FFmpeg 函式庫進行解碼。
- **使用方式**: 
    - 需要手動編譯 FFmpeg 原始碼 (NDK)。
    - 在 `DefaultRenderersFactory` 中將 `extensionRendererMode` 設為 `ON` 或 `PREFER`。
- **為什麼需要它？**: 解決 Android 碎片化問題，保證在所有裝置上都有統一的解碼支援。

## 4. Transformer 模組 (`lib-transformer`)
Media3 新增的媒體處理引擎，旨在替代舊有的 `MediaMuxer` 與 `MediaCodec` 手動拼接邏輯。
- **用途**: 轉碼、剪輯、影片特效、慢動作處理。
- **優勢**: 使用與 ExoPlayer 相同的基礎架構，處理流程極其高效。

## 5. 資料源擴展 (`lib-datasource-*`)
- **Cronet**: 使用 Google 的網路堆疊 (支援 QUIC/HTTP3)。
- **OkHttp**: 整合常用的 OkHttp 網路函式庫，方便共享攔截器與緩存配置。
