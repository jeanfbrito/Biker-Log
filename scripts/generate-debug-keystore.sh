#!/bin/bash

# Generate a consistent debug keystore for the project
# This ensures all builds can update each other

KEYSTORE_PATH="app/debug.keystore"

if [ -f "$KEYSTORE_PATH" ]; then
    echo "Debug keystore already exists at $KEYSTORE_PATH"
    exit 0
fi

echo "Generating debug keystore..."
keytool -genkey -v \
    -keystore "$KEYSTORE_PATH" \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Biker Log Debug,O=Biker Log,C=US" \
    -noprompt

echo "Debug keystore generated at $KEYSTORE_PATH"
echo "This keystore should be committed to the repository for consistent CI builds"