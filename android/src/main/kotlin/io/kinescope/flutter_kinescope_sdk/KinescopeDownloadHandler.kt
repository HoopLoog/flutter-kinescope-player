package io.kinescope.flutter_kinescope_sdk

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import io.kinescope.sdk.download.DownloadVideoOffline
import io.kinescope.sdk.models.videos.KinescopeVideo
import io.kinescope.sdk.player.KinescopeVideoPlayer
import org.json.JSONObject

@OptIn(UnstableApi::class)
class KinescopeDownloadHandler(
    private val contextProvider: () -> Context,
    private val eventSink: (Map<String, Any?>) -> Unit,
) {
    private var downloadListener: DownloadManager.Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val context: Context
        get() = contextProvider()

    fun initialize() {
        DownloadVideoOffline.initialize(context)
        DownloadVideoOffline.getDownloadManager(context).resumeDownloads()
        ensureDownloadListener()
    }

    fun startDownload(args: Map<*, *>) {
        initialize()
        val contentId = args["contentId"] as String
        val manifestUri = Uri.parse(args["manifestUri"] as String)
        val mimeType = args["mimeType"] as? String ?: MimeTypes.APPLICATION_M3U8
        val metadata = (args["metadata"] as? String)?.toByteArray(Charsets.UTF_8)

        val builder = DownloadRequest.Builder(contentId, manifestUri)
            .setMimeType(mimeType)

        metadata?.let { builder.setData(it) }

        val keySetIdBase64 = args["keySetId"] as? String
        if (!keySetIdBase64.isNullOrEmpty()) {
            builder.setKeySetId(Base64.decode(keySetIdBase64, Base64.DEFAULT))
        }

        DownloadVideoOffline.startDownload(context, builder.build())
        emitDownloadsChanged()
    }

    fun downloadVideo(videoId: String, contentId: String?, apiKey: String?, callback: (Result<Map<String, Any?>>) -> Unit) {
        initialize()
        val tempPlayer = KinescopeVideoPlayer(context.applicationContext)
        tempPlayer.loadVideo(
            videoId,
            onSuccess = { video ->
                try {
                    val result = startDownloadForVideo(video, contentId, apiKey) { downloadResult ->
                        tempPlayer.release()
                        callback(downloadResult)
                    }
                    if (result != null) {
                        tempPlayer.release()
                        callback(Result.success(result))
                    }
                } catch (error: Exception) {
                    tempPlayer.release()
                    callback(Result.failure(error))
                }
            },
            onFailed = { error ->
                tempPlayer.release()
                callback(
                    Result.failure(
                        error ?: IllegalStateException("Failed to load video metadata"),
                    ),
                )
            },
        )
    }

    private fun startDownloadForVideo(
        video: KinescopeVideo?,
        contentId: String?,
        apiKey: String?,
        onAsyncStarted: ((Result<Map<String, Any?>>) -> Unit)? = null,
    ): Map<String, Any?>? {
        if (video == null) {
            throw IllegalStateException("Failed to load video metadata")
        }

        val manifestUri: Uri
        val mimeType: String

        when {
            !video.hlsLink.isNullOrEmpty() -> {
                manifestUri = Uri.parse(video.hlsLink)
                mimeType = MimeTypes.APPLICATION_M3U8
            }
            !video.dashLink.isNullOrEmpty() -> {
                manifestUri = Uri.parse(video.dashLink)
                mimeType = MimeTypes.APPLICATION_MPD
            }
            else -> throw IllegalStateException("Video has no downloadable stream")
        }

        val resolvedContentId = contentId
            ?: KinescopeOfflineIds.stableContentId(manifestUri.toString())

        val existing = DownloadVideoOffline.getDownloadById(context, resolvedContentId)
        if (existing?.state == Download.STATE_COMPLETED) {
            return existing.toMap()
        }

        if (onAsyncStarted != null) {
            KinescopeDrmDownloadHelper.startDownloadWithOptionalDrm(
                context = context,
                video = video,
                manifestUri = manifestUri,
                mimeType = mimeType,
                contentId = resolvedContentId,
                apiKey = KinescopeSdkConfig.resolveApiKey(apiKey),
            ) { result ->
                result
                    .onSuccess {
                        emitDownloadsChanged()
                        val download = DownloadVideoOffline.getDownloadById(context, resolvedContentId)
                        onAsyncStarted(
                            Result.success(
                                download?.toMap() ?: mapOf(
                                    "contentId" to resolvedContentId,
                                    "title" to video.title,
                                    "uri" to manifestUri.toString(),
                                    "mimeType" to mimeType,
                                    "state" to "queued",
                                    "percent" to 0,
                                    "bytesDownloaded" to 0L,
                                ),
                            ),
                        )
                    }
                    .onFailure { error ->
                        onAsyncStarted(Result.failure(error))
                    }
            }
            return null
        }

        val metadata = KinescopeDrmDownloadHelper.buildMetadata(
            video = video,
            manifestUri = manifestUri.toString(),
            licenseUrl = KinescopeDrmDownloadHelper.widevineLicenseUrl(
                video.id,
                KinescopeSdkConfig.resolveApiKey(apiKey),
            ),
            contentId = resolvedContentId,
        )
        val request = DownloadRequest.Builder(resolvedContentId, manifestUri)
            .setMimeType(mimeType)
            .setData(metadata)
            .build()

        DownloadVideoOffline.startDownload(context, request)
        emitDownloadsChanged()

        val download = DownloadVideoOffline.getDownloadById(context, resolvedContentId)
        return download?.toMap() ?: mapOf(
            "contentId" to resolvedContentId,
            "title" to video.title,
            "uri" to manifestUri.toString(),
            "mimeType" to mimeType,
            "state" to "queued",
            "percent" to 0,
            "bytesDownloaded" to 0L,
        )
    }

    fun removeDownload(downloadId: String) {
        initialize()
        DownloadVideoOffline.removeDownload(context, downloadId)
        emitDownloadsChanged()
    }

    fun getAllDownloads(): List<Map<String, Any?>> {
        initialize()
        val downloadManager = DownloadVideoOffline.getDownloadManager(context)
        val cursor = downloadManager.downloadIndex.getDownloads()
        val downloads = mutableListOf<Map<String, Any?>>()
        while (cursor.moveToNext()) {
            downloads.add(cursor.download.toMap())
        }
        cursor.close()
        return downloads
    }

    fun getCompletedDownloads(): List<Map<String, Any?>> {
        initialize()
        return DownloadVideoOffline.getAllCompletedDownloads(context).map { it.toMap() }
    }

    fun getDownload(downloadId: String): Map<String, Any?>? {
        initialize()
        return DownloadVideoOffline.getDownloadById(context, downloadId)?.toMap()
    }

    fun dispose() {
        downloadListener?.let { DownloadVideoOffline.removeDownloadListener(it) }
        downloadListener = null
    }

    private fun ensureDownloadListener() {
        if (downloadListener != null) {
            return
        }

        downloadListener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                emitDownloadUpdate(download, finalException)
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                emitDownloadsChanged()
            }
        }
        DownloadVideoOffline.addDownloadListener(context, downloadListener!!)
    }

    private fun emitDownloadUpdate(download: Download, error: Exception?) {
        val (percent, bytes) = DownloadVideoOffline.getDownloadProgress(download)
        postEvent(
            mapOf(
                "type" to "downloadChanged",
                "download" to download.toMap(percent, bytes),
                "error" to error?.message,
            ),
        )
    }

    private fun emitDownloadsChanged() {
        postEvent(
            mapOf(
                "type" to "downloadsChanged",
                "downloads" to getAllDownloads(),
            ),
        )
    }

    private fun postEvent(event: Map<String, Any?>) {
        mainHandler.post { eventSink(event) }
    }

    private fun parseTitle(download: Download): String? {
        KinescopeDrmDownloadHelper.parseMetadata(download.request.data)?.title?.let { return it }
        val data = download.request.data ?: return null
        return try {
            val title = JSONObject(String(data, Charsets.UTF_8)).optString("title")
            title.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVideoId(download: Download): String? {
        return KinescopeDrmDownloadHelper.parseMetadata(download.request.data)?.videoId
    }

    private fun Download.toMap(
        percent: Int = DownloadVideoOffline.getDownloadProgress(this).first,
        bytesDownloaded: Long = DownloadVideoOffline.getDownloadProgress(this).second,
    ): Map<String, Any?> {
        return mapOf(
            "contentId" to request.id,
            "videoId" to parseVideoId(this),
            "title" to parseTitle(this),
            "state" to stateToString(state),
            "percent" to percent,
            "bytesDownloaded" to bytesDownloaded,
            "contentLength" to contentLength,
            "mimeType" to request.mimeType,
            "uri" to request.uri.toString(),
        )
    }

    private fun stateToString(state: Int): String = when (state) {
        Download.STATE_QUEUED -> "queued"
        Download.STATE_STOPPED -> "stopped"
        Download.STATE_DOWNLOADING -> "downloading"
        Download.STATE_COMPLETED -> "completed"
        Download.STATE_FAILED -> "failed"
        Download.STATE_REMOVING -> "removing"
        Download.STATE_RESTARTING -> "restarting"
        else -> "unknown"
    }
}
