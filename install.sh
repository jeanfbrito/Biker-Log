#!/bin/bash

echo "🏍️ Installing Moto Sensor Logger..."
echo ""

# Auto-detect and use Java from Homebrew if needed
if [ -f "/opt/homebrew/opt/openjdk@17/bin/java" ]; then
    export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
elif [ -f "/usr/local/opt/openjdk@17/bin/java" ]; then
    export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
    export JAVA_HOME="/usr/local/opt/openjdk@17"
fi

# Auto-accept Android licenses
mkdir -p "$HOME/Library/Android/sdk/licenses"
echo -e "8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e\n24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$HOME/Library/Android/sdk/licenses/android-sdk-license"
echo "84831b9409646a918e30573bab4c9c91346d8abd" > "$HOME/Library/Android/sdk/licenses/android-sdk-preview-license"

# Build and install (Gradle auto-downloads SDK)
./gradlew installDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ App installed! Check your phone for 'Moto Sensor Logger'"
else
    echo ""
    echo "❌ Installation failed"
    echo ""
    echo "Common fixes:"
    echo "1. Connect your Android device via USB"
    echo "2. Enable USB Debugging (Settings → Developer Options)"
    echo "3. If Java is missing: brew install openjdk@17"
    echo ""
    echo "Or run ./setup.sh for complete setup assistance"
fi