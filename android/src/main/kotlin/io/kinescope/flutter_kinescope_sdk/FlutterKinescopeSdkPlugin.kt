package io.kinescope.flutter_kinescope_sdk

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

@OptIn(UnstableApi::class)
class FlutterKinescopeSdkPlugin :
    FlutterPlugin,
    MethodChannel.MethodCallHandler,
    ActivityAware {

    companion object {
        const val METHOD_CHANNEL = "flutter_kinescope_sdk"
        const val PLAYER_EVENT_CHANNEL = "flutter_kinescope_sdk/player_events"
        const val DOWNLOAD_EVENT_CHANNEL = "flutter_kinescope_sdk/download_events"
        const val VIEW_TYPE = "kinescope-player-view"
        const val OFFLINE_VIEW_TYPE = "kinescope-offline-player-view"
    }

    private lateinit var appContext: android.content.Context
    private lateinit var methodChannel: MethodChannel
    private var activity: Activity? = null
    private var activityLifecycle: Lifecycle? = null
    private var playerEventSink: EventChannel.EventSink? = null
    private var downloadEventSink: EventChannel.EventSink? = null
    private lateinit var playerRegistry: KinescopePlayerRegistry
    private lateinit var downloadHandler: KinescopeDownloadHandler
    private var pipLifecycleObserver: DefaultLifecycleObserver? = null
    private var pipHooksInstalled = false

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        appContext = binding.applicationContext
        playerRegistry = KinescopePlayerRegistry { event ->
            playerEventSink?.success(event)
        }
        downloadHandler = KinescopeDownloadHandler({ activity ?: appContext }) { event ->
            downloadEventSink?.success(event)
        }
        methodChannel = MethodChannel(binding.binaryMessenger, METHOD_CHANNEL)
        methodChannel.setMethodCallHandler(this)
        KinescopePipFlutterNotifier.install(methodChannel)
        EventChannel(binding.binaryMessenger, PLAYER_EVENT_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    playerEventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    playerEventSink = null
                }
            },
        )
        EventChannel(binding.binaryMessenger, DOWNLOAD_EVENT_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    downloadEventSink = events
                    downloadHandler.initialize()
                }

                override fun onCancel(arguments: Any?) {
                    downloadEventSink = null
                }
            },
        )

        binding.platformViewRegistry.registerViewFactory(
            VIEW_TYPE,
            KinescopePlayerViewFactory(
                playerRegistry,
                activityProvider = { activity },
                lifecycleProvider = { activityLifecycle },
            ) { isFullscreen ->
                if (isFullscreen) {
                    methodChannel.invokeMethod("onEnterFullscreen", null)
                } else {
                    methodChannel.invokeMethod("onExitFullscreen", null)
                }
            },
        )

        binding.platformViewRegistry.registerViewFactory(
            OFFLINE_VIEW_TYPE,
            KinescopeOfflinePlayerViewFactory(
                activityProvider = { activity },
                lifecycleProvider = { activityLifecycle },
            ) { isFullscreen ->
                if (isFullscreen) {
                    methodChannel.invokeMethod("onEnterFullscreen", null)
                } else {
                    methodChannel.invokeMethod("onExitFullscreen", null)
                }
            },
        )
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "configure" -> {
                    @Suppress("UNCHECKED_CAST")
                    val args = call.arguments as? Map<String, Any?>
                    KinescopeSdkConfig.setApiKey(args?.get("apiKey") as? String)
                    result.success(null)
                }

                "createPlayer" -> {
                    @Suppress("UNCHECKED_CAST")
                    val args = call.arguments as? Map<String, Any?>
                    val playerId = playerRegistry.create(appContext, args)
                    result.success(playerId)
                }

                "disposePlayer" -> {
                    val playerId = playerIdFrom(call.arguments)
                    playerRegistry.dispose(playerId)
                    result.success(null)
                }

                "hidePlayerView" -> {
                    val playerId = playerIdFrom(call.arguments)
                    playerRegistry.hideView(playerId)
                    result.success(null)
                }

                "exitFullscreen" -> {
                    val playerId = playerIdFrom(call.arguments)
                    playerRegistry.exitFullscreen(playerId)
                    result.success(null)
                }

                "loadVideo" -> {
                    val args = call.arguments as Map<*, *>
                    val playerId = (args["playerId"] as Number).toLong()
                    val videoId = args["videoId"] as String
                    playerRegistry.loadVideo(
                        playerId = playerId,
                        videoId = videoId,
                        onSuccess = { result.success(null) },
                        onFailed = { error ->
                            result.error(
                                "LOAD_FAILED",
                                error?.message ?: "Failed to load video",
                                null,
                            )
                        },
                    )
                }

                "play" -> {
                    playerRegistry.get(playerIdFrom(call.arguments)).play()
                    result.success(null)
                }

                "pause" -> {
                    playerRegistry.get(playerIdFrom(call.arguments)).pause()
                    result.success(null)
                }

                "stop" -> {
                    playerRegistry.get(playerIdFrom(call.arguments)).stop()
                    result.success(null)
                }

                "seekTo" -> {
                    val args = call.arguments as Map<*, *>
                    val playerId = (args["playerId"] as Number).toLong()
                    val positionMs = (args["positionMs"] as Number).toLong()
                    playerRegistry.get(playerId).exoPlayer?.seekTo(positionMs)
                    result.success(null)
                }

                "getCurrentTime" -> {
                    val positionMs =
                        playerRegistry.get(playerIdFrom(call.arguments)).exoPlayer?.currentPosition
                            ?: 0L
                    result.success(positionMs / 1000.0)
                }

                "getDuration" -> {
                    val durationMs =
                        playerRegistry.get(playerIdFrom(call.arguments)).exoPlayer?.duration ?: 0L
                    result.success(if (durationMs > 0) durationMs / 1000.0 else 0.0)
                }

                "setVolume" -> {
                    val args = call.arguments as Map<*, *>
                    val playerId = (args["playerId"] as Number).toLong()
                    val volume = (args["volume"] as Number).toFloat()
                    playerRegistry.get(playerId).exoPlayer?.volume = volume.coerceIn(0f, 1f)
                    result.success(null)
                }

                "mute" -> {
                    val player = playerRegistry.get(playerIdFrom(call.arguments))
                    player.kinescopePlayerOptions.muted = true
                    player.applyPlaybackOptions()
                    result.success(null)
                }

                "unmute" -> {
                    val player = playerRegistry.get(playerIdFrom(call.arguments))
                    player.kinescopePlayerOptions.muted = false
                    player.applyPlaybackOptions()
                    result.success(null)
                }

                "initializeDownloads" -> {
                    downloadHandler.initialize()
                    result.success(null)
                }

                "ensureDownloadPermissions" -> {
                    val currentActivity = activity
                    if (currentActivity == null) {
                        result.success(false)
                    } else {
                        DownloadPermissionHelper.requestPermissions(currentActivity)
                        result.success(DownloadPermissionHelper.hasPermissions(currentActivity))
                    }
                }

                "startDownload" -> {
                    @Suppress("UNCHECKED_CAST")
                    downloadHandler.startDownload(call.arguments as Map<String, Any?>)
                    result.success(null)
                }

                "downloadVideo" -> {
                    val args = call.arguments as Map<*, *>
                    val videoId = args["videoId"] as String
                    val contentId = args["contentId"] as? String
                    val apiKey = KinescopeSdkConfig.resolveApiKey(args["apiKey"] as? String)
                    downloadHandler.downloadVideo(videoId, contentId, apiKey) { downloadResult ->
                        downloadResult
                            .onSuccess { result.success(it) }
                            .onFailure {
                                result.error(
                                    "DOWNLOAD_FAILED",
                                    it.message,
                                    null,
                                )
                            }
                    }
                }

                "removeDownload" -> {
                    downloadHandler.removeDownload(call.arguments as String)
                    result.success(null)
                }

                "getCompletedDownloads" -> {
                    result.success(downloadHandler.getCompletedDownloads())
                }

                "getAllDownloads" -> {
                    result.success(downloadHandler.getAllDownloads())
                }

                "getVideoCatalog" -> {
                    KinescopeVideoCatalogHandler.getVideos { catalogResult ->
                        catalogResult
                            .onSuccess { result.success(it) }
                            .onFailure {
                                result.error(
                                    "CATALOG_FAILED",
                                    it.message,
                                    null,
                                )
                            }
                    }
                }

                "getDownload" -> {
                    result.success(downloadHandler.getDownload(call.arguments as String))
                }

                else -> result.notImplemented()
            }
        } catch (error: Exception) {
            result.error("PLUGIN_ERROR", error.message, null)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        playerRegistry.disposeAll()
        downloadHandler.dispose()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        activityLifecycle = resolveLifecycle(binding)
        KinescopePlatformViewFocus.install(binding.activity)
        installPipActivityHooks(binding.activity)
        KinescopePipAttachHelper.retryPending()
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
        activityLifecycle = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        activityLifecycle = resolveLifecycle(binding)
        KinescopePipAttachHelper.retryPending()
    }

    override fun onDetachedFromActivity() {
        KinescopePlatformViewFocus.uninstall()
        activity = null
        activityLifecycle = null
    }

    private fun installPipActivityHooks(hostActivity: Activity) {
        if (pipHooksInstalled) {
            return
        }
        val componentActivity = hostActivity as? ComponentActivity ?: return
        pipHooksInstalled = true

        componentActivity.addOnPictureInPictureModeChangedListener { info ->
            KinescopePipRegistry.dispatchModeChanged(
                info.isInPictureInPictureMode,
                hostActivity.resources.configuration,
            )
        }

        pipLifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                KinescopePipRegistry.dispatchOnStop()
            }
        }.also { componentActivity.lifecycle.addObserver(it) }
    }

    private fun resolveLifecycle(binding: ActivityPluginBinding): Lifecycle? {
        return KinescopePipWiring.resolveLifecycle(binding.activity) {
            when (val lifecycle = binding.lifecycle) {
                is Lifecycle -> lifecycle
                is LifecycleOwner -> lifecycle.lifecycle
                else -> null
            }
        }
    }

    private fun playerIdFrom(arguments: Any?): Long =
        (arguments as Number).toLong()
}
