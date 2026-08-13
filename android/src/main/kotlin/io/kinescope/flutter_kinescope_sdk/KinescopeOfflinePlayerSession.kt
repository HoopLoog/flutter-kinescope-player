package io.kinescope.flutter_kinescope_sdk

/**
 * Active offline PlatformView actions for Flutter pop / hide before route transition.
 * Hybrid-composition views leave a video frame if torn down only in [PlatformView.dispose].
 */
internal object KinescopeOfflinePlayerSession {
    @Volatile
    private var hideHandler: (() -> Unit)? = null

    @Volatile
    private var exitFullscreenHandler: (() -> Unit)? = null

    @Volatile
    private var isFullscreen: Boolean = false

    fun register(
        onHide: () -> Unit,
        onExitFullscreen: () -> Unit,
    ) {
        hideHandler = onHide
        exitFullscreenHandler = onExitFullscreen
    }

    fun unregister(
        onHide: () -> Unit,
        onExitFullscreen: () -> Unit,
    ) {
        if (hideHandler === onHide) {
            hideHandler = null
        }
        if (exitFullscreenHandler === onExitFullscreen) {
            exitFullscreenHandler = null
        }
        isFullscreen = false
    }

    fun setFullscreen(active: Boolean) {
        isFullscreen = active
    }

    fun isFullscreenActive(): Boolean = isFullscreen

    fun hideView() {
        hideHandler?.invoke()
    }

    fun exitFullscreen() {
        exitFullscreenHandler?.invoke()
    }
}
