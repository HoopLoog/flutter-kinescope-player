package io.kinescope.flutter_kinescope_sdk

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import io.kinescope.sdk.view.KinescopePlayerView

internal object KinescopePlayerViewChrome {
    private const val SDK_PACKAGE = "io.kinescope.sdk"

    private val controlButtonIds = listOf(
        "kinescope_fullscreen",
        "kinescope_play_pause",
        "kinescope_pip",
        "kinescope_options",
        "kinescope_chapters",
        "kinescope_playlist",
        "kinescope_subtitles",
    )

    fun prepare(playerView: KinescopePlayerView) {
        playerView.post {
            stripControlButtonRipples(playerView)
            clearControlButtonPressState(playerView)
        }
    }

    fun stripControlButtonRipples(playerView: KinescopePlayerView) {
        for (idName in controlButtonIds) {
            findViewBySdkId(playerView, idName)?.let(::stripRipple)
        }
        stripRipplesInTree(playerView)
    }

    fun clearControlButtonPressState(playerView: KinescopePlayerView) {
        for (idName in controlButtonIds) {
            findViewBySdkId(playerView, idName)?.let(::clearPressState)
        }
        clearPressStateInTree(playerView)
    }

    private fun findViewBySdkId(root: View, idName: String): View? {
        val id = root.resources.getIdentifier(idName, "id", SDK_PACKAGE)
        return if (id != 0) root.findViewById(id) else null
    }

    private fun stripRipple(view: View) {
        view.background = null
        view.foreground = null
        view.stateListAnimator = null
    }

    private fun clearPressState(view: View) {
        view.cancelPendingInputEvents()
        view.isPressed = false
        view.isSelected = false
        view.isActivated = false
        view.clearFocus()
        view.jumpDrawablesToCurrentState()
        view.refreshDrawableState()
    }

    private fun stripRipplesInTree(root: View) {
        if (root is ImageButton) {
            stripRipple(root)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                stripRipplesInTree(root.getChildAt(index))
            }
        }
    }

    private fun clearPressStateInTree(root: View) {
        if (root is ImageButton) {
            clearPressState(root)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                clearPressStateInTree(root.getChildAt(index))
            }
        }
    }
}
