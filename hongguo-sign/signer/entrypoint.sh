#!/bin/bash
set -euo pipefail

ADB_TARGET="${ADB_TARGET:-android:5555}"
APK_PATH="${APK_PATH:-/apk/base.apk}"
TARGET_PKG="${TARGET_PKG:-com.phoenix.read}"
FRIDA_SERVER_BIN="${FRIDA_SERVER_BIN:-/opt/frida/frida-server}"
FRIDA_PORT="${FRIDA_PORT:-27042}"
FRIDA_DIRECT="${FRIDA_DIRECT:-0}"
PORT="${PORT:-8901}"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-300}"
APP_WARMUP="${APP_WARMUP:-30}"
APP_START_MODE="${APP_START_MODE:-provider}"
SIGN_READY_TIMEOUT="${SIGN_READY_TIMEOUT:-120}"
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

log() { echo "[signer] $*"; }
# 给每条 ADB 命令设置上限，断线后不能永远卡在等待设备或端口转发。
adb_device() { timeout -k 5 25 adb -s "$ADB_TARGET" "$@"; }
connect_device() { timeout -k 5 10 adb connect "$ADB_TARGET" >/dev/null 2>&1 || true; }

http_pid=''
watch_pid=''
cleanup() {
    trap - EXIT TERM INT
    for child in "$watch_pid" "$http_pid"; do
        if [[ -n "$child" ]]; then
            kill -TERM "$child" 2>/dev/null || true
        fi
    done
    wait 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT

timeout -k 5 10 adb start-server >/dev/null 2>&1
# 清理重启前留下的离线会话，让 ADB 重新建立真实连接。
timeout -k 5 10 adb disconnect "$ADB_TARGET" >/dev/null 2>&1 || true
log "等待 Android：$ADB_TARGET"
deadline=$((SECONDS + BOOT_TIMEOUT))
booted=0
while ((SECONDS < deadline)); do
    connect_device
    if [[ "$(adb_device shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
        booted=1
        break
    fi
    sleep 3
done
if ((booted == 0)); then
    log 'Android 启动超时，退出后自动重新初始化'
    exit 1
fi

if [[ "$(adb_device shell id -u | tr -d '\r')" != 0 ]]; then
    adb_device root >/dev/null
    sleep 2
    connect_device
    adb_device wait-for-device
fi
if [[ "$(adb_device shell id -u | tr -d '\r')" != 0 ]]; then
    log '需要 root ADB，以安装 Frida 服务'
    exit 1
fi

if ! adb_device shell pm path "$TARGET_PKG" 2>/dev/null | grep -q '^package:'; then
    if [[ ! -f "$APK_PATH" ]]; then
        log "缺少红果安装包：$APK_PATH"
        exit 1
    fi
    log '安装红果 App'
    timeout -k 5 180 adb -s "$ADB_TARGET" install -r -g "$APK_PATH"
fi

# 清除上次注入及 App 崩溃后残留的子进程，不修改 App 数据。
adb_device shell am force-stop "$TARGET_PKG"
log '启动 x86_64 Frida 服务'
for old_pid in $(adb_device shell pidof frida-server | tr -d '\r'); do
    if [[ "$old_pid" =~ ^[0-9]+$ ]]; then
        adb_device shell kill -9 "$old_pid" >/dev/null 2>&1 || true
    fi
done
sleep 1
adb_device push "$FRIDA_SERVER_BIN" /data/local/tmp/frida-server >/dev/null
adb_device shell chmod 755 /data/local/tmp/frida-server
adb_device shell "/data/local/tmp/frida-server -l 127.0.0.1:$FRIDA_PORT >/data/local/tmp/frida-server.log 2>&1 </dev/null &"
if [[ "$FRIDA_DIRECT" != 1 ]]; then
    # 双容器回退方案需要 ADB 转发；单容器共用网络命名空间，直接访问监听端口。
    adb_device forward "tcp:$FRIDA_PORT" "tcp:$FRIDA_PORT" >/dev/null
fi
export FRIDA_HOST="127.0.0.1:$FRIDA_PORT" ADB_SERIAL="$ADB_TARGET" TARGET_PKG PORT

# 不依赖固定的三秒延时，必须等 Frida 能完成一次真实的协议调用。
deadline=$((SECONDS + 60))
frida_ready=0
while ((SECONDS < deadline)); do
    if timeout -k 5 10 python - <<'PY' >/dev/null 2>&1
import os
import frida
frida.get_device_manager().add_remote_device(os.environ['FRIDA_HOST']).enumerate_processes()
PY
    then
        frida_ready=1
        break
    fi
    sleep 2
done
if ((frida_ready == 0)); then
    log 'Frida 启动超时'
    adb_device shell cat /data/local/tmp/frida-server.log || true
    exit 1
fi

if [[ "$APP_START_MODE" == provider ]]; then
    log '通过 ContentProvider 初始化红果主进程'
    adb_device shell content call --uri "content://$TARGET_PKG.file_provider" --method huluwa_sign_init >/dev/null
elif [[ "$APP_START_MODE" == activity ]]; then
    log '启动红果界面（用于首次引导）'
    activity="$(adb_device shell cmd package resolve-activity --brief "$TARGET_PKG" | tr -d '\r' | tail -n 1)"
    if [[ "$activity" != "$TARGET_PKG/"* ]]; then
        log "无法解析 App 启动页面：$activity"
        exit 1
    fi
    adb_device shell am start -n "$activity" >/dev/null
else
    log "不支持的 App 启动方式：$APP_START_MODE"
    exit 1
fi
deadline=$((SECONDS + 120))
app_started=0
while ((SECONDS < deadline)); do
    if adb_device shell pidof "$TARGET_PKG" 2>/dev/null | grep -q '[0-9]'; then
        app_started=1
        break
    fi
    sleep 2
done
if ((app_started == 0)); then
    log '红果 App 未能启动'
    exit 1
fi
sleep "$APP_WARMUP"

log "启动签名接口：$PORT"
python /app/sign_server.py --bind 0.0.0.0 --port "$PORT" &
http_pid=$!
# 启动后的首个健康检查同样要实际获得完整签名和设备档案。
deadline=$((SECONDS + SIGN_READY_TIMEOUT))
ready=0
while ((SECONDS < deadline)); do
    if ! kill -0 "$http_pid" 2>/dev/null; then
        log '签名接口启动失败'
        exit 1
    fi
    if /app/docker-healthcheck.sh; then
        ready=1
        break
    fi
    sleep 3
done
if ((ready == 0)); then
    log '签名和设备档案未能就绪，重新初始化'
    exit 1
fi
log '完整签名链路已就绪'

# Android 框架、App 或 Frida 失联时退出，让上级重新执行完整初始化。
(
    failures=0
    while sleep 30; do
        if /app/docker-healthcheck.sh >/dev/null 2>&1; then
            failures=0
        else
            failures=$((failures + 1))
        fi
        if ((failures >= 3)); then
            log '完整签名链路连续不可用，重新初始化 App 和 Frida'
            kill -TERM "$http_pid" 2>/dev/null || true
            exit
        fi
    done
) &
watch_pid=$!
wait "$http_pid" || true
# 即使 Python 意外正常退出，也交给上级恢复服务。
exit 1
