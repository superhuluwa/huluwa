#!/busybox sh
set -eu

# Android 的故障看门狗会写 SysRq；启动前必须保留 Docker 的只读挂载。
if ! /busybox awk '$5 == "/proc/sysrq-trigger" && $6 ~ /(^|,)ro(,|$)/ { safe = 1 } END { exit !safe }' /proc/self/mountinfo; then
    echo '[android] 缺少 SysRq 只读挂载，拒绝启动' >&2
    exit 1
fi

# Docker 已将 cgroup 根限制在本容器；只解除该挂载的只读标志。
/busybox mount -t cgroup2 -o remount,rw,nosuid,nodev,noexec cgroup /sys/fs/cgroup
echo '[android] 已启用本容器 cgroup 的写权限'
exec /init qemu=1 androidboot.hardware=redroid "$@"
