package idv.neo.ffmpeg.media.player.core.buffer

import kotlinx.coroutines.flow.StateFlow

/**
 * 影格佇列介面。
 */
interface MediaFrameQueue<T : MediaFrame> {
    suspend fun enqueue(frame: T)
    suspend fun dequeue(): T?
    suspend fun peek(): T?

    /** 清空佇列。必須是非掛起函數。 */
    fun clear()
    val size: Int
    val isEmpty: Boolean
    val bufferedDurationUs: Long
    val isFull: Boolean
    val state: StateFlow<QueueState>

    sealed interface QueueState {
        object Empty : QueueState
        object Buffering : QueueState
        object Ready : QueueState
        object Full : QueueState
    }
}
