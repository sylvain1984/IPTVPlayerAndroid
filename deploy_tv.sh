#!/bin/bash
# Deploy IPTV Player to TCL TV at 192.168.1.6
# TGuard on this TV blocks debug APKs; use release build with debuggable=true

TV_IP="192.168.1.6:5555"
APK_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
APK_SIGNED="app/build/outputs/apk/release/app-release-signed.apk"
DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
BUILD_TOOLS="/Users/yue/Library/Android/sdk/build-tools/34.0.0"

set -e

echo "=== Building release APK ==="
./gradlew assembleRelease

echo "=== Signing with debug keystore ==="
"$BUILD_TOOLS/apksigner" sign \
  --ks "$DEBUG_KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$APK_SIGNED" \
  "$APK_UNSIGNED"

echo "=== Connecting to TV ==="
adb connect "$TV_IP"

echo "=== Installing APK ==="
adb -s "$TV_IP" install -r "$APK_SIGNED"

echo "=== Launching app ==="
adb -s "$TV_IP" shell am start -n com.iptv.player/.MainActivity

echo ""
echo "=== App running! Follow logs with: ==="
echo "adb -s $TV_IP logcat --pid=\$(adb -s $TV_IP shell pidof com.iptv.player)"
