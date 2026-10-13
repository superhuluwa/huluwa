#!/usr/bin/env bash
# 葫芦娃助手：CentOS / Docker 一键部署脚本
# 用法：sudo ./deploy-centos.sh
set -euo pipefail

cd "$(dirname "$0")"

log()  { printf '\033[36m[%s] %s\033[0m\n' "$(date '+%H:%M:%S')" "$*"; }
fail() { printf '\033[31m[ERROR] %s\033[0m\n' "$*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "未检测到 docker，请先安装 Docker Engine（CentOS: yum install -y docker-ce && systemctl enable --now docker）"
docker compose version >/dev/null 2>&1 || fail "未检测到 docker compose v2，请安装 docker-compose-plugin"
[ -S /var/run/docker.sock ] || fail "/var/run/docker.sock 不存在，请确认 Docker 服务已启动：systemctl start docker"

if [ ! -f .env ]; then
  log "未找到 .env，从 .env.example 生成"
  cp .env.example .env
fi

# 读取数据目录（与 compose 保持一致，默认 ./data）
DATA_DIR="$(grep -E '^HULUWA_DATA_DIR=' .env | tail -n1 | cut -d= -f2- || true)"
DATA_DIR="${DATA_DIR:-./data}"
case "${DATA_DIR}" in
  /*) ;;
  *) DATA_DIR="$(pwd)/${DATA_DIR#./}" ;;
esac
log "数据目录：${DATA_DIR}"
mkdir -p "${DATA_DIR}"
# HULUWA_MEDIA_DIR 仅保留旧版本文件；新下载与迅雷、qBittorrent 使用同一绝对路径。
DOWNLOAD_DIR="/vol1/1000/downloads"
MEDIA_DIR="$(grep -E '^HULUWA_MEDIA_DIR=' .env | tail -n1 | cut -d= -f2- || true)"
MEDIA_DIR="${MEDIA_DIR:-./data/media}"
mkdir -p "${MEDIA_DIR}" "${DOWNLOAD_DIR}"
chown -R 10001:10001 "${DATA_DIR}"
# 仅授权共享根目录及第一层剧名目录，不递归修改已有媒体文件属主。
if command -v setfacl >/dev/null 2>&1; then
  setfacl -m u:10001:rwx,d:u:10001:rwx "${DOWNLOAD_DIR}" || log "下载目录授权失败，请检查 uid 10001 的写权限"
  find "${DOWNLOAD_DIR}" -mindepth 1 -maxdepth 1 -type d \
    -exec setfacl -m u:10001:rwx,d:u:10001:rwx {} + || log "已有剧名目录授权失败，请检查目录权限"
else
  log "请确认下载目录 ${DOWNLOAD_DIR} 及已有剧名目录可由 uid 10001 写入"
fi
log "共用下载目录：${DOWNLOAD_DIR}（容器内同一路径，完成文件直接保存到剧名目录）"

# Frp 管理：宿主机 frpc.ini 目录（compose 挂载到容器 /app/frp）
FRP_DIR_DEFAULT="/root/frp"
FRP_DIR="$(grep -E '^HULUWA_FRP_DIR=' .env | tail -n1 | cut -d= -f2- || true)"
FRP_DIR="${FRP_DIR:-$FRP_DIR_DEFAULT}"
if [ -f "${FRP_DIR}/frpc.ini" ]; then
  log "Frp 配置目录：${FRP_DIR}/frpc.ini"
  # 容器内进程以 10001 运行，需要该目录可写才能创建临时文件并原子替换 frpc.ini。
  # 优先用 ACL（不动属主）；没有 setfacl 时只调整目录自身属主与权限，不改动目录内其它文件。
  if command -v setfacl >/dev/null 2>&1; then
    setfacl -m u:10001:rwx "${FRP_DIR}" 2>/dev/null && log "已授予 uid 10001 对 ${FRP_DIR} 的访问（ACL）"
    setfacl -m u:10001:rw "${FRP_DIR}/frpc.ini" 2>/dev/null || true
  else
    chown 10001:10001 "${FRP_DIR}"
    chmod u+rwx "${FRP_DIR}"
    chown 10001:10001 "${FRP_DIR}/frpc.ini"
    log "已调整 ${FRP_DIR} 属主，允许容器内 10001 读写（安装 setfacl 可改为 ACL 方式）"
  fi
else
  log "警告：未找到 ${FRP_DIR}/frpc.ini，Frp 管理页将无法读写配置"
fi

SOCKET_GID="$(stat -c '%g' /var/run/docker.sock)"
log "宿主机 /var/run/docker.sock 的 GID = ${SOCKET_GID}"
if grep -q '^DOCKER_GID=' .env; then
  sed -i "s/^DOCKER_GID=.*/DOCKER_GID=${SOCKET_GID}/" .env
else
  printf '\nDOCKER_GID=%s\n' "${SOCKET_GID}" >> .env
fi

# CentOS 7 默认 SELinux enforcing：compose.yaml 已给 socket 挂载标注 :z
if command -v getenforce >/dev/null 2>&1 && [ "$(getenforce)" = "Enforcing" ]; then
  log "SELinux 处于 Enforcing，compose.yaml 已使用 :z 重新标记 socket"
fi

log "构建镜像并启动容器"
docker compose up -d --build

log "等待服务健康检查通过"
CONSOLE_CONTAINER="$(docker compose ps -q huluwa || true)"
for i in $(seq 1 30); do
  STATUS="$(docker inspect -f '{{.State.Health.Status}}' "${CONSOLE_CONTAINER}" 2>/dev/null || echo starting)"
  [ "$STATUS" = "healthy" ] && break
  sleep 2
done

log "容器状态：$(docker inspect -f '{{.State.Status}} / 健康：{{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}' "${CONSOLE_CONTAINER}" 2>/dev/null || echo unknown)"

if grep -q '^HULUWA_BIND_ADDRESS=127.0.0.1' .env; then
  log "服务已启动，且仅监听 127.0.0.1。"
  log "在服务器本机执行以下命令创建 SSH 隧道后，用浏览器打开 http://127.0.0.1:8000 ："
  log "  ssh -L 8000:127.0.0.1:8000 <你的CentOS用户>@<服务器IP>"
  log "完成管理员初始化后，把 .env 中 HULUWA_BIND_ADDRESS 改为 0.0.0.0 并执行 docker compose up -d 即可远程访问。"
else
  log "服务已启动，请通过 http://<服务器IP>:${HULUWA_PORT:-8000} 访问。"
fi

log "查看日志：docker compose logs -f huluwa"
