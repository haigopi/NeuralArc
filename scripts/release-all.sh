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
  --version X.Y.Z   Use explicit version (otherwise auto-increments latest release patch)
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

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required. Install it first." >&2
  exit 1
fi

if [[ "$DRY_RUN" == false ]] && ! gh auth status -h github.com >/dev/null 2>&1; then
  echo "gh is not authenticated. Run: gh auth login" >&2
  exit 1
fi

version_from_gradle() {
  "$PROJECT_DIR/gradlew" -q properties --property version | tail -n 1 | awk '{print $2}'
}

normalize_version() {
  printf '%s' "$1" | sed -E 's/^v//; s/[^0-9.].*$//'
}

latest_release_version() {
  if [[ "$DRY_RUN" == true ]]; then
    git tag --list 'v[0-9]*.[0-9]*.[0-9]*' | sed -E 's/^v//' | sort -V | tail -n 1
    return
  fi
  gh release list --limit 100 --json tagName --jq '.[].tagName' \
    | sed -E 's/^v//; s/[^0-9.].*$//' \
    | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' \
    | sort -V \
    | tail -n 1
}

increment_patch_version() {
  local version="$1"
  local major minor patch
  IFS='.' read -r major minor patch <<<"$version"
  major="${major:-0}"
  minor="${minor:-0}"
  patch="${patch:-0}"
  printf '%s.%s.%s\n' "$major" "$minor" "$((patch + 1))"
}

release_exists() {
  local tag="$1"
  [[ "$DRY_RUN" == true ]] && return 1
  gh release view "$tag" >/dev/null 2>&1
}

tag_exists() {
  local tag="$1"
  if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    return 0
  fi
  [[ "$DRY_RUN" == true ]] && return 1
  git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1
}

next_release_version() {
  local latest base candidate
  latest="$(latest_release_version || true)"
  if [[ -n "$latest" ]]; then
    candidate="$(increment_patch_version "$latest")"
  else
    base="$(normalize_version "$(version_from_gradle)")"
    candidate="${base:-1.0.0}"
  fi

  while release_exists "v$candidate" || tag_exists "v$candidate"; do
    candidate="$(increment_patch_version "$candidate")"
  done
  printf '%s\n' "$candidate"
}

previous_release_tag() {
  local current_version="$1"
  local previous=""
  while IFS= read -r candidate; do
    [[ -z "$candidate" ]] && continue
    if [[ "$candidate" == "$current_version" ]]; then
      break
    fi
    previous="$candidate"
  done < <(
    {
      git tag --list 'v[0-9]*.[0-9]*.[0-9]*' | sed -E 's/^v//'
      printf '%s\n' "$current_version"
    } | awk 'NF' | sort -V -u
  )
  if [[ -n "$previous" ]]; then
    printf 'v%s\n' "$previous"
  fi
}

generate_release_notes() {
  local version="$1"
  local tag="$2"
  local notes_file="$PROJECT_DIR/build/release-notes-$version.md"
  local previous_tag
  local range
  local commits

  mkdir -p "$PROJECT_DIR/build"
  previous_tag="$(previous_release_tag "$version" || true)"
  if [[ -n "$previous_tag" ]]; then
    range="$previous_tag..HEAD"
  else
    range="HEAD"
  fi

  commits="$(git --no-pager log --pretty=format:'- %s (%h)' $range || true)"
  if [[ -z "$commits" ]]; then
    commits="- No commit messages found for this release range."
  fi

  cat > "$notes_file" <<EOF
# NeuralArc $tag

## Changes
$commits
EOF
  printf '%s\n' "$notes_file"
}

verify_download_links_point_to_latest_release() {
  local index_file="$PROJECT_DIR/docs/index.html"
  if [[ ! -f "$index_file" ]]; then
    echo "Missing docs/index.html; cannot verify download links." >&2
    exit 1
  fi
  if ! grep -q "https://api.github.com/repos/haigopi/NeuralArc/releases/latest" "$index_file"; then
    echo "docs/index.html must use the GitHub latest release API for download asset resolution." >&2
    exit 1
  fi
  if ! grep -q "https://github.com/haigopi/NeuralArc/releases/latest" "$index_file"; then
    echo "docs/index.html must use /releases/latest as the download fallback URL." >&2
    exit 1
  fi
}

if [[ -n "$EXPLICIT_VERSION" ]]; then
  VERSION="$(normalize_version "$EXPLICIT_VERSION")"
else
  VERSION="$(next_release_version)"
fi

if [[ -z "$VERSION" ]]; then
  echo "Unable to derive a valid numeric release version." >&2
  exit 1
fi

TAG="v$VERSION"
verify_download_links_point_to_latest_release

if [[ "$SKIP_BUILD" == false ]]; then
  run_cmd "$SCRIPT_DIR/build-all.sh" --version "$VERSION"
fi

MAC_DMG="$PROJECT_DIR/artifacts/macos/NeuralArc-$VERSION.dmg"
WIN_EXE="$PROJECT_DIR/artifacts/windows/NeuralArc-$VERSION.exe"
LINUX_DEB="$PROJECT_DIR/artifacts/linux/NeuralArc-$VERSION.deb"
MAC_README="$PROJECT_DIR/artifacts/macos/README-$VERSION.md"
WIN_README="$PROJECT_DIR/artifacts/windows/README-$VERSION.md"
LINUX_README="$PROJECT_DIR/artifacts/linux/README-$VERSION.md"

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

RELEASE_NOTES_FILE="$(generate_release_notes "$VERSION" "$TAG")"
NOTES_ARGS=(--notes-file "$RELEASE_NOTES_FILE")

COMMIT_LINES="$(sed -n '/^## Changes$/,$p' "$RELEASE_NOTES_FILE" | tail -n +2)"
if [[ -z "$COMMIT_LINES" ]]; then
  COMMIT_LINES="- No commit messages found for this release range."
fi

mkdir -p "$PROJECT_DIR/artifacts/macos" "$PROJECT_DIR/artifacts/windows" "$PROJECT_DIR/artifacts/linux"

cat > "$MAC_README" <<EOF
# NeuralArc macOS Release $VERSION

## Artifact
- File: NeuralArc-$VERSION.dmg
- Path: artifacts/macos/NeuralArc-$VERSION.dmg

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd $PROJECT_DIR
  shasum -a 256 artifacts/macos/NeuralArc-$VERSION.dmg

## Changes
$COMMIT_LINES

EOF

cat > "$WIN_README" <<EOF
# NeuralArc Windows Release $VERSION

## Artifact
- File: NeuralArc-$VERSION.exe
- Path: artifacts/windows/NeuralArc-$VERSION.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\\path\\to\\NeuralArc-$VERSION.exe" -Algorithm SHA256

## Changes
$COMMIT_LINES

EOF

cat > "$LINUX_README" <<EOF
# NeuralArc Linux Release $VERSION

## Artifact
- File: NeuralArc-$VERSION.deb
- Path: artifacts/linux/NeuralArc-$VERSION.deb

## Install
1. Install the DEB package (for Debian/Ubuntu-based distributions).
2. Launch NeuralArc from applications menu.

## Verify checksum (optional)
zsh:
  cd $PROJECT_DIR
  sha256sum artifacts/linux/NeuralArc-$VERSION.deb

## Changes
$COMMIT_LINES

EOF

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
