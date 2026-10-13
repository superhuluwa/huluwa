#!/usr/bin/env bash
# 葫芦娃助手 · 飞牛 fnOS 远端部署脚本
#
# 由 deploy_fnos.bat 上传到 /tmp 后以 root 身份执行（飞牛默认禁止 root 登录，
# 因此由普通管理员账号通过 sudo 调用）。职责：
#   1. 校验 Docker / Compose / socket
#   2. 解压源码、规范换行
#   3. 准备数据目录与 Frp 目录属主（容器内以 10001 运行）
#   4. 生成 .env、构建镜像、重建容器
#   5. 校验容器内可访问宿主机 Docker socket、等待健康检查通过
#
# 用法：
#   sudo bash deploy-fnos-remote.sh APP_DIR DATA_DIR FRP_DIR HOST_PORT CONTAINER IMAGE DEPLOY_USER

set -euo pipefail

if [ "$#" -ne 7 ]; then
  echo "用法: sudo bash $0 APP_DIR DATA_DIR FRP_DIR HOST_PORT CONTAINER IMAGE DEPLOY_USER" >&2
  exit 2
fi

APP_DIR="$1"
DATA_DIR="$2"
FRP_DIR="$3"
HOST_PORT="$4"
CONTAINER="$5"
IMAGE="$6"
DEPLOY_USER="$7"

SRC_DIR="${APP_DIR}/src"
TARBALL="/tmp/huluwa-src.tar.gz"
BUILD_LOG="/tmp/huluwa-build.log"

log()  { printf '\033[36m[%s] %s\033[0m\n' "$(date '+%H:%M:%S')" "$*"; }
warn() { printf '\033[33m[%s] %s\033[0m\n' "$(date '+%H:%M:%S')" "$*" >&2; }
fail() { printf '\033[31m[ERROR] %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || fail "需要 root 权限运行（请通过 sudo 调用）"

# ---------- 1. 环境检查 ----------
command -v docker >/dev/null 2>&1 || fail "未检测到 docker"
docker compose version >/dev/null 2>&1 || fail "未检测到 docker compose v2"

if ! docker info >/dev/null 2>&1; then
  warn "Docker 守护进程未运行，尝试启动 docker.service"
  systemctl start docker 2>/dev/null || true
  sleep 3
fi
docker info >/dev/null 2>&1 || fail "Docker 守护进程不可用，请检查：systemctl status docker"
[ -S /var/run/docker.sock ] || fail "/var/run/docker.sock 不存在，请确认飞牛 Docker 服务已启动"

SOCKET_GID="$(stat -c '%g' /var/run/docker.sock)"
log "宿主机 /var/run/docker.sock 的 GID = ${SOCKET_GID}"

# ---------- 2. 解压源码 ----------
[ -f "${TARBALL}" ] || fail "未找到源码包 ${TARBALL}"
log "解压源码到 ${SRC_DIR}"
rm -rf "${SRC_DIR}"
mkdir -p "${SRC_DIR}"
tar -xzf "${TARBALL}" -C "${SRC_DIR}"

# 上传自 Windows，统一规范文本文件换行，避免 CRLF 影响构建
find "${SRC_DIR}" -type f \
  \( -name '*.sh' -o -name 'mvnw' -o -name 'Dockerfile' -o -name '*.yml' -o -name '*.yaml' \
     -o -name '*.properties' -o -name '.dockerignore' \) \
  -exec sed -i 's/\r$//' {} + 2>/dev/null || true

[ -f "${SRC_DIR}/compose.yaml" ] || fail "源码包中缺少 compose.yaml"
[ -f "${SRC_DIR}/Dockerfile" ]   || fail "源码包中缺少 Dockerfile"
cd "${SRC_DIR}"

# 飞牛的镜像加速源拉不到 docker/dockerfile:1 这个前端镜像（HEAD 返回 401），
# 而 Docker 28 自带的 BuildKit 已支持本项目用到的 RUN --mount=type=cache，
# 因此去掉 # syntax= 行改用内置前端，避免构建在一开始就失败。
if grep -q '^# syntax=docker/dockerfile:1' Dockerfile; then
  sed -i '/^# syntax=docker\/dockerfile:1/d' Dockerfile
  log "已改用 Docker 内置构建前端（移除 Dockerfile 的 syntax 行）"
fi

# ---------- 3. 数据目录 ----------
mkdir -p "${DATA_DIR}"
chown -R 10001:10001 "${DATA_DIR}"
log "数据目录：${DATA_DIR}（属主 10001:10001 -> 容器内 /app/data）"

# ---------- 4. Frp 配置目录 ----------
mkdir -p "${FRP_DIR}"
if [ -f "${FRP_DIR}/frpc.ini" ]; then
  # 容器内进程以 10001 运行，需要该目录可写才能原子替换 frpc.ini。
  # 飞牛自带 setfacl，优先用 ACL（不动属主）。
  if command -v setfacl >/dev/null 2>&1; then
    setfacl -m u:10001:rwx "${FRP_DIR}" 2>/dev/null || true
    setfacl -m u:10001:rw  "${FRP_DIR}/frpc.ini" 2>/dev/null || true
  fi
  chown 10001:10001 "${FRP_DIR}/frpc.ini" 2>/dev/null || true
  log "Frp 配置：${FRP_DIR}/frpc.ini（已授予 uid 10001 读写）"
else
  chown 10001:10001 "${FRP_DIR}"
  warn "未找到 ${FRP_DIR}/frpc.ini，Frp 管理页将无法读写配置"
fi

# ---------- 5. 生成 .env ----------
# COMPOSE_PROJECT_NAME 固定为 huluwa：源码位于 src/ 子目录，不指定项目名会变成 src，
# 与迁移过来的既有容器/网络对不上。
cat > .env <<ENV
HULUWA_BIND_ADDRESS=0.0.0.0
HULUWA_PORT=${HOST_PORT}
HULUWA_DATA_DIR=${DATA_DIR}
HULUWA_FRP_DIR=${FRP_DIR}
DOCKER_GID=${SOCKET_GID}
COMPOSE_PROJECT_NAME=huluwa
ENV
chmod 600 .env
log "已生成 ${SRC_DIR}/.env"

# 飞牛没有 SELinux，compose.yaml 里 socket 挂载的 :z 标记无意义；
# 去掉可避免个别 Docker 版本误报权限问题。
if grep -q 'docker.sock:/var/run/docker.sock:z' compose.yaml; then
  sed -i 's#\(/var/run/docker.sock:/var/run/docker.sock\):z#\1#' compose.yaml
  log "已移除 compose.yaml 中 socket 挂载的 :z（飞牛无 SELinux）"
fi

# ---------- 6. 构建镜像 ----------
log "构建镜像 ${IMAGE}（首次构建需拉取前端/后端依赖，可能较慢）"
if ! docker build -t "${IMAGE}" . > "${BUILD_LOG}" 2>&1; then
  tail -n 25 "${BUILD_LOG}" >&2 || true
  fail "Docker 镜像构建失败，完整日志：${BUILD_LOG}（线上容器未被改动）"
fi
tail -n 3 "${BUILD_LOG}" || true

# ---------- 7. 重建容器 ----------
log "重建容器 ${CONTAINER}"
docker compose down --remove-orphans >/dev/null 2>&1 || true
docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
docker compose up -d --no-build --remove-orphans 2>&1 | tail -n 12

# ---------- 8. 校验容器内可访问宿主机 Docker socket ----------
if ! docker exec "${CONTAINER}" sh -c 'curl -sS --max-time 5 --unix-socket /var/run/docker.sock http://localhost/_ping' 2>/dev/null | grep -q OK; then
  docker inspect "${CONTAINER}" --format '{{json .Mounts}}' >&2 2>&1 || true
  docker inspect "${CONTAINER}" --format '{{json .HostConfig.GroupAdd}}' >&2 2>&1 || true
  fail "容器内无法访问宿主机 Docker socket（检查 .env 的 DOCKER_GID=${SOCKET_GID}）"
fi
log "Docker socket 挂载校验通过"

# ---------- 9. 等待健康检查 ----------
log "等待服务健康检查通过"
for _ in $(seq 1 40); do
  if curl -fsS --max-time 3 "http://127.0.0.1:${HOST_PORT}/api/auth/status" >/dev/null 2>&1; then
    log "健康检查通过"
    docker ps --filter "name=${CONTAINER}" --format '{{.Names}} | {{.Status}} | {{.Ports}}'
    # 便于后续用普通账号直接维护源码目录
    chown -R "${DEPLOY_USER}:Users" "${SRC_DIR}" 2>/dev/null || true
    exit 0
  fi
  sleep 3
done

docker logs --tail 40 "${CONTAINER}" >&2 2>&1 || true
fail "新容器启动后健康检查未通过"