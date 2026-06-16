package com.bionicmusic.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bionicmusic.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Application-wide ExoPlayer wrapper. Gapless playback is enabled by feeding
 * the full queue via [setMediaItems]; ExoPlayer transitions between adjacent
 * items without a gap automatically.
 *
 * Exposes reactive state so any UI (library, player, queue, notification) can
 * observe the same single source of truth.
 */
object BionicPlayer {

    private var exo: ExoPlayer? = null

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle

    val current: Song?
        get() = _queue.value.getOrNull(_currentIndex.value)

    /** Lazily create the ExoPlayer on first use. Must be called from main thread. */
    fun ensure(context: Context): ExoPlayer {
        return exo ?: ExoPlayer.Builder(context.applicationContext)
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        _currentIndex.value = player.currentMediaItemIndex
                    }

                    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                        _shuffle.value = enabled
                    }
                })
                exo = player
            }
    }

    fun player(): ExoPlayer? = exo

    /** Replace the queue and start playing from [startIndex]. */
    fun setQueue(context: Context, songs: List<Song>, startIndex: Int) {
        val player = ensure(context)
        _queue.value = songs
        // Gapless: hand ExoPlayer all items at once.
        player.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
        _currentIndex.value = startIndex
    }

    fun playPause() {
        val p = exo ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() = exo?.seekToNextMediaItem()
    fun prev() = exo?.seekToPreviousMediaItem()
    fun seekTo(positionMs: Long) = exo?.seekTo(positionMs)
    fun playIndex(index: Int) = exo?.seekTo(index, 0L)

    fun toggleShuffle() {
        val p = exo ?: return
        p.shuffleModeEnabled = !p.shuffleModeEnabled
    }

    fun toggleMute() {
        val p = exo ?: return
        p.volume = if (p.volume > 0f) 0f else 1f
    }

    val positionMs: Long get() = exo?.currentPosition ?: 0L
    val durationMs: Long get() = exo?.duration?.coerceAtLeast(0L) ?: 0L

    fun release() {
        exo?.release()
        exo = null
    }
}
