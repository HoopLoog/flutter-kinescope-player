package io.kinescope.flutter_kinescope_sdk

import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

@OptIn(UnstableApi::class)
internal object KinescopeVideoSurfaceHelper {
    private const val SDK_PACKAGE = "io.kinescope.sdk"
    private const val EXO_PLAYER_VIEW_ID = "view_exoplayer"

    fun rebind(playerView: KinescopePlayerView, videoPlayer: KinescopeVideoPlayer) {
        val exoPlayer = videoPlayer.exoPlayer ?: return
        val exoViewId = playerView.resources.getIdentifier(EXO_PLAYER_VIEW_ID, "id", SDK_PACKAGE)
        if (exoViewId == 0) {
            return
        }
        val exoView = playerView.findViewById<PlayerView>(exoViewId) ?: return
        playerView.post {
            restoreVideoSurfaceInTree(playerView)
            exoView.player = null
            exoPlayer.clearVideoSurface()
            restoreVideoSurfaceInTree(playerView)
            exoView.player = exoPlayer
            exoView.invalidate()
            playerView.invalidate()
            playerView.requestLayout()
        }
    }

    /** Rebinds after layout so SurfaceView has a real surface (avoids black PiP frame). */
    fun rebindAfterLayout(
        playerView: KinescopePlayerView,
        videoPlayer: KinescopeVideoPlayer,
        delaysMs: LongArray = longArrayOf(0L, 50L, 200L, 500L),
    ) {
        restoreVideoSurface(playerView)
        for (delayMs in delaysMs) {
            playerView.postDelayed({
                restoreVideoSurface(playerView)
                rebind(playerView, videoPlayer)
            }, delayMs)
        }
    }

    fun restoreVideoSurface(playerView: KinescopePlayerView) {
        restoreVideoSurfaceInTree(playerView)
        playerView.invalidate()
        playerView.requestLayout()
    }

    /**
     * Detaches the view from ExoPlayer without stopping playback.
     * Used when a PlatformView is disposed while PiP/fullscreen still owns the player.
     */
    fun unbindView(playerView: KinescopePlayerView, videoPlayer: KinescopeVideoPlayer? = null) {
        hideAllSurfaces(playerView)
        playerView.visibility = View.GONE
        (playerView.parent as? View)?.let { parent ->
            parent.visibility = View.GONE
            parent.invalidate()
        }
        playerView.setPlayer(null)
        videoPlayer?.exoPlayer?.clearVideoSurface()
        playerView.invalidate()
    }

    /**
     * Clears the last video frame and stops playback. Use when leaving the player route.
     */
    fun teardown(playerView: KinescopePlayerView, videoPlayer: KinescopeVideoPlayer? = null) {
        unbindView(playerView, videoPlayer)
        val exoPlayer = videoPlayer?.exoPlayer ?: return
        exoPlayer.pause()
        exoPlayer.stop()
    }

    private fun restoreVideoSurfaceInTree(view: View) {
        when (view) {
            is TextureView -> {
                view.visibility = View.VISIBLE
                view.alpha = 1f
            }
            is SurfaceView -> {
                view.visibility = View.VISIBLE
                view.alpha = 1f
            }
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    restoreVideoSurfaceInTree(view.getChildAt(index))
                }
            }
        }
    }

    private fun hideAllSurfaces(view: View) {
        when (view) {
            is TextureView -> {
                view.visibility = View.GONE
                view.alpha = 0f
            }
            is SurfaceView -> {
                view.visibility = View.GONE
                view.alpha = 0f
            }
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    hideAllSurfaces(view.getChildAt(index))
                }
            }
        }
    }
}
