#!/bin/sh
# Runs inside the booted Android emulator supplied by android-emulator-runner.
set -eu

api_level="${1:-}"
case "$api_level" in
  23|27|35) ;;
  *)
    echo "Unsupported emulator API level: $api_level" >&2
    exit 2
    ;;
esac

./gradlew --no-daemon connectedCheck --stacktrace

# API 27 is Android 8.1, the platform in the reported ACRA crash.
# Launch the real app while this emulator is still booted so resource
# inflation failures cannot slip through a compile-only CI run.
if [ "$api_level" = "27" ]; then
  ./gradlew --no-daemon assembleContinuous --stacktrace

  apk="$(find app/build/outputs/apk/continuous -maxdepth 1 -type f -name '*.apk' -print -quit)"
  test -n "$apk"

  package_name="$("$ANDROID_HOME/build-tools/37.0.0/aapt" dump badging "$apk" |
    awk -F"'" '/^package: name=/{print $2; exit}')"
  test -n "$package_name"

  adb install -r "$apk"
  adb shell am force-stop "$package_name" || true
  adb logcat -c
  adb shell am start -W -n "$package_name/org.schabi.newpipe.MainActivity"
  sleep 5

  runtime_log="$(adb logcat -d -s AndroidRuntime:E)"
  if printf '%s\n' "$runtime_log" |
    grep -E 'FATAL EXCEPTION|Unable to start activity|Error inflating class' >/dev/null; then
    printf '%s\n' "$runtime_log"
    exit 1
  fi

  adb shell pidof "$package_name" >/dev/null
fi
