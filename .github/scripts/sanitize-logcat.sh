#!/usr/bin/env bash
# Sanitize emulator logcat before publishing CI artifacts.
# Strips lines that look like secrets, ciphertext blobs, or clipboard dumps.
set -euo pipefail

src="${1:?source logcat}"
dst="${2:?destination sanitized logcat}"

if [[ ! -f "$src" ]]; then
  echo "No logcat at $src" > "$dst"
  exit 0
fi

# Patterns: vault prefs, synthetic instrumentation secrets, redaction markers misuse,
# long base64-ish blobs typical of ciphertext, clipboard dumps.
grep -Eiv \
  -e 'instrumentation-secret' \
  -e 'ui-test-secret' \
  -e 'lab-test-secret' \
  -e 'NetworkSecret\(' \
  -e 'secret_vault' \
  -e 'ClipboardManager' \
  -e 'clipdata' \
  -e 'password' \
  -e 'passphrase' \
  -e '[A-Za-z0-9+/]{64,}={0,2}' \
  "$src" > "$dst" || true

echo "Sanitized logcat written to $dst ($(wc -l < "$dst") lines retained)"
