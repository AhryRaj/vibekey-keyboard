# VibeKey — GIF & Sticker Keyboard 🎹✨

> A modern, lightweight, privacy-first GIF, Sticker, and QWERTY chat keyboard for Android and iOS. Built with official OS Input Method APIs and powered by the official KLIPY v2 API for instant movie dialogue reactions, trending GIFs, and stickers.

---

## 🌟 Key Features

- **⚡ 1-Tap Instant Send**: Direct insertion into chats via Android's official `commitContent` API and iOS `UIPasteboard`.
- **💬 Dual-Platform Messaging Optimization**:
  - **WhatsApp**: Automatically generates and delivers borderless `.webp` (`image/webp.wasticker`) stickers.
  - **Instagram**: Automatically delivers transparent animated `.gif` and 512×512 `.png` stickers for Instagram DMs and Stories.
  - **Telegram & Messages**: Universal rich media support with automatic fallback to system clipboard.
- **🔍 Powered by Official KLIPY v2 API**:
  - Official drop-in replacement for Google Tenor v2 with 100% legal API compliance.
  - Instant movie dialogue autocomplete chips (Tamil, Bollywood, Hollywood & viral memes).
  - High-speed in-memory LRU caching and perceptual duplicate detection.
- **⌨️ Built-in Full Chat QWERTY Keyboard**:
  - Dynamic Shift (`⇧`) uppercase/lowercase toggling.
  - Punctuation & numeric keypad (`?123`) and advanced mathematical/currency symbols (`=\<`).
  - Fast period (`.`), comma (`,`), and action keys (`Send` / `Enter`).
- **⇄ Visual Keyboard Switcher**:
  - Custom canvas-rendered keyboard switcher icon (`⇄`) to easily swap between VibeKey and your system keyboard (Gboard, Samsung Keyboard, etc.).
- **🎬 Interactive Guided Onboarding**:
  - 60fps 3-scene auto-playing motion showcase guiding users through enabling the keyboard, switching, and 1-tap sending.
  - Real-time setup status badges with direct links to Android system input settings.
- **🔒 Privacy & Play Store Ready**:
  - Zero keystroke tracking or logging.
  - Encrypted HTTPS only (`android:usesCleartextTraffic="false"`).
  - Minimal permissions: only `INTERNET` (for GIF search) and `VIBRATE`.

---

## 📁 Repository Structure

```text
vibekey-keyboard/
├── app/                             # Standalone Android Application & IME Module
│   ├── src/main/
│   │   ├── AndroidManifest.xml      # IME service declarations & package queries
│   │   ├── java/com/cinepulse/keyboard/
│   │   │   ├── CinePulseInputMethodService.java  # Core IME engine & chat layout
│   │   │   ├── KeyboardSetupActivity.java        # Modern guided onboarding UI
│   │   │   ├── OfficialMediaRepository.java      # KLIPY v2 API & dialogue cache
│   │   │   └── AudioGifCompiler.java             # WebP/PNG sticker & MP4 compiler
│   │   ├── res/                     # Strings, styles, IME method & FileProvider configs
│   │   └── assets/                  # Offline fallback reactions & demo media
│   ├── build.gradle                 # Module build configuration (SDK 36)
│   └── proguard-rules.pro           # Proguard optimization rules
├── ios/                             # iOS Keyboard Extension
│   ├── KeyboardViewController.swift # Official UIInputViewController implementation
│   └── Info.plist                   # iOS extension configuration
├── gradle/wrapper/                  # Gradle 8.14.3 wrapper
├── gradlew                          # Linux/macOS Gradle launcher
├── gradlew.bat                      # Windows Gradle launcher
├── build.gradle                     # Root project build configuration
├── settings.gradle                  # Gradle project settings
├── gradle.properties                # AndroidX and JVM flags
└── scripts/
    └── build-apk.sh                 # Quick CLI script to build VibeKey.apk
```

---

## 🚀 Getting Started

### Prerequisites

- **Android SDK**: Android API 36 (min SDK 24 / Android 7.0+)
- **JDK**: OpenJDK 17 or 21
- **Gradle**: Gradle 8.14 (included via `./gradlew`)

### Building the Android APK

1. Clone this repository:
   ```bash
   git clone https://github.com/<your-username>/vibekey-keyboard.git
   cd vibekey-keyboard
   ```

2. Build using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
   Or use the automated script:
   ```bash
   bash scripts/build-apk.sh
   ```

3. The compiled APK will be located at:
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   # or release/VibeKey.apk
   ```

4. Install directly to a connected Android device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🔑 KLIPY API Configuration

VibeKey uses the official **KLIPY v2 API** (`https://api.klipy.com/v2/search`) for GIF and sticker search.

To update or replace your API key, edit `OfficialMediaRepository.java`:

```java
public static final String DEVELOPER_TENOR_OR_KLIPY_API_KEY = "YOUR_KLIPY_API_KEY_HERE";
```

> **Note**: For Play Store production releases with unlimited calls, register free at [partner.klipy.com](https://partner.klipy.com) and request production access.

---

## 📲 iOS Extension Setup

The `ios/` folder contains the official Swift implementation for Apple's `UIInputViewController`:

1. Open your iOS project in Xcode.
2. Select **File ➔ New ➔ Target ➔ Custom Keyboard Extension**.
3. Name it `VibeKeyExtension`.
4. Copy `ios/KeyboardViewController.swift` and `ios/Info.plist` into your extension group.
5. In your extension's `Info.plist`, ensure `RequestsOpenAccess` is set to `YES` to allow network access for GIF searching.

---

## 📄 License

This project is open-source under the [MIT License](LICENSE).
