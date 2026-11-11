![Next player banner](fastlane/metadata/android/en-US/images/featureGraphic.png)

# Next Player (Enhanced Edition - KennyPhan123 Fork)

[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/KennyPhan123/nextplayer.svg?logo=github&label=Latest%20Release&cacheSeconds=3600)](https://github.com/KennyPhan123/nextplayer/releases)
[![Build Status](https://github.com/KennyPhan123/nextplayer/actions/workflows/android_build.yaml/badge.svg)](https://github.com/KennyPhan123/nextplayer/actions/workflows/android_build.yaml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)

An advanced, feature-rich Android native video player written in Kotlin, based on [Next Player](https://github.com/anilbeesetti/nextplayer) by [Anil Beesetti](https://github.com/anilbeesetti).

This fork integrates specialized cinematic features, Kodi-grade audio processing, playback bug fixes, and tailored optimizations for Remux/Blu-ray 4K HDR playback on modern Android smartphones and tablets.

---

## Key Features and Enhancements in This Fork

### 1. Kodi ActiveAE Audio Engine and Anti-Clipping Peak Limiter
Ported directly from Kodi's (`xbmc/cores/AudioEngine`) audio architecture:
- **32-bit Float Internal Audio Pipeline**: Retains full 32-bit floating-point headroom (`C.ENCODING_PCM_FLOAT`) throughout decoding, volume scaling, and downmixing. Prevents premature 16-bit truncation and distortion from high dynamic range audio codecs (TrueHD, DTS-HD MA, Dolby Atmos, E-AC3, FLAC).
- **Kodi `CAELimiter` Integration**: Replaces buggy Android hardware effects (`LoudnessEnhancer`) with Kodi's genuine instantaneous peak limiter:
  - **Instant Attack (0 ms)**: Catches full-scale transients in the exact sample frame before clipping can occur.
  - **25 ms Hold Counter**: Eliminates flutter and pumping artifacts during intense action or explosion sequences.
  - **100 ms Exponential Release**: Smoothly restores natural dynamic range following human psychoacoustic curves.
  - **Zero-Clipping Volume Boost (up to 200% / +20 dB)**: Amplify quiet movies without any audio distortion or speaker rattle.
- **Smart Stereo Downmixing (5.1 / 7.1 -> 2.0)**:
  - **Unnormalized Downmix**: Preserves original theatrical volume levels instead of dividing all channels down (which makes dialogue in standard players inaudible).
  - **Dialogue Enhancement**: Boosts center channel dialogue (`FC = 0.85f` / `-1.4 dB`) so speech cuts through clearly on small phone speakers.
  - **Hardware Output Route Detection (Real-Time)**:
    - *Built-in Phone Speakers*: Cuts LFE subwoofer frequencies (`0.0f`) to protect miniature speaker cones from rattle and save headroom for vocals.
    - *Stereo Headphones/Earbuds*: Automatically injects 35% LFE (`0.35f`) for warm, punchy sub-bass without requiring manual EQ tweaking.
    - *Spatial Audio / Multi-channel Sinks*: Retains full multi-channel streams for Android's native `Spatializer` and external HDMI/eARC systems.
  - Easily toggle via **Settings -> Audio -> Smart audio & anti-clipping**.

---

### 2. Dolby Vision Profile 7 (DV7) HDR10 Fallback
- Safely strips unsupported `KEY_PROFILE` and `KEY_COLOR_TRANSFER_REQUEST` metadata when decoding Dolby Vision Profile 7 (common in 4K UHD Remux titles).
- Enables seamless fallback to standard HDR10 on devices (such as Samsung Exynos and various MediaTek/Snapdragon chipsets) that would otherwise display a black screen with audio only.
- Configurable in **Settings -> Player -> Dolby Vision Profile 7 Fallback**.

---

### 3. Independent X / Y Zoom and Axis Snapping
- Stretch and adjust video aspect ratios horizontally (X) or vertically (Y) independently using two-finger pinch/drag gestures.
- Built-in **intelligent axis snapping** prevents accidental cross-axis distortion while resizing.
- Automatically persists custom zoom ratios per video in the local database.

---

### 4. Subtitle Enhancements and PGS Fixes
- **PGS Subtitle Aspect Ratio Protection**: Prevents bitmap subtitles (PGS / DVB from Blu-ray Remuxes) from stretching horizontally when custom video scaling is applied.
- **Pin Subtitles to Video Surface (`subtitlePinToVideo`)**: Option to lock subtitles directly to the video frame so they smoothly follow zoom and pan gestures rather than staying fixed to screen borders.
- Configurable in **Settings -> Subtitles -> Pin subtitles to video**.

---

### 5. Picture-in-Picture (PiP) Fixes and Playback Controls
- Fixed an upstream issue preventing manual entry into Picture-in-Picture mode when playback is paused.
- Enhanced gesture responsiveness and auto-hiding controls.

---

### 6. Dual-Edition Architecture and Modern Material 3 UI
This repository maintains both the stable baseline and modern upstream updates:
- **`nextplayer-latest/`**: Upstream NextPlayer with Material 3 expressive UI, updated nextlib decoder engine, and all custom features ported over.
- **Automated CI/CD Workflows**: GitHub Actions builds signed APKs (`universal`, `arm64-v8a`) on demand via `workflow_dispatch` and on commit pushes.

---

## Downloads

Grab the latest prebuilt APKs from the [Releases](https://github.com/KennyPhan123/nextplayer/releases) section or from the **Artifacts** tab on [GitHub Actions](https://github.com/KennyPhan123/nextplayer/actions):
- **`nextplayer-arm64-v8a-release.apk`**: Optimized for modern Android smartphones (64-bit ARM).
- **`nextplayer-universal-release.apk`**: Compatible with all supported CPU architectures (arm64-v8a, armeabi-v7a, x86, x86_64).

---

## Supported Formats

- **Video**: H.264 AVC, H.265 HEVC (10-bit, HDR10, DV7 fallback), VP8, VP9, AV1, MPEG-4, H.263
- **Audio**: TrueHD, Dolby Atmos, DTS-HD MA, DTS, AC-3, E-AC-3, FLAC, ALAC, Opus, Vorbis, AAC, PCM/WAVE
- **Subtitles**: PGS (Blu-ray bitmap), ASS/SSA (with embedded styling), SRT, VTT, DVB, TTML

---

## Building From Source

### Prerequisites
- JDK 17 (Temurin recommended)
- Android SDK (API 35, Build Tools 35.0.0+)

### Build commands
```bash
# Build the root project
./gradlew assembleRelease-with-debug-signing

# Build the modern latest edition
cd nextplayer-latest
./gradlew assembleRelease-with-debug-signing
```

---

## Credits and Acknowledgements

- Original [Next Player](https://github.com/anilbeesetti/nextplayer) by [Anil Beesetti](https://github.com/anilbeesetti).
- [Team Kodi](https://kodi.tv/) for the battle-tested `ActiveAE` and `CAELimiter` DSP design.
- [ExoPlayer / AndroidX Media3](https://github.com/androidx/media) and [nextlib](https://github.com/anilbeesetti/nextlib).

---

## License

Next Player is licensed under the [GNU General Public License v3.0](LICENSE).
