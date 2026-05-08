#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/encrypt-mailjet-secret.sh --key
  ./scripts/encrypt-mailjet-secret.sh --secret

Optional override:
  NEURALARC_MAILJET_PASSPHRASE='passphrase' ./scripts/encrypt-mailjet-secret.sh --key

The script reads the plaintext value from a hidden prompt and prints only the
encrypted value. Paste the result into app.properties:
  mailjet.api.key.encrypted=<encrypted key>
  mailjet.api.secret.encrypted=<encrypted secret>
USAGE
}

if [[ $# -ne 1 || "$1" != "--key" && "$1" != "--secret" ]]; then
  usage >&2
  exit 1
fi

LABEL="Mailjet API key"
if [[ "$1" == "--secret" ]]; then
  LABEL="Mailjet API secret"
fi

printf "Enter %s: " "$LABEL" >&2
restore_echo() {
  stty echo
}
trap restore_echo EXIT
stty -echo
IFS= read -r PLAINTEXT
restore_echo
trap - EXIT
printf "\n" >&2

if [[ -z "$PLAINTEXT" ]]; then
  echo "No value entered." >&2
  exit 1
fi

"$PROJECT_DIR/gradlew" -q compileJava >/dev/null

printf "%s" "$PLAINTEXT" | java -cp "$PROJECT_DIR/build/classes/java/main" com.neuralarc.util.SecretEncryptCli
