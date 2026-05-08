#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

APP_NAME="NeuralArc"
MAIN_CLASS="com.neuralarc.NeuralArc"
version_from_gradle() {
  "$PROJECT_DIR/gradlew" -q properties --property version | tail -n 1 | awk '{print $2}'
}

RAW_VERSION="${1:-$(version_from_gradle)}"
APP_VERSION="$(printf '%s' "$RAW_VERSION" | sed -E 's/[^0-9.].*$//')"
DEST_DIR="$PROJECT_DIR/build/installer/linux"
ARTIFACTS_DIR="$PROJECT_DIR/artifacts/linux"
LOGO_PNG="$PROJECT_DIR/src/main/resources/logo.png"

if [[ -z "$APP_VERSION" ]]; then
  echo "Unable to derive a valid numeric app version from [$RAW_VERSION]. Pass an explicit release version, e.g. ./scripts/package-linux.sh 1.0.0" >&2
  exit 1
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage is required and was not found in PATH." >&2
  exit 1
fi

cd "$PROJECT_DIR"

"$PROJECT_DIR/gradlew" clean installDist -PreleaseVersion="$APP_VERSION"
mkdir -p "$DEST_DIR"
mkdir -p "$ARTIFACTS_DIR"

if [[ "$RAW_VERSION" != "$APP_VERSION" ]]; then
  echo "Using package version $APP_VERSION derived from project version $RAW_VERSION"
fi

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
  --type deb \
  --name "$APP_NAME" \
  --dest "$DEST_DIR" \
  --input "$INPUT_DIR" \
  --main-jar "$(basename "$MAIN_JAR")" \
  --main-class "$MAIN_CLASS" \
  --app-version "$APP_VERSION" \
  --icon "$LOGO_PNG" \
  --vendor "NeuralArc" \
  --linux-deb-maintainer "support@Vantashala.com" \
  --linux-menu-group "Office" \
  --linux-shortcut \
  --copyright "Copyright © 2026 NeuralArc | Patent Pending."

FINAL_DEB="$(find "$DEST_DIR" -maxdepth 1 -type f -name "*.deb" | head -n 1)"
if [[ -z "${FINAL_DEB:-}" ]]; then
  echo "Unable to locate generated DEB in $DEST_DIR" >&2
  exit 1
fi

VERSIONED_DEB="$ARTIFACTS_DIR/${APP_NAME}-${APP_VERSION}.deb"
cp -f "$FINAL_DEB" "$VERSIONED_DEB"

echo "Linux installer created in $DEST_DIR"
echo "Release artifact copied to $VERSIONED_DEB"
