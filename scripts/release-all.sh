#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

EXPLICIT_VERSION=""
PUBLISH=false
SKIP_BUILD=false
DRY_RUN=false

usage() {
  cat <<'USAGE'
Usage: ./scripts/release-all.sh [options]

Options:
  --version X.Y.Z   Use explicit version (otherwise derived from Gradle)
  --publish         Publish release (default creates/keeps draft)
  --skip-build      Skip build step and only publish existing artifacts
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
    --publish)
      PUBLISH=true
      shift
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
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

if [[ "$SKIP_BUILD" == false ]]; then
  run_cmd "$SCRIPT_DIR/build-all.sh" --version "$VERSION"
fi

TAG="v$VERSION"
MAC_DMG="$PROJECT_DIR/artifacts/macos/NeuralArc-$VERSION.dmg"
WIN_EXE="$PROJECT_DIR/artifacts/windows/NeuralArc-$VERSION.exe"
MAC_README="$PROJECT_DIR/artifacts/macos/README-$VERSION.md"
WIN_README="$PROJECT_DIR/artifacts/windows/README-$VERSION.md"

MISSING_ASSETS=()
[[ ! -f "$MAC_DMG" ]] && MISSING_ASSETS+=("$MAC_DMG")
[[ ! -f "$WIN_EXE" ]] && MISSING_ASSETS+=("$WIN_EXE")

if [[ ${#MISSING_ASSETS[@]} -gt 0 ]]; then
  echo "Both artifacts are required before release." >&2
  for missing in "${MISSING_ASSETS[@]}"; do
    echo "  missing: $missing" >&2
  done
  if [[ "$DRY_RUN" == false ]]; then
    exit 1
  fi
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required. Install it first." >&2
  exit 1
fi

if [[ "$DRY_RUN" == false ]] && ! gh auth status -h github.com >/dev/null 2>&1; then
  echo "gh is not authenticated. Run: gh auth login" >&2
  exit 1
fi

NOTES_ARGS=(--notes "Release $TAG")
if [[ -f "$MAC_README" ]]; then
  NOTES_ARGS=(--notes-file "$MAC_README")
elif [[ -f "$WIN_README" ]]; then
  NOTES_ARGS=(--notes-file "$WIN_README")
fi

cd "$PROJECT_DIR"

if [[ "$DRY_RUN" == true ]]; then
  echo "[dry-run] gh release view $TAG"
  if [[ "$PUBLISH" == true ]]; then
    echo "[dry-run] gh release create $TAG --title NeuralArc $TAG ${NOTES_ARGS[*]}"
  else
    echo "[dry-run] gh release create $TAG --title NeuralArc $TAG --draft ${NOTES_ARGS[*]}"
  fi
  echo "[dry-run] gh release upload $TAG --clobber $MAC_DMG $WIN_EXE"
  exit 0
fi

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release $TAG already exists. Uploading artifacts with --clobber..."
  gh release upload "$TAG" --clobber "$MAC_DMG" "$WIN_EXE"
else
  echo "Creating release $TAG..."
  if [[ "$PUBLISH" == true ]]; then
    gh release create "$TAG" --title "NeuralArc $TAG" "${NOTES_ARGS[@]}"
  else
    gh release create "$TAG" --title "NeuralArc $TAG" --draft "${NOTES_ARGS[@]}"
  fi
  gh release upload "$TAG" --clobber "$MAC_DMG" "$WIN_EXE"
fi

echo "Release completed for $TAG"

