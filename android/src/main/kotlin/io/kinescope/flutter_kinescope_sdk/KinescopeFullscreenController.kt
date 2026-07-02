package io.kinescope.flutter_kinescope_sdk

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * Fullscreen player overlay on top of the Activity content view.
 *
 * Video moves to overlay via [KinescopePlayerView.switchTargetView]; Activity rotates to landscape.
 */
@OptIn(UnstableApi::class)
class KinescopeFullscreenController(
    private val activityProvider: () -> Activity?,
    private val onFullscreenChanged: (Boolean) -> Unit = {},
) {
    private var inlineView: KinescopePlayerView? = null
    private var overlayContainer: FrameLayout? = null
    private var fullscreenView: KinescopePlayerView? = null
    private var player: KinescopeVideoPlayer? = null
    private var isVideoFullscreen = false
    private var savedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private val mainHandler = Handler(Looper.getMainLooper())

    fun attach(
        inlineView: KinescopePlayerView,
        player: KinescopeVideoPlayer,
    ) {
        this.inlineView = inlineView
        this.player = player

        inlineView.setIsFullscreen(false)
        inlineView.onFullscreenButtonCallback = { onFullscreenButtonClicked(inlineView) }
        KinescopePlayerViewChrome.prepare(inlineView)
    }

    fun detach() {
        if (isVideoFullscreen) {
            exitFullscreen()
        }
        removeOverlay()
        inlineView?.onFullscreenButtonCallback = null
        inlineView = null
        player = null
    }

    private fun onFullscreenButtonClicked(sourceView: KinescopePlayerView) {
        KinescopePlayerViewChrome.clearControlButtonPressState(sourceView)
        mainHandler.post { toggleFullscreen() }
    }

    private fun ensureOverlay(activity: Activity) {
        if (fullscreenView != null) {
            return
        }

        val fullscreenPlayerView = KinescopePlayerView(activity, null).apply {
            setIsFullscreen(true)
            applyTemplateOptions()
        }

        val container = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            clipChildren = false
            clipToPadding = false
            isVisible = false
            elevation = 10000f
            translationZ = 10000f
            addView(
                fullscreenPlayerView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        activity.addContentView(
            container,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        overlayContainer = container
        fullscreenView = fullscreenPlayerView
        fullscreenPlayerView.onFullscreenButtonCallback = {
            onFullscreenButtonClicked(fullscreenPlayerView)
        }
        KinescopePlayerViewChrome.prepare(fullscreenPlayerView)
    }

    private fun toggleFullscreen() {
        if (isVideoFullscreen) {
            exitFullscreen()
        } else {
            enterFullscreen()
        }
    }

    private fun enterFullscreen() {
        val activity = activityProvider() ?: return
        val inline = inlineView ?: return
        val kinescopePlayer = player ?: return

        ensureOverlay(activity)
        val overlay = overlayContainer ?: return
        val fullscreen = fullscreenView ?: return

        isVideoFullscreen = true
        onFullscreenChanged(true)

        savedOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )

        mainHandler.post {
            if (!isVideoFullscreen) {
                return@post
            }

            KinescopePlayerViewChrome.clearControlButtonPressState(inline)

            overlay.isVisible = true
            overlay.bringToFront()
            (overlay.parent as? ViewGroup)?.invalidate()

            KinescopePlayerView.switchTargetView(inline, fullscreen, kinescopePlayer)
            fullscreen.applyTemplateOptions()
            fullscreen.refreshPlayerChrome()
            KinescopePlayerViewChrome.stripControlButtonRipples(fullscreen)
            KinescopePlayerViewChrome.clearControlButtonPressState(fullscreen)
            ViewCompat.requestApplyInsets(overlay)
        }
    }

    private fun exitFullscreen() {
        val activity = activityProvider()
        val inline = inlineView
        val fullscreen = fullscreenView
        val kinescopePlayer = player

        isVideoFullscreen = false
        onFullscreenChanged(false)

        activity?.let {
            it.requestedOrientation = savedOrientation
            it.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        mainHandler.post {
            if (inline != null && fullscreen != null && kinescopePlayer != null) {
                KinescopePlayerViewChrome.clearControlButtonPressState(fullscreen)

                KinescopePlayerView.switchTargetView(fullscreen, inline, kinescopePlayer)
                inline.applyTemplateOptions()
                inline.refreshPlayerChrome()
                KinescopePlayerViewChrome.stripControlButtonRipples(inline)
                KinescopePlayerViewChrome.clearControlButtonPressState(inline)
            }
            overlayContainer?.isVisible = false
        }
    }

    private fun removeOverlay() {
        overlayContainer?.let { container ->
            (container.parent as? ViewGroup)?.removeView(container)
        }
        overlayContainer = null
        fullscreenView = null
    }
}
