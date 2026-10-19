#!/usr/bin/env bash
# Rough memory check for the performance pass: clean start, open the first draft, scroll the
# timeline back and forth, then print the app's memory (dumpsys meminfo). Needs adb and an
# emulator/phone with one draft. Usage: tools/perf_timeline.sh [label]
set -e
cd "$(dirname "$0")/.."
PKG=com.savie.precut
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.MainActivity >/dev/null
# Wait for the Projects screen (slow emulators take a while), then open the draft.
for i in $(seq 1 20); do python3 -I tools/ui.py tap "Edited" >/dev/null 2>&1 && break; sleep 2; done
sleep 12
# The timeline strip sits about 2/3 down the screen.
Y=$(( $(adb shell wm size | grep -o '[0-9]*$') * 66 / 100 ))
for i in $(seq 1 6); do
  adb shell input swipe 900 $Y 150 $Y 250
  sleep 1
  adb shell input swipe 150 $Y 900 $Y 250
  sleep 1
done
sleep 3
echo "== ${1:-run} $(date +%T)"
adb shell dumpsys meminfo $PKG | grep -E "Java Heap|Native Heap|Graphics|Private Other|TOTAL PSS|TOTAL:" | sed 's/^ */  /'
