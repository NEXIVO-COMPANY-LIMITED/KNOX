#!/usr/bin/env bash
# Collect a single lab report text file. Masks tokens/keys/IMEI.
# Usage: tools/collect-lab-report.sh [outputPath]
set -euo pipefail

PKG="com.nexivo.payo.lab"
DUMP_RECV="${PKG}/com.nexivo.payo.security.LabHardeningToggleReceiver"
OUT="${1:-lab-report-$(date +%Y%m%d-%H%M%S).txt}"

mask() {
  sed -E \
    -e 's/(imei|serial|token|accessToken|refreshToken|offlineUnlockKey|Bearer|Authorization)[[:space:]]*[:=]?[[:space:]]*[^[:space:]]+/\1=[MASKED]/Ig'
}

{
  echo "==== Payo lab report ===="
  echo "generated=$(date -Iseconds 2>/dev/null || date)"
  echo

  echo "---- device ----"
  adb shell getprop ro.product.model || true
  adb shell getprop ro.build.version.release || true
  adb shell getprop ro.build.version.sdk || true
  adb shell getprop ro.product.manufacturer || true
  echo

  echo "---- dumpsys device_policy (Device Owner / Payo lines) ----"
  adb shell dumpsys device_policy 2>&1 | mask | grep -E -i 'Device Owner|Active admin|com\.nexivo\.payo|Owner' || true
  echo

  echo "---- DUMP_POLICY_STATE ----"
  adb logcat -c >/dev/null 2>&1 || true
  adb shell am broadcast -a com.nexivo.payo.lab.DUMP_POLICY_STATE -n "$DUMP_RECV" || true
  sleep 2
  adb logcat -d -s PayoPolicyState:W DueAlarmScheduler:I 2>&1 | mask || true
  echo

  echo "---- logcat (filtered tags; masked) ----"
  adb logcat -d -s \
    PayoPolicyState:W \
    BootLockGap:W \
    LabAutoRelease:W \
    LabAutoReleaseAlarm:W \
    PostProvisioning:W \
    DueAlarmScheduler:I \
    DeviceMonitorService:I 2>&1 | mask || true
  echo

  echo "---- exact-alarm / FGS ----"
  adb shell appops get "$PKG" SCHEDULE_EXACT_ALARM 2>&1 || true
  echo "Note: canScheduleExactAlarms and foregroundServiceTypeDeclared are in PayoPolicyState DUMP."
  echo
  echo "==== end report ===="
} >"$OUT"

echo "Done: $OUT"
