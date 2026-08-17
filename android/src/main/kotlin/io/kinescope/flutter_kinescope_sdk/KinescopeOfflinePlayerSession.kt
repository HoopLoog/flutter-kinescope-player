package io.kinescope.flutter_kinescope_sdk

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks every active offline PlatformView so Flutter pop / hide reaches all of them.
 * Hybrid-composition views leave a video frame if torn down only in [PlatformView.dispose].
 */
internal object KinescopeOfflinePlayerSession {
    private val sessions = CopyOnWriteArrayList<Handle>()

    class Handle internal constructor(
        private val onHide: () -> Unit,
        private val onExitFullscreen: () -> Unit,
    ) {
        @Volatile
        var isFullscreen: Boolean = false
            private set

        fun setFullscreen(active: Boolean) {
            isFullscreen = active
        }

        fun hide() {
            onHide()
        }

        fun exitFullscreen() {
            onExitFullscreen()
        }
    }

    fun register(
        onHide: () -> Unit,
        onExitFullscreen: () -> Unit,
    ): Handle {
        val handle = Handle(onHide, onExitFullscreen)
        sessions.add(handle)
        return handle
    }

    fun unregister(handle: Handle) {
        sessions.remove(handle)
    }

    fun isFullscreenActive(): Boolean = sessions.any { it.isFullscreen }

    fun hideView() {
        sessions.toList().forEach { it.hide() }
    }

    fun exitFullscreen() {
        sessions.toList().forEach { it.exitFullscreen() }
    }
}
