#!/usr/bin/env bash
# Allowlisted wrapper for the Android CLI. Deny by default.
set -euo pipefail

log() { printf 'android-safe: %s\n' "$*" >&2; }
deny() { log "DENIED: $*"; exit 64; }

[ "$#" -gt 0 ] || deny "no command"

# Never allow these strings anywhere in the argument list.
for arg in "$@"; do
  case "$arg" in
    *doc.set*|*rm\ -rf*|*adb\ root*|*adb\ remount*|*adb\ reboot*|*fastboot*)
      deny "blocked argument: $arg" ;;
  esac
done

cmd="$1"; shift || true

case "$cmd" in
  info|describe|docs|analyze|layout|screen|build|run|install|preview|create)
    ;;
  emulator)
    sub="${1:-}"
    case "$sub" in
      list|start|stop|create) ;;
      remove) deny "emulator remove requires an out-of-band approval" ;;
      *) deny "emulator $sub" ;;
    esac
    ;;
  sdk)
    sub="${1:-}"
    case "$sub" in
      list) ;;
      install|update)
        [ "${ANDROID_SAFE_CONFIRM_SDK:-0}" = "1" ] ||
          deny "set ANDROID_SAFE_CONFIRM_SDK=1 for sdk $sub"
        ;;
      remove) deny "sdk remove" ;;
      *) deny "sdk $sub" ;;
    esac
    ;;
  studio)
    sub="${1:-}"
    [ "$sub" = "check" ] || deny "studio $sub"
    ;;
  update|auth|skills|signals|device|profiler|completion|init|help)
    deny "$cmd"
    ;;
  *)
    deny "unknown command: $cmd"
    ;;
esac

log "running: android $cmd $*"
exec android "$cmd" "$@"
