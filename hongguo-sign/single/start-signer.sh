#!/busybox sh
set -eu
exec > /proc/1/fd/1 2> /proc/1/fd/2

root=/signer-rootfs
# 递归绑定以保留 Docker 对 /proc 下危险节点的只读和屏蔽挂载。
for directory in dev proc; do
    if ! /busybox mountpoint -q "$root/$directory"; then
        /busybox mount -o rbind "/$directory" "$root/$directory"
    fi
done
/busybox cp /etc/resolv.conf "$root/etc/resolv.conf"
/busybox cp /etc/hosts "$root/etc/hosts"
/busybox mkdir -p /data/local/hongguo-signer/adb "$root/root/.android"
if ! /busybox mountpoint -q "$root/root/.android"; then
    /busybox mount -o bind /data/local/hongguo-signer/adb "$root/root/.android"
fi
exec /busybox chroot "$root" /usr/local/bin/python3 /app/launch.py
