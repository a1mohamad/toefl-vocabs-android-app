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

# Time to wait after the activity reports itself displayed, covering the
# harness's own 500 ms navigation delay plus first-frame composition.
SETTLE_SECONDS="${SETTLE_SECONDS:-5}"

# A capture of the launch window — the themed background with nothing drawn on
# it yet — compresses to about 21 KB at this resolution, where every real screen
# lands above 100 KB. Anything under this threshold is treated as "caught the
# app too early" and retried once.
#
# This check exists because a blank frame is exactly what the file count cannot
# catch: the file is there, it is a valid PNG, and it is useless.
MIN_BYTES="${MIN_BYTES:-45000}"

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

    capture_once() {
      adb shell am force-stop "$PACKAGE" || true
      # `-W` blocks until the activity reports itself displayed, which is what
      # makes this robust against a slow cold start rather than hoping a fixed
      # sleep is long enough.
      adb shell am start -W -n "$PACKAGE/.app.MainActivity" \
        -e screenshot "$screen" > /dev/null
      sleep "$SETTLE_SECONDS"
      # `exec-out` rather than `shell` so the PNG is not mangled on the way out
      # of the device.
      adb exec-out screencap -p > "$target"
    }

    capture_once
    size=$(wc -c < "$target")

    if [ "$size" -lt "$MIN_BYTES" ]; then
      echo "  ${screen}-${appearance} looked blank at ${size} bytes, retrying"
      sleep 3
      capture_once
      size=$(wc -c < "$target")
    fi

    if [ "$size" -lt "$MIN_BYTES" ]; then
      echo "::error::${screen}-${appearance} is still blank at ${size} bytes." \
           "The app did not draw — check for a crash on this screen."
      exit 1
    fi

    echo "captured ${screen}-${appearance} (${size} bytes)"
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
