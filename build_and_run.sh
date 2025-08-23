#!/bin/bash

# Simple build and run script for Moto Sensor Logger

echo "🏍️ Moto Sensor Logger - Build & Run"
echo "====================================="
echo ""

# Read SDK path from local.properties if it exists
if [ -f "local.properties" ]; then
    SDK_DIR=$(grep "sdk.dir" local.properties | cut -d'=' -f2)
    if [ -n "$SDK_DIR" ]; then
        # Export paths for this session
        export ANDROID_HOME="$SDK_DIR"
        export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin:$PATH"
    fi
fi

# Try to find ADB
ADB_CMD=""
if command -v adb &> /dev/null; then
    ADB_CMD="adb"
elif [ -f "$ANDROID_HOME/platform-tools/adb" ]; then
    ADB_CMD="$ANDROID_HOME/platform-tools/adb"
elif [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    ADB_CMD="$HOME/Library/Android/sdk/platform-tools/adb"
elif [ -f "$HOME/Android/Sdk/platform-tools/adb" ]; then
    ADB_CMD="$HOME/Android/Sdk/platform-tools/adb"
fi

# Check if device is connected
echo "Checking for connected devices..."
if [ -n "$ADB_CMD" ]; then
    DEVICES=$($ADB_CMD devices | grep -E "device$" | wc -l)
    if [ "$DEVICES" -eq 0 ]; then
        echo "❌ No Android device connected"
        echo "Please connect your device with USB debugging enabled"
        exit 1
    else
        echo "✅ Found $DEVICES connected device(s)"
        $ADB_CMD devices
    fi
else
    echo "⚠️  ADB not found. Running setup to install Android SDK..."
    echo ""
    ./setup_android_env.sh
    echo ""
    echo "Please restart your terminal or run:"
    echo "  source ~/.zshrc    (on macOS)"
    echo "  source ~/.bashrc   (on Linux)"
    echo ""
    echo "Then run this script again."
    exit 1
fi
echo ""

# Clean previous builds (optional)
if [ "$1" == "clean" ]; then
    echo "Cleaning previous builds..."
    ./gradlew clean
    echo ""
fi

# Build the app
echo "Building debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo ""
    
    # Install and run
    echo "Installing on device..."
    ./gradlew installDebug
    
    if [ $? -eq 0 ]; then
        echo "✅ Installation successful!"
        echo ""
        
        # Launch the app
        echo "Launching app..."
        $ADB_CMD shell am start -n com.motosensorlogger/.MainActivity
        
        echo ""
        echo "✅ App launched successfully!"
        echo ""
        echo "To view logs: $ADB_CMD logcat | grep motosensor"
    else
        echo "❌ Installation failed"
        exit 1
    fi
else
    echo "❌ Build failed"
    echo "Run './setup_android_env.sh' if this is your first build"
    exit 1
fi