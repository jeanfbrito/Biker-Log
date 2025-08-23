#!/bin/bash

echo "🔧 Quick Fix for Android Development Environment"
echo "================================================"
echo ""

# Set Android SDK path
ANDROID_HOME="$HOME/Library/Android/sdk"
echo "Using Android SDK at: $ANDROID_HOME"

# Create SDK directory if it doesn't exist
mkdir -p "$ANDROID_HOME"

# Download platform-tools directly
echo "Downloading platform-tools (includes adb)..."
curl -L -o /tmp/platform-tools.zip "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"

echo "Installing platform-tools..."
unzip -q -o /tmp/platform-tools.zip -d "$ANDROID_HOME/"
rm /tmp/platform-tools.zip

# Update local.properties
echo "Updating local.properties..."
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Test adb
echo ""
echo "Testing adb..."
if [ -f "$ANDROID_HOME/platform-tools/adb" ]; then
    "$ANDROID_HOME/platform-tools/adb" version
    echo "✅ ADB installed successfully!"
else
    echo "❌ ADB installation failed"
    exit 1
fi

# Add to shell profile
echo ""
echo "Adding to shell profile..."
SHELL_PROFILE="$HOME/.zshrc"
if ! grep -q "ANDROID_HOME" "$SHELL_PROFILE" 2>/dev/null; then
    echo "" >> "$SHELL_PROFILE"
    echo "# Android SDK" >> "$SHELL_PROFILE"
    echo "export ANDROID_HOME=\"$ANDROID_HOME\"" >> "$SHELL_PROFILE"
    echo "export PATH=\"\$ANDROID_HOME/platform-tools:\$PATH\"" >> "$SHELL_PROFILE"
    echo "✅ Added to $SHELL_PROFILE"
else
    echo "✅ Already in $SHELL_PROFILE"
fi

echo ""
echo "================================================"
echo "✅ Quick fix complete!"
echo ""
echo "Now run:"
echo "  source ~/.zshrc"
echo ""
echo "Then you can build and run the app:"
echo "  ./build_and_run.sh"
echo ""
echo "Make sure your Android device is connected with USB debugging enabled!"