#!/bin/bash

echo "🏍️ Installing Moto Sensor Logger..."
echo ""

# Gradle handles everything automatically!
./gradlew installDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ App installed! Check your phone."
else
    echo ""
    echo "❌ Installation failed. Make sure:"
    echo "   - Your phone is connected via USB"
    echo "   - USB debugging is enabled"
    echo "   - You have Java 17+ installed"
fi