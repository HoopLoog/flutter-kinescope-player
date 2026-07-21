package io.kinescope.flutter_kinescope_sdk

import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * - Inline [PlatformView]: TextureView (required for hybrid composition).
 * - PiP / fullscreen Activity overlay: SurfaceView (same as kotlin-kinescope-player stand APK).
 */
@OptIn(UnstableApi::class)
internal object KinescopeFlutterPlayerViewFactory {
    fun createInline(context: android.content.Context): KinescopePlayerView {
        return KinescopePlayerView(
            context,
            null,
            useTextureSurface = true,
        )
    }

    fun createOverlay(context: android.content.Context): KinescopePlayerView {
        return KinescopePlayerView(
            context,
            null,
            useTextureSurface = false,
        )
    }

    @Deprecated("Use createInline or createOverlay", ReplaceWith("createInline(context)"))
    fun create(context: android.content.Context): KinescopePlayerView = createInline(context)
}
