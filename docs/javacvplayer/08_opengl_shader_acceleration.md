# OpenGL Shader 渲染加速詳解

當你的目標是 4K 60fps 或是需要在影片上疊加複雜濾鏡時，使用 GPU 進行 **色彩空間轉換 (CSC)** 是唯一的選擇。

---

## 1. JVM 上的 OpenGL 處理：需要進階處理嗎？

**是的，需要。**
雖然 OS 與顯示卡驅動程式提供了 OpenGL 的實現，但 JVM 本身並不包含直接呼叫 OpenGL 的標準 API。

### 你需要解決的兩件事：
1. **繫結 (Bindings)**：你需要一個 JNI 橋接庫來呼叫 OpenGL 函數。常用的有 **LWJGL** (Lightweight Java Game Library) 或 **JOGL**。
2. **上下文管理 (Context Management)**：OpenGL 需要一個 Window 或 Surface 作為渲染目標。
   - **Compose Desktop**：內建 Skia，你可以透過 Skia 的 `Canvas` 獲取 GPU 上下文。
   - **JavaFX**：底層雖然使用 Prism (OpenGL/D3D)，但對外開放自定義 Shader 的難度較高，通常需要透過 `GLCanvas` (如 LWJGL-FX) 進行橋接。

---

## 2. YUV420P to RGB Fragment Shader 範例

這是最常用的 Shader，它在 GPU 內將 JavaCV 抓取的 Y、U、V 三個平面合成為一個 RGB 像素。

### GLSL 程式碼 (Fragment Shader)
```glsl
#version 330 core
out vec4 FragColor;
in vec2 TexCoord;

// 三個紋理，分別對應 Y, U, V 平面
uniform sampler2D yTexture;
uniform sampler2D uTexture;
uniform sampler2D vTexture;

void main() {
    float y = texture(yTexture, TexCoord).r;
    float u = texture(uTexture, TexCoord).r - 0.5;
    float v = texture(vTexture, TexCoord).r - 0.5;

    // YUV to RGB 轉換矩陣 (BT.601 標準)
    float r = y + 1.402 * v;
    float g = y - 0.344136 * u - 0.714136 * v;
    float b = y + 1.772 * u;

    FragColor = vec4(r, g, b, 1.0);
}
```

---

## 3. 實作流程設計

### Step 1: 提取 YUV 數據
不要在 Grabber 中設定 `setPixelFormat(BGRA)`。保持原始的 `AV_PIX_FMT_YUV420P`，這樣傳輸到 GPU 的數據量會減少一半。

### Step 2: 上傳紋理 (Texture Upload)
在每一幀渲染時：
1. `glActiveTexture(GL_TEXTURE0)` -> 繫結 Y 平面數據。
2. `glActiveTexture(GL_TEXTURE1)` -> 繫結 U 平面數據。
3. `glActiveTexture(GL_TEXTURE2)` -> 繫結 V 平面數據。
*注意：U 和 V 平面的寬高通常只有 Y 的一半。*

### Step 3: 繪製四邊形
在畫面上畫一個填滿窗口的矩形，並應用上述 Shader。

---

## 4. 跨平台與驅動程式

- **驅動程式處理了什麼？**：驅動程式負責將你的 GLSL 程式碼編譯成 GPU 指令，並執行高效的並行計算。
- **你需要處理什麼？**：
    - **同步 (Synchronization)**：確保解碼器寫入 Buffer 時，GPU 沒有正在讀取它（可以使用 PBO - Pixel Buffer Object 進行加速）。
    - **跨平台差異**：
        - Windows: 驅動程式通常對 Direct3D 支援更好，OpenGL 可能需要特定的顯卡驅動。
        - macOS: OpenGL 已被棄用 (Deprecated)，建議使用 Metal，或透過 Skia (Compose Desktop) 隱藏這些差異。

---

## 5. 在 Compose Desktop 中的「優雅」實作方式

如果你使用 Compose Desktop，你可以利用 **Skia 的 RuntimeShader** (AGSL，語法極像 GLSL)：

```kotlin
val shader = RuntimeEffect.makeForShader("""
    uniform shader content;
    uniform shader yTex;
    uniform shader uTex;
    uniform shader vTex;
    
    half4 main(float2 fragCoord) {
        float y = yTex.eval(fragCoord).r;
        float u = uTex.eval(fragCoord).r - 0.5;
        float v = vTex.eval(fragCoord).r - 0.5;
        // ... 執行轉換 ...
        return half4(r, g, b, 1.0);
    }
""").makeShader(...)
```

**總結**：使用 OpenGL Shader 能極大地降低 CPU 使用率（從 30-50% 降至 5% 以下），但會增加開發複雜度。對於一般應用，建議優先使用 **Skia 零拷貝** 或 **JavaFX PixelBuffer**；只有在處理 4K+ 或是需要特效時才動用 OpenGL Shader。
