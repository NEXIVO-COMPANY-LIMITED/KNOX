# Build Release APK - Optimized for PAYO
# Sets 16GB memory and extended timeouts for stable builds

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Release APK (Optimized)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Clean build
Write-Host "Cleaning build..." -ForegroundColor Yellow
Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue

# Stop any existing daemon to avoid conflicts
Write-Host "Stopping any existing Gradle daemons..." -ForegroundColor Yellow
./gradlew --stop

# Set environment with 16GB memory and extended timeouts
Write-Host "Setting JVM memory to 16GB..." -ForegroundColor Yellow
$env:GRADLE_OPTS = "-Xmx16g -XX:MaxMetaspaceSize=4g -XX:+UseG1GC -Dorg.gradle.daemon.idletimeout=120000"

# Build using the custom Gradle task we added to build.gradle.kts
Write-Host "Starting build and checksum generation..." -ForegroundColor Yellow
./gradlew :app:buildReleaseApkWithChecksums --max-workers=4 --parallel

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✅ Build and Post-Processing Complete!" -ForegroundColor Green
    Write-Host "Check 'app-release-checksums.txt' for details." -ForegroundColor Gray
} else {
    Write-Host "`n❌ Build failed!" -ForegroundColor Red
    exit 1
}
