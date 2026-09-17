@echo off
REM Build script for ValorSky-SkySignals
REM Creates a clean distribution with the plugin JAR and config

setlocal enabledelayedexpansion

echo === Building ValorSky-SkySignals ===

echo Cleaning previous build...
gradlew clean

echo Compiling sources and running tests...
gradlew test

echo Creating distribution...
gradlew dist

echo.
echo === Build complete ===
echo Distribution ZIP: build\distributions\ValorSky-SkySignals-2.0.0.zip
echo Shaded JAR: build\libs\ValorSky-SkySignals-2.0.0.jar

endlocal
