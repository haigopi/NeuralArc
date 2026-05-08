#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

EXPLICIT_VERSION=""
DRAFT=false
SKIP_BUILD=false
DRY_RUN=false

usage() {
  cat <<'USAGE'
Usage: ./scripts/release-all.sh [options]

Options:
  --version X.Y.Z   Use explicit version (otherwise derived from Gradle)
  --draft           Create or keep release as draft (default publishes)
  --publish         Publish release (default; kept for compatibility)
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
      DRAFT=false
      shift
      ;;
    --draft)
      DRAFT=true
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
LINUX_DEB="$PROJECT_DIR/artifacts/linux/NeuralArc-$VERSION.deb"
MAC_README="$PROJECT_DIR/artifacts/macos/README-$VERSION.md"
WIN_README="$PROJECT_DIR/artifacts/windows/README-$VERSION.md"

ASSETS=()
MISSING_ASSETS=()
for candidate in "$MAC_DMG" "$WIN_EXE" "$LINUX_DEB"; do
  if [[ -f "$candidate" ]]; then
    ASSETS+=("$candidate")
  else
    MISSING_ASSETS+=("$candidate")
  fi
done

if [[ ${#ASSETS[@]} -eq 0 ]]; then
  echo "No release artifacts found for $TAG." >&2
  echo "Expected one or more of:" >&2
  for missing in "${MISSING_ASSETS[@]}"; do
    echo "  $missing" >&2
  done
  exit 1
fi

if [[ ${#MISSING_ASSETS[@]} -gt 0 ]]; then
  echo "Continuing with ${#ASSETS[@]} built artifact(s). Missing artifact(s):" >&2
  for missing in "${MISSING_ASSETS[@]}"; do
    echo "  $missing" >&2
  done
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

ensure_git_tag() {
  if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    echo "Git tag $TAG already exists locally."
  else
    run_cmd git tag -a "$TAG" -m "NeuralArc $TAG"
  fi

  if [[ "$DRY_RUN" == true ]]; then
    echo "[dry-run] git ls-remote --exit-code --tags origin refs/tags/$TAG"
    echo "[dry-run] git push origin $TAG"
    return
  fi

  if git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
    echo "Git tag $TAG already exists on origin."
  else
    git push origin "$TAG"
  fi
}

if [[ "$DRY_RUN" == true ]]; then
  ensure_git_tag
  echo "[dry-run] gh release view $TAG"
  if [[ "$DRAFT" == true ]]; then
    echo "[dry-run] gh release create $TAG ${ASSETS[*]} --title NeuralArc $TAG --draft ${NOTES_ARGS[*]}"
    echo "[dry-run] if release exists: gh release upload $TAG --clobber ${ASSETS[*]}"
    echo "[dry-run] if release exists: gh release edit $TAG --title NeuralArc $TAG --draft ${NOTES_ARGS[*]}"
  else
    echo "[dry-run] gh release create $TAG ${ASSETS[*]} --title NeuralArc $TAG --verify-tag --latest ${NOTES_ARGS[*]}"
    echo "[dry-run] if release exists: gh release upload $TAG --clobber ${ASSETS[*]}"
    echo "[dry-run] if release exists: gh release edit $TAG --title NeuralArc $TAG --draft=false --latest ${NOTES_ARGS[*]}"
  fi
  exit 0
fi

ensure_git_tag

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release $TAG already exists. Uploading artifacts with --clobber and updating release metadata..."
  gh release upload "$TAG" --clobber "${ASSETS[@]}"
  if [[ "$DRAFT" == true ]]; then
    gh release edit "$TAG" --title "NeuralArc $TAG" --draft "${NOTES_ARGS[@]}"
  else
    gh release edit "$TAG" --title "NeuralArc $TAG" --draft=false --latest "${NOTES_ARGS[@]}"
  fi
else
  echo "Creating release $TAG..."
  if [[ "$DRAFT" == true ]]; then
    gh release create "$TAG" "${ASSETS[@]}" --title "NeuralArc $TAG" --draft "${NOTES_ARGS[@]}"
  else
    gh release create "$TAG" "${ASSETS[@]}" --title "NeuralArc $TAG" --verify-tag --latest "${NOTES_ARGS[@]}"
  fi
fi

echo "Release completed for $TAG with ${#ASSETS[@]} artifact(s)."
