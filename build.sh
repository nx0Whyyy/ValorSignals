#!/bin/bash
# Build script for ValorSky-SkySignals
# Creates a clean distribution with the plugin JAR and config

set -e

echo "=== Building ValorSky-SkySignals ==="

# Clean previous build
echo "Cleaning previous build..."
./gradlew clean

# Build and run tests
echo "Compiling sources and running tests..."
./gradlew test

# Create distribution
echo "Creating distribution..."
./gradlew dist

echo ""
echo "=== Build complete ==="
echo "Distribution ZIP: build/distributions/ValorSky-SkySignals.zip"
echo "Shaded JAR: build/libs/ValorSky-SkySignals-*.jar"
