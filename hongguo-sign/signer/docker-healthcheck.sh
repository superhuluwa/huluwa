#!/bin/sh
# 完整设备身份和六个签名头都就绪后，才将容器标记为健康。
set -eu
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
PORT="${PORT:-8901}"

if ! result="$(curl -fsS --max-time 40 "http://127.0.0.1:${PORT}/health")"; then
    echo '签名链路尚未就绪'
    exit 1
fi
if printf '%s' "$result" | grep -q '"ok": *true'; then
    echo '签名和设备档案已就绪'
    exit 0
fi
printf '签名链路未就绪：%s\n' "$result"
exit 1
