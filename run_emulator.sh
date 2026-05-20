#!/bin/bash
# Run IPTV Player in Android Emulator

export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH

# Build
echo "Building APK..."
gradle assembleDebug || exit 1

# Start emulator if not already running
if ! adb devices | grep -q "emulator"; then
    echo "Starting emulator..."
    emulator -avd iptv_tv -no-snapshot-load &
    echo "Waiting for emulator to boot..."
    adb wait-for-device
    sleep 10
fi

# Install and launch
echo "Installing APK..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "Launching app..."
adb shell am start -n com.iptv.player/.MainActivity
