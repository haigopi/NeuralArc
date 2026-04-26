#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

APP_NAME="NeuralArc"
MAIN_CLASS="com.neuralarc.NeuralArc"
RAW_VERSION="${1:-$("$PROJECT_DIR/gradlew" -q properties --property version | tail -n 1 | awk '{print $2}')}"
APP_VERSION="$(printf '%s' "$RAW_VERSION" | sed -E 's/[^0-9.].*$//')"
DEST_DIR="$PROJECT_DIR/build/installer/macos"
LOGO_PNG="$PROJECT_DIR/src/main/resources/logo.png"
ICONSET_DIR="$PROJECT_DIR/build/jpackage/macos/NeuralArc.iconset"
ICON_ICNS="$PROJECT_DIR/build/jpackage/macos/NeuralArc.icns"

if [[ -z "$APP_VERSION" ]]; then
  echo "Unable to derive a valid numeric app version from [$RAW_VERSION]. Pass an explicit release version, e.g. ./package-macos.sh 1.0.0" >&2
  exit 1
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage is required and was not found in PATH." >&2
  exit 1
fi

if ! command -v sips >/dev/null 2>&1 || ! command -v iconutil >/dev/null 2>&1; then
  echo "sips and iconutil are required to generate the macOS installer icon." >&2
  exit 1
fi

cd "$PROJECT_DIR"

"$PROJECT_DIR/gradlew" clean installDist
mkdir -p "$DEST_DIR"
mkdir -p "$(dirname "$ICON_ICNS")"

if [[ "$RAW_VERSION" != "$APP_VERSION" ]]; then
  echo "Using package version $APP_VERSION derived from project version $RAW_VERSION"
fi

rm -rf "$ICONSET_DIR"
mkdir -p "$ICONSET_DIR"

sips -z 16 16     "$LOGO_PNG" --out "$ICONSET_DIR/icon_16x16.png" >/dev/null
sips -z 32 32     "$LOGO_PNG" --out "$ICONSET_DIR/icon_16x16@2x.png" >/dev/null
sips -z 32 32     "$LOGO_PNG" --out "$ICONSET_DIR/icon_32x32.png" >/dev/null
sips -z 64 64     "$LOGO_PNG" --out "$ICONSET_DIR/icon_32x32@2x.png" >/dev/null
sips -z 128 128   "$LOGO_PNG" --out "$ICONSET_DIR/icon_128x128.png" >/dev/null
sips -z 256 256   "$LOGO_PNG" --out "$ICONSET_DIR/icon_128x128@2x.png" >/dev/null
sips -z 256 256   "$LOGO_PNG" --out "$ICONSET_DIR/icon_256x256.png" >/dev/null
sips -z 512 512   "$LOGO_PNG" --out "$ICONSET_DIR/icon_256x256@2x.png" >/dev/null
sips -z 512 512   "$LOGO_PNG" --out "$ICONSET_DIR/icon_512x512.png" >/dev/null
sips -z 1024 1024 "$LOGO_PNG" --out "$ICONSET_DIR/icon_512x512@2x.png" >/dev/null

iconutil -c icns "$ICONSET_DIR" -o "$ICON_ICNS"

DIST_DIR=$(find "$PROJECT_DIR/build/install" -mindepth 1 -maxdepth 1 -type d | head -n 1)
if [[ -z "${DIST_DIR:-}" ]]; then
  echo "Unable to locate installDist output under build/install" >&2
  exit 1
fi

INPUT_DIR="$DIST_DIR/lib"
MAIN_JAR=$(find "$INPUT_DIR" -maxdepth 1 -name "*.jar" | grep -v -- "-plain" | head -n 1)
if [[ -z "${MAIN_JAR:-}" ]]; then
  echo "Unable to locate packaged application jar in $INPUT_DIR" >&2
  exit 1
fi

jpackage \
  --type dmg \
  --name "$APP_NAME" \
  --dest "$DEST_DIR" \
  --input "$INPUT_DIR" \
  --main-jar "$(basename "$MAIN_JAR")" \
  --main-class "$MAIN_CLASS" \
  --app-version "$APP_VERSION" \
  --icon "$ICON_ICNS" \
  --vendor "NeuralArc" \
  --copyright "Copyright © 2026 NeuralArc | Patent Pending."

echo "macOS installer created in $DEST_DIR"
