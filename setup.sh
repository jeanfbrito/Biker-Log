#!/bin/bash

# Moto Sensor Logger - Complete Setup & Install Script
# This script handles EVERYTHING automatically

set -e

echo "🏍️ Moto Sensor Logger - Setup & Install"
echo "========================================"
echo ""

# 1. Check and setup Java
echo "Step 1: Checking Java..."
if command -v java &>/dev/null; then
    echo "✅ Java is installed"
else
    # Try to use Homebrew Java if installed
    if [ -f "/opt/homebrew/opt/openjdk@17/bin/java" ]; then
        export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
        export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
        echo "✅ Using Homebrew Java"
    elif [ -f "/usr/local/opt/openjdk@17/bin/java" ]; then
        export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
        export JAVA_HOME="/usr/local/opt/openjdk@17"
        echo "✅ Using Homebrew Java"
    else
        echo "❌ Java not found. Installing..."
        
        # Check if Homebrew is installed
        if ! command -v brew &>/dev/null; then
            echo "Installing Homebrew..."
            /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
            
            # Add Homebrew to PATH for Apple Silicon Macs
            if [[ $(uname -m) == "arm64" ]]; then
                eval "$(/opt/homebrew/bin/brew shellenv)"
            fi
        fi
        
        echo "Installing OpenJDK 17..."
        brew install openjdk@17
        
        # Setup Java path
        if [ -f "/opt/homebrew/opt/openjdk@17/bin/java" ]; then
            export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
            export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
        else
            export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
            export JAVA_HOME="/usr/local/opt/openjdk@17"
        fi
        
        # Add to shell profile
        echo "" >> ~/.zshrc
        echo "# Java for Android development" >> ~/.zshrc
        echo "export PATH=\"$JAVA_HOME/bin:\$PATH\"" >> ~/.zshrc
        echo "export JAVA_HOME=\"$JAVA_HOME\"" >> ~/.zshrc
        
        echo "✅ Java installed"
    fi
fi
echo ""

# 2. Accept Android SDK licenses
echo "Step 2: Setting up Android SDK licenses..."
mkdir -p "$HOME/Library/Android/sdk/licenses"

cat << EOF > "$HOME/Library/Android/sdk/licenses/android-sdk-license"
8933bad161af4178b1185d1a37fbf41ea5269c55
d56f5187479451eabf01fb78af6dfcb131a6481e
24333f8a63b6825ea9c5514f83c2829b004d1fee
EOF

cat << EOF > "$HOME/Library/Android/sdk/licenses/android-sdk-preview-license"
84831b9409646a918e30573bab4c9c91346d8abd
EOF

echo "✅ Android licenses accepted"
echo ""

# 3. Check device connection
echo "Step 3: Checking device connection..."
echo ""
echo "📱 Make sure your Android device is:"
echo "   1. Connected via USB cable"
echo "   2. USB Debugging is enabled (Settings → Developer Options)"
echo "   3. You tap 'Allow' when asked about USB debugging"
echo ""
read -p "Press ENTER when your device is connected..."
echo ""

# 4. Build and install
echo "Step 4: Building and installing app..."
echo "(Gradle will download Android SDK and dependencies automatically)"
echo ""

./gradlew installDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "================================================"
    echo "✅ SUCCESS! App installed on your phone!"
    echo "================================================"
    echo ""
    echo "Look for 'Moto Sensor Logger' on your device"
    echo ""
    echo "When you open the app:"
    echo "1. Grant all permissions (Location, Storage, etc.)"
    echo "2. Tap 'Start Recording' to begin logging"
    echo "3. Ride! 🏍️"
    echo ""
else
    echo ""
    echo "❌ Installation failed"
    echo ""
    echo "Troubleshooting:"
    echo "1. Make sure USB debugging is enabled"
    echo "2. Check that your device shows up in: adb devices"
    echo "3. Try disconnecting and reconnecting the USB cable"
    echo "4. Select 'File Transfer' mode on your phone"
    echo ""
    echo "If it still doesn't work, try Android Studio instead."
fi