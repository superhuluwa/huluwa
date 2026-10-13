@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
title 葫芦娃助手 - 一键部署到飞牛 fnOS

REM ==================== CONFIG ====================
REM 飞牛 fnOS 默认禁止 root 登录（PermitRootLogin no），因此使用普通管理员账号 + sudo 部署。
set SERVER=192.168.3.77
set SSH_PORT=22
set USER=397383523
set APP_DIR=/vol1/@appdata/huluwa
set REMOTE_DIR=%APP_DIR%/src
set DATA_DIR=%APP_DIR%/data
set FRP_DIR=%APP_DIR%/frp
set CONTAINER=huluwa-console
set IMAGE=huluwa-console:latest
set HOST_PORT=8000
set CONTAINER_PORT=8000
set SSH_RETRY=3
set SSH_WAIT=3
REM 部署账号的 .ssh 目录（飞牛禁止 root 登录，公钥装到普通用户家目录）
set SSH_HOME=/home/397383523
REM 本机保存的飞牛凭据（用于首次装公钥与自动获取 sudo 密码）；不存在时会提示手工输入
set FNOS_CRED=%USERPROFILE%\.codex\private\fnos\config.json

REM Git 自带的 ssh/scp: 本机 Windows 自带 OpenSSH 已损坏, 不可用
set GIT_USR=C:\Program Files\Git\usr\bin
set SSH_EXE=%GIT_USR%\ssh.exe
set SCP_EXE=%GIT_USR%\scp.exe
set KEYGEN_EXE=%GIT_USR%\ssh-keygen.exe
set SSH_KEY=%USERPROFILE%\.ssh\huluwa_deploy
set KNOWN_HOSTS=%TEMP%\huluwa_fnos_known_hosts
set SSH_OPTS=-i "%SSH_KEY%" -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile="%KNOWN_HOSTS%" -o LogLevel=ERROR
REM ================================================

cd /d "%~dp0"
set PROJECT_DIR=%CD%
set TARBALL=%TEMP%\huluwa-src.tar.gz
set REMOTE_SH=%TEMP%\huluwa-fnos-deploy.sh
set PW_FILE=%TEMP%\huluwa_fnos_pw.tmp

REM ---------- 0. 拉取最新代码 ----------
echo 正在拉取最新代码...
git pull --ff-only
if errorlevel 1 goto :git_pull_fail
echo.

echo ========== 葫芦娃助手 一键部署（飞牛 fnOS） ==========
echo 目标: %USER%@%SERVER%:%SSH_PORT%   应用目录: %APP_DIR%
echo.

REM ---------- 0b. 环境检查 ----------
if not exist "%SSH_EXE%" goto :no_git
if not exist "Dockerfile" goto :wrong_dir
if not exist "compose.yaml" goto :wrong_dir
if not exist "scripts\deploy-fnos-remote.sh" goto :no_remote_sh
where tar >nul 2>nul
if errorlevel 1 goto :no_tar

REM ---------- 1. 首次运行: 生成密钥并安装到飞牛 ----------
if not exist "%SSH_KEY%" goto :setup_key
"%SSH_EXE%" %SSH_OPTS% -o ConnectTimeout=8 -p %SSH_PORT% %USER%@%SERVER% "echo ok" >nul 2>nul
if not errorlevel 1 goto :key_ok
echo [提示] 免密登录失效, 需要重新安装公钥...

:setup_key
echo [首次运行] 生成本机部署密钥...
if not exist "%SSH_KEY%" "%KEYGEN_EXE%" -t ed25519 -N "" -C huluwa-deploy -f "%SSH_KEY%" >nul
where python >nul 2>nul
if errorlevel 1 goto :need_python
python -c "import paramiko" >nul 2>nul
if errorlevel 1 python -m pip install --quiet --disable-pip-version-check paramiko
echo [首次运行] 正在安装公钥到飞牛, 需要认证一次...
python "scripts\install-key.py" --host %SERVER% --port %SSH_PORT% --user %USER% --pubkey "%SSH_KEY%.pub" --ssh-dir %SSH_HOME%/.ssh --config "%FNOS_CRED%"
if errorlevel 1 goto :fail

:key_ok
echo [1/6] 免密认证通过 (%USER%@%SERVER%)
echo.

REM ---------- 2. 打包本机源码 (仅上传代码, 不含 .git/构建产物/数据) ----------
echo [2/6] 打包源码...
del /q "%TARBALL%" 2>nul
tar -czf "%TARBALL%" --exclude=./.git --exclude=./node_modules --exclude=*/node_modules --exclude=target --exclude=./data --exclude=./output --exclude=./research_spk --exclude=./research_xunlei --exclude=./ps7420.zip --exclude=./src/main/resources/static "--exclude=./%%SystemDrive%%" -C "%PROJECT_DIR%" .
if errorlevel 1 goto :tar_fail
for %%A in ("%TARBALL%") do set TARBYTES=%%~zA
echo       源码包: !TARBYTES! 字节

REM ---------- 3. 上传源码包与远端部署脚本 ----------
echo [3/6] 上传到飞牛 /tmp ...
copy /y "scripts\deploy-fnos-remote.sh" "%REMOTE_SH%" >nul
if errorlevel 1 goto :fail

set UPLOAD_OK=0
for /L %%i in (1,1,!SSH_RETRY!) do (
    if !UPLOAD_OK!==0 (
        echo   尝试 %%i/!SSH_RETRY!...
        "%SCP_EXE%" -i "%SSH_KEY%" -o StrictHostKeyChecking=no -o UserKnownHostsFile="%KNOWN_HOSTS%" -o LogLevel=ERROR -P %SSH_PORT% "%TARBALL%" "%REMOTE_SH%" %USER%@%SERVER%:/tmp/
        if not errorlevel 1 (
            set UPLOAD_OK=1
        ) else (
            echo   上传失败, !SSH_WAIT! 秒后重试...
            timeout /t !SSH_WAIT! /nobreak >nul
        )
    )
)
if !UPLOAD_OK!==0 goto :upload_fail

REM ---------- 4. 准备 sudo 凭据 ----------
echo [4/6] 准备 sudo 凭据（飞牛禁止 root 登录, 特权步骤需要一次 sudo）...
del /q "%PW_FILE%" 2>nul
python "scripts\fnos-sudo-pw.py" "%PW_FILE%" --config "%FNOS_CRED%" --host %SERVER% --user %USER%
if errorlevel 1 goto :no_sudo_pw

REM ---------- 5. 远端构建 + 重建容器 ----------
echo [5/6] 在飞牛上构建镜像并重建容器（首次构建较慢, 请耐心等待）...
type "%PW_FILE%" | "%SSH_EXE%" -i "%SSH_KEY%" -o StrictHostKeyChecking=no -o UserKnownHostsFile="%KNOWN_HOSTS%" -o LogLevel=ERROR -p %SSH_PORT% %USER%@%SERVER% "sed -i 's/\r$//' /tmp/huluwa-fnos-deploy.sh; tr -d '\r' | sudo -S -p '' bash /tmp/huluwa-fnos-deploy.sh %APP_DIR% %DATA_DIR% %FRP_DIR% %HOST_PORT% %CONTAINER% %IMAGE% %USER%"
if errorlevel 1 goto :deploy_fail
del /q "%PW_FILE%" 2>nul

REM ---------- 6. 完成 ----------
echo.
echo ========== 部署完成 ==========
echo 访问地址: http://%SERVER%:%HOST_PORT%
echo 数据目录: %DATA_DIR%  (容器内 /app/data)
echo 源码目录: %REMOTE_DIR%
echo Frp 配置: %FRP_DIR%/frpc.ini
echo 查看日志: "%SSH_EXE%" -i "%SSH_KEY%" -p %SSH_PORT% %USER%@%SERVER% "docker logs -f %CONTAINER%"
"%SSH_EXE%" %SSH_OPTS% -p %SSH_PORT% %USER%@%SERVER% "docker ps -a --filter name=huluwa --format '{{.Names}} | {{.Status}} | {{.Ports}}'"
echo.
del /q "%TARBALL%" "%REMOTE_SH%" 2>nul
if not "%HULUWA_NO_PAUSE%"=="1" pause
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
echo [错误] 当前目录缺少 Dockerfile 或 compose.yaml, 请把 deploy_fnos.bat 放在项目根目录。
echo        当前目录: %PROJECT_DIR%
goto :fail

:no_remote_sh
echo [错误] 缺少 scripts\deploy-fnos-remote.sh（飞牛远端部署脚本）。
goto :fail

:need_python
echo [错误] 首次安装公钥需要 Python 3 + paramiko。
echo        请安装 Python 3 后重试, 或手动把 %SSH_KEY%.pub 追加到 %SSH_HOME%/.ssh/authorized_keys
goto :fail

:no_sudo_pw
echo [错误] 未取得 sudo 密码, 无法执行特权步骤。
echo        可设置环境变量 HULUWA_FNOS_SUDO_PW, 或在 %FNOS_CRED% 中提供 password 字段。
goto :fail

:git_pull_fail
echo [错误] 拉取最新代码失败, 请检查 Git 凭据、网络或本地仓库状态。
goto :fail

:tar_fail
echo [错误] 打包源码失败。
goto :fail

:upload_fail
echo [错误] 源码上传失败(%SSH_RETRY% 次重试均失败)。
goto :fail

:deploy_fail
echo.
echo [错误] 飞牛上的构建或启动失败, 详见上方输出。
echo        线上容器未被改动（构建失败时）; 如需排查请登录飞牛执行:
echo          docker logs --tail 100 %CONTAINER%
goto :fail

:fail
del /q "%PW_FILE%" 2>nul
echo.
echo ---------- 部署失败 ----------
echo.
if not "%HULUWA_NO_PAUSE%"=="1" pause
endlocal
exit /b 1