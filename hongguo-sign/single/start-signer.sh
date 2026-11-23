#!/busybox sh
set -eu
exec > /hongguo/log.pipe 2>&1

root=/signer-rootfs
# 递归绑定以保留 Docker 对 /proc 下危险节点的只读和屏蔽挂载。
for directory in dev proc; do
    if ! /busybox mountpoint -q "$root/$directory"; then
        /busybox mount -o rbind "/$directory" "$root/$directory"
    fi
done
# Android 服务可能处于不同的挂载命名空间，显式保留 chroot 内的内核只读保护。
for node in sys sysrq-trigger; do
    if ! /busybox mountpoint -q "$root/proc/$node"; then
        /busybox mount -o bind "$root/proc/$node" "$root/proc/$node"
    fi
    /busybox mount -o remount,bind,ro,nosuid,nodev,noexec "$root/proc/$node" "$root/proc/$node"
done
/busybox mkdir -p /data/local/hongguo-signer/adb "$root/root/.android"
if ! /busybox mountpoint -q "$root/root/.android"; then
    /busybox mount -o bind /data/local/hongguo-signer/adb "$root/root/.android"
fi
exec /busybox chroot "$root" /usr/local/bin/python3 /app/launch.py
