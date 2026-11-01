#!/usr/bin/env bash
# Source this only when launching a scratch hc/core for tests.
set -u

export HC_SESSION="herdroid-test"
export XDG_CONFIG_HOME="/tmp/herdroid/hc-config"
export XDG_STATE_HOME="/tmp/herdroid/hc-state"
mkdir -p "$XDG_CONFIG_HOME" "$XDG_STATE_HOME"
printf 'HC_SESSION=%s\nXDG_CONFIG_HOME=%s\nXDG_STATE_HOME=%s\n' \
  "$HC_SESSION" "$XDG_CONFIG_HOME" "$XDG_STATE_HOME"
