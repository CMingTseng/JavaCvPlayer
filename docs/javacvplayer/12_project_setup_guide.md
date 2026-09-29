# JavaCvPlayer 開發環境與 Git 架構指引

為了在開發時能讓 AI Agent 同時讀取 Media3 參考原始碼，且保證 JavaCvPlayer 是個純淨的獨立 Repo，建議採用以下「嵌套但不關聯」的配置方式。

---

## 1. 目錄結構參考配置

將你的新專案建立在 Media3 專案的子目錄下，但透過 `.gitignore` 切斷 Git 聯繫。

```text
/ExoPlayer_media3/ (主目錄, AI 的 Context 邊界)
├── .git/ (官方 Media3 Git)
├── .gitignore (在這裡加入 JavaCvPlayer/)
└── JavaCvPlayer/ (你的新專案目錄)
    ├── .git/ (你自己的獨立 Git)
    ├── demo/app/ (JavaCvPlayer範例)
    ├── libraries/common-lite/ (抽出的 lib-common)
    └── libraries/core/ (JavaCvPlayer 核心)
```

## 2. 建立步驟 (Step-by-Step)

### 第一步：在主專案中「排除」子專案
編輯 `~/.gitignore`：
```gitignore
# 在檔案末尾加入
JavaCvPlayer/
```
*這能確保你不會意外將開發中的程式碼 commit 到 Media3 的分支中。*

### 第二步：建立並初始化子專案
```bash

mkdir JavaCvPlayer
cd JavaCvPlayer
git init
```

### 第三步：開始「抽出」開發
現在，你可以要求 AI 執行類似以下的指令：
> "參考 ../libraries/common/src/main/java/androidx/media3/common/Player.java 的定義，在我的 JavaCvPlayer/libs/common-lite/ 下建立一個精簡化的 Kotlin Player 介面。"

---

## 3. 此架構的優點

1. **AI 全域視角**：AI 可以同時看到 `libraries/` (參考源) 與 `JavaCvPlayer/` (目標)，跨目錄重構與參考非常方便。
2. **Git 獨立性**：`JavaCvPlayer` 是一個完整的、獨立的 Repo。當你開發完成，你可以直接將這個資料夾搬移到任何地方，它與 Media3 之間沒有任何 `submodule` 或實體依賴。
3. **無縫替換**：你在子專案中使用的 `package androidx.media3.common` 會與 AI 讀取到的官方定義一致，保證了邏輯的正確性。

## 4. 交付與分發
當你最終要發布 `JavaCvPlayer` 時，只需將 `JavaCvPlayer/` 目錄打包或 Push 即可。這是一個純粹的 Kotlin/JVM 專案，不帶有任何 Media3 的 Git 歷史紀錄。
