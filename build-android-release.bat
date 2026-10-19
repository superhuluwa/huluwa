@echo off
chcp 65001 >nul
setlocal EnableExtensions DisableDelayedExpansion

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-android-release.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%HULUWA_NO_PAUSE%"=="1" (
    echo 按任意键关闭窗口……
    pause >nul
)
exit /b %EXIT_CODE%
