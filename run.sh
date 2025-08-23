#!/bin/bash

# Super simple run script - Gradle handles everything!

echo "🏍️ Moto Sensor Logger"
echo "===================="
echo ""

# Just run gradle - it will download everything needed automatically!
echo "Building and installing app (Gradle will handle all dependencies)..."
echo ""

# This single command does EVERYTHING:
# - Downloads Android SDK if needed
# - Downloads dependencies
# - Builds the app
# - Installs on your phone
./gradlew installDebug --stacktrace

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Success! The app is now on your phone"
    echo ""
    # Try to launch (will work if adb is available)
    if [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        $HOME/Library/Android/sdk/platform-tools/adb shell am start -n com.motosensorlogger/.MainActivity 2>/dev/null
        echo "App launched!"
    fi
else
    echo ""
    echo "❌ Build failed. Make sure:"
    echo "1. Your phone is connected via USB"
    echo "2. USB debugging is enabled"
    echo "3. You have Java installed (java -version)"
fi