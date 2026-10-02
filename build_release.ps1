# Build Release APK for Payo + write SHA-256 checksums.
# Requires local.properties (or env) with SERVER_BASE_URL and CERT_PIN_* — see local.properties.example.
# Does not assume high RAM; uses Gradle defaults / GRADLE_OPTS if already set.

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Release APK" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "Running assembleRelease..." -ForegroundColor Yellow
./gradlew :app:assembleRelease --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

$apkDir = "app\build\outputs\apk\release"
$checksumFile = "app-release-checksums.txt"
if (-not (Test-Path $apkDir)) {
    Write-Host "APK output directory not found: $apkDir" -ForegroundColor Red
    exit 1
}

$lines = @()
$lines += "Payo release checksums — $(Get-Date -Format o)"
$lines += ""

Get-ChildItem -Path $apkDir -Filter "*.apk" | ForEach-Object {
    $hash = Get-FileHash -Algorithm SHA256 -Path $_.FullName
    $line = "$($hash.Hash)  $($_.Name)"
    $lines += $line
    Write-Host $line -ForegroundColor Green
}

$lines | Set-Content -Path $checksumFile -Encoding UTF8
Write-Host ""
Write-Host "Checksums written to $checksumFile" -ForegroundColor Cyan
Write-Host "Build complete." -ForegroundColor Green
