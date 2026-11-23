#!/busybox sh
set -eu
exec > /hongguo/log.pipe 2>&1
. /hongguo/runtime.conf

uptime_seconds() {
    read -r elapsed rest < /proc/uptime
    echo "${elapsed%.*}"
}

last_ready="$(uptime_seconds)"
budget="$STARTUP_TIMEOUT"
while :; do
    if /busybox timeout 45 /busybox wget -q -T 40 -O /hongguo/health.json "http://127.0.0.1:$PORT/health" 2>/dev/null &&
        /busybox grep -q '"ok": *true' /hongguo/health.json; then
        last_ready="$(uptime_seconds)"
        budget="$RECOVERY_TIMEOUT"
    fi
    now="$(uptime_seconds)"
    if [ "$((now - last_ready))" -ge "$budget" ]; then
        echo "[hongguo] 完整签名链路连续 $budget 秒未就绪，重建整个容器运行环境"
        # 仅终止本容器 PID 命名空间的 init，Docker 重启策略负责恢复；不调用 Android reboot。
        /busybox kill -KILL 1
        exit 1
    fi
    /busybox sleep 20
done
