package io.kinescope.flutter_kinescope_sdk

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashChunkSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleExtractor
import io.kinescope.sdk.models.videos.KinescopeVideo
import io.kinescope.sdk.models.videos.KinescopeVideoSubtitle
import io.kinescope.sdk.player.KinescopeDashManifestParser
import io.kinescope.sdk.player.KinescopeErrorHandlingPolicy
import io.kinescope.sdk.player.KinescopeVideoPlayer

@OptIn(UnstableApi::class)
object KinescopeSubtitlePlayback {
    private const val USER_AGENT = "KinescopeAndroidVideoKotlin"

    fun shouldEnableForVideo(texttrackPreference: Boolean?, video: KinescopeVideo): Boolean {
        if (video.subtitles.isEmpty()) {
            return false
        }
        return when (texttrackPreference) {
            false -> false
            true -> true
            else -> true
        }
    }

    fun apply(player: KinescopeVideoPlayer, video: KinescopeVideo) {
        if (video.subtitles.isEmpty() || !player.getShowSubtitles()) {
            return
        }

        val referer = player.kinescopePlayerOptions.referer
        val mediaSource = createMediaSourceWithSideloadedSubtitles(video, referer) ?: return
        val exoPlayer = player.exoPlayer ?: return

        val positionMs = exoPlayer.currentPosition
        val playWhenReady = exoPlayer.playWhenReady

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        if (positionMs > 0L) {
            exoPlayer.seekTo(positionMs)
        }
        exoPlayer.playWhenReady = playWhenReady
    }

    private fun createMediaSourceWithSideloadedSubtitles(
        video: KinescopeVideo,
        referer: String,
    ): MediaSource? {
        return when {
            !video.dashLink.isNullOrEmpty() ->
                createDashWithSideloadedSubtitles(video, referer)

            !video.hlsLink.isNullOrEmpty() ->
                createHlsWithSideloadedSubtitles(video, referer)

            else -> null
        }
    }

    private fun createDashWithSideloadedSubtitles(
        video: KinescopeVideo,
        referer: String,
    ): MediaSource {
        val dashLink = video.dashLink.orEmpty()
        val httpFactory = createHttpDataSourceFactory(referer)
        val dashChunkSourceFactory: DashChunkSource.Factory =
            DefaultDashChunkSource.Factory(httpFactory)
        val dashSource = DashMediaSource.Factory(dashChunkSourceFactory, httpFactory)
            .setManifestParser(KinescopeDashManifestParser())
            .setLoadErrorHandlingPolicy(KinescopeErrorHandlingPolicy())
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(Uri.parse(dashLink))
                    .setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).build(),
                    )
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build(),
            )

        if (video.subtitles.isEmpty()) {
            return dashSource
        }

        val parserFactory = DefaultSubtitleParserFactory()
        val subtitleSources = video.subtitles.map { subtitle ->
            createSubtitleMediaSource(subtitle, httpFactory, parserFactory)
        }

        return MergingMediaSource(false, false, dashSource, *subtitleSources.toTypedArray())
    }

    private fun createHlsWithSideloadedSubtitles(
        video: KinescopeVideo,
        referer: String,
    ): MediaSource {
        val hlsLink = video.hlsLink.orEmpty()
        val httpFactory = createHttpDataSourceFactory(referer)
        val hlsSource = HlsMediaSource.Factory(httpFactory)
            .setLoadErrorHandlingPolicy(KinescopeErrorHandlingPolicy())
            .createMediaSource(MediaItem.fromUri(Uri.parse(hlsLink)))

        if (video.subtitles.isEmpty()) {
            return hlsSource
        }

        val parserFactory = DefaultSubtitleParserFactory()
        val subtitleSources = video.subtitles.map { subtitle ->
            createSubtitleMediaSource(subtitle, httpFactory, parserFactory)
        }

        return MergingMediaSource(false, false, hlsSource, *subtitleSources.toTypedArray())
    }

    private fun createSubtitleMediaSource(
        subtitle: KinescopeVideoSubtitle,
        httpFactory: DefaultHttpDataSource.Factory,
        parserFactory: DefaultSubtitleParserFactory,
    ): ProgressiveMediaSource {
        val format = Format.Builder()
            .setSampleMimeType(resolveSubtitleMimeType(subtitle.url))
            .setLanguage(subtitle.language.takeIf { it.isNotBlank() })
            .setLabel(subtitle.description.takeIf { it.isNotBlank() } ?: subtitle.language)
            .build()
        val extractorsFactory: ExtractorsFactory = ExtractorsFactory {
            arrayOf(SubtitleExtractor(parserFactory.create(format), format))
        }
        return ProgressiveMediaSource.Factory(httpFactory, extractorsFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(subtitle.url)))
    }

    private fun resolveSubtitleMimeType(url: String): String =
        when {
            url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
            url.contains(".ttml", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
            url.contains(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
            else -> MimeTypes.TEXT_VTT
        }

    private fun createHttpDataSourceFactory(referer: String): DefaultHttpDataSource.Factory {
        val headers = mapOf(
            "Origin" to "*/*",
            "x-drm-type" to "widevine",
            "Referer" to referer,
        )
        return DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(headers)
    }
}
