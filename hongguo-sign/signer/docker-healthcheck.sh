#!/bin/sh
# 容器健康检查：签名服务能否真正返回签名头
# 注意：签名依赖 App 在线，失败也可能只是 App 未就绪

set -e

PORT="${PORT:-8901}"

if ! curl -sf --max-time 40 "http://127.0.0.1:${PORT}/health" -o /tmp/hc.json; then
    echo "health endpoint unreachable"
    exit 1
fi

if grep -q '"ok": *true' /tmp/hc.json; then
    echo "ok"
    exit 0
fi

echo "sign not ready: $(cat /tmp/hc.json 2>/dev/null)"
exit 1
