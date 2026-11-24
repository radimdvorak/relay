#!/usr/bin/env bash
# Renders the screenshot tests (app/src/test/.../shots) to PNGs on the JVM and prints their paths.
# Usage: scripts/shots.sh [test filter]   e.g. scripts/shots.sh UiShots   or   scripts/shots.sh 'HomeShots.activity'
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}

filter="${1:-}"
out="$here/app/build/shots"
rm -rf "$out"

# A bare class or method name is found anywhere under the shots package.
pattern="dev.relay.android.shots.*"
[ -n "$filter" ] && pattern="dev.relay.android.shots.*${filter}*"

# --rerun: the PNGs are the point, so an up-to-date test task still draws them.
"$here/gradlew" -p "$here" :app:testDebugUnitTest -Pshots --tests "$pattern" --rerun --quiet

find "$out" -name '*.png' | sort
