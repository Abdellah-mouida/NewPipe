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

run_gradle_with_dependency_retries() {
  task="$1"
  shift
  attempt=1
  log_file="${RUNNER_TEMP:-/tmp}/gradle-emulator-${api_level}.log"

  while [ "$attempt" -le 4 ]; do
    echo "Gradle $task attempt $attempt/4"
    if [ "$attempt" -eq 1 ]; then
      if ./gradlew --no-daemon "$task" "$@" >"$log_file" 2>&1; then
        result=0
      else
        result=$?
      fi
    else
      if ./gradlew --no-daemon --refresh-dependencies "$task" "$@" >"$log_file" 2>&1; then
        result=0
      else
        result=$?
      fi
    fi
    cat "$log_file"

    if [ "$result" -eq 0 ]; then
      return 0
    fi

    # Retry only transient repository failures. Real test or source failures
    # must fail immediately rather than being hidden by another attempt.
    if ! grep -Eiq \
      'status code 429|HTTP 429|Too Many Requests|status code 5(02|03|04)|HTTP 5(02|03|04)|Could not resolve all files for configuration|ModuleVersionNotFoundException|Could not GET|Read timed out|Connection reset|UnknownHostException' \
      "$log_file"; then
      return "$result"
    fi

    if [ "$attempt" -eq 4 ]; then
      echo "Dependency resolution failed after four attempts." >&2
      return "$result"
    fi

    delay=$((20 * (2 ** (attempt - 1))))
    echo "Transient dependency lookup failure; retrying with refreshed metadata in ${delay}s."
    sleep "$delay"
    attempt=$((attempt + 1))
  done
}

run_gradle_with_dependency_retries connectedCheck --stacktrace

# API 27 is Android 8.1, the platform in the reported ACRA crash.
# Build and launch the real app here so resource inflation failures cannot
# slip through a compile-only CI run.
if [ "$api_level" = "27" ]; then
  run_gradle_with_dependency_retries assembleContinuous --stacktrace

  apk="$(find app/build/outputs/apk/continuous -maxdepth 1 -type f -name '*.apk' -print -quit)"
  test -n "$apk"

  package_name="$("$ANDROID_HOME/build-tools/37.0.0/aapt" dump badging "$apk" |
    awk -F"'" '/^package: name=/{print $2; exit}')"
  test -n "$package_name"
  component="$package_name/org.schabi.newpipe.MainActivity"

  dump_emulator_diagnostics() {
    echo "::group::Android 8.1 startup diagnostics"
    echo "--- adb state ---"
    adb get-state 2>&1 || true
    echo "--- AndroidRuntime and system_server errors ---"
    adb logcat -b all -d -v threadtime 2>&1 |
      grep -E -i 'FATAL EXCEPTION|AndroidRuntime|system_server|ActivityManager|am_crash|am_anr|Fatal signal|SIG(SEGV|ABRT)|Error inflating class|Unable to start activity' |
      tail -n 400 || true
    echo "--- activity state ---"
    adb shell dumpsys activity activities 2>&1 || true
    echo "--- process list ---"
    adb shell ps -A 2>&1 | grep -F "$package_name" || true
    echo "::endgroup::"
  }

  adb install -r "$apk"
  adb shell am force-stop "$package_name" || true
  adb logcat -c

  # Do not use am start -W: on API 27 its blocking activity-manager wait
  # intermittently loses the ADB shell pipe even when Android is still alive.
  # A launch-command transport error is not treated as a pass or a crash;
  # the process and resumed activity are verified independently below.
  launch_output=""
  if launch_output="$(timeout 30s adb shell am start -n "$component" 2>&1)"; then
    :
  else
    launch_status=$?
    echo "Non-blocking activity launch returned status $launch_status; checking emulator state."
    printf '%s\n' "$launch_output"
    adb reconnect device >/dev/null 2>&1 || true
    timeout 30s adb wait-for-device
  fi
  printf '%s\n' "$launch_output"

  app_pid=""
  attempt=0
  while [ "$attempt" -lt 45 ]; do
    if adb get-state >/dev/null 2>&1; then
      app_pid="$(adb shell pidof "$package_name" 2>/dev/null | tr -d '\r' || true)"
      if [ -n "$app_pid" ]; then
        break
      fi
    fi
    attempt=$((attempt + 1))
    sleep 1
  done

  if [ -z "$app_pid" ]; then
    echo "Main process did not start within 45 seconds." >&2
    dump_emulator_diagnostics
    exit 1
  fi

  # Give startup a moment to expose delayed resource inflation or runtime
  # crashes, then require both a live process and a resumed MainActivity.
  sleep 5
  runtime_log="$(adb logcat -d -s AndroidRuntime:E 2>&1 || true)"
  if printf '%s\n' "$runtime_log" |
    grep -E 'FATAL EXCEPTION|Unable to start activity|Error inflating class' >/dev/null; then
    printf '%s\n' "$runtime_log" >&2
    dump_emulator_diagnostics
    exit 1
  fi

  app_pid="$(adb shell pidof "$package_name" 2>/dev/null | tr -d '\r' || true)"
  activity_dump="$(adb shell dumpsys activity activities 2>/dev/null || true)"
  if [ -z "$app_pid" ] || ! printf '%s\n' "$activity_dump" | grep -E "mResumedActivity:.*$package_name/" >/dev/null; then
    echo "MainActivity did not remain alive and resumed after launch." >&2
    dump_emulator_diagnostics
    exit 1
  fi

  echo "Android 8.1 startup smoke test passed (PID: $app_pid)."
fi
