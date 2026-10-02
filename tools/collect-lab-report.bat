@echo off
REM Collect a single lab report text file. Masks tokens/keys/IMEI.
REM Usage: tools\collect-lab-report.bat [outputPath]
setlocal EnableExtensions EnableDelayedExpansion

set "PKG=com.nexivo.payo.lab"
set "DUMP_RECV=%PKG%/com.nexivo.payo.security.LabHardeningToggleReceiver"
set "OUT=%~1"
if "%OUT%"=="" (
  for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "TS=%%i"
  set "OUT=lab-report-!TS!.txt"
)

echo Writing report to %OUT%

(
  echo ==== Payo lab report ====
  echo generated=%DATE% %TIME%
  echo.
  echo ---- device ----
) > "%OUT%"

adb shell getprop ro.product.model >> "%OUT%" 2>&1
adb shell getprop ro.build.version.release >> "%OUT%" 2>&1
adb shell getprop ro.build.version.sdk >> "%OUT%" 2>&1
adb shell getprop ro.product.manufacturer >> "%OUT%" 2>&1

(
  echo.
  echo ---- dumpsys device_policy ^(Device Owner / Payo lines^) ----
) >> "%OUT%"

adb shell dumpsys device_policy > "%TEMP%\payo_dpm_raw.txt" 2>&1
powershell -NoProfile -Command ^
  "Get-Content '%TEMP%\payo_dpm_raw.txt' | Where-Object { $_ -match 'Device Owner|Active admin|com\.nexivo\.payo|Owner' } | ForEach-Object { $_ -replace '(?i)(imei|serial|token|accessToken|refreshToken|offlineUnlockKey|Bearer|Authorization)\s*[:=]?\s*\S+','$1=[MASKED]' } | Out-File -Append -Encoding utf8 '%OUT%'"

(
  echo.
  echo ---- DUMP_POLICY_STATE ----
) >> "%OUT%"

adb logcat -c >nul 2>&1
adb shell am broadcast -a com.nexivo.payo.lab.DUMP_POLICY_STATE -n %DUMP_RECV% >> "%OUT%" 2>&1
timeout /t 2 /nobreak >nul
adb logcat -d -s PayoPolicyState:W DueAlarmScheduler:I > "%TEMP%\payo_dump_log.txt" 2>&1
powershell -NoProfile -Command ^
  "Get-Content '%TEMP%\payo_dump_log.txt' | ForEach-Object { $_ -replace '(?i)(imei|serial|token|accessToken|refreshToken|offlineUnlockKey|Bearer|Authorization)\s*[:=]?\s*\S+','$1=[MASKED]' } | Out-File -Append -Encoding utf8 '%OUT%'"

(
  echo.
  echo ---- logcat ^(filtered tags; masked^) ----
) >> "%OUT%"

adb logcat -d -s PayoPolicyState:W BootLockGap:W LabAutoRelease:W LabAutoReleaseAlarm:W PostProvisioning:W DueAlarmScheduler:I DeviceMonitorService:I > "%TEMP%\payo_log_raw.txt" 2>&1
powershell -NoProfile -Command ^
  "Get-Content '%TEMP%\payo_log_raw.txt' | ForEach-Object { $_ -replace '(?i)(imei|serial|token|accessToken|refreshToken|offlineUnlockKey|Bearer|Authorization)\s*[:=]?\s*\S+','$1=[MASKED]' } | Out-File -Append -Encoding utf8 '%OUT%'"

(
  echo.
  echo ---- exact-alarm / FGS ----
) >> "%OUT%"
adb shell appops get %PKG% SCHEDULE_EXACT_ALARM >> "%OUT%" 2>&1
(
  echo Note: canScheduleExactAlarms and foregroundServiceTypeDeclared are in PayoPolicyState DUMP.
  echo.
  echo ==== end report ====
) >> "%OUT%"

echo Done: %OUT%
endlocal
