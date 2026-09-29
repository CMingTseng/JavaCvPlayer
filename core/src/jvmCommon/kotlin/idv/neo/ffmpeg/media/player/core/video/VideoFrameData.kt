//package idv.neo.ffmpeg.media.player.core.video
//
//import java.nio.ByteBuffer
//
///**
// * 平台中立的影格數據訪問介面，用於解決 JVM 端的 VerifyError 問題。
// */
//interface VideoFrameData {
//    val width: Int
//    val height: Int
//    val stride: Int
//    val timestampUs: Long
//
//    /** 取得影像數據的 ByteBuffer */
//    fun getByteBuffer(): ByteBuffer?
//
//    /** 取得底層原始影格物件 (例如 org.bytedeco.javacv.Frame) */
//    fun getRawFrame(): Any?
//}
