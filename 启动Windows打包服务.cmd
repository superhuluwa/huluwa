@echo off
setlocal
chcp 65001 >nul
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0android-builder\start-windows.ps1"
set "startResult=%errorlevel%"
echo.
pause
exit /b %startResult%
