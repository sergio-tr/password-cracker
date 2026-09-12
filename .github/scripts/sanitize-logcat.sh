#!/bin/sh
# Sanitize emulator logcat before publishing CI artifacts.
# POSIX sh — android-emulator-runner executes scripts with /usr/bin/sh.
set -eu

src="${1:?source logcat}"
dst="${2:?destination sanitized logcat}"

if [ ! -f "$src" ]; then
  echo "No logcat at $src" > "$dst"
  exit 0
fi

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

echo "Sanitized logcat written to $dst"
