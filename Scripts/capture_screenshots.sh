#!/usr/bin/env bash
#
# Photograph every screen of the app, in light and dark, on a booted emulator.
#
# Why this is a file rather than an inline `script:` block
# -------------------------------------------------------
# reactivecircus/android-emulator-runner runs the `script` input **one line per
# `sh -c` invocation**. Each line therefore gets a fresh shell: a variable
# assigned on one line is gone by the next, and a `for` loop spanning several
# lines is a syntax error in the first of them. The action's own docs steer you
# towards a script file for exactly this reason, and the workflow calls this in
# a single line.
#
# How the screens are reached
# ---------------------------
# The app is relaunched once per screen with a `screenshot` intent extra. The
# debug-only harness (app/.../app/ScreenshotHarness.kt) reads it, seeds
# deterministic progress so Reports and the checklists are never photographed
# empty, and opens straight to that page. No UI automation, so nothing here goes
# stale when a layout changes.

set -euo pipefail

PACKAGE="${PACKAGE:-io.github.a1mohamad.toeflvocab}"
APK="${APK:-app/build/outputs/apk/debug/app-debug.apk}"
OUT_DIR="${OUT_DIR:-screenshots}"

SCREENS="library book section practice practice-revealed summary reports settings about"
APPEARANCES="light dark"

# Long enough for a cold start plus the harness's own 500 ms navigation delay.
SETTLE_SECONDS="${SETTLE_SECONDS:-5}"

echo "Package : $PACKAGE"
echo "APK     : $APK"

if [ ! -f "$APK" ]; then
  echo "::error::APK not found at $APK"
  exit 1
fi

mkdir -p "$OUT_DIR"

adb wait-for-device
adb install -r "$APK"

expected=0

for appearance in $APPEARANCES; do
  if [ "$appearance" = "dark" ]; then
    adb shell cmd uimode night yes
  else
    adb shell cmd uimode night no
  fi
  # The night-mode change restarts the activity; give it a moment before the
  # first force-stop or the relaunch races the reconfiguration.
  sleep 3

  for screen in $SCREENS; do
    expected=$((expected + 1))
    target="$OUT_DIR/${screen}-${appearance}.png"

    adb shell am force-stop "$PACKAGE" || true
    adb shell am start -n "$PACKAGE/.app.MainActivity" -e screenshot "$screen" > /dev/null
    sleep "$SETTLE_SECONDS"

    # `exec-out` rather than `shell` so the PNG is not mangled by CRLF
    # translation on the way out of the device.
    adb exec-out screencap -p > "$target"

    if [ ! -s "$target" ]; then
      echo "::error::$target is empty — screencap produced nothing"
      exit 1
    fi
    echo "captured ${screen}-${appearance} ($(wc -c < "$target") bytes)"
  done
done

count=$(find "$OUT_DIR" -name '*.png' | wc -l | tr -d ' ')
echo "captured $count of $expected screenshots"

# A crash on launch still yields a screenshot — of the launcher — so a plausible
# file count is not proof of success, but a short count is proof of failure.
if [ "$count" -lt "$expected" ]; then
  echo "::error::expected $expected screenshots, got $count"
  exit 1
fi
