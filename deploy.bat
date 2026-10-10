@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
title 葫芦娃助手 - 一键部署到服务器

REM ==================== CONFIG ====================
set SERVER=192.168.3.202
set SSH_PORT=22
set USER=root
set REMOTE_DIR=/root/huluwa-src
set DATA_DIR=/root/huluwa
set CONTAINER=huluwa-console
set IMAGE=huluwa-console:latest
set HOST_PORT=8008
set CONTAINER_PORT=8089
set SSH_RETRY=3
set SSH_WAIT=3

REM Git 自带的 ssh/scp: 本机 Windows 自带 OpenSSH 已损坏, 不可用
set GIT_USR=C:\Program Files\Git\usr\bin
set SSH_EXE=%GIT_USR%\ssh.exe
set SCP_EXE=%GIT_USR%\scp.exe
set KEYGEN_EXE=%GIT_USR%\ssh-keygen.exe
set SSH_KEY=%USERPROFILE%\.ssh\huluwa_deploy
set KNOWN_HOSTS=%TEMP%\huluwa_known_hosts
REM ================================================

cd /d "%~dp0"
set PROJECT_DIR=%CD%
set TARBALL=%TEMP%\huluwa-src.tar.gz
set OUT_LOG=%TEMP%\huluwa_deploy_out.log

echo ========== 葫芦娃助手 一键部署 ==========
echo.

REM ---------- 0. 环境检查 ----------
if not exist "%SSH_EXE%" goto :no_git
if not exist "Dockerfile" goto :wrong_dir
where tar >nul 2>nul
if errorlevel 1 goto :no_tar

REM ---------- 1. 首次运行: 生成密钥并安装到服务器 ----------
if not exist "%SSH_KEY%" goto :setup_key
"%SSH_EXE%" -i "%SSH_KEY%" -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile="%KNOWN_HOSTS%" -o LogLevel=ERROR -o ConnectTimeout=8 -p %SSH_PORT% %USER%@%SERVER% "echo ok" >nul 2>nul
if not errorlevel 1 goto :key_ok
echo [提示] 免密登录失效, 需要重新安装公钥...

:setup_key
echo [首次运行] 生成本机部署密钥...
if not exist "%SSH_KEY%" "%KEYGEN_EXE%" -t ed25519 -N "" -C huluwa-deploy -f "%SSH_KEY%" >nul
where python >nul 2>nul
if errorlevel 1 goto :need_python
python -c "import paramiko" >nul 2>nul
if errorlevel 1 python -m pip install --quiet --disable-pip-version-check paramiko
echo [首次运行] 正在安装公钥到服务器, 需要认证一次...
python "scripts\install-key.py" --host %SERVER% --port %SSH_PORT% --user %USER% --pubkey "%SSH_KEY%.pub"
if errorlevel 1 goto :fail

:key_ok
echo [1/5] 免密认证通过 (%USER%@%SERVER%)
echo.

REM ---------- 2. 打包本机源码 (仅上传代码, 不含 .git/构建产物/数据) ----------
echo [2/5] 打包源码...
del /q "%TARBALL%" 2>nul
tar -czf "%TARBALL%" --exclude=./.git --exclude=./node_modules --exclude=*/node_modules --exclude=target --exclude=./data --exclude=./output "--exclude=./%%SystemDrive%%" -C "%PROJECT_DIR%" .
if errorlevel 1 goto :tar_fail
for %%A in ("%TARBALL%") do set TARBYTES=%%~zA
echo       源码包: %TARBYTES% 字节

REM ---------- 3. 上传并解压 (文本文件统一转为 LF) ----------
echo [3/5] 上传到服务器 %REMOTE_DIR% ...
set UPLOAD_OK=0
for /L %%i in (1,1,!SSH_RETRY!) do (
    if !UPLOAD_OK!==0 (
        echo   尝试 %%i/!SSH_RETRY!...
        "%SCP_EXE%" -i "%SSH_KEY%" -o StrictHostKeyChecking=no -o UserKnownHostsFile="%KNOWN_HOSTS%" -o LogLevel=ERROR -P %SSH_PORT% "%TARBALL%" %USER%@%SERVER%:/tmp/huluwa-src.tar.gz
        if not errorlevel 1 (
            set UPLOAD_OK=1
        ) else (
            echo   上传失败, !SSH_WAIT! 秒后重试...
            timeout /t !SSH_WAIT! /nobreak >nul
        )
    )
)
if !UPLOAD_OK!==0 goto :upload_fail

"%SSH_EXE%" -i "%SSH_KEY%" -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile="%KNOWN_HOSTS%" -o LogLevel=ERROR -p %SSH_PORT% %USER%@%SERVER% "set -e; rm -rf %REMOTE_DIR%; mkdir -p %REMOTE_DIR%; tar -xzf /tmp/huluwa-src.tar.gz -C %REMOTE_DIR%; find %REMOTE_DIR% -type f -exec sed -i 's/\r$//' {} +; echo 解压完成: $(find %REMOTE_DIR% -type f | wc -l) 个文件"
if errorlevel 1 goto :fail

REM ---------- 4. 构建镜像 ----------
echo [4/5] 构建 Docker 镜像 %IMAGE% ...
"%SSH_EXE%" -i "%SSH_KEY%" -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile="%KNOWN_HOSTS%" -o LogLevel=ERROR -p %SSH_PORT% %USER%@%SERVER% "cd %REMOTE_DIR% && docker build -t %IMAGE% . > /root/huluwa-build.log 2>&1; echo BUILD_EXIT=$?; tail -n 5 /root/huluwa-build.log"
if errorlevel 1 goto :build_fail

REM ---------- 5. 重建容器 ----------
echo [5/5] 重建容器 %CONTAINER% ...
"%SSH_EXE%" -i "%SSH_KEY%" -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile="%KNOWN_HOSTS%" -o LogLevel=ERROR -p %SSH_PORT% %USER%@%SERVER% "docker rm -f %CONTAINER% >/dev/null 2>&1; mkdir -p %DATA_DIR%; chown -R 10001:10001 %DATA_DIR%; docker run -d --name %CONTAINER% --restart unless-stopped --init -p %HOST_PORT%:%CONTAINER_PORT% -v %DATA_DIR%:/app/data -e TZ=Asia/Shanghai %IMAGE% >/dev/null && echo 容器已启动"
if errorlevel 1 goto :fail

echo       等待服务就绪...
"%SSH_EXE%" -i "%SSH_KEY%" -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile="%KNOWN_HOSTS%" -o LogLevel=ERROR -p %SSH_PORT% %USER%@%SERVER% "for i in $(seq 1 60); do c=$(curl -s -o /dev/null -w '%%{http_code}' --max-time 3 http://127.0.0.1:%HOST_PORT%/api/auth/status); if [ \"$c\" = 200 ]; then echo HEALTH_OK; docker ps --filter name=%CONTAINER% --format '{{.Names}} | {{.Status}} | {{.Ports}}'; exit 0; fi; sleep 3; done; echo HEALTH_FAIL; docker logs --tail 30 %CONTAINER% 2>&1; exit 1"
if errorlevel 1 goto :health_fail

echo.
echo ========== 部署完成 ==========
echo 访问地址: http://%SERVER%:%HOST_PORT%
echo 数据目录: %DATA_DIR%  (容器内 /app/data)
echo 查看日志: "%SSH_EXE%" -i "%SSH_KEY%" -p %SSH_PORT% %USER%@%SERVER% "docker logs -f %CONTAINER%"
echo.
del /q "%TARBALL%" 2>nul
pause
endlocal
exit /b 0

:no_git
echo [错误] 找不到 Git 自带的 ssh: %SSH_EXE%
echo        本机 Windows 自带 OpenSSH 已损坏, 因此改用 Git 的 ssh。
echo        请先安装 Git for Windows, 或修改脚本里的 GIT_USR 路径。
goto :fail

:no_tar
echo [错误] 找不到 tar 命令, 无法打包源码。
echo        Windows 10 1803+ 自带 tar, 若缺失请安装。
goto :fail

:wrong_dir
echo [错误] 当前目录没有 Dockerfile, 请把 deploy.bat 放在项目根目录。
echo        当前目录: %PROJECT_DIR%
goto :fail

:need_python
echo [错误] 首次安装公钥需要 Python 3 + paramiko。
echo        请安装 Python 3 后重试, 或手动把 %SSH_KEY%.pub 内容追加到服务器 /root/.ssh/authorized_keys
goto :fail

:tar_fail
echo [错误] 打包源码失败。
goto :fail

:upload_fail
echo [错误] 源码上传失败(%SSH_RETRY% 次重试均失败)。
goto :fail

:build_fail
echo [错误] Docker 镜像构建失败, 请查看上面的日志输出。
echo        线上容器未被改动, 仍是上一个可用版本。
goto :fail

:health_fail
echo [错误] 新容器启动后健康检查未通过。
echo        请手动登录服务器排查: docker logs --tail 100 %CONTAINER%
goto :fail

:fail
echo.
echo ---------- 部署失败 ----------
echo.
pause
endlocal
exit /b 1
