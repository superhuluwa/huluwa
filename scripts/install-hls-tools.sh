#!/usr/bin/env sh
# 镜像构建阶段下载官方固定版本；两个架构的校验值来自 GitHub Release 元数据。
set -eu

case "${1:?需要目标架构}" in
  amd64)
    package='N_m3u8DL-RE_v0.6.0-beta_linux-x64_20260629.tar.gz'
    checksum='2ebcd450594a08c0de0bcd502a80c5d3f89a702a0fa6604199527bb8d6eee4db'
    ;;
  arm64)
    package='N_m3u8DL-RE_v0.6.0-beta_linux-arm64_20260629.tar.gz'
    checksum='17f3c2c2372cbb859bf42e77bf59f7a049e5642fc4f6d234195e70bbea178299'
    ;;
  *) printf '不支持的目标架构：%s\n' "$1" >&2; exit 1 ;;
esac

mkdir -p /tmp/hls-tool /out
curl --fail --location --retry 3 --connect-timeout 20 \
  "https://github.com/nilaoda/N_m3u8DL-RE/releases/download/v0.6.0-beta/${package}" \
  --output /tmp/hls-tool.tar.gz
printf '%s  %s\n' "$checksum" /tmp/hls-tool.tar.gz | sha256sum --check --status
tar -xzf /tmp/hls-tool.tar.gz -C /tmp/hls-tool
binary_path="$(find /tmp/hls-tool -type f -name N_m3u8DL-RE -print -quit)"
[ -n "$binary_path" ] || { printf '官方下载包中没有找到下载工具\n' >&2; exit 1; }
install -m 0755 "$binary_path" /out/N_m3u8DL-RE
