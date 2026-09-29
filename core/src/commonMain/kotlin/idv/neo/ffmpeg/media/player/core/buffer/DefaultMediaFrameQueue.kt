package idv.neo.ffmpeg.media.player.core.buffer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 預設的影格佇列實作。
 */
class DefaultMediaFrameQueue<T : VideoFrame>(
    private val capacity: Int = 60
) : MediaFrameQueue<T> {

    private val queue = ArrayDeque<T>()
    private val mutex = Mutex()
    
    private val _state = MutableStateFlow<MediaFrameQueue.QueueState>(MediaFrameQueue.QueueState.Empty)
    override val state: StateFlow<MediaFrameQueue.QueueState> = _state.asStateFlow()

    private var _bufferedDurationUs = 0L
    override val bufferedDurationUs: Long get() = _bufferedDurationUs

    override val size: Int get() = queue.size
    override val isEmpty: Boolean get() = queue.isEmpty()
    override val isFull: Boolean get() = queue.size >= capacity

    override suspend fun enqueue(frame: T) {
        while (isFull) {
            delay(5)
        }
        mutex.withLock {
            queue.addLast(frame)
            updateStateLocked()
        }
    }

    override suspend fun dequeue(): T? {
        return mutex.withLock {
            val frame = if (queue.isNotEmpty()) queue.removeFirst() else null
            updateStateLocked()
            frame
        }
    }

    override suspend fun peek(): T? {
        return mutex.withLock {
            queue.firstOrNull()
        }
    }

    /**
     * 清空隊列。必須是非掛起函數。
     */
    override fun clear() {
        // 使用 synchronized 確保在 JVM 環境下的執行緒安全
        synchronized(queue) {
            while (queue.isNotEmpty()) {
                queue.removeFirst().release()
            }
            _state.value = MediaFrameQueue.QueueState.Empty
            _bufferedDurationUs = 0
        }
    }

    private fun updateStateLocked() {
        _state.value = when {
            queue.isEmpty() -> MediaFrameQueue.QueueState.Empty
            queue.size >= capacity -> MediaFrameQueue.QueueState.Full
            else -> MediaFrameQueue.QueueState.Ready
        }
    }
}
