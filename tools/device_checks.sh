#!/usr/bin/env bash
# Phase 13 device checks (video.mp4 is grey with brightness 20 + 4 x (frame mod 50), so a frame
# can be told from its neighbours): makes test media with ffmpeg, copies it into the app's files (run-as),
# runs DeviceChecksTest on the connected emulator/phone (exports and preview frames through the
# app's own pipelines), pulls the results and measures them (tools/device_checks.py).
# Usage: tools/device_checks.sh [--skip-run]   (--skip-run only re-measures pulled files)
set -e
cd "$(dirname "$0")/.."
PKG=com.savie.precut
DEVICE_DIR=files/device_checks # inside the app's data, through run-as
TMP=/data/local/tmp/precut_checks
WORK=build/device_checks
mkdir -p "$WORK/in"

if [ "$1" != "--skip-run" ]; then
  [ -f "$WORK/in/video.mp4" ] || ffmpeg -v error -y \
    -f lavfi -i "color=c=0x808080:s=1280x720:r=30:d=10,format=yuv420p,geq=lum='20+mod(N\\,50)*4':cb=128:cr=128" \
    -f lavfi -i "sine=f=440:d=10:sample_rate=48000" \
    -c:v libx264 -pix_fmt yuv420p -g 30 -crf 12 -c:a aac -b:a 128k -shortest "$WORK/in/video.mp4"
  [ -f "$WORK/in/long.mp4" ] || ffmpeg -v error -y \
    -f lavfi -i "color=c=0x0000ff:s=1280x720:r=30:d=60" -f lavfi -i "anullsrc=r=48000:cl=stereo" \
    -c:v libx264 -pix_fmt yuv420p -g 30 -c:a aac -b:a 64k -t 60 "$WORK/in/long.mp4"
  [ -f "$WORK/in/rotated.mp4" ] || ffmpeg -v error -y -display_rotation 90 -i "$WORK/in/video.mp4" -c copy "$WORK/in/rotated.mp4"
  [ -f "$WORK/in/clicks.m4a" ] || ffmpeg -v error -y \
    -f lavfi -i "aevalsrc='0.8*sin(2*PI*1000*t)*exp(-120*mod(t-0.25\,0.5))*gte(t\,0.25)*lt(mod(t-0.25\,0.5)\,0.03)':s=44100:d=20" \
    -c:a aac -b:a 128k "$WORK/in/clicks.m4a"
  [ -f "$WORK/in/pattern.mp4" ] || ffmpeg -v error -y \
    -f lavfi -i "testsrc2=s=1280x720:r=30:d=10" -c:v libx264 -pix_fmt yuv420p -g 30 -crf 18 "$WORK/in/pattern.mp4"
  [ -f "$WORK/in/tone.m4a" ] || ffmpeg -v error -y \
    -f lavfi -i "sine=f=1000:d=10:sample_rate=48000" -c:a aac -b:a 128k "$WORK/in/tone.m4a"

  # Installed, not run through connectedAndroidTest: that uninstalls the app afterwards, which
  # deletes its data (the media and the results).
  # No daemon: a resident Gradle takes the memory a local emulator needs.
  ./gradlew -q --no-daemon assembleDebug assembleDebugAndroidTest
  adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
  adb shell rm -rf $TMP && adb shell mkdir -p $TMP
  adb push "$WORK/in/." $TMP/ >/dev/null
  adb shell run-as $PKG sh -c "'rm -rf $DEVICE_DIR cache/proxies && mkdir -p $DEVICE_DIR/in && cp $TMP/* $DEVICE_DIR/in/'"
  adb shell am instrument -w -e class com.savie.precut.media.DeviceChecksTest \
    $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee "$WORK/instrument.log" | grep -E "^OK|FAILURES|Tests run"
  rm -rf "$WORK/out" && mkdir -p "$WORK/out"
  for f in $(adb shell run-as $PKG ls $DEVICE_DIR/out | tr -d '\r'); do
    adb exec-out run-as $PKG cat "$DEVICE_DIR/out/$f" > "$WORK/out/$f"
  done
fi

python3 -I tools/device_checks.py "$WORK/out"
