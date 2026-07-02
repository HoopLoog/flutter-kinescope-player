# Flutter Kinescope SDK

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://kinescope.io/)

Flutter plugin for the [Kinescope](https://kinescope.io/) video player.

The package supports **Android**, **iOS**, and **Web**. The public Dart API is shared across platforms, but the player implementation differs by platform.

## Platform support

| Platform | Online player | Implementation |
| -------- | ------------- | -------------- |
| **Android** | `KinescopePlayer` | Native [`kotlin-kinescope-player`](https://github.com/kinescope/kotlin-kinescope-player) via `PlatformView` (ExoPlayer, Kinescope UI) |
| **iOS** | `KinescopePlayer` | Embedded player in **WebView** ([`webview_flutter`](https://pub.dev/packages/webview_flutter) / WKWebView) |
| **Web** | `KinescopePlayer` | iframe / JS player API |

| Feature | Android | iOS | Web |
| ------- | ------- | --- | --- |
| Online playback | Yes | Yes | Yes |
| Native player UI & fullscreen | Yes | No (WebView) | No |
| `setVolume()` | Yes | No | No |
| Subtitles (`texttrack`) | Yes (native) | Yes (embed) | Yes (embed) |
| Offline download & playback | Yes | No | No |
| DRM offline | Yes | No | No |
| Video catalog API | Yes | No | No |

On **Android**, call `KinescopeOfflineDownload.instance.initialize(apiKey: ...)` once at app startup if you use offline downloads or DRM (same API key as in the [native demo](https://github.com/kinescope/kotlin-kinescope-player)).

## Requirements

- **Flutter:** `>=3.35.0`
- **Dart:** `>=3.9.0 <4.0.0`
- **Android:** `minSdkVersion 24`, AndroidX
- **iOS:** Swift, Xcode 11+

## Installation

Add `flutter_kinescope_sdk` to your `pubspec.yaml`:

```yaml
dependencies:
  flutter_kinescope_sdk: ^0.2.4
```

### Android setup (offline / DRM)

1. Initialize the SDK with your Kinescope project API key:

```dart
import 'package:flutter_kinescope_sdk/flutter_kinescope_sdk.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await KinescopeOfflineDownload.instance.initialize(
    apiKey: 'your-kinescope-api-key',
  );
  runApp(const MyApp());
}
```

2. Ensure your app manifest includes permissions used by the plugin (downloads, notifications). See [example/android/app/src/main/AndroidManifest.xml](./example/android/app/src/main/AndroidManifest.xml).

## Usage

### Online player

```dart
KinescopePlayer(
  controller: KinescopePlayerController(
    'yourVideoId',
    parameters: const PlayerParameters(
      autoplay: true,
      muted: true,
      loop: true,
      texttrack: true, // enable subtitles when available (Android native)
    ),
  ),
  aspectRatio: 16 / 9,
)
```

Listen to playback events:

```dart
final controller = KinescopePlayerController('yourVideoId');

controller.status.listen((status) {
  // KinescopePlayerStatus: play, pause, ended, ...
});

controller.timeUpdateStream.listen((update) {
  // current position & percent
});
```

### Offline playback (Android only)

Download a video by ID (DRM is handled automatically when an API key is configured):

```dart
final info = await KinescopeOfflineDownload.instance.downloadVideo('yourVideoId');

// Listen to progress
KinescopeOfflineDownload.instance.updates.listen((update) {
  final download = update.download;
  // download.percent, download.state, download.progressLabel, ...
});

// Play a completed download
KinescopeOfflinePlayer(
  contentId: info.contentId,
)
```

Other offline APIs:

- `getVideoCatalog()` — list videos from your Kinescope project
- `getCompletedDownloads()` / `getAllDownloads()`
- `removeDownload(contentId)`

See the [example app](./example/lib/main.dart) for online player, offline viewing, and download flows.

### `KinescopePlayerController` methods

| Method | Description | Android | iOS / Web |
| ------ | ----------- | ------- | --------- |
| `play()` | Start playback | Yes | Yes |
| `pause()` | Pause playback | Yes | Yes |
| `stop()` | Stop and reset | Yes | Yes |
| `load(String videoId)` | Load another video | Yes | Yes |
| `getCurrentTime()` | Current position | Yes | Yes |
| `getDuration()` | Video duration | Yes | Yes |
| `seekTo(Duration)` | Seek | Yes | Yes |
| `mute()` / `unmute()` | Mute control | Yes | Yes |
| `setVolume(double)` | Volume `0.0`–`1.0` | Yes | No |

### `PlayerParameters`

Initial player options. On **iOS** and **Web**, parameters map to the [Kinescope iframe embed](https://player.kinescope.io/latest/docs/iframe/IframeRegular.html).

On **Android**, the native player uses: `autoplay`, `muted`, `loop`, `controls`, `playsinline`, and `texttrack` (subtitles). Other iframe-only options are ignored on Android.

| Parameter | Description |
| --------- | ----------- |
| `autoplay` | Start playback when the video loads |
| `muted` | Start muted |
| `loop` | Restart when finished |
| `playsinline` | Inline playback (no forced fullscreen) |
| `texttrack` | Enable subtitles when available |
| `controls` | Show player controls |
| `preload` | Preload metadata (iOS / Web) |
| `userAgent` | Custom User-Agent (iOS / Web) |
| `externalId` | User id for analytics |
| `baseUrl` | Custom embed host (iOS / Web) |
| `dnt` | Disable analytics (iOS / Web) |
| `background` | Chromeless mode (iOS / Web) |
| `transparent` | Transparent background (iOS / Web) |
| `header`, `speedbtn`, `disableFiles`, `watermark` | Embed UI options (iOS / Web) |
| `onEnterFullScreen` / `onExitFullScreen` | Fullscreen callbacks |

## Example

```bash
cd example
flutter run
```

The demo includes:

- **Player view** — online playback with native Android player
- **Offline viewing** — downloaded videos list, `+` to add from API catalog (Android only)

## Changelog

See [CHANGELOG.md](./CHANGELOG.md).

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
