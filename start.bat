@echo off
setlocal EnableExtensions

cd /d "%~dp0"

set "MAVEN_COMMAND="

rem Reuse JAVA_HOME when it points to a usable JDK.
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" goto :java_ready

set "JAVA_HOME="
for /f "delims=" %%J in ('where java 2^>nul') do (
    for %%I in ("%%~dpJ..") do set "JAVA_HOME=%%~fI"
    goto :java_ready
)

echo [ERROR] Java was not found. Install JDK 21 or later and configure PATH.
set "EXIT_CODE=1"
goto :failed

:java_ready
if not exist "%~dp0mvnw.cmd" (
    echo [ERROR] Maven Wrapper was not found: %~dp0mvnw.cmd
    set "EXIT_CODE=1"
    goto :failed
)
set "MAVEN_COMMAND=%~dp0mvnw.cmd"

where node >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Node.js was not found. Install Node.js with npm and configure PATH.
    set "EXIT_CODE=1"
    goto :failed
)
where npm.cmd >nul 2>nul
if errorlevel 1 (
    echo [ERROR] npm.cmd was not found. Install Node.js with npm and configure PATH.
    set "EXIT_CODE=1"
    goto :failed
)

echo Preparing frontend assets and starting 葫芦娃助手...
echo Open http://localhost:8089 after startup completes.
echo.

rem Refresh static assets before the backend serves the login page.
call "%MAVEN_COMMAND%" -Pfrontend spring-boot:run "-Dspring-boot.run.arguments=%*"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" goto :failed

exit /b 0

:failed
echo.
echo [ERROR] 葫芦娃助手 stopped with exit code %EXIT_CODE%.
if not "%HULUWA_NO_PAUSE%"=="1" (
    echo Press any key to close this window...
    pause >nul
)
exit /b %EXIT_CODE%