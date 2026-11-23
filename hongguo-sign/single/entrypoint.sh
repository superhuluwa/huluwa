#!/busybox sh
set -eu

# 保留现有部署的宿主机隔离，禁止改用 privileged 或解除 SysRq 保护。
if ! /busybox awk '$5 == "/proc/sysrq-trigger" && $6 ~ /(^|,)ro(,|$)/ { safe = 1 } END { exit !safe }' /proc/self/mountinfo; then
    echo '[hongguo] 缺少 SysRq 只读挂载，拒绝启动' >&2
    exit 1
fi
/busybox mount -t cgroup2 -o remount,rw,nosuid,nodev,noexec cgroup /sys/fs/cgroup

for device in binder hwbinder vndbinder; do
    if [ ! -c "/dev/$device" ]; then
        echo "[hongguo] 缺少 /dev/$device，请先执行 prepare-host.sh" >&2
        exit 1
    fi
done

# Android init 不继承 Docker 的完整环境，将配置交给 chroot 中的启动器。
/busybox mkdir -p /signer-rootfs/run
/busybox env -0 > /signer-rootfs/run/container.env
/busybox chmod 600 /signer-rootfs/run/container.env
: > /hongguo/runtime.conf
for name in PORT BOOT_TIMEOUT APP_WARMUP SIGN_READY_TIMEOUT STARTUP_TIMEOUT RECOVERY_TIMEOUT; do
    case "$name" in
        PORT) value="${PORT:-8901}" ;;
        BOOT_TIMEOUT) value="${BOOT_TIMEOUT:-300}" ;;
        APP_WARMUP) value="${APP_WARMUP:-30}" ;;
        SIGN_READY_TIMEOUT) value="${SIGN_READY_TIMEOUT:-120}" ;;
        STARTUP_TIMEOUT) value="${STARTUP_TIMEOUT:-600}" ;;
        RECOVERY_TIMEOUT) value="${RECOVERY_TIMEOUT:-300}" ;;
    esac
    case "$value" in
        ''|*[!0-9]*) echo "[hongguo] $name 必须为非负整数" >&2; exit 1 ;;
    esac
    if [ "$name" != APP_WARMUP ] && [ "$value" -eq 0 ]; then
        echo "[hongguo] $name 必须大于零" >&2
        exit 1
    fi
    /busybox printf '%s=%s\n' "$name" "$value" >> /hongguo/runtime.conf
done
. /hongguo/runtime.conf
if [ "$PORT" -gt 65535 ] || [ "$STARTUP_TIMEOUT" -le "$((BOOT_TIMEOUT + APP_WARMUP + SIGN_READY_TIMEOUT))" ]; then
    echo '[hongguo] 端口必须有效，STARTUP_TIMEOUT 必须大于启动各阶段的超时总和' >&2
    exit 1
fi

echo '[hongguo] 启动 Android；完成引导后自动启动 App、Frida 和签名接口'
# Android init 必须保留为 PID 1，由它管理 zygote 和签名服务的进程组。
exec /init qemu=1 androidboot.hardware=redroid "$@"
