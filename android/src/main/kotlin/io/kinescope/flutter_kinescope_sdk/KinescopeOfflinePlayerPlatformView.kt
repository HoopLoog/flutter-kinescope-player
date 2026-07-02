package io.kinescope.flutter_kinescope_sdk



import android.app.Activity

import android.content.Context

import android.view.View

import android.widget.FrameLayout

import androidx.annotation.OptIn

import androidx.media3.common.util.UnstableApi

import io.flutter.plugin.common.StandardMessageCodec

import io.flutter.plugin.platform.PlatformView

import io.flutter.plugin.platform.PlatformViewFactory

import io.kinescope.sdk.player.KinescopeVideoPlayer

import io.kinescope.sdk.view.KinescopePlayerView



@OptIn(UnstableApi::class)

class KinescopeOfflinePlayerViewFactory(

    private val activityProvider: () -> Activity?,

    private val onFullscreenChanged: (Boolean) -> Unit,

) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {



    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {

        @Suppress("UNCHECKED_CAST")

        val params = args as Map<String, Any?>

        val contentId = params["contentId"] as String

        @Suppress("UNCHECKED_CAST")

        val options = params["options"] as? Map<String, Any?>

        return KinescopeOfflinePlayerPlatformView(

            context = context,

            contentId = contentId,

            options = options,

            activityProvider = activityProvider,

            onFullscreenChanged = onFullscreenChanged,

        )

    }

}



@OptIn(UnstableApi::class)

class KinescopeOfflinePlayerPlatformView(

    private val context: Context,

    private val contentId: String,

    options: Map<String, Any?>?,

    activityProvider: () -> Activity?,

    onFullscreenChanged: (Boolean) -> Unit,

) : PlatformView {



    private val kinescopePlayer = KinescopeVideoPlayer(

        context.applicationContext,

        KinescopePlayerOptionsFactory.fromMap(options),

    ).also { player ->

        if (player.kinescopePlayerOptions.showSubtitlesButton) {

            player.setShowSubtitles(true)

        }

    }



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

        playerView.setPlayer(kinescopePlayer)

        playerView.applyTemplateOptions()

        fullscreenController.attach(playerView, kinescopePlayer)

        prepareOfflinePlayback()
    }

    private fun prepareOfflinePlayback(attempt: Int = 0) {
        playerView.post {
            if (kinescopePlayer.exoPlayer == null) {
                if (attempt < 10) {
                    playerView.postDelayed({ prepareOfflinePlayback(attempt + 1) }, 50)
                } else {
                    android.util.Log.e(
                        "KinescopeOfflinePlayer",
                        "ExoPlayer is not available for contentId=$contentId",
                    )
                }
                return@post
            }

            try {
                KinescopeOfflinePlayback.prepare(
                    context.applicationContext,
                    contentId,
                    kinescopePlayer,
                )
            } catch (error: Exception) {
                android.util.Log.e(
                    "KinescopeOfflinePlayer",
                    "Failed to prepare offline playback for contentId=$contentId",
                    error,
                )
            }
        }
    }

    override fun getView(): View = container



    override fun dispose() {

        fullscreenController.detach()

        playerView.setPlayer(null)

        kinescopePlayer.release()

    }

}


