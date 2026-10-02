package idv.neo.ffmpeg.media.player.core.video

/**
 * 支援視覺特效與顏色調整的 VideoSink 擴充合約。
 * 綜合彙整了 ColorFilter (色調)、ImageFilter (模糊) 與 Fragment Shader 
 * 三者「共通」能夠實現的核心濾鏡能力：亮度、對比度、飽和度、模糊、灰階。
 * 1.
 * 色彩矩陣類（ColorMatrix / ColorFilter / Shader 均支援）：
 * ◦
 * 亮度 (setBrightness)：調整畫面整體的明暗度。
 * ◦
 * 對比度 (setContrast)：強化亮部與暗部的對比區隔。
 * ◦
 * 飽和度 (setSaturation)：控制色彩的鮮豔程度（從彩色到黑白）。
 * ◦
 * 灰階 (setGrayscale)：移除色彩，呈現純黑白效果。
 * 2.
 * 空間卷積類（ImageFilter / Shader 均支援）：
 * ◦
 * 模糊 (setBlur)：高斯模糊效果（在 Skia 中透過 ImageFilter.makeBlur 實現，在 Shader 中透過多點取樣實現）。
 */
interface FilterableVideoSink : VideoSink {
    /** 設定亮度 [-1.0 ~ 1.0]，預設 0.0 */
    fun setBrightness(brightness: Float)

    /** 設定對比度 [0.0 ~ 2.0]，預設 1.0 */
    fun setContrast(contrast: Float)

    /** 設定飽和度 [0.0 ~ 2.0]，預設 1.0 */
    fun setSaturation(saturation: Float)

    /** 設定高斯模糊半徑 [0.0 ~ max]，預設 0.0 */
    fun setBlur(radius: Float)

    /** 設定是否啟用灰階 (黑白) 效果 */
    fun setGrayscale(enabled: Boolean)
}
