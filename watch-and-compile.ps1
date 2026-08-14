# watch-and-compile.ps1
# 監看 src\main\java 底下的 .java 檔案，存檔後自動觸發 Maven 重新編譯 (只編譯，不重啟整個 run.bat)。
# DevTools 偵測到 target\classes 有變動後，會自動幫執行中的 Spring Boot 應用程式做「熱重啟」
# (通常只要幾秒鐘)，不需要手動關掉重開 run.bat。
#
# 使用方式：跟 run.bat 一起、另外開一個視窗執行這個腳本 (雙擊 watch-and-compile.bat 即可)。
# 兩個視窗都要保持開著：
#   視窗一：run.bat            (啟動並持續執行 Spring Boot)
#   視窗二：watch-and-compile.bat  (監看 .java 存檔，自動觸發編譯)

$ErrorActionPreference = "SilentlyContinue"
Set-Location -Path $PSScriptRoot

$watchPath = Join-Path $PSScriptRoot "src\main\java"
if (-not (Test-Path $watchPath)) {
    Write-Host "[錯誤] 找不到路徑: $watchPath" -ForegroundColor Red
    Read-Host "按 Enter 結束"
    exit 1
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  正在監看 Java 原始碼變更 (存檔後自動編譯)" -ForegroundColor Cyan
Write-Host "  監看路徑: $watchPath" -ForegroundColor Cyan
Write-Host "  請保持這個視窗開著，跟 run.bat 一起執行" -ForegroundColor Cyan
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
        # 稍微等一下 debounce，避免存檔瞬間觸發好幾次編譯
        Start-Sleep -Seconds $debounceSeconds
        $script:pendingCompile = $false

        $now = Get-Date -Format "HH:mm:ss"
        Write-Host "[$now] 偵測到 .java 變更，開始重新編譯..." -ForegroundColor Yellow

        $mvnw = Join-Path $PSScriptRoot "mvnw.cmd"
        $result = & $mvnw compile -q -o 2>&1
        $exitCode = $LASTEXITCODE

        if ($exitCode -eq 0) {
            Write-Host "[$now] 編譯成功，DevTools 會自動幫執行中的程式熱重啟 (稍等幾秒再重新整理瀏覽器)" -ForegroundColor Green
        } else {
            Write-Host "[$now] 編譯失敗，請檢查下面的錯誤訊息：" -ForegroundColor Red
            Write-Host $result -ForegroundColor Red
        }
        Write-Host ""
    }
}
