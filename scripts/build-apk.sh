#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

echo "============================================================"
echo "🎹 VibeKey — GIF & Sticker Keyboard APK Builder"
echo "============================================================"

if [ -d "/opt/homebrew/opt/openjdk@21" ]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ -d "/opt/homebrew/share/android-commandlinetools" ]; then
  export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
fi

chmod +x gradlew
./gradlew clean assembleDebug

APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_SRC" ]; then
  mkdir -p release
  cp "$APK_SRC" release/VibeKey.apk
  echo ""
  echo "✅ SUCCESS! Built standalone VibeKey APK:"
  echo "   👉 release/VibeKey.apk"
else
  echo "❌ Build failed: APK not found at $APK_SRC"
  exit 1
fi
