#!/usr/bin/env bash
# Fail if a non-negotiable HerdrOid invariant is broken.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

fail=0
check_absent() {
  local label="$1"; shift
  if rg -n "$@" app/src/main core/*/src/main 2>/dev/null; then
    echo "FAIL: $label" >&2
    fail=1
  else
    echo "ok: $label"
  fi
}

check_absent "no doc.set in app/core main sources" 'doc\.set'
check_absent "no WebView/xterm.js in main sources" 'WebView|android\.webkit|xterm\.js'
check_absent "no cleartext traffic opt-in" 'usesCleartextTraffic="true"'
check_absent "no analytics/telemetry SDKs" 'firebase|crashlytics|google-analytics|appcenter'
check_absent "no host-key checking disabled" 'StrictHostKeyChecking[^\\n]*"no"'

if rg -n 'allowBackup="true"' app/src/main/AndroidManifest.xml 2>/dev/null; then
  echo "FAIL: Android backup must stay disabled" >&2
  fail=1
else
  echo "ok: Android backup disabled"
fi

if [ "$fail" -ne 0 ]; then
  exit 1
fi
echo "all invariants hold"
