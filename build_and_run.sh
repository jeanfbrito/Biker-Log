#!/bin/bash

# Simple build and run script for Moto Sensor Logger

echo "🏍️ Moto Sensor Logger - Build & Run"
echo "====================================="
echo ""

# Check if device is connected
echo "Checking for connected devices..."
if command -v adb &> /dev/null; then
    DEVICES=$(adb devices | grep -E "device$" | wc -l)
    if [ "$DEVICES" -eq 0 ]; then
        echo "❌ No Android device connected"
        echo "Please connect your device with USB debugging enabled"
        exit 1
    else
        echo "✅ Found $DEVICES connected device(s)"
        adb devices
    fi
else
    echo "⚠️  ADB not found. Please install Android SDK or run ./setup_android_env.sh"
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
        adb shell am start -n com.motosensorlogger/.MainActivity
        
        echo ""
        echo "✅ App launched successfully!"
        echo ""
        echo "To view logs: adb logcat | grep motosensor"
    else
        echo "❌ Installation failed"
        exit 1
    fi
else
    echo "❌ Build failed"
    echo "Run './setup_android_env.sh' if this is your first build"
    exit 1
fi