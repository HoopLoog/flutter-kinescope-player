# Shared API

Types shared by **native (Android)** and **embed (iOS / Web)**.

← [Home](../README.md) · [Android](android-native.md) · [Embed](embed-webview.md)

---

## KinescopePlayerController

```dart
final controller = KinescopePlayerController(
  'yourVideoId',
  parameters: const PlayerParameters(autoplay: true),
);

controller.status.listen((status) { /* play, pause, ended, … */ });
controller.timeUpdateStream.listen((update) { /* position & percent */ });
```

| Method | Android | iOS / Web |
| ------ | :---: | :---: |
| `play()` / `pause()` / `stop()` | ✓ | ✓ |
| `load(String videoId)` | ✓ | ✓ |
| `getCurrentTime()` / `getDuration()` | ✓ | ✓ |
| `seekTo(Duration)` | ✓ | ✓ |
| `mute()` / `unmute()` | ✓ | ✓ |
| `setVolume(double)` (`0.0`–`1.0`) | ✓ | — |

Widget:

```dart
KinescopePlayer(
  controller: controller,
  aspectRatio: 16 / 9,
)
```

---

## PlayerParameters

On **iOS / Web**, parameters map to the iframe embed.  
On **Android**, the native player uses only a subset (below).

### Shared (with notes)

| Parameter | Android | iOS / Web |
| --------- | :---: | :---: |
| `autoplay` | ✓ | ✓ |
| `muted` | ✓ | ✓ |
| `loop` | ✓ | ✓ |
| `playsinline` | ✓ | ✓ |
| `texttrack` | ✓ native | ✓ embed |
| `controls` | ✓ master chrome | ✓ |
| `onEnterFullScreen` / `onExitFullScreen` | ✓ | ✓ |
| `onEnterPictureInPicture` / `onExitPictureInPicture` | ✓ | — |

### iOS / Web only (embed)

| Parameter | |
| --------- | --- |
| `preload` | Preload metadata |
| `userAgent` | Custom User-Agent |
| `externalId` | Analytics user id |
| `baseUrl` | Custom embed host |
| `dnt` | Disable analytics |
| `background` / `transparent` | Chromeless / transparent |
| `header`, `speedbtn`, `disableFiles`, `watermark` | Embed UI |

### Android only (native)

See [android-native.md](android-native.md): offline API, PiP setup, native chrome.  
There are no extra native-only fields on `PlayerParameters` — behaviour comes from the native SDK and the app manifest.
