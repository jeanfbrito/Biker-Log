#!/bin/bash

# Moto Sensor Logger - Android Development Environment Setup Script
# This script helps set up the development environment for building the Android app
# It's safe to run multiple times - it will skip already installed components

set -e

echo "=========================================="
echo "Moto Sensor Logger - Environment Setup"
echo "=========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

# Detect OS
OS="unknown"
if [[ "$OSTYPE" == "darwin"* ]]; then
    OS="macos"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    OS="linux"
else
    print_error "Unsupported OS: $OSTYPE"
    exit 1
fi

print_status "Detected OS: $OS"
echo ""

# Check for Homebrew (macOS)
if [ "$OS" == "macos" ]; then
    if ! command -v brew &> /dev/null; then
        print_warning "Homebrew not found. Installing..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    else
        print_status "Homebrew is installed"
    fi
fi

# Check for Java
echo "Checking Java installation..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -ge 17 ]; then
        print_status "Java $JAVA_VERSION is installed"
    else
        print_warning "Java version is $JAVA_VERSION, but 17 or higher is recommended"
        if [ "$OS" == "macos" ]; then
            echo "Installing OpenJDK 17..."
            brew install openjdk@17
            echo 'export PATH="/usr/local/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
            export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
        else
            echo "Please install Java 17 or higher manually"
        fi
    fi
else
    print_warning "Java not found. Installing..."
    if [ "$OS" == "macos" ]; then
        brew install openjdk@17
        echo 'export PATH="/usr/local/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
        export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
    else
        print_error "Please install Java 17 or higher manually"
        echo "Ubuntu/Debian: sudo apt-get install openjdk-17-jdk"
        echo "Fedora: sudo dnf install java-17-openjdk"
        exit 1
    fi
fi
echo ""

# Check for Android SDK
echo "Checking Android SDK..."
ANDROID_HOME_SET=false
if [ -n "$ANDROID_HOME" ]; then
    if [ -d "$ANDROID_HOME" ]; then
        print_status "ANDROID_HOME is set: $ANDROID_HOME"
        ANDROID_HOME_SET=true
    else
        print_warning "ANDROID_HOME is set but directory doesn't exist: $ANDROID_HOME"
    fi
else
    # Check common locations
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
        print_status "Found Android SDK at: $ANDROID_HOME"
        ANDROID_HOME_SET=true
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
        print_status "Found Android SDK at: $ANDROID_HOME"
        ANDROID_HOME_SET=true
    else
        print_warning "Android SDK not found"
    fi
fi

# Install Android SDK if not found
if [ "$ANDROID_HOME_SET" = false ]; then
    print_warning "Android SDK not found. Installing Android command line tools..."
    
    if [ "$OS" == "macos" ]; then
        SDK_DIR="$HOME/Library/Android/sdk"
    else
        SDK_DIR="$HOME/Android/Sdk"
    fi
    
    mkdir -p "$SDK_DIR/cmdline-tools"
    
    # Download command line tools
    if [ "$OS" == "macos" ]; then
        CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-mac-10406996_latest.zip"
    else
        CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip"
    fi
    
    echo "Downloading Android command line tools..."
    curl -o /tmp/cmdline-tools.zip "$CMDLINE_TOOLS_URL"
    unzip -q /tmp/cmdline-tools.zip -d "$SDK_DIR/cmdline-tools/"
    mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm /tmp/cmdline-tools.zip
    
    export ANDROID_HOME="$SDK_DIR"
    export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
    
    # Add to shell profile
    if [ "$OS" == "macos" ]; then
        echo "export ANDROID_HOME=\"$SDK_DIR\"" >> ~/.zshrc
        echo 'export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"' >> ~/.zshrc
    else
        echo "export ANDROID_HOME=\"$SDK_DIR\"" >> ~/.bashrc
        echo 'export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"' >> ~/.bashrc
    fi
    
    print_status "Android command line tools installed"
fi

# Set up PATH if needed
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin:$PATH"
echo ""

# Install platform-tools and other components
echo "Installing Android SDK components..."

# Try to find sdkmanager
SDKMANAGER=""
if command -v sdkmanager &> /dev/null; then
    SDKMANAGER="sdkmanager"
elif [ -f "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
elif [ -f "$ANDROID_HOME/tools/bin/sdkmanager" ]; then
    SDKMANAGER="$ANDROID_HOME/tools/bin/sdkmanager"
fi

if [ -n "$SDKMANAGER" ]; then
    print_status "Found sdkmanager at: $SDKMANAGER"
    
    # Accept licenses
    echo "Accepting Android SDK licenses..."
    yes | $SDKMANAGER --licenses 2>/dev/null || true
    
    # Install required SDK components
    echo "Installing platform-tools..."
    $SDKMANAGER "platform-tools" 2>/dev/null || {
        print_warning "Failed to install via sdkmanager, trying direct download..."
        
        # Direct download platform-tools as fallback
        if [ "$OS" == "macos" ]; then
            PLATFORM_TOOLS_URL="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
        else
            PLATFORM_TOOLS_URL="https://dl.google.com/android/repository/platform-tools-latest-linux.zip"
        fi
        
        echo "Downloading platform-tools..."
        curl -o /tmp/platform-tools.zip "$PLATFORM_TOOLS_URL"
        unzip -q -o /tmp/platform-tools.zip -d "$ANDROID_HOME/"
        rm /tmp/platform-tools.zip
        print_status "Platform-tools installed via direct download"
    }
    
    echo "Installing Android SDK 34 and build tools..."
    $SDKMANAGER "platforms;android-34" "build-tools;34.0.0" 2>/dev/null || true
    
    print_status "Android SDK components installed"
else
    print_warning "sdkmanager not found, downloading platform-tools directly..."
    
    # Direct download platform-tools
    if [ "$OS" == "macos" ]; then
        PLATFORM_TOOLS_URL="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
    else
        PLATFORM_TOOLS_URL="https://dl.google.com/android/repository/platform-tools-latest-linux.zip"
    fi
    
    echo "Downloading platform-tools..."
    curl -L -o /tmp/platform-tools.zip "$PLATFORM_TOOLS_URL"
    mkdir -p "$ANDROID_HOME"
    unzip -q -o /tmp/platform-tools.zip -d "$ANDROID_HOME/"
    rm /tmp/platform-tools.zip
    print_status "Platform-tools installed"
fi
echo ""

# Update local.properties
echo "Updating local.properties..."
if [ -n "$ANDROID_HOME" ]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    print_status "local.properties updated with SDK path"
else
    print_error "Could not update local.properties - ANDROID_HOME not set"
fi
echo ""

# Check Gradle wrapper
echo "Checking Gradle wrapper..."
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    print_status "Gradle wrapper JAR exists"
else
    print_warning "Gradle wrapper JAR missing. Downloading..."
    mkdir -p gradle/wrapper
    curl -L https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar
    print_status "Gradle wrapper JAR downloaded"
fi

# Make gradlew executable
chmod +x gradlew 2>/dev/null || true
echo ""

# Test Gradle build
echo "Testing Gradle configuration..."
if ./gradlew tasks &> /dev/null; then
    print_status "Gradle configuration is working"
else
    print_warning "Gradle configuration test failed. You may need to configure manually."
fi
echo ""

# Final instructions
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. If this is a fresh install, restart your terminal or run:"
if [ "$OS" == "macos" ]; then
    echo "   source ~/.zshrc"
else
    echo "   source ~/.bashrc"
fi
echo ""
echo "2. Build the app:"
echo "   ./gradlew assembleDebug"
echo ""
echo "3. Install on connected device:"
echo "   ./gradlew installDebug"
echo ""
echo "4. Or open the project in Android Studio"
echo ""
echo "If you encounter issues:"
echo "- Ensure USB debugging is enabled on your Android device"
echo "- Check that your device is connected: adb devices"
echo "- For permission issues, try: sudo chown -R \$(whoami) gradle/"
echo ""
print_status "Happy coding! 🏍️"