package io.kinescope.flutter_kinescope_sdk



import android.content.Context

import android.net.Uri

import android.os.Handler

import android.os.Looper

import androidx.annotation.OptIn

import androidx.media3.common.C

import androidx.media3.common.MediaItem

import androidx.media3.common.PlaybackException

import androidx.media3.common.Player

import androidx.media3.common.util.UnstableApi

import androidx.media3.exoplayer.ExoPlayer

import io.kinescope.sdk.download.DownloadVideoOffline

import io.kinescope.sdk.models.videos.KinescopeVideo

import io.kinescope.sdk.shorts.drm.DrmConfigurator

import io.kinescope.sdk.shorts.drm.DrmContentProtection

import io.kinescope.sdk.shorts.drm.DrmHelper

import io.kinescope.sdk.shorts.models.DrmInfo

import io.kinescope.sdk.shorts.models.VideoData

import io.kinescope.sdk.shorts.models.WidevineInfo

import org.json.JSONObject



@OptIn(UnstableApi::class)

object KinescopeDrmDownloadHelper {

    private val mainHandler = Handler(Looper.getMainLooper())



    fun widevineLicenseUrl(videoId: String, apiKey: String? = null): String {

        val base = "https://license.kinescope.io/v1/vod/$videoId/acquire/widevine"

        val token = KinescopeSdkConfig.resolveApiKey(apiKey) ?: return base

        return "$base?token=$token"

    }



    fun buildMetadata(

        video: KinescopeVideo,

        manifestUri: String,

        licenseUrl: String?,

        contentId: String,

    ): ByteArray {

        val json = JSONObject().apply {

            put("contentId", contentId)

            put("videoId", video.id)

            put("title", video.title)

            put("manifestUri", manifestUri)

            if (!licenseUrl.isNullOrBlank()) {

                put("licenseUrl", licenseUrl)

            }

        }

        return json.toString().toByteArray(Charsets.UTF_8)

    }



    fun parseMetadata(data: ByteArray?): OfflineDownloadMetadata? {

        if (data == null || data.isEmpty()) {

            return null

        }

        return try {

            val json = JSONObject(String(data, Charsets.UTF_8))

            OfflineDownloadMetadata(

                contentId = json.optString("contentId").takeIf { it.isNotEmpty() },

                videoId = json.optString("videoId").takeIf { it.isNotEmpty() },

                title = json.optString("title").takeIf { it.isNotEmpty() },

                manifestUri = json.optString("manifestUri").takeIf { it.isNotEmpty() },

                licenseUrl = json.optString("licenseUrl").takeIf { it.isNotEmpty() },

            )

        } catch (_: Exception) {

            null

        }

    }



    fun startDownloadWithOptionalDrm(

        context: Context,

        video: KinescopeVideo,

        manifestUri: Uri,

        mimeType: String,

        contentId: String,

        apiKey: String? = null,

        onComplete: (Result<Unit>) -> Unit,

    ) {

        val appContext = context.applicationContext

        val manifest = manifestUri.toString()

        val licenseUrl = widevineLicenseUrl(video.id, apiKey)

        val metadata = buildMetadata(video, manifest, licenseUrl, contentId)

        val hlsLink = video.hlsLink ?: manifest



        var finished = false

        var drmProbeStarted = false



        fun finish(result: Result<Unit>) {

            if (finished) {

                return

            }

            finished = true

            mainHandler.post { onComplete(result) }

        }



        fun startClearDownload() {

            try {

                startDownloadRequest(

                    context = appContext,

                    contentId = contentId,

                    manifestUri = manifestUri,

                    mimeType = mimeType,

                    metadata = metadata,

                    keySetId = null,

                )

                finish(Result.success(Unit))

            } catch (error: Exception) {

                finish(Result.failure(error))

            }

        }



        fun startDrmDownload(pssh: ByteArray) {

            val drmConfigurator = DrmConfigurator(appContext)

            val protection = DrmContentProtection(

                schemeUri = C.WIDEVINE_UUID.toString(),

                licenseUrl = licenseUrl,

                schemeUuid = C.WIDEVINE_UUID,

            )

            drmConfigurator.downloadOfflineLicense(

                videoUrl = hlsLink,

                drmContentProtection = protection,

                contentId = contentId,

                psshData = pssh,

            ) { keySetId ->

                if (keySetId == null) {

                    finish(

                        Result.failure(

                            IllegalStateException(

                                "Failed to acquire offline DRM license. Configure Kinescope SDK apiKey.",

                            ),

                        ),

                    )

                    return@downloadOfflineLicense

                }

                try {

                    startDownloadRequest(

                        context = appContext,

                        contentId = contentId,

                        manifestUri = manifestUri,

                        mimeType = mimeType,

                        metadata = metadata,

                        keySetId = keySetId,

                    )

                    finish(Result.success(Unit))

                } catch (error: Exception) {

                    finish(Result.failure(error))

                }

            }

        }



        var tempPlayer: ExoPlayer? = null

        val drmConfigurator = DrmConfigurator(appContext)

        val videoData = VideoData(

            hlsLink = hlsLink,

            drm = DrmInfo(widevine = WidevineInfo(licenseUrl = licenseUrl)),

            title = video.title,

            subtitle = video.subtitle,

            description = video.description,

        )



        val drmHelper = DrmHelper(appContext, drmConfigurator) { _, pssh ->

            drmProbeStarted = true

            tempPlayer?.release()

            tempPlayer = null

            startDrmDownload(pssh)

        }



        drmHelper.setOfflineDownloadPending(videoData)

        tempPlayer = ExoPlayer.Builder(appContext).build().also { player ->

            player.addListener(

                object : Player.Listener {

                    override fun onPlayerError(error: PlaybackException) {

                        if (finished) {

                            return

                        }

                        tempPlayer?.release()

                        tempPlayer = null

                        if (drmProbeStarted) {

                            finish(

                                Result.failure(

                                    error.cause ?: IllegalStateException("DRM license acquisition failed"),

                                ),

                            )

                            return

                        }

                        if (isDrmRelatedError(error)) {

                            finish(

                                Result.failure(

                                    IllegalStateException(

                                        "DRM-protected video requires Kinescope SDK apiKey",

                                        error,

                                    ),

                                ),

                            )

                            return

                        }

                        finish(

                            Result.failure(

                                error.cause ?: IllegalStateException("Failed to inspect stream for DRM"),

                            ),

                        )

                    }

                },

            )



            val mediaItem = MediaItem.Builder()

                .setUri(hlsLink)

                .setDrmUuid(C.WIDEVINE_UUID)

                .setDrmLicenseUri(licenseUrl)

                .setDrmMultiSession(true)

                .build()

            player.setMediaItem(mediaItem)

            drmHelper.attachToPlayer(player)

            player.prepare()

        }



        mainHandler.postDelayed(

            {

                if (finished || drmProbeStarted) {

                    return@postDelayed

                }

                tempPlayer?.release()

                tempPlayer = null

                startClearDownload()

            },

            20_000L,

        )

    }



    private fun isDrmRelatedError(error: PlaybackException): Boolean {

        return when (error.errorCode) {

            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,

            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,

            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,

            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,

            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,

            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,

            -> true

            else -> error.message?.contains("DRM", ignoreCase = true) == true ||

                error.message?.contains("Drm", ignoreCase = true) == true

        }

    }



    private fun startDownloadRequest(

        context: Context,

        contentId: String,

        manifestUri: Uri,

        mimeType: String,

        metadata: ByteArray,

        keySetId: ByteArray?,

    ) {

        val builder = androidx.media3.exoplayer.offline.DownloadRequest.Builder(contentId, manifestUri)

            .setMimeType(mimeType)

            .setData(metadata)



        if (keySetId != null) {

            builder.setKeySetId(keySetId)

        }



        DownloadVideoOffline.startDownload(context, builder.build())

    }

}



data class OfflineDownloadMetadata(

    val contentId: String?,

    val videoId: String?,

    val title: String?,

    val manifestUri: String?,

    val licenseUrl: String?,

)


