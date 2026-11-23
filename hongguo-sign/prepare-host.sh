#!/bin/bash
set -euo pipefail

if [[ $EUID != 0 || $(uname -m) != x86_64 ]]; then
    echo '请在 x86_64 Debian 主机上以 root 执行。' >&2
    exit 1
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# 显式设备模式来自 Redroid 官方 Debian 文档，不要求重新编译 binderfs。
install -d /etc/modprobe.d /etc/modules-load.d /etc/udev/rules.d
printf '%s\n' 'binder_linux' > /etc/modules-load.d/hongguo-binder.conf
printf '%s\n' 'options binder_linux devices=binder,hwbinder,vndbinder' > /etc/modprobe.d/hongguo-binder.conf
printf '%s\n' 'KERNEL=="binder", MODE="0666"' 'KERNEL=="hwbinder", MODE="0666"' 'KERNEL=="vndbinder", MODE="0666"' > /etc/udev/rules.d/99-hongguo-binder.rules
udevadm control --reload-rules
modprobe binder_linux devices=binder,hwbinder,vndbinder
for device in binder hwbinder vndbinder; do
    if [[ ! -c /dev/$device ]]; then
        echo "缺少 /dev/$device。模块参数已保存，请正常重启后重新执行本脚本。" >&2
        exit 2
    fi
    chmod 666 "/dev/$device"
done

if (( $(cat /proc/sys/vm/max_map_count) < 262144 )); then
    printf '%s\n' 'vm.max_map_count=262144' > /etc/sysctl.d/99-hongguo.conf
    sysctl -p /etc/sysctl.d/99-hongguo.conf
fi
# 确保宿主机重启时先恢复 Binder，再由 Docker 自动恢复容器；不重启 Docker。
install -d /usr/local/libexec /etc/systemd/system/docker.service.d
install -m 755 "$script_dir/host/prepare-binder.sh" /usr/local/libexec/hongguo-prepare-binder
install -m 644 "$script_dir/host/hongguo-binder.service" /etc/systemd/system/hongguo-binder.service
cat > /etc/systemd/system/docker.service.d/hongguo-binder.conf <<'UNIT'
[Unit]
Wants=hongguo-binder.service
After=hongguo-binder.service
UNIT
systemctl daemon-reload
systemctl enable --now hongguo-binder.service

# 保留双容器回退方案使用的网络，新部署使用独立数据卷。
docker network inspect hongguo-native >/dev/null 2>&1 || docker network create hongguo-native >/dev/null
docker volume create hongguo-frida-data >/dev/null
echo '宿主机配置完成。'
