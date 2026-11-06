# Scrcpy Android

Scrcpy ported as a pure Android app: **mirror your phone's screen to an Android TV** over a
plain USB cable — no app, daemon or companion on the phone; only *USB debugging* enabled.

## How it works

The TV app embeds the official `scrcpy-server-v5.0.1` jar. On connect it:

1. Detects the phone on the USB host port (hot-plug aware).
2. Speaks the ADB protocol natively (RSA-2048 auth, file sync, shell streams).
3. Pushes the scrcpy server to `/data/local/tmp` and launches it via `app_process` exactly
   like the desktop client.
4. Decrypts nothing — streams the H.264/H.265 video + OPUS/AAC/FLAC audio straight into
   `MediaCodec` decoders rendered fullscreen immersive on the TV.
5. Sends back control: TV remote keys as Android keycodes, TV-connected mouse as native
   touch/hover/scroll injections, keyboards as direct key injection (HID grab).

### Features
- **Mirror mode** (display 0) and **Desktop / virtual display mode** (`new_display`, like
  scrcpy's desktop mode) with configurable resolution + DPI.
- Phone screen can be **off** while streaming (display-power control).
- Quality (bitrate), FPS and codec selection; audio sharing optional.
- Material 3 (Compose) UI, auto-scaled for TV vs phone, dark/light + dynamic (Monet)
  color schemes and palette picker.
- Optional battery % + temperature overlay of the phone (polled via ADB shell).
- Immersive fullscreen player, all input grabbed and forwarded to the phone.

### Build
GitHub Actions builds both debug and release APKs on every push (`.github/workflows/build.yml`),
downloading the official Scrcpy server jar at build time.

## Credits
Built on top of [Scrcpy](https://github.com/Genymobile/scrcpy) by Romain Vimont / Genymobile
(Apache-2.0). This port adds the Android-side client: an embedded minimal ADB host and a
Material 3 TV/player UI.

## Licence
Apache-2.0 (see [LICENSE](LICENSE)). scrcpy-server © Genymobile, bundled unmodified.
