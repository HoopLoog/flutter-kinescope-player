package io.kinescope.flutter_kinescope_sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.flutter.plugin.common.EventChannel
import io.kinescope.sdk.player.KinescopePlayerOptions
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView
import java.util.concurrent.atomic.AtomicLong

@OptIn(UnstableApi::class)
class KinescopePlayerRegistry(
    private val eventSink: (Map<String, Any?>) -> Unit,
) {
    private val players = mutableMapOf<Long, PlayerEntry>()
    private val nextId = AtomicLong(1)
    private val mainHandler = Handler(Looper.getMainLooper())

    data class PlayerEntry(
        val player: KinescopeVideoPlayer,
        var playerView: KinescopePlayerView? = null,
        val listener: Player.Listener,
        val timeUpdateRunnable: Runnable,
        val texttrackPreference: Boolean?,
    )

    fun create(context: Context, args: Map<*, *>?): Long {
        val options = KinescopePlayerOptionsFactory.fromMap(args)
        val texttrackPreference = args?.get("texttrack") as? Boolean

        val id = nextId.getAndIncrement()
        val player = KinescopeVideoPlayer(context, options)
        if (options.showSubtitlesButton) {
            player.setShowSubtitles(true)
        }
        var entryRef: PlayerEntry? = null

        val timeUpdateRunnable = object : Runnable {
            override fun run() {
                val entry = entryRef ?: return
                val exoPlayer = entry.player.exoPlayer ?: return
                val durationMs = exoPlayer.duration
                if (durationMs <= 0) {
                    mainHandler.postDelayed(this, TIME_UPDATE_INTERVAL_MS)
                    return
                }
                val currentMs = exoPlayer.currentPosition
                val percent = ((currentMs.toDouble() / durationMs.toDouble()) * 100).toInt()
                emit(
                    id,
                    mapOf(
                        "type" to "timeUpdate",
                        "currentTime" to currentMs / 1000.0,
                        "percent" to percent,
                    ),
                )
                mainHandler.postDelayed(this, TIME_UPDATE_INTERVAL_MS)
            }
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> emit(id, statusEvent("unknown"))
                    Player.STATE_BUFFERING -> emit(id, statusEvent("waiting"))
                    Player.STATE_READY -> {
                        if (player.exoPlayer?.isPlaying == true) {
                            emit(id, statusEvent("playing"))
                        } else {
                            emit(id, statusEvent("ready"))
                        }
                    }
                    Player.STATE_ENDED -> emit(id, statusEvent("ended"))
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    emit(id, statusEvent("playing"))
                    mainHandler.removeCallbacks(timeUpdateRunnable)
                    mainHandler.post(timeUpdateRunnable)
                } else {
                    val state = player.exoPlayer?.playbackState
                    if (state == Player.STATE_ENDED) {
                        emit(id, statusEvent("ended"))
                    } else if (state == Player.STATE_READY) {
                        emit(id, statusEvent("pause"))
                    }
                    mainHandler.removeCallbacks(timeUpdateRunnable)
                }
            }
        }

        val entry = PlayerEntry(
            player = player,
            listener = listener,
            timeUpdateRunnable = timeUpdateRunnable,
            texttrackPreference = texttrackPreference,
        )
        entryRef = entry
        players[id] = entry
        player.exoPlayer?.addListener(listener)
        emit(id, statusEvent("init"))
        return id
    }

    fun attachView(playerId: Long, playerView: KinescopePlayerView) {
        val entry = getEntry(playerId)
        entry.playerView = playerView
        playerView.setPlayer(entry.player)
        playerView.applyTemplateOptions()
        if (entry.player.getVideo() != null) {
            playerView.refreshPlayerChrome()
        }
    }

    fun detachView(playerId: Long) {
        players[playerId]?.playerView?.setPlayer(null)
        players[playerId]?.playerView = null
    }

    fun get(playerId: Long): KinescopeVideoPlayer = getEntry(playerId).player

    fun loadVideo(
        playerId: Long,
        videoId: String,
        onSuccess: () -> Unit,
        onFailed: (Throwable?) -> Unit,
    ) {
        val entry = getEntry(playerId)
        val player = entry.player
        if (player.kinescopePlayerOptions.showSubtitlesButton) {
            player.setShowSubtitles(true)
        }
        player.loadVideo(
            videoId,
            onSuccess = { video ->
                if (video != null && KinescopeSubtitlePlayback.shouldEnableForVideo(entry.texttrackPreference, video)) {
                    player.setShowSubtitles(true)
                    KinescopeSubtitlePlayback.apply(player, video)
                }
                mainHandler.post {
                    entry.playerView?.refreshPlayerChrome()
                    onSuccess()
                }
            },
            onFailed = onFailed,
        )
    }

    fun dispose(playerId: Long) {
        val entry = players.remove(playerId) ?: return
        mainHandler.removeCallbacks(entry.timeUpdateRunnable)
        entry.player.exoPlayer?.removeListener(entry.listener)
        entry.playerView?.setPlayer(null)
        entry.player.release()
    }

    fun disposeAll() {
        players.keys.toList().forEach { dispose(it) }
    }

    private fun getEntry(playerId: Long): PlayerEntry =
        players[playerId] ?: throw IllegalStateException("No player for id $playerId")

    private fun emit(playerId: Long, payload: Map<String, Any?>) {
        mainHandler.post {
            eventSink(payload + mapOf("playerId" to playerId))
        }
    }

    private fun statusEvent(status: String): Map<String, Any?> =
        mapOf("type" to "status", "status" to status)

    companion object {
        private const val TIME_UPDATE_INTERVAL_MS = 500L
    }
}
