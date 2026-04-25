#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

APP_NAME="NeuralArc"
MAIN_CLASS="com.neuralarc.NeuralArc"
APP_VERSION="${1:-$("$PROJECT_DIR/gradlew" -q properties --property version | tail -n 1 | awk '{print $2}')}"
DEST_DIR="$PROJECT_DIR/build/installer/macos"

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage is required and was not found in PATH." >&2
  exit 1
fi

cd "$PROJECT_DIR"

"$PROJECT_DIR/gradlew" clean installDist
mkdir -p "$DEST_DIR"

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
  --vendor "NeuralArc" \
  --copyright "Copyright © 2026 NeuralArc. Patent Pending."

echo "macOS installer created in $DEST_DIR"
