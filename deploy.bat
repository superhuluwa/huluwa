@echo off
setlocal enabledelayedexpansion

REM ==================== CONFIG ====================
set SERVER=192.168.3.202
set SSH_PORT=22
set USER=root
set REMOTE_DIR=/root/face
set JAR_NAME=Face2Face-0.0.1-SNAPSHOT.jar
set APP_PORT=80
set JAVA_PATH=/opt/jdk-17.0.19+10/bin/java
set SSH_RETRY=10
set SSH_WAIT=3
REM ================================================

echo ========== DEPLOY START ==========

REM 1. Build
echo [1/3] Maven build...
call mvnw.cmd clean package -DskipTests
if errorlevel 1 (
    echo BUILD FAILED!
    pause
    exit /b 1
)
echo Build success!

REM 2. Upload
echo [2/3] Uploading JAR...
set UPLOAD_OK=0
for /L %%i in (1,1,!SSH_RETRY!) do (
    if !UPLOAD_OK!==0 (
        echo   Attempt %%i/!SSH_RETRY!...
        scp -P %SSH_PORT% target\%JAR_NAME% %USER%@%SERVER%:%REMOTE_DIR%/
        if not errorlevel 1 (
            set UPLOAD_OK=1
        ) else (
            if %%i==1 ssh -p %SSH_PORT% %USER%@%SERVER% "mkdir -p %REMOTE_DIR%" 2>nul
            echo   Failed, retry in !SSH_WAIT!s...
            timeout /t !SSH_WAIT! /nobreak >nul
        )
    )
)
if !UPLOAD_OK!==0 (
    echo Upload failed after !SSH_RETRY! retries!
    pause
    exit /b 1
)
echo Upload complete!

REM 3. Stop + Start in single SSH connection
echo [3/3] Restarting service...
set START_OK=0
for /L %%i in (1,1,!SSH_RETRY!) do (
    if !START_OK!==0 (
        echo   Attempt %%i/!SSH_RETRY!...
        ssh -p %SSH_PORT% %USER%@%SERVER% "kill -9 $(lsof -t -i:%APP_PORT%) 2>/dev/null; sleep 1; cd %REMOTE_DIR% && nohup %JAVA_PATH% -jar %JAR_NAME% --server.port=%APP_PORT% > app.log 2>&1 & sleep 3 && for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do sleep 1; PID=$(lsof -t -i:%APP_PORT% 2>/dev/null); if [ -n ""$PID"" ]; then echo Started! PID: $PID; exit 0; fi; done; echo Start failed; tail -20 app.log"
        if not errorlevel 1 (
            set START_OK=1
        ) else (
            echo   Failed, retry in !SSH_WAIT!s...
            timeout /t !SSH_WAIT! /nobreak >nul
        )
    )
)

echo ========== DEPLOY DONE ==========
echo.
echo Service URL: http://%SERVER%:%APP_PORT%
echo Logs: ssh -p %SSH_PORT% %USER%@%SERVER% "tail -f %REMOTE_DIR%/app.log"
echo.
pause
