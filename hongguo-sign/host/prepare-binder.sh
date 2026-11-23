#!/bin/bash
set -euo pipefail

modprobe binder_linux devices=binder,hwbinder,vndbinder
for device in binder hwbinder vndbinder; do
    if [[ ! -c "/dev/$device" ]]; then
        echo "缺少 /dev/$device，Android 容器无法启动。请检查 binder_linux 模块参数。" >&2
        exit 1
    fi
    chmod 666 "/dev/$device"
done
