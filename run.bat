@echo off
chcp 65001 >nul
REM ============================================================
REM run.bat - 啟動 TRAVELER EASY GATE (TravelerEasyGateApplication)
REM 放在專案根目錄 (跟 pom.xml / mvnw.cmd 同一層) 直接雙擊執行
REM ============================================================

cd /d "%~dp0"

echo ============================================
echo   TRAVELER EASY GATE - 啟動中...
echo ============================================
echo.

REM 檢查 Java 是否存在
java -version >nul 2>&1
if errorlevel 1 (
    echo [錯誤] 找不到 java，請先確認 JDK 已安裝並設定好 PATH。
    pause
    exit /b 1
)

REM 用 Maven Wrapper 啟動 Spring Boot 應用程式
call mvnw.cmd spring-boot:run

echo.
echo ============================================
echo   程式已結束 (若是錯誤結束，請往上捲看錯誤訊息)
echo ============================================
pause
