#!/bin/bash

echo "🔍 Checking Android Development Environment"
echo "==========================================="
echo ""

# Check Java
echo "Java Version:"
if command -v java &> /dev/null; then
    java -version 2>&1 | head -n 1
    echo "✅ Java is installed"
else
    echo "❌ Java not found"
fi
echo ""

# Check ANDROID_HOME from environment
echo "ANDROID_HOME (from environment):"
if [ -n "$ANDROID_HOME" ]; then
    echo "  $ANDROID_HOME"
    if [ -d "$ANDROID_HOME" ]; then
        echo "  ✅ Directory exists"
    else
        echo "  ❌ Directory does not exist"
    fi
else
    echo "  ❌ Not set"
fi
echo ""

# Check local.properties
echo "SDK Path (from local.properties):"
if [ -f "local.properties" ]; then
    SDK_DIR=$(grep "sdk.dir" local.properties | cut -d'=' -f2)
    echo "  $SDK_DIR"
    if [ -d "$SDK_DIR" ]; then
        echo "  ✅ Directory exists"
        
        # Check for platform-tools
        if [ -d "$SDK_DIR/platform-tools" ]; then
            echo "  ✅ platform-tools found"
            
            # Check for adb
            if [ -f "$SDK_DIR/platform-tools/adb" ]; then
                echo "  ✅ adb executable found"
                
                # Test adb
                echo ""
                echo "Testing ADB:"
                "$SDK_DIR/platform-tools/adb" version
            else
                echo "  ❌ adb executable not found"
            fi
        else
            echo "  ❌ platform-tools not found"
            echo "  Run: sdkmanager 'platform-tools'"
        fi
    else
        echo "  ❌ Directory does not exist"
    fi
else
    echo "  ❌ local.properties not found"
fi
echo ""

# Check PATH
echo "Checking PATH for Android tools:"
if command -v adb &> /dev/null; then
    echo "  ✅ adb is in PATH: $(which adb)"
else
    echo "  ❌ adb not in PATH"
    echo ""
    echo "  To fix this, add to your shell profile (~/.zshrc or ~/.bashrc):"
    if [ -f "local.properties" ]; then
        SDK_DIR=$(grep "sdk.dir" local.properties | cut -d'=' -f2)
        echo "    export ANDROID_HOME=\"$SDK_DIR\""
        echo "    export PATH=\"\$ANDROID_HOME/platform-tools:\$PATH\""
    else
        echo "    export ANDROID_HOME=\"\$HOME/Library/Android/sdk\""
        echo "    export PATH=\"\$ANDROID_HOME/platform-tools:\$PATH\""
    fi
    echo ""
    echo "  Then run: source ~/.zshrc (or source ~/.bashrc)"
fi
echo ""

# Check connected devices
echo "Connected Devices:"
ADB_CMD=""
if command -v adb &> /dev/null; then
    ADB_CMD="adb"
elif [ -f "local.properties" ]; then
    SDK_DIR=$(grep "sdk.dir" local.properties | cut -d'=' -f2)
    if [ -f "$SDK_DIR/platform-tools/adb" ]; then
        ADB_CMD="$SDK_DIR/platform-tools/adb"
    fi
fi

if [ -n "$ADB_CMD" ]; then
    DEVICES=$($ADB_CMD devices | tail -n +2 | grep -v "^$" | wc -l)
    if [ "$DEVICES" -gt 0 ]; then
        echo "  ✅ $DEVICES device(s) connected:"
        $ADB_CMD devices | tail -n +2 | grep -v "^$" | sed 's/^/    /'
    else
        echo "  ⚠️  No devices connected"
        echo "  Make sure USB debugging is enabled on your device"
    fi
else
    echo "  ❌ Cannot check - adb not available"
fi
echo ""

# Summary
echo "==========================================="
echo "Summary:"
if [ -n "$ADB_CMD" ]; then
    echo "✅ Your environment is ready!"
    echo ""
    echo "You can now run: ./build_and_run.sh"
else
    echo "❌ Setup incomplete"
    echo ""
    echo "Please run: ./setup_android_env.sh"
    echo "Then restart your terminal or run:"
    echo "  source ~/.zshrc    (on macOS)"
    echo "  source ~/.bashrc   (on Linux)"
fi