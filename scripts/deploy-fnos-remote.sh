#!/usr/bin/env bash
# 葫芦娃助手 · 飞牛 fnOS 远端部署脚本
#
# 由 deploy_fnos.bat 上传到 /tmp 后以 root 身份执行（飞牛默认禁止 root 登录，
# 因此由普通管理员账号通过 sudo 调用）。职责：
#   1. 校验 Docker / Compose / socket
#   2. 保障公共基础镜像可用（本地缓存优先，缺失才联网，支持离线备份恢复）
#   3. 解压源码、规范换行
#   4. 准备数据目录与 Frp 目录属主（容器内以 10001 运行）
#   5. 生成 .env、按需构建镜像（复用层缓存）、按需重建容器
#   6. 校验容器内可访问宿主机 Docker socket、等待健康检查通过
#
# 增量策略（避免重复劳动）：
#   - 基础镜像：本地镜像命中即不访问网络；缺失时先查离线备份，再依次尝试备用镜像源，
#     避免飞牛镜像加速源故障时构建在第一步就失败。
#   - 镜像构建：Docker 按文件内容复用分层；源码指纹与上次一致时整体跳过构建。
#   - 容器重建：镜像 ID 与运行配置都没变时保持容器运行，服务零中断。
#
# 用法：
#   sudo bash deploy-fnos-remote.sh APP_DIR DATA_DIR FRP_DIR HOST_PORT CONTAINER IMAGE DEPLOY_USER [LEGACY_MEDIA_DIR] [BACKUP_BASE_IMAGES]

set -euo pipefail

if [ "$#" -lt 7 ]; then
  echo "用法: sudo bash $0 APP_DIR DATA_DIR FRP_DIR HOST_PORT CONTAINER IMAGE DEPLOY_USER [LEGACY_MEDIA_DIR] [--no-base-image-backup]" >&2
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
DOWNLOAD_DIR="/vol1/1000/downloads"
# 剩余参数：可选的位置参数（旧媒体目录）与命名开关（--no-base-image-backup）。
# 用命名开关而不是固定位置，避免与位置参数互相挤压。
MEDIA_DIR="${HULUWA_MEDIA_DIR:-}"
BACKUP_BASE_IMAGES="1"
for arg in "${@:8}"; do
  case "${arg}" in
    --no-base-image-backup) BACKUP_BASE_IMAGES="0" ;;
    --*) fail "未知参数：${arg}" ;;
    *) MEDIA_DIR="${arg}" ;;
  esac
done
# 源码目录随后会被替换，保留旧媒体挂载供历史文件和日志只读访问。
if [ -z "$MEDIA_DIR" ] && [ -f "${SRC_DIR}/.env" ]; then
  MEDIA_DIR="$(sed -n 's/^HULUWA_MEDIA_DIR=//p' "${SRC_DIR}/.env" | tail -n 1)"
fi
MEDIA_DIR="${MEDIA_DIR:-${DATA_DIR}/media}"
case "$MEDIA_DIR" in
  /*) ;;
  *) printf '飞牛媒体目录必须是绝对路径：%s\n' "$MEDIA_DIR" >&2; exit 2 ;;
esac

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
# ---------- 1b. 公共基础镜像缓存 ----------
# Dockerfile 的四个基础镜像属于「长期资产」：飞牛的镜像加速源会间歇性返回 401，
# 飞牛自身也会清理没被容器占用的镜像。策略是本地有就直接复用，缺了先看离线备份，
# 最后才联网拉取。这样既不重复下载，也不会再出现「基础镜像丢失 -> 构建第一步失败」。
BASE_IMAGES=(
  "node:22-bookworm-slim"
  "maven:3.9.9-eclipse-temurin-21"
  "eclipse-temurin:21-jre-noble"
  "ubuntu:24.04"
)
# 备用镜像源：按顺序尝试，命中即止；/library/ 前缀用于 Docker Hub 官方镜像。
BASE_MIRRORS=(
  "dockerproxy.net"
  "docker.xuanyuan.me"
  "docker.1ms.run"
  "docker.m.daocloud.io"
)
BASE_CACHE_DIR="${APP_DIR}/base-images"

have_image() { docker image inspect "$1" >/dev/null 2>&1; }
backup_file() { printf '%s/%s.tar.gz' "${BASE_CACHE_DIR}" "$(printf '%s' "$1" | tr '/:' '__')"; }

# 从备用源拉取并重打成规范 tag，让 Dockerfile 的 FROM 无需改写。
pull_base_image() {
  local spec="$1" name="${1%%:*}" tag="${1##*:}" mirror
  for mirror in "${BASE_MIRRORS[@]}"; do
    if timeout 900 docker pull "${mirror}/library/${name}:${tag}" >/tmp/huluwa-base-pull.log 2>&1; then
      docker tag "${mirror}/library/${name}:${tag}" "${name}:${tag}"
      docker rmi "${mirror}/library/${name}:${tag}" >/dev/null 2>&1 || true
      return 0
    fi
    log "    ${mirror} 不可用：$(tail -1 /tmp/huluwa-base-pull.log 2>/dev/null | cut -c1-90)"
  done
  return 1
}

log "检查公共基础镜像"
BASE_MISSING=""
for img in "${BASE_IMAGES[@]}"; do
  if have_image "$img"; then
    log "  ✓ ${img}（本地缓存命中，不访问网络）"
  else
    BASE_MISSING="${BASE_MISSING} ${img}"
  fi
done

# 缺失的镜像：离线备份 -> 备用镜像源。备份优先，保证镜像源全线故障时仍可部署。
for img in ${BASE_MISSING}; do
  backup="$(backup_file "${img}")"
  if [ -f "${backup}" ]; then
    log "  离线备份恢复：${img}"
    if gzip -dc "${backup}" | docker load >/dev/null 2>&1 && have_image "${img}"; then
      log "  ✓ ${img} 已从备份恢复"
      continue
    fi
    warn "  备份不可用，改为联网拉取：${img}"
  fi
  log "  联网拉取：${img}"
  pull_base_image "${img}" || fail "基础镜像 ${img} 不可用：本地无缓存、备用源均失败。可稍后重试，或先在飞牛修复镜像加速源。"
  log "  ✓ ${img} 就绪"
done
if [ -z "${BASE_MISSING}" ]; then
  log "四个基础镜像全部命中缓存，构建不会访问镜像仓库"
else
  log "基础镜像准备完成（已补齐缺失项）"
fi

# 可选：把基础镜像导出到 ${BASE_CACHE_DIR}，作为镜像源全挂时的离线恢复来源。
if [ "${BACKUP_BASE_IMAGES}" = "1" ]; then
  mkdir -p "${BASE_CACHE_DIR}"
  log "导出基础镜像离线备份到 ${BASE_CACHE_DIR}"
  for img in "${BASE_IMAGES[@]}"; do
    backup="$(backup_file "${img}")"
    if [ -f "${backup}" ]; then
      log "  ✓ 已存在，跳过：$(basename "${backup}")"
    else
      docker save "${img}" | gzip -1 > "${backup}.tmp" && mv "${backup}.tmp" "${backup}"
      log "  ✓ 已导出：$(basename "${backup}")"
    fi
  done
  log "  备份目录占用：$(du -sh "${BASE_CACHE_DIR}" 2>/dev/null | cut -f1)"
fi


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
[ -f "${SRC_DIR}/scripts/hls-download-dir.sh" ] || fail "源码包中缺少下载目录授权脚本"
cd "${SRC_DIR}"

# 飞牛的镜像加速源拉不到 docker/dockerfile:1 这个前端镜像（HEAD 返回 401），
# 而 Docker 28 自带的 BuildKit 已支持本项目用到的 RUN --mount=type=cache，
# 因此去掉 # syntax= 行改用内置前端，避免构建在一开始就失败。
if grep -q '^# syntax=docker/dockerfile:1' Dockerfile; then
  sed -i '/^# syntax=docker\/dockerfile:1/d' Dockerfile
  log "已改用 Docker 内置构建前端（移除 Dockerfile 的 syntax 行）"
fi

# ---------- 3. 数据目录 ----------
mkdir -p "${DATA_DIR}" "${MEDIA_DIR}" "${DOWNLOAD_DIR}"
chown -R 10001:10001 "${DATA_DIR}"
log "数据目录：${DATA_DIR}（属主 10001:10001 -> 容器内 /app/data）"
# 授权失败立即停止，避免容器看似部署成功而订阅下载不可用。
bash scripts/hls-download-dir.sh prepare || fail "共享下载目录准备失败（线上容器未被改动）"
log "下载目录：${DOWNLOAD_DIR}（容器内同一路径，与迅雷、qBittorrent 共用，文件直接放在剧名目录）"
log "旧媒体目录：${MEDIA_DIR}（容器内 /media，只读保留历史文件和日志）"

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
HULUWA_MEDIA_DIR=${MEDIA_DIR}
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

# ---------- 6. 构建镜像（复用层缓存，源码未变则整体跳过） ----------
# Docker 的 COPY 缓存按文件内容校验（不看 mtime），所以解压出新目录也能命中旧层。
# 这里额外记录源码指纹：完全没变时连 docker build 都不发起。
STATE_FILE="${APP_DIR}/.deploy-state"
BUILD_INPUTS=(Dockerfile .dockerignore compose.yaml pom.xml src frontend scripts)

hash_inputs() {
  local paths=() item
  for item in "${BUILD_INPUTS[@]}"; do
    if [ -e "${item}" ]; then
      paths+=("${item}")
    fi
  done
  if [ "${#paths[@]}" -eq 0 ]; then
    printf 'no-inputs'
    return 0
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    { find "${paths[@]}" -type f -print0 | sort -z | xargs -0 -r sha256sum || true; } | sha256sum | cut -d' ' -f1
  else
    { find "${paths[@]}" -type f -print0 | sort -z | xargs -0 -r cat || true; } | shasum -a 256 | cut -d' ' -f1
  fi
}
# 读取上次部署状态；首次部署没有状态文件，必须让函数始终以 0 退出（set -e）。
state_get() {
  if [ -f "${STATE_FILE}" ]; then
    sed -n "s/^$1=//p" "${STATE_FILE}" 2>/dev/null | tail -n 1 || true
  fi
}

SOURCE_HASH="$(hash_inputs)"
PREV_SOURCE_HASH="$(state_get source)"
IMAGE_EXISTS=0
if have_image "${IMAGE}"; then
  IMAGE_EXISTS=1
fi

if [ "${IMAGE_EXISTS}" -eq 1 ] && [ -n "${PREV_SOURCE_HASH}" ] && [ "${PREV_SOURCE_HASH}" = "${SOURCE_HASH}" ]; then
  log "源码未变化（指纹 ${SOURCE_HASH:0:12}），跳过构建，复用镜像 ${IMAGE}"
else
  if [ "${IMAGE_EXISTS}" -eq 1 ]; then
    log "构建镜像 ${IMAGE}（复用已有分层，只重建变动部分）"
  else
    log "构建镜像 ${IMAGE}（首次构建需拉取依赖，可能较慢）"
  fi
  if ! docker build -t "${IMAGE}" . > "${BUILD_LOG}" 2>&1; then
    tail -n 25 "${BUILD_LOG}" >&2 || true
    fail "Docker 镜像构建失败，完整日志：${BUILD_LOG}（线上容器未被改动）"
  fi
  CACHED_COUNT="$(grep -c 'CACHED' "${BUILD_LOG}" 2>/dev/null || true)"
  log "  构建完成，命中缓存分层 ${CACHED_COUNT:-0} 个"
  tail -n 3 "${BUILD_LOG}" || true
fi
NEW_IMAGE_ID="$(docker image inspect "${IMAGE}" --format '{{.Id}}')"

# ---------- 7. 重建容器（镜像与运行时配置都没变则保持运行） ----------
if command -v sha256sum >/dev/null 2>&1; then
  CONFIG_HASH="$(cat compose.yaml .env | sha256sum | cut -d' ' -f1)"
else
  CONFIG_HASH="$(cat compose.yaml .env | shasum -a 256 | cut -d' ' -f1)"
fi
CURRENT_IMAGE_ID="$(docker inspect "${CONTAINER}" --format '{{.Image}}' 2>/dev/null || true)"
CURRENT_RUNNING="$(docker inspect "${CONTAINER}" --format '{{.State.Running}}' 2>/dev/null || true)"
PREV_CONFIG_HASH="$(state_get config)"

if [ "${CURRENT_RUNNING}" = "true" ] && [ "${NEW_IMAGE_ID}" = "${CURRENT_IMAGE_ID}" ] && [ "${CONFIG_HASH}" = "${PREV_CONFIG_HASH}" ]; then
  log "镜像与运行配置均未变化，容器保持运行（服务零中断）"
else
  log "重建容器 ${CONTAINER}"
  docker compose down --remove-orphans >/dev/null 2>&1 || true
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
  docker compose up -d --no-build --remove-orphans 2>&1 | tail -n 12
fi

# 记录本次部署状态，供下次比对（源码指纹 / 镜像 / 运行配置）
{
  printf 'source=%s\n' "${SOURCE_HASH}"
  printf 'image=%s\n' "${NEW_IMAGE_ID}"
  printf 'config=%s\n' "${CONFIG_HASH}"
} > "${STATE_FILE}"
chmod 600 "${STATE_FILE}" 2>/dev/null || true

# API 健康检查无法证明媒体目录可写，必须另行核对绑定挂载并以应用 UID 验证。
log "检查共享下载目录挂载和 UID 10001 写权限"
bash scripts/hls-download-dir.sh check "${CONTAINER}" || fail "下载目录检查失败，请先修复挂载或权限，再使用电影天堂订阅"

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
