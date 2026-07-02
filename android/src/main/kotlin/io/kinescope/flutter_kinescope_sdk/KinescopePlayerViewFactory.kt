package io.kinescope.flutter_kinescope_sdk

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import io.kinescope.sdk.view.KinescopePlayerView

@OptIn(UnstableApi::class)
class KinescopePlayerViewFactory(
    private val registry: KinescopePlayerRegistry,
    private val activityProvider: () -> android.app.Activity?,
    private val onFullscreenChanged: (Boolean) -> Unit,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        @Suppress("UNCHECKED_CAST")
        val params = args as Map<String, Any?>
        val playerId = (params["playerId"] as Number).toLong()
        return KinescopePlayerPlatformView(
            context = context,
            playerId = playerId,
            registry = registry,
            activityProvider = activityProvider,
            onFullscreenChanged = onFullscreenChanged,
        )
    }
}

@OptIn(UnstableApi::class)
class KinescopePlayerPlatformView(
    context: Context,
    private val playerId: Long,
    private val registry: KinescopePlayerRegistry,
    activityProvider: () -> android.app.Activity?,
    onFullscreenChanged: (Boolean) -> Unit,
) : PlatformView {

    private val container = FrameLayout(context)
    private val playerView = KinescopePlayerView(context, null)
    private val fullscreenController = KinescopeFullscreenController(
        activityProvider = activityProvider,
        onFullscreenChanged = onFullscreenChanged,
    )

    init {
        container.addView(
            playerView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        registry.attachView(playerId, playerView)
        fullscreenController.attach(playerView, registry.get(playerId))
    }

    override fun getView(): View = container

    override fun dispose() {
        fullscreenController.detach()
        registry.detachView(playerId)
    }
}
