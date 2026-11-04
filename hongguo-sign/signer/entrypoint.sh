#!/bin/bash
set -euo pipefail

ADB_TARGET="${ADB_TARGET:-android:5555}"
APK_PATH="${APK_PATH:-/apk/base.apk}"
TARGET_PKG="${TARGET_PKG:-com.phoenix.read}"
FRIDA_SERVER_BIN="${FRIDA_SERVER_BIN:-/opt/frida/frida-server}"
FRIDA_PORT="${FRIDA_PORT:-27042}"
PORT="${PORT:-8901}"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-300}"
APP_WARMUP="${APP_WARMUP:-30}"

log() { echo "[signer] $*"; }
adb_device() { adb -s "$ADB_TARGET" "$@"; }

adb start-server >/dev/null 2>&1
log "等待 Android：$ADB_TARGET"
deadline=$((SECONDS + BOOT_TIMEOUT))
while ((SECONDS < deadline)); do
    adb connect "$ADB_TARGET" >/dev/null 2>&1 || true
    if [[ "$(adb_device shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
        break
    fi
    sleep 3
done
if [[ "$(adb_device shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != 1 ]]; then
    log 'Android 启动超时'
    exit 1
fi

if [[ "$(adb_device shell id -u | tr -d '\r')" != 0 ]]; then
    adb_device root >/dev/null
    sleep 2
    adb connect "$ADB_TARGET" >/dev/null
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
    adb_device install -r -g "$APK_PATH"
fi

log '启动 x86_64 Frida 服务'
adb_device shell 'pkill -x frida-server' >/dev/null 2>&1 || true
adb_device push "$FRIDA_SERVER_BIN" /data/local/tmp/frida-server >/dev/null
adb_device shell chmod 755 /data/local/tmp/frida-server
adb_device shell "/data/local/tmp/frida-server -l 127.0.0.1:$FRIDA_PORT >/data/local/tmp/frida-server.log 2>&1 </dev/null &"
adb_device forward "tcp:$FRIDA_PORT" "tcp:$FRIDA_PORT" >/dev/null
sleep 3
if ! adb_device shell pidof frida-server | grep -q '[0-9]'; then
    adb_device shell cat /data/local/tmp/frida-server.log
    exit 1
fi

log '启动红果 App'
activity="$(adb_device shell cmd package resolve-activity --brief "$TARGET_PKG" | tr -d '\r' | tail -n 1)"
if [[ "$activity" != "$TARGET_PKG/"* ]]; then
    log "无法解析 App 启动页面：$activity"
    exit 1
fi
adb_device shell am start -n "$activity" >/dev/null
deadline=$((SECONDS + 120))
while ((SECONDS < deadline)); do
    if adb_device shell pidof "$TARGET_PKG" 2>/dev/null | grep -q '[0-9]'; then
        break
    fi
    sleep 2
done
if ! adb_device shell pidof "$TARGET_PKG" 2>/dev/null | grep -q '[0-9]'; then
    log '红果 App 未能启动'
    exit 1
fi
sleep "$APP_WARMUP"

export FRIDA_HOST="127.0.0.1:$FRIDA_PORT" ADB_SERIAL="$ADB_TARGET" TARGET_PKG
log "启动签名接口：$PORT"
exec python /app/sign_server.py --bind 0.0.0.0 --port "$PORT"
