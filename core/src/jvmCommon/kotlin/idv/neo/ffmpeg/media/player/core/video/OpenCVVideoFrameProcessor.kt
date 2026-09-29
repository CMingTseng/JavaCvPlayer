package idv.neo.ffmpeg.media.player.core.video

import co.touchlab.kermit.Logger
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat

/**
 * 基於 OpenCV 的影格處理器。
 * 負責在渲染前執行旋轉、縮放、濾鏡等「全處理」邏輯。
 */
class OpenCVVideoFrameProcessor : VideoFrameProcessor {
    private val logger = Logger.Companion.withTag("OpenCVVideoFrameProcessor")
    private val converter = OpenCVFrameConverter.ToMat()

    // 配置參數
    var rotationAngle: Int = 0 // 0, 90, 180, 270
    var isMirror: Boolean = false
    var applyGrayscale: Boolean = false

    override fun process(frame: Any): Any {
        if (frame !is Frame || frame.image == null) return frame

        try {
            // 1. 轉換為 OpenCV Mat
            var mat = converter.convert(frame) ?: return frame

            // 2. 執行特效處理鏈
            mat = applyEffects(mat)

            // 3. 轉回 JavaCV Frame
            // 注意：converter.convert() 會複用 Buffer，但在處理鏈中可能產生了新 Mat
            return converter.convert(mat)
        } catch (e: Exception) {
            logger.e(e) { "OpenCV processing failed" }
            return frame
        }
    }

    private fun applyEffects(input: Mat): Mat {
        var result = input

        // 旋轉處理
        if (rotationAngle != 0) {
            val code = when (rotationAngle) {
                90 -> opencv_core.ROTATE_90_CLOCKWISE
                180 -> opencv_core.ROTATE_180
                270 -> opencv_core.ROTATE_90_COUNTERCLOCKWISE
                else -> -1
            }
            if (code != -1) {
                val rotated = Mat()
                opencv_core.rotate(result, rotated, code)
                result = rotated
            }
        }

        // 鏡像處理
        if (isMirror) {
            val flipped = Mat()
            opencv_core.flip(result, flipped, 1) // 1: 水平翻轉
            result = flipped
        }

        // 灰階濾鏡 (範例)
        if (applyGrayscale) {
            val gray = Mat()
            opencv_imgproc.cvtColor(result, gray, opencv_imgproc.COLOR_RGBA2GRAY)
            // 轉回 RGBA 以便後續 Sink 渲染 (假設 Sink 偏好 RGBA)
            val rgba = Mat()
            opencv_imgproc.cvtColor(gray, rgba, opencv_imgproc.COLOR_GRAY2RGBA)
            result = rgba
        }

        return result
    }

    override fun reset() {
        // 重置狀態
    }

    override fun release() {
        converter.close()
    }
}