@echo off
chcp 65001 >nul
setlocal EnableExtensions

cd /d "%~dp0"

set "MAVEN_COMMAND="
set "JAVA_HOME_CANDIDATE=%JAVA_HOME%"
set "JAVA_HOME="
set "TOO_OLD_JAVA="

rem This project targets Java 21 and Maven Wrapper needs JAVA_HOME to point at a JDK.
rem Try the existing JAVA_HOME first, then java on PATH, then the usual install folders.
if defined JAVA_HOME_CANDIDATE call :try_java "%JAVA_HOME_CANDIDATE%"
if not defined JAVA_HOME for /f "delims=" %%J in ('where java 2^>nul') do call :try_java "%%~dpJ.."
if not defined JAVA_HOME for /d %%J in ("%ProgramFiles%\Eclipse Adoptium\jdk-*" "%ProgramFiles%\Java\jdk-*" "%ProgramFiles%\Microsoft\jdk-*" "%ProgramFiles%\Amazon Corretto\jdk*" "%ProgramFiles%\Zulu\zulu-*" "%ProgramFiles%\BellSoft\LibericaJDK-*" "%ProgramFiles%\Semeru\jdk-*" "%USERPROFILE%\.jdks\jdk-*") do call :try_java "%%~fJ"

if not defined JAVA_HOME goto :java_missing

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
echo Open http://localhost:8000 after startup completes.
echo.

rem Refresh static assets before the backend serves the login page.
call "%MAVEN_COMMAND%" -Pfrontend spring-boot:run "-Dspring-boot.run.arguments=%*"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" goto :failed

exit /b 0

:java_missing
echo [ERROR] JDK 21 or later was not found.
if defined TOO_OLD_JAVA echo [ERROR] Found %TOO_OLD_JAVA%, which is older than JDK 21.
echo [ERROR] Install JDK 21 or later and set JAVA_HOME to its install folder.
set "EXIT_CODE=1"
goto :failed

:failed
echo.
echo [ERROR] 葫芦娃助手 stopped with exit code %EXIT_CODE%.
if not "%HULUWA_NO_PAUSE%"=="1" (
    echo Press any key to close this window...
    pause >nul
)
exit /b %EXIT_CODE%

:try_java
rem Accept %1 only when it is a JDK 21+ home; otherwise keep searching.
if defined JAVA_HOME exit /b 0
set "JAVA_CANDIDATE=%~f1"
set "JAVA_CANDIDATE_VERSION="
set "JAVA_CANDIDATE_MAJOR="
set "JAVA_CANDIDATE_MAJOR_NUM="
if not exist "%JAVA_CANDIDATE%\bin\java.exe" exit /b 0
if not exist "%JAVA_CANDIDATE%\bin\javac.exe" exit /b 0
for /f "tokens=3" %%V in ('"%JAVA_CANDIDATE%\bin\java.exe" -version 2^>^&1') do if not defined JAVA_CANDIDATE_VERSION set "JAVA_CANDIDATE_VERSION=%%~V"
if not defined JAVA_CANDIDATE_VERSION exit /b 0
for /f "tokens=1 delims=." %%M in ("%JAVA_CANDIDATE_VERSION%") do set "JAVA_CANDIDATE_MAJOR=%%M"
set /a "JAVA_CANDIDATE_MAJOR_NUM=JAVA_CANDIDATE_MAJOR" >nul 2>nul
if not defined JAVA_CANDIDATE_MAJOR_NUM exit /b 0
if %JAVA_CANDIDATE_MAJOR_NUM% GEQ 21 (
    set "JAVA_HOME=%JAVA_CANDIDATE%"
) else (
    if not defined TOO_OLD_JAVA set "TOO_OLD_JAVA=%JAVA_CANDIDATE% (Java %JAVA_CANDIDATE_VERSION%)"
)
exit /b 0
