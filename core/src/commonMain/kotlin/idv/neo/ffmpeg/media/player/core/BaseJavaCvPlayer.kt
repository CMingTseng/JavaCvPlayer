package idv.neo.ffmpeg.media.player.core

import idv.neo.ffmpeg.media.player.core.audio.AudioSink
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import idv.neo.ffmpeg.media.player.core.buffer.MediaFrameQueue
import idv.neo.ffmpeg.media.player.core.buffer.VideoFrame
import idv.neo.ffmpeg.media.player.core.loader.FrameLoader
import idv.neo.ffmpeg.media.player.core.time.KmpMediaClock
import idv.neo.ffmpeg.media.player.core.video.VideoSink
import kotlinx.coroutines.*
import co.touchlab.kermit.Logger

/**
 * JavaCvPlayer 的核心實作，參考 Media3 ExoPlayerImpl 的結構。
 */
@OptIn(UnstableApi::class)
abstract class BaseJavaCvPlayer<V : VideoFrame, A : VideoFrame>(
    protected val videoQueue: MediaFrameQueue<V>,
    protected val audioQueue: MediaFrameQueue<A>,
    protected val audioSink: AudioSink,
    protected val videoSink: VideoSink,
    protected val loader: FrameLoader
) : Player {

    protected val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val logger = Logger.withTag("BaseJavaCvPlayer")
    private val clock = KmpMediaClock()
    private val listeners = mutableListOf<Player.Listener>()
    private val playlist = mutableListOf<MediaItem>()
    private var currentMediaItemIndexInternal = -1
    override val currentMediaItemIndex: Int get() = currentMediaItemIndexInternal

    override val playerError: PlaybackException?
        get() = null

    override val currentMediaItem: MediaItem?
        get() = if (currentMediaItemIndexInternal in playlist.indices) playlist[currentMediaItemIndexInternal] else null

    override val mediaMetadata: MediaMetadata
        get() = MediaMetadata.EMPTY

    override val playlistMetadata: MediaMetadata
        get() = MediaMetadata.EMPTY

    override val isCurrentMediaItemLive: Boolean
        get() = false

    override val isPlayingAd: Boolean
        get() = false

    override val currentTimeline: Timeline
        get() = Timeline.EMPTY

    override val currentPeriodIndex: Int
        get() = 0

    override val currentTracks: Tracks
        get() = Tracks.EMPTY

    override fun getAvailableCommands(): Player.Commands = Player.Commands(emptySet())

    override var playWhenReady: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                clock.onPlaybackStateChanged(value && playbackState == Player.STATE_READY)
                notifyListeners { it.onPlayWhenReadyChanged(value, 1) } // 1: PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                if (value && playbackState == Player.STATE_READY) {
                    startPlaybackLoop()
                }
            }
        }

    override var repeatMode: Int = Player.REPEAT_MODE_OFF
        set(value) {
            field = value
            notifyListeners { it.onRepeatModeChanged(value) }
        }

    override var shuffleModeEnabled: Boolean = false
        set(value) {
            field = value
            notifyListeners { it.onShuffleModeEnabledChanged(value) }
        }

    override var volume: Float = 1.0f
        set(value) {
            field = value
            audioSink.setVolume(value)
            notifyListeners { it.onVolumeChanged(value) }
        }

    private var _playbackState = Player.STATE_IDLE
    override val playbackState: Int get() = _playbackState

    override val videoSize: VideoSize
        get() = VideoSize(width = videoWidth, height = videoHeight)

    protected var videoWidth: Int = 0
        private set
    protected var videoHeight: Int = 0
        private set
    override var resizeMode: Int = 0 // Default: FIT

    override val isPlaying: Boolean
        get() = playbackState == Player.STATE_READY && playWhenReady

    override val currentPosition: Long
        get() = clock.getPositionMicros(audioSink) / 1000

    private var _duration = 0L
    override val duration: Long get() = _duration

    protected var currentMetadata: FrameLoader.Metadata? = null
        private set

    override val bufferedPosition: Long
        get() = currentPosition + (videoQueue.bufferedDurationUs / 1000)

    private var playbackJob: Job? = null

    protected fun setPlaybackState(state: Int) {
        if (_playbackState != state) {
            _playbackState = state
            // 當進入 READY 狀態時，若 playWhenReady 為 true，確保時鐘也啟動
            if (state == Player.STATE_READY) {
                clock.onPlaybackStateChanged(playWhenReady)
            }
            notifyListeners { it.onPlaybackStateChanged(state) }
            if (state == Player.STATE_READY) {
                notifyListeners { it.onIsPlayingChanged(isPlaying) }
            }
        }
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_PLAY_PAUSE -> true
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM -> true
            Player.COMMAND_SET_VIDEO_SURFACE -> true
            Player.COMMAND_SEEK_BACK -> true
            Player.COMMAND_SEEK_FORWARD -> true
            Player.COMMAND_SEEK_TO_NEXT -> hasNext()
            Player.COMMAND_SEEK_TO_PREVIOUS -> hasPrevious()
            else -> false
        }
    }

    override fun prepare() {
        setPlaybackState(Player.STATE_BUFFERING)
    }

    override fun play() {
        playWhenReady = true
    }

    override fun pause() {
        playWhenReady = false
    }

    override fun stop() {
        playWhenReady = false
        loader.stop()
        videoQueue.clear()
        audioQueue.clear()
        audioSink.stop()
        clock.reset()
        onReset()
        setPlaybackState(Player.STATE_IDLE)
    }

    fun flush() {
        audioSink.flush()
    }

    override fun release() {
        stop()
        loader.release()
        audioSink.release()
        mainScope.cancel()
    }

    override fun seekTo(positionMs: Long) {
        loader.seekTo(positionMs * 1000)
        clock.reset()
        onReset()
        notifyListeners { it.onPositionDiscontinuity(0) } // 0: DISCONTINUITY_REASON_SEEK
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex in playlist.indices) {
            currentMediaItemIndexInternal = mediaItemIndex
            setMediaItemInternal(playlist[mediaItemIndex], positionMs)
        }
    }

    override fun seekToDefaultPosition() {
        seekTo(0L)
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        seekTo(mediaItemIndex, 0L)
    }

    /**
     * 當播放器重置 (Stop 或 Seek) 時呼叫，供子類別重置內部狀態。
     */
    protected open fun onReset() {}

    override fun setMediaItem(mediaItem: MediaItem) {
        playlist.clear()
        playlist.add(mediaItem)
        currentMediaItemIndexInternal = 0
        setMediaItemInternal(mediaItem, 0L)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        playlist.clear()
        playlist.addAll(mediaItems)
        if (playlist.isNotEmpty()) {
            currentMediaItemIndexInternal = 0
            setMediaItemInternal(playlist[0], 0L)
        }
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        playlist.clear()
        playlist.add(mediaItem)
        currentMediaItemIndexInternal = 0
        setMediaItemInternal(mediaItem, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        setMediaItem(mediaItem, if (resetPosition) 0L else currentPosition)
    }

    private fun setMediaItemInternal(mediaItem: MediaItem, startPositionMs: Long) {
        val url = mediaItem.localConfiguration?.uri.toString()
        logger.i { "setMediaItemInternal: $url" }
        setPlaybackState(Player.STATE_BUFFERING)
        
        // 停止舊的播放
        playbackJob?.cancel()
        loader.stop()
        videoQueue.clear()
        audioQueue.clear()
        audioSink.stop()
        clock.reset()
        onReset()
        
        // 使用 mainScope 啟動，確保 loader.start 不會阻塞
        mainScope.launch {
            // 給予極短時間讓舊 Grabber 釋放資源
            delay(50)
            
            loader.start(url) { metadata ->
                currentMetadata = metadata
                _duration = metadata.durationUs / 1000
                mainScope.launch {
                    onMetadataLoaded(metadata)
                    if (startPositionMs > 0) {
                        seekTo(startPositionMs)
                    }
                    setPlaybackState(Player.STATE_READY)
                    if (playWhenReady) startPlaybackLoop()
                }
            }
        }
    }

    override fun seekToNext() {
        if (hasNext()) {
            currentMediaItemIndexInternal++
            setMediaItemInternal(playlist[currentMediaItemIndexInternal], 0L)
        }
    }

    override fun seekToPrevious() {
        if (hasPrevious()) {
            currentMediaItemIndexInternal--
            setMediaItemInternal(playlist[currentMediaItemIndexInternal], 0L)
        }
    }

    override val seekBackIncrement: Long = 10_000L
    override val seekForwardIncrement: Long = 10_000L

    override fun seekBack() {
        seekTo(currentPosition - seekBackIncrement)
    }

    override fun seekForward() {
        seekTo(currentPosition + seekForwardIncrement)
    }

    override fun hasNext(): Boolean = currentMediaItemIndexInternal < playlist.size - 1
    override fun hasPrevious(): Boolean = currentMediaItemIndexInternal > 0

    override fun setVideoSurface(surface: Any?) {}
    override fun setVideoSurfaceView(surfaceView: Any?) {}
    override fun clearVideoSurfaceView(surfaceView: Any?) {}
    override fun setVideoTextureView(textureView: Any?) {}
    override fun clearVideoTextureView(textureView: Any?) {}

    protected open fun onMetadataLoaded(metadata: FrameLoader.Metadata) {
        videoWidth = metadata.width
        videoHeight = metadata.height
        
        // 關鍵修正：通知 VideoSink 影片尺寸，以便分配 Buffer
        videoSink.setVideoSize(videoWidth, videoHeight)

        if (metadata.hasAudio) {
            audioSink.setup(metadata.sampleRate, metadata.audioChannels)
        }
        notifyListeners { it.onVideoSizeChanged(VideoSize(width = videoWidth, height = videoHeight)) }
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = mainScope.launch(Dispatchers.Default) {
            while (isActive && playWhenReady && playbackState == Player.STATE_READY) {
                doSomeWork()
                yield()
            }
        }
    }

    protected abstract suspend fun doSomeWork()

    protected fun getClock(): KmpMediaClock = clock

    private var _playbackParameters = PlaybackParameters.DEFAULT
    override var playbackParameters: PlaybackParameters
        get() = _playbackParameters
        set(value) {
            if (_playbackParameters != value) {
                _playbackParameters = value
                audioSink.setPlaybackSpeed(value.speed)
                clock.setPlaybackSpeed(value.speed)
                notifyListeners { it.onPlaybackParametersChanged(value) }
            }
        }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }

    protected fun notifyListeners(action: (Player.Listener) -> Unit) {
        mainScope.launch {
            listeners.forEach(action)
        }
    }
}