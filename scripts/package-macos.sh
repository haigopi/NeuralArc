#!/usr/bin/env bash
set -euo pipefail

# Usage: package-macos.sh [VERSION] [--arch arm64|x64|native|all]
#   native (default)  build for the CPU of this Mac
#   arm64 | x64       build one architecture (Apple Silicon | Intel)
#   all               build both, skipping with a warning an architecture this Mac has no JDK for
#
# A DMG bundles the JDK that runs jpackage, so each architecture needs a JDK of that architecture:
# JAVA_HOME_ARM64 / JAVA_HOME_X64, or one that `/usr/libexec/java_home -a` can find. An Apple Silicon
# Mac can run an x86_64 JDK under Rosetta and so build both; an Intel Mac cannot run an arm64 JDK.
#
# Output: artifacts/macos/NeuralArc-<version>-arm64.dmg and NeuralArc-<version>-x64.dmg. The
# architecture is part of the file name so the website and the in-app updater can pick the right one.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

APP_NAME="NeuralArc"
MAIN_CLASS="com.neuralarc.NeuralArc"
version_from_gradle() {
  "$PROJECT_DIR/gradlew" -q properties --property version | tail -n 1 | awk '{print $2}'
}

VERSION_ARG=""
ARCH_MODE="native"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --arch)
      ARCH_MODE="${2:-}"
      shift 2
      ;;
    -*)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
    *)
      VERSION_ARG="$1"
      shift
      ;;
  esac
done

RAW_VERSION="${VERSION_ARG:-$(version_from_gradle)}"
APP_VERSION="$(printf '%s' "$RAW_VERSION" | sed -E 's/[^0-9.].*$//')"
ARTIFACTS_DIR="$PROJECT_DIR/artifacts/macos"
# Packaging icon source of truth. Keep this aligned with the runtime app icon.
LOGO_PNG="$PROJECT_DIR/src/main/resources/logo.png"
ICONSET_DIR="$PROJECT_DIR/build/jpackage/macos/NeuralArc.iconset"
ICON_ICNS="$PROJECT_DIR/build/jpackage/macos/NeuralArc.icns"

if [[ -z "$APP_VERSION" ]]; then
  echo "Unable to derive a valid numeric app version from [$RAW_VERSION]. Pass an explicit release version, e.g. ./scripts/package-macos.sh 1.0.0" >&2
  exit 1
fi

if ! command -v sips >/dev/null 2>&1 || ! command -v iconutil >/dev/null 2>&1; then
  echo "sips and iconutil are required to generate the macOS installer icon." >&2
  exit 1
fi

host_arch() {
  if [[ "$(sysctl -n hw.optional.arm64 2>/dev/null || echo 0)" == "1" ]]; then
    echo arm64
  else
    echo x64
  fi
}

# Prints the jpackage binary for $1 (arm64|x64), or nothing when this Mac cannot build that architecture.
jpackage_for_arch() {
  local arch="$1"
  local home java_home_arch
  if [[ "$arch" == "arm64" ]]; then
    home="${JAVA_HOME_ARM64:-}"
    java_home_arch="arm64"
  else
    home="${JAVA_HOME_X64:-}"
    java_home_arch="x86_64"
  fi
  if [[ -z "$home" && -x /usr/libexec/java_home ]]; then
    home="$(/usr/libexec/java_home -a "$java_home_arch" -v 21+ 2>/dev/null || true)"
  fi
  # `java_home -a` hands back whatever JDK it has when none matches the architecture, and a DMG
  # bundles the JDK it was built with, so check the binary really is the requested architecture
  # rather than ship, say, an Intel build named arm64.
  if [[ -n "$home" && -x "$home/bin/java" ]] && ! lipo -archs "$home/bin/java" 2>/dev/null | grep -qw "$java_home_arch"; then
    home=""
  fi
  if [[ -n "$home" && -x "$home/bin/jpackage" ]]; then
    # An x86_64 JDK on Apple Silicon cannot run without Rosetta.
    if [[ "$arch" == "x64" && "$(host_arch)" == "arm64" ]] && ! arch -x86_64 /usr/bin/true >/dev/null 2>&1; then
      return 0
    fi
    echo "$home/bin/jpackage"
    return 0
  fi
  if [[ "$arch" == "$(host_arch)" ]] && command -v jpackage >/dev/null 2>&1; then
    local on_path
    on_path="$(command -v jpackage)"
    if lipo -archs "$(dirname "$on_path")/java" 2>/dev/null | grep -qw "$java_home_arch"; then
      echo "$on_path"
    fi
  fi
}

case "$ARCH_MODE" in
  native) ARCHES=("$(host_arch)") ;;
  arm64|x64) ARCHES=("$ARCH_MODE") ;;
  all) ARCHES=(arm64 x64) ;;
  *)
    echo "Unknown --arch [$ARCH_MODE]. Use arm64, x64, native or all." >&2
    exit 1
    ;;
esac

# Resolve every toolchain before the slow build, so a missing JDK fails fast. Under "all" a missing
# architecture is skipped; when it was asked for by name it is an error.
BUILD_ARCHES=()
JPACKAGES=()
for arch in "${ARCHES[@]}"; do
  tool="$(jpackage_for_arch "$arch")"
  if [[ -n "$tool" ]]; then
    BUILD_ARCHES+=("$arch")
    JPACKAGES+=("$tool")
  elif [[ "$ARCH_MODE" == "all" ]]; then
    echo "WARNING: skipping macOS $arch: no $arch JDK 21+ with jpackage runs on this Mac (set JAVA_HOME_$(printf '%s' "$arch" | tr '[:lower:]' '[:upper:]'))." >&2
  else
    echo "jpackage for macOS $arch was not found. Install a $arch JDK 21+ or set JAVA_HOME_$(printf '%s' "$arch" | tr '[:lower:]' '[:upper:]')." >&2
    exit 1
  fi
done
if [[ ${#BUILD_ARCHES[@]} -eq 0 ]]; then
  echo "No macOS architecture can be built on this Mac." >&2
  exit 1
fi

cd "$PROJECT_DIR"

"$PROJECT_DIR/gradlew" clean installDist -PreleaseVersion="$APP_VERSION"
mkdir -p "$ARTIFACTS_DIR"
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

for index in "${!BUILD_ARCHES[@]}"; do
  arch="${BUILD_ARCHES[$index]}"
  # One destination per architecture, emptied first, so one build never picks up another's DMG.
  dest_dir="$PROJECT_DIR/build/installer/macos-$arch"
  rm -rf "$dest_dir"
  mkdir -p "$dest_dir"

  echo "Packaging NeuralArc $APP_VERSION for macOS $arch with ${JPACKAGES[$index]}"
  "${JPACKAGES[$index]}" \
    --type dmg \
    --name "$APP_NAME" \
    --dest "$dest_dir" \
    --input "$INPUT_DIR" \
    --main-jar "$(basename "$MAIN_JAR")" \
    --main-class "$MAIN_CLASS" \
    --app-version "$APP_VERSION" \
    --icon "$ICON_ICNS" \
    --vendor "NeuralArc" \
    --copyright "Copyright © 2026 NeuralArc | Patent Pending."

  built_dmg="$(find "$dest_dir" -maxdepth 1 -type f -name "*.dmg" | head -n 1)"
  if [[ -z "${built_dmg:-}" ]]; then
    echo "Unable to locate generated DMG in $dest_dir" >&2
    exit 1
  fi

  versioned_dmg="$ARTIFACTS_DIR/${APP_NAME}-${APP_VERSION}-${arch}.dmg"
  cp -f "$built_dmg" "$versioned_dmg"
  echo "macOS $arch installer copied to $versioned_dmg"
done
