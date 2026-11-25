#!/usr/bin/env bash
# Runs ./gradlew in one of a few machine-wide slots, so parallel worktrees don't all build at once
# and swap the laptop to death. Usage: scripts/gradle-slot.sh :app:compileDebugKotlin
set -euo pipefail

SLOTS=${RELAY_GRADLE_SLOTS:-3}
here="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}

slot=""
waited=0
while [ -z "$slot" ]; do
  for i in $(seq 1 "$SLOTS"); do
    dir="/tmp/relay-gradle-slot-$i"
    if mkdir "$dir" 2>/dev/null; then
      echo "$$ $here" > "$dir/owner"
      slot="$dir"
      break
    fi
    # A slot whose owner died is free again.
    owner=$(cut -d' ' -f1 "$dir/owner" 2>/dev/null || true)
    if [ -n "$owner" ] && ! kill -0 "$owner" 2>/dev/null; then rm -rf "$dir"; fi
  done
  if [ -z "$slot" ]; then
    [ $((waited % 60)) -eq 0 ] && echo "gradle-slot: all $SLOTS slots busy, waiting…" >&2
    sleep 5
    waited=$((waited + 5))
  fi
done
trap 'rm -rf "$slot"' EXIT

cd "$here"
./gradlew "$@"
