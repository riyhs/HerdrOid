#!/usr/bin/env bash
# Source this file: . scripts/env.sh
set -u

if command -v cygpath >/dev/null 2>&1; then
  ANDROID_HOME="${ANDROID_HOME:-$(cygpath -u "${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk")}"
else
  ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

printf 'ANDROID_HOME=%s\nPATH has adb: ' "$ANDROID_HOME"
command -v adb || true
printf 'JAVA_HOME=%s\n' "${JAVA_HOME:-unset}"
