#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

EXPLICIT_VERSION=""
DRAFT=false
SKIP_BUILD=false
DRY_RUN=false
SUBMIT_MACOS_SCAN=false

if [[ "${MALWARE_SCAN_AUTO_SUBMIT:-false}" == "true" ]]; then
  SUBMIT_MACOS_SCAN=true
fi

usage() {
  cat <<'USAGE'
Usage: ./scripts/release-all.sh [options]

Options:
  --version X.Y.Z   Use explicit version (otherwise auto-increments latest release patch)
  --draft           Create or keep release as draft (default publishes)
  --publish         Publish release (default; kept for compatibility)
  --skip-build      Skip build step and only publish existing artifacts
  --submit-macos-scan  Submit latest macOS artifact for malware scanning before release publish
  --skip-macos-scan    Skip malware scan submission even when MALWARE_SCAN_AUTO_SUBMIT=true
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
    --submit-macos-scan)
      SUBMIT_MACOS_SCAN=true
      shift
      ;;
    --skip-macos-scan)
      SUBMIT_MACOS_SCAN=false
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

file_mtime_epoch() {
  local file="$1"
  if stat -f "%m" "$file" >/dev/null 2>&1; then
    stat -f "%m" "$file"
  else
    stat -c "%Y" "$file"
  fi
}

resolve_latest_macos_artifact_for_version() {
  local version="$1"
  local latest=""
  local latest_mtime=0
  local candidate=""
  local mtime=0

  shopt -s nullglob
  for candidate in "$PROJECT_DIR"/artifacts/macos/NeuralArc-"$version"*.dmg; do
    [[ -f "$candidate" ]] || continue
    mtime="$(file_mtime_epoch "$candidate")"
    if [[ -z "$latest" || "$mtime" -gt "$latest_mtime" ]]; then
      latest="$candidate"
      latest_mtime="$mtime"
    fi
  done
  shopt -u nullglob

  if [[ -n "$latest" ]]; then
    printf '%s\n' "$latest"
  fi
}

submit_macos_artifact_for_malware_scan() {
  local artifact="$1"
  local version="$2"
  local tag="$3"
  local api_url="${MALWARE_SCAN_API_URL:-}"
  local auth_header_name="${MALWARE_SCAN_AUTH_HEADER_NAME:-Authorization}"
  local auth_header_value="${MALWARE_SCAN_AUTH_HEADER_VALUE:-}"
  local token="${MALWARE_SCAN_API_TOKEN:-}"
  local profile_id="${MALWARE_SCAN_PROFILE_ID:-}"
  local response_file="$PROJECT_DIR/build/malware-scan-submission-$version.json"
  local artifact_name
  artifact_name="$(basename "$artifact")"

  if [[ ! -f "$artifact" ]]; then
    echo "macOS artifact not found for malware scan submission: $artifact" >&2
    exit 1
  fi
  if [[ -z "$api_url" ]]; then
    echo "MALWARE_SCAN_API_URL is required when --submit-macos-scan is enabled." >&2
    exit 1
  fi
  if [[ -z "$auth_header_value" ]]; then
    if [[ -z "$token" ]]; then
      echo "Set MALWARE_SCAN_API_TOKEN or MALWARE_SCAN_AUTH_HEADER_VALUE when --submit-macos-scan is enabled." >&2
      exit 1
    fi
    auth_header_value="Bearer $token"
  fi
  if ! command -v curl >/dev/null 2>&1; then
    echo "curl is required for malware scan submission." >&2
    exit 1
  fi

  mkdir -p "$PROJECT_DIR/build"
  if [[ "$DRY_RUN" == true ]]; then
    echo "[dry-run] submit macOS malware scan for $artifact_name to $api_url"
    echo "[dry-run] curl --fail --silent --show-error -X POST \"$api_url\" -H \"$auth_header_name: [REDACTED]\" -F \"file=@$artifact\" -F \"artifactName=$artifact_name\" -F \"version=$version\" -F \"tag=$tag\" -o \"$response_file\""
    if [[ -n "$profile_id" ]]; then
      echo "[dry-run] additional form field: profileId=$profile_id"
    fi
    return
  fi

  echo "Submitting macOS artifact for malware scan: $artifact_name"
  if [[ -n "$profile_id" ]]; then
    curl --fail --silent --show-error \
      -X POST "$api_url" \
      -H "$auth_header_name: $auth_header_value" \
      -F "file=@$artifact" \
      -F "artifactName=$artifact_name" \
      -F "version=$version" \
      -F "tag=$tag" \
      -F "profileId=$profile_id" \
      -o "$response_file"
  else
    curl --fail --silent --show-error \
      -X POST "$api_url" \
      -H "$auth_header_name: $auth_header_value" \
      -F "file=@$artifact" \
      -F "artifactName=$artifact_name" \
      -F "version=$version" \
      -F "tag=$tag" \
      -o "$response_file"
  fi
  echo "Malware scan submission response saved to: $response_file"
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

MAC_DMG="$(resolve_latest_macos_artifact_for_version "$VERSION")"
if [[ -z "$MAC_DMG" ]]; then
  MAC_DMG="$PROJECT_DIR/artifacts/macos/NeuralArc-$VERSION.dmg"
fi
WIN_EXE="$PROJECT_DIR/artifacts/windows/NeuralArc-$VERSION.exe"
LINUX_DEB="$PROJECT_DIR/artifacts/linux/NeuralArc-$VERSION.deb"
MAC_README="$PROJECT_DIR/artifacts/macos/README-$VERSION.md"
WIN_README="$PROJECT_DIR/artifacts/windows/README-$VERSION.md"
LINUX_README="$PROJECT_DIR/artifacts/linux/README-$VERSION.md"
MAC_ARTIFACT_NAME="$(basename "$MAC_DMG")"

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

if [[ "$SUBMIT_MACOS_SCAN" == true ]]; then
  if [[ ! -f "$MAC_DMG" ]]; then
    echo "--submit-macos-scan is enabled but macOS artifact is missing: $MAC_DMG" >&2
    exit 1
  fi
  submit_macos_artifact_for_malware_scan "$MAC_DMG" "$VERSION" "$TAG"
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
- File: $MAC_ARTIFACT_NAME
- Path: artifacts/macos/$MAC_ARTIFACT_NAME

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd $PROJECT_DIR
  shasum -a 256 artifacts/macos/$MAC_ARTIFACT_NAME

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
