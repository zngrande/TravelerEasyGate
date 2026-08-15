# watch-and-compile.ps1
# Watches src\main\java for .java file saves, and triggers a Maven incremental
# recompile automatically. Spring Boot DevTools will then hot-restart the running
# app once target\classes changes (usually within a few seconds).
#
# Usage: run alongside run.bat, in a SEPARATE window (double-click watch-and-compile.bat).
# Keep BOTH windows open:
#   Window 1: run.bat                 (starts and keeps Spring Boot running)
#   Window 2: watch-and-compile.bat   (watches .java saves, triggers recompile)

$ErrorActionPreference = "SilentlyContinue"
Set-Location -Path $PSScriptRoot

$watchPath = Join-Path $PSScriptRoot "src\main\java"
if (-not (Test-Path $watchPath)) {
    Write-Host "[ERROR] Path not found: $watchPath" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Watching Java source for changes (auto compile on save)" -ForegroundColor Cyan
Write-Host "  Watch path: $watchPath" -ForegroundColor Cyan
Write-Host "  Keep this window open, together with run.bat" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

$fsw = New-Object System.IO.FileSystemWatcher
$fsw.Path = $watchPath
$fsw.IncludeSubdirectories = $true
$fsw.Filter = "*.java"
$fsw.EnableRaisingEvents = $true

$lastCompileTime = Get-Date "2000-01-01"
$debounceSeconds = 2

$action = {
    $script:pendingCompile = $true
}

Register-ObjectEvent $fsw Changed -Action $action | Out-Null
Register-ObjectEvent $fsw Created -Action $action | Out-Null
Register-ObjectEvent $fsw Renamed -Action $action | Out-Null

$script:pendingCompile = $false

while ($true) {
    Start-Sleep -Seconds 1
    if ($script:pendingCompile) {
        # Small debounce delay so a single save doesn't trigger multiple compiles
        Start-Sleep -Seconds $debounceSeconds
        $script:pendingCompile = $false

        $now = Get-Date -Format "HH:mm:ss"
        Write-Host "[$now] Detected .java change, recompiling..." -ForegroundColor Yellow

        $mvnw = Join-Path $PSScriptRoot "mvnw.cmd"
        $result = & $mvnw compile -q -o 2>&1
        $exitCode = $LASTEXITCODE

        if ($exitCode -eq 0) {
            Write-Host "[$now] Compile succeeded. DevTools will hot-restart the running app (wait a few seconds, then refresh the browser)" -ForegroundColor Green
        } else {
            Write-Host "[$now] Compile FAILED. See error output below:" -ForegroundColor Red
            Write-Host $result -ForegroundColor Red
        }
        Write-Host ""
    }
}