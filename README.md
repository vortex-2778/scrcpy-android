# Scrcpy Android Port

Mirror an Android phone's screen to **Android TV** (or another Android device) over a
**wired USB** connection — no app or receiver required on the phone, just USB debugging.
The TV app implements a minimal **ADB host over USB**, pushes the official
[scrcpy](https://github.com/Genymobile/scrcpy) **v5.0.1** server to the phone, launches it
with `app_process`, and decodes the H.264/H.265 video (plus audio) live.

Remote-control input (D-pad, keyboard, mouse clicks/hover/scroll) is injected back to the
phone through scrcpy's control protocol. It can also create a **virtual display (desktop
mode)** on supported Android versions, with automatic fallback to plain mirroring.

## Features

- **Mirror mode** (physical display) and **desktop mode** (virtual display, Android 10+),
  with `Auto` fallback to mirroring when virtual displays are unavailable.
- Video codec **H.264 / H.265**, bitrate **1–24 Mbps**, max FPS **0/30/60/90/120**, max size.
- **Audio** streaming & playback: Opus, AAC, FLAC or raw PCM (Android 11+ for capture).
- **Screen-off streaming**: keep the phone display dark while the TV keeps rendering.
- **Battery level + temperature overlay** (polled via `dumpsys battery`).
- **USB hot-plug** detection, permission prompt, automatic app launch when the phone is plugged.
- TV **and** phone friendly Material 3 UI, immersive fullscreen player, animated transitions.
- **Theming**: Monet/dynamic colors (Android 12+) or 4 built-in palettes.
- Input takeover while mirroring: all remote/keyboard/mouse events go to the phone.

## How it works

1. On connect the app finds the phone's ADB USB interface (`ff/42/01`) and performs the
   ADB `CNXN`/`AUTH` handshake with a persisted RSA-2048 key (first time: accept the
   "Allow USB debugging" dialog on the phone).
2. `scrcpy-server.jar` (bundled from the official release at CI build time) is pushed to
   `/data/local/tmp` using the ADB `sync:` file-transfer protocol.
3. The server is launched with `app_process` (`tunnel_forward=true`), and the app connects
   to the `localabstract:scrcpy_<scid>` socket in the required order: video, audio, control.
4. Video is decoded with `MediaCodec` straight onto a `SurfaceView`; audio is decoded with
   `MediaCodec` or fed to `AudioTrack` (raw PCM).
5. Remote input is written as scrcpy control messages (big-endian) over the control stream.

## Build (GitHub Actions)

Every push to `main` builds the APKs on GitHub Actions:

- downloads `scrcpy-server-v5.0.1` into `app/src/main/assets/scrcpy-server.jar`,
- generates a release keystore,
- runs `assembleDebug` + `assembleRelease`,
- uploads both APKs as the `scrcpy-android-apks` artifact.

No local Android SDK/Gradle build is required; grab the latest artifact from the
Actions tab.

## Usage

1. On the phone: enable **Developer options → USB debugging**.
2. Connect the phone to the TV with a data-capable USB cable.
3. Open the app (it launches automatically on TV when the phone is attached), grant the
   USB access prompt, then press **Connect**.
4. Accept the RSA key prompt on the phone (first time only).
5. Press **Open player** to view; `BACK` exits, `MENU` toggles the overlay.

## Project layout

```
app/src/main/java/com/scrcpy/android/
  adb/       minimal ADB host (packets, auth, streams, sync push)
  usb/       USB host discovery, hot-plug, permission flow
  scrcpy/    server args, control writer, video/audio decoders, session, poller
  mirror/    MirrorController state machine + display/overlay state
  data/      settings + enums
  ui/        Compose Material 3 theme, components, screens
```

## License

MIT — see [LICENSE](LICENSE). scrcpy itself is Apache-2.0 by Genymobile.
