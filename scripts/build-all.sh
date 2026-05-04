#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

EXPLICIT_VERSION=""
DRY_RUN=false

usage() {
  cat <<'USAGE'
Usage: ./scripts/build-all.sh [options]

Options:
  --version X.Y.Z   Use explicit version (otherwise derived from Gradle)
  --dry-run         Print commands without executing
  --help            Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      EXPLICIT_VERSION="${2:-}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

run_cmd() {
  if [[ "$DRY_RUN" == true ]]; then
    echo "[dry-run] $*"
  else
    "$@"
  fi
}

version_from_gradle() {
  "$PROJECT_DIR/gradlew" -q properties --property version | tail -n 1 | awk '{print $2}'
}

RAW_VERSION="${EXPLICIT_VERSION:-$(version_from_gradle)}"
VERSION="$(printf '%s' "$RAW_VERSION" | sed -E 's/[^0-9.].*$//')"

if [[ -z "$VERSION" ]]; then
  echo "Unable to derive a valid numeric version from [$RAW_VERSION]." >&2
  exit 1
fi

echo "Building NeuralArc artifacts for version $VERSION"

OS_NAME="$(uname -s)"
case "$OS_NAME" in
  Darwin)
    run_cmd "$SCRIPT_DIR/package-macos.sh" "$VERSION"
    echo "macOS artifact done."
    echo "Windows EXE must be built on Windows: ./scripts/package-windows.ps1 $VERSION"
    ;;
  MINGW*|MSYS*|CYGWIN*)
    if command -v pwsh >/dev/null 2>&1; then
      run_cmd pwsh -File "$SCRIPT_DIR/package-windows.ps1" "$VERSION"
    else
      run_cmd powershell -ExecutionPolicy Bypass -File "$SCRIPT_DIR/package-windows.ps1" "$VERSION"
    fi
    echo "Windows artifact done."
    echo "macOS DMG must be built on macOS: ./scripts/package-macos.sh $VERSION"
    ;;
  Linux)
    echo "This host cannot produce DMG/EXE with jpackage." >&2
    echo "Build on macOS for DMG and on Windows for EXE." >&2
    exit 1
    ;;
  *)
    echo "Unsupported host OS [$OS_NAME]." >&2
    exit 1
    ;;
esac

echo "Artifacts directory: $PROJECT_DIR/artifacts"

