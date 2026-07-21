package io.kinescope.flutter_kinescope_sdk

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * Flutter equivalent of native demo `applyPictureInPictureLayout()`:
 * moves playback to an Activity-level overlay (SurfaceView) before PiP.
 */
@OptIn(UnstableApi::class)
class KinescopePipHostController(
    private val activityProvider: () -> Activity?,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var inlineView: KinescopePlayerView? = null
    private var inlineContainer: View? = null
    private var player: KinescopeVideoPlayer? = null
    private var overlayContainer: FrameLayout? = null
    private var hostView: KinescopePlayerView? = null
    private var isHostedForPip = false
    private var pipReturnView: KinescopePlayerView? = null

    var onRestoreFullscreenAfterPip: (() -> Unit)? = null
    var onReturnedToInlineAfterPip: (() -> Unit)? = null

    fun attach(inlineView: KinescopePlayerView, player: KinescopeVideoPlayer, container: View? = null) {
        this.inlineView = inlineView
        this.inlineContainer = container
        this.player = player
    }

    fun detach() {
        val inPip = isActivityInPictureInPictureMode()
        if (isHostedForPip && inPip) {
            // Keep the PiP SurfaceView host alive; tearing it down mid-PiP yields
            // black frame + audio. Inline PlatformView may already be disposing.
            releaseInlineOnly()
            return
        }
        if (isHostedForPip) {
            moveBackToInline()
        }
        removeOverlay()
        inlineView = null
        inlineContainer = null
        player = null
    }

    /** Drops the inline PlatformView reference while PiP overlay keeps playback. */
    fun releaseInlineOnly() {
        inlineView = null
        inlineContainer = null
        if (pipReturnView !== hostView) {
            pipReturnView = null
        }
    }

    private fun isActivityInPictureInPictureMode(): Boolean {
        val activity = activityProvider() ?: return false
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
            activity.isInPictureInPictureMode
    }

    fun hideImmediately() {
        overlayContainer?.isVisible = false
        hostView?.visibility = View.GONE
        inlineView?.visibility = View.GONE
        inlineContainer?.visibility = View.GONE
    }

    fun inlinePlayerView(): KinescopePlayerView =
        inlineView ?: hostView ?: error("PiP host has no player view")

    fun activeView(): KinescopePlayerView {
        return if (isHostedForPip) {
            hostView ?: inlineView ?: error("PiP host has no player view")
        } else {
            inlineView ?: hostView ?: error("PiP host has no player view")
        }
    }

    fun additionalViews(): List<KinescopePlayerView> {
        val inline = inlineView ?: return emptyList()
        val host = hostView
        return if (host != null && host !== inline) {
            listOf(inline)
        } else {
            emptyList()
        }
    }

    fun prepareForEnter(playbackSource: KinescopePlayerView? = null) {
        val activity = activityProvider() ?: return
        KinescopeSoftKeyboard.dismiss(activity)

        val inline = inlineView ?: return
        val videoPlayer = player ?: return
        val source = playbackSource ?: inline
        pipReturnView = source
        if (isHostedForPip) {
            hostView?.visibility = View.VISIBLE
            bringOverlayToFront()
            KinescopeVideoSurfaceHelper.rebind(hostView ?: source, videoPlayer)
            return
        }

        ensureOverlay(activity)
        val overlay = overlayContainer ?: return
        val host = hostView ?: return

        host.visibility = View.VISIBLE
        overlay.isVisible = true
        bringOverlayToFront()
        // FlutterView must be hidden: SurfaceView is composited under it, so a visible
        // FlutterView shows as a black PiP window while audio keeps playing.
        hideFlutterContentForPip(activity)

        KinescopeVideoSurfaceHelper.restoreVideoSurface(host)
        KinescopePlayerView.switchTargetView(source, host, videoPlayer)
        hideInlineForPip()
        host.applyTemplateOptions()
        host.refreshPlayerChrome()
        KinescopePlayerViewChrome.scheduleStrip(host)
        isHostedForPip = true
        bringOverlayToFront()
        KinescopeVideoSurfaceHelper.rebindAfterLayout(host, videoPlayer)
        host.post {
            bringOverlayToFront()
        }
    }

    private fun hideInlineForPip() {
        inlineView?.visibility = View.INVISIBLE
        inlineContainer?.visibility = View.INVISIBLE
    }

    private fun showInlineAfterPip() {
        inlineView?.let { KinescopeVideoSurfaceHelper.restoreVideoSurface(it) }
        inlineView?.visibility = View.VISIBLE
        inlineContainer?.visibility = View.VISIBLE
    }

    private fun hidePipHost() {
        hostView?.visibility = View.GONE
        overlayContainer?.isVisible = false
    }

    fun bringOverlayToFront() {
        val overlay = overlayContainer ?: return
        overlay.isVisible = true
        overlay.bringToFront()
        (overlay.parent as? ViewGroup)?.bringChildToFront(overlay)
        overlay.translationZ = 10001f
        overlay.elevation = 10001f
    }

    fun prepareForExit() {
        moveBackToInline()
    }

    fun moveBackToInline() {
        val host = hostView ?: return
        val videoPlayer = player ?: return
        if (!isHostedForPip) {
            return
        }
        val inline = inlineView

        val restore = Runnable {
            activityProvider()?.let(KinescopeSoftKeyboard::dismiss)

            // Inline PlatformView may have been disposed while FlutterView was hidden.
            val target = when {
                pipReturnView != null && pipReturnView !== host -> pipReturnView
                inline != null -> inline
                else -> null
            }
            if (target == null) {
                hidePipHost()
                showFlutterContentAfterPip()
                isHostedForPip = false
                pipReturnView = null
                onReturnedToInlineAfterPip?.invoke()
                return@Runnable
            }

            KinescopePlayerView.switchTargetView(host, target, videoPlayer)
            host.prepareForPictureInPicture(false)
            hidePipHost()
            showInlineAfterPip()
            showFlutterContentAfterPip()
            if (target === inline) {
                val container = inlineContainer as? ViewGroup
                if (container != null) {
                    KinescopeInlineInteractivity.restoreAfterPip(container, inline, videoPlayer)
                } else {
                    KinescopeVideoSurfaceHelper.restoreVideoSurface(inline)
                    KinescopeVideoSurfaceHelper.rebindAfterLayout(inline, videoPlayer)
                }
                onReturnedToInlineAfterPip?.invoke()
            } else {
                target.prepareForPictureInPicture(false)
                target.refreshPlayerChromeAfterPictureInPictureExit()
                target.applyTemplateOptions()
                KinescopeVideoSurfaceHelper.rebindAfterLayout(target, videoPlayer)
                KinescopePlayerViewChrome.scheduleStrip(target)
                onRestoreFullscreenAfterPip?.invoke()
            }
            isHostedForPip = false
            pipReturnView = null
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            restore.run()
        } else {
            mainHandler.post(restore)
        }
    }

    private fun ensureOverlay(activity: Activity) {
        if (hostView != null) {
            return
        }

        val pipHostView = KinescopeFlutterPlayerViewFactory.createOverlay(activity).apply {
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
            elevation = 10001f
            translationZ = 10001f
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(
                pipHostView,
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
        hostView = pipHostView
        KinescopePlayerViewChrome.prepare(pipHostView)
    }

    private fun hideFlutterContentForPip(activity: Activity) {
        val content = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val overlay = overlayContainer ?: return
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child !== overlay) {
                child.visibility = View.INVISIBLE
            }
        }
    }

    private fun showFlutterContentAfterPip() {
        val activity = activityProvider() ?: return
        val content = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val overlay = overlayContainer
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child !== overlay) {
                child.visibility = View.VISIBLE
            }
        }
    }

    private fun removeOverlay() {
        overlayContainer?.let { container ->
            (container.parent as? ViewGroup)?.removeView(container)
        }
        overlayContainer = null
        hostView = null
    }
}
