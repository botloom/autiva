#!/bin/bash
set -euo pipefail

PROJECT="$(cd "$(dirname "$0")" && pwd)"
MVN="${MVN:-mvn}"

# Use JDK 25 to match the project's Java version
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using JDK: $JAVA_HOME"
java -version

JDK_MODULES="java.base,java.desktop,java.net.http,java.logging,java.xml,java.sql,java.naming,java.management,java.instrument,java.scripting,java.prefs,java.security.sasl,java.transaction.xa,jdk.jsobject,jdk.unsupported,jdk.crypto.ec,jdk.xml.dom"

echo -e "\033[32m=== Step 1: Build JAR ===\033[0m"
$MVN package -pl autiva-desktop -am -DskipTests
$MVN dependency:copy-dependencies -pl autiva-desktop -DincludeScope=runtime -DexcludeArtifactIds=lombok

echo -e "\033[32m=== Step 2: Create custom JRE ===\033[0m"
JLINK_OUTPUT="$PROJECT/autiva-desktop/target/custom-jre"
rm -rf "$JLINK_OUTPUT"

DEP_DIR="$PROJECT/autiva-desktop/target/dependency"
JAVAFX_MODULE_PATH=$(ls "$DEP_DIR"/javafx-*.jar 2>/dev/null | tr '\n' ':')
JAVAFX_MODULES="javafx.controls,javafx.fxml,javafx.swing"
ALL_MODULES="$JDK_MODULES,$JAVAFX_MODULES"

jlink \
  --module-path "$JAVAFX_MODULE_PATH" \
  --add-modules "$ALL_MODULES" \
  --output "$JLINK_OUTPUT" \
  --strip-debug \
  --no-man-pages \
  --no-header-files \
  --compress=2

echo -e "\033[36m  JRE size: $(du -sh "$JLINK_OUTPUT" | cut -f1)\033[0m"

echo -e "\033[32m=== Step 3: Prepare jpackage input ===\033[0m"
JPACKAGE_INPUT="$PROJECT/autiva-desktop/target/jpackage-input"
rm -rf "$JPACKAGE_INPUT"
mkdir -p "$JPACKAGE_INPUT"

cp autiva-desktop/target/autiva-desktop-1.0-SNAPSHOT.jar "$JPACKAGE_INPUT/"
for jar in "$DEP_DIR"/*.jar; do
    basename=$(basename "$jar")
    if [[ ! "$basename" =~ ^javafx-.*\.jar$ ]]; then
        cp "$jar" "$JPACKAGE_INPUT/"
    fi
done

# Remove platform-irrelevant JARs
for pattern in "netty-transport-native-epoll-*.jar" "netty-transport-classes-epoll-*.jar" "netty-transport-native-windows-*.jar" "netty-transport-native-kqueue-*.jar"; do
    rm -f "$JPACKAGE_INPUT"/$pattern
done

# Generate icon
ICON_SRC="$PROJECT/autiva-desktop/src/main/resources/cn/bitloom/images/icon.png"
ICON_ICNS="$JPACKAGE_INPUT/icon.icns"
ICONSET_DIR=$(mktemp -d)/icon.iconset
mkdir -p "$ICONSET_DIR"
for s in 16 32 64 128 256 512; do
    sips -z "$s" "$s" "$ICON_SRC" --out "$ICONSET_DIR/icon_${s}x${s}.png" >/dev/null 2>&1
done
sips -z 32 32 "$ICON_SRC" --out "$ICONSET_DIR/icon_16x16@2x.png" >/dev/null 2>&1
sips -z 64 64 "$ICON_SRC" --out "$ICONSET_DIR/icon_32x32@2x.png" >/dev/null 2>&1
sips -z 128 128 "$ICON_SRC" --out "$ICONSET_DIR/icon_64x64@2x.png" >/dev/null 2>&1
sips -z 256 256 "$ICON_SRC" --out "$ICONSET_DIR/icon_128x128@2x.png" >/dev/null 2>&1
sips -z 512 512 "$ICON_SRC" --out "$ICONSET_DIR/icon_256x256@2x.png" >/dev/null 2>&1
sips -z 1024 1024 "$ICON_SRC" --out "$ICONSET_DIR/icon_512x512@2x.png" >/dev/null 2>&1
iconutil -c icns "$ICONSET_DIR" -o "$ICON_ICNS"
rm -rf "$(dirname "$ICONSET_DIR")"

echo -e "\033[32m=== Step 4: Build app ===\033[0m"
rm -rf autiva-desktop/target/jpackage-output
jpackage \
  --type app-image \
  --name Autiva \
  --input autiva-desktop/target/jpackage-input \
  --main-jar autiva-desktop-1.0-SNAPSHOT.jar \
  --main-class cn.bitloom.AutivaApplication \
  --runtime-image "$JLINK_OUTPUT" \
  --java-options "-Xms128m" \
  --java-options "-Xmx1024m" \
  --java-options "-Dprism.order=es2,sw" \
  --java-options "-Dprism.maxvram=200M" \
  --java-options "-Dprism.forceGPU=false" \
  --dest autiva-desktop/target/jpackage-output \
  --app-version 1.0.0 \
  --vendor "Bitloom" \
  --icon "$ICON_ICNS"

echo -e "\033[32m=== Step 5: Generate CDS archive ===\033[0m"
APP_DIR="$PROJECT/autiva-desktop/target/jpackage-output/Autiva.app"
CDS_ARCHIVE="$APP_DIR/Contents/app/app-cds.jsa"
APP_LAUNCHER="$APP_DIR/Contents/MacOS/Autiva"

"$APP_LAUNCHER" "-XX:ArchiveClassesAtExit=$CDS_ARCHIVE" &
APP_PID=$!
sleep 15
if kill -0 "$APP_PID" 2>/dev/null; then
    sleep 5
    kill "$APP_PID"
    wait "$APP_PID" 2>/dev/null || true
fi

[ -f "$CDS_ARCHIVE" ] && echo -e "\033[36m  CDS archive: $(du -sh "$CDS_ARCHIVE" | cut -f1)\033[0m"

echo -e "\033[32m=== Step 6: Create DMG ===\033[0m"
DMG_OUTPUT="$PROJECT/autiva-desktop/target/jpackage-output"
DMG_FILE="$DMG_OUTPUT/Autiva-1.0.0.dmg"
DMG_TEMP="$PROJECT/autiva-desktop/target/dmg-temp"
DMG_RAW="$DMG_OUTPUT/Autiva-raw.dmg"

# Cleanup
rm -rf "$DMG_TEMP"
rm -f "$DMG_RAW" "$DMG_FILE"
hdiutil info | grep -q "/Volumes/Autiva" && hdiutil detach "/Volumes/Autiva" -force 2>/dev/null || true

mkdir -p "$DMG_TEMP"
cp -R "$APP_DIR" "$DMG_TEMP/"
ln -sf /Applications "$DMG_TEMP/Applications"

# Create read-write DMG first
hdiutil create -volname "Autiva" -srcfolder "$DMG_TEMP" -ov -format UDRW "$DMG_RAW"
rm -rf "$DMG_TEMP"

# Mount and configure window
MOUNT_DIR=$(hdiutil attach "$DMG_RAW" -readwrite -noverify -noautoopen | grep "/Volumes/Autiva" | awk '{print $NF}')

if [ -n "$MOUNT_DIR" ]; then
  # Set window layout using AppleScript
  osascript << 'APPLESCRIPT'
tell application "Finder"
  tell disk "Autiva"
    open
    set current view of container window to icon view
    set the bounds of container window to {400, 100, 900, 400}
    set theViewOptions to the icon view options of container window
    set arrangement of theViewOptions to not arranged
    set icon size of theViewOptions to 100
    set position of item "Autiva.app" of container window to {100, 150}
    set position of item "Applications" of container window to {300, 150}
    close container window
  end tell
end tell
APPLESCRIPT

  # Make sure changes are saved
  sync
  sleep 2
  hdiutil detach "$MOUNT_DIR"
fi

# Convert to compressed read-only DMG
hdiutil convert "$DMG_RAW" -format UDZO -imagekey zlib-level=9 -o "$DMG_FILE"
rm -f "$DMG_RAW"

echo ""
echo -e "\033[32m=== Build Complete ===\033[0m"
echo "DMG: $DMG_FILE ($(du -sh "$DMG_FILE" | cut -f1))"
echo "App: $APP_DIR ($(du -sh "$APP_DIR" | cut -f1))"