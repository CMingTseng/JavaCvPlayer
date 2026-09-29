package idv.neo.ffmpeg.media.player.core.time

/**
 * JVM 端的系統時間實作。
 */
actual fun getCurrentTimeNanos(): Long = System.nanoTime()
