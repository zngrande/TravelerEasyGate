@echo off
chcp 65001 >nul
REM ============================================================
REM watch-and-compile.bat - 監看 Java 檔案存檔，自動觸發重新編譯
REM 放在專案根目錄 (跟 pom.xml / mvnw.cmd / run.bat 同一層) 直接雙擊執行
REM 請跟 run.bat 一起開著 (兩個視窗都要保持執行中)
REM ============================================================
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0watch-and-compile.ps1"
pause
