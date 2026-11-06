# NewPipe

An unofficial redesign fork of [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe), focused on a modern, polished Android experience while retaining NewPipe's open-source media browsing and playback foundation.

## Download

**[Download the latest stable APK](https://github.com/Abdellah-mouida/NewPipe/releases/latest/download/NewPipe-v1.0.0.apk)**

[View all releases](https://github.com/Abdellah-mouida/NewPipe/releases)

Stable packages are published from GitHub Actions after the build, lint, unit tests, Android emulator tests, and release-signature verification pass.

## Design

- Floating, animated bottom navigation with a Telegram-inspired active indicator.
- Rounded, animated side drawer with service switching and quick destinations.
- Violet and blue accents with soft gradient surfaces.
- Rounded, full-width video cards and larger 16:9 thumbnails.
- Smooth page, tab, drawer, and press-state animations.
- Light, dark, and AMOLED themes.

## Features

The fork retains NewPipe's core functionality, including search, subscriptions, playlists, history, downloads, background playback, popup playback, subtitles, and comments.

Supported services include YouTube and YouTube Music, PeerTube, Bandcamp, SoundCloud, and media.ccc.de. Availability depends on upstream service changes and NewPipe Extractor support.

## Installation

1. Download the APK from the stable-release link above.
2. Allow your browser or file manager to install apps from this source when Android asks.
3. Open the downloaded APK and follow the installer.

**Switching from another NewPipe build:** Android does not allow an APK signed with a different key to update an existing installation. Export your NewPipe database first, uninstall the differently signed app if necessary, install this fork, and then restore your data. Keep a backup before changing builds.

## Build from source

Requirements: JDK 21 and Android SDK 37.0.

```bash
git clone https://github.com/Abdellah-mouida/NewPipe.git
cd NewPipe
git checkout redesign/premium-ui
./gradlew assembleContinuous
```

For production release builds, configure these GitHub Actions repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Never commit a keystore or signing password to the repository.

## Credits and license

This project is an unofficial fork based on [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe). The upstream project and its contributors are credited; see the source headers and upstream repository for attribution.

Licensed under the **GNU General Public License v3.0 or later**. See [LICENSE](LICENSE).
