#!/usr/bin/env bash
# 葫芦娃助手 · 飞牛 fnOS 本机部署核心
#
# 在飞牛本机项目根目录执行：
#   sudo bash deploy-fnos.sh
#
# 本脚本只处理当前工作区源码，不执行 SSH、scp，也不会默认 git pull。
#
# 部署顺序：
#   1. 校验 Docker / Compose / socket / 目录 / .env
#   2. 构建新镜像（旧容器继续运行）
#   3. 修正 data / downloads / frpc 权限
#   4. 备份前先停旧容器，完整快照 /app/data
#   5. 重建容器并等待健康检查
#   6. 校验挂载、UID 10001 权限、Docker socket、frpc 可写
#   7. 失败时尝试恢复旧镜像
#
# 可选参数：
#   --check       只检查环境和配置，不构建、不重建
#   --no-backup   跳过停机数据快照（不建议）
#   --git-pull    部署前拉取 Git 代码（默认不拉取）
#
# 环境变量覆盖：
#   HULUWA_APP_DIR（默认脚本所在项目根目录）
#   HULUWA_DATA_DIR（默认 <项目>/data）
#   HULUWA_MEDIA_DIR（默认 <DATA_DIR>/media）
#   HULUWA_DOWNLOAD_DIR（默认 /vol1/1000/downloads）
#   HULUWA_FRP_DIR（默认 /vol1/1000/docker/frpc）
#   HULUWA_BACKUP_ROOT（默认 /vol1/1000/docker/huluwa-backups）
#   HULUWA_CONTAINER（默认 huluwa-console）
#   HULUWA_IMAGE（默认 huluwa-console:latest）
#   HULUWA_BIND_ADDRESS（默认 0.0.0.0）
#   HULUWA_PORT（默认 8000）
#   HULUWA_DOCKER_SOCKET（默认 /var/run/docker.sock）
set -euo pipefail

CORE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_PROJECT_DIR="$(cd "${CORE_DIR}/.." && pwd)"
PROJECT_DIR="${HULUWA_APP_DIR:-${DEFAULT_PROJECT_DIR}}"
case "${PROJECT_DIR}" in
  /*) ;;
  *) PROJECT_DIR="${DEFAULT_PROJECT_DIR}/${PROJECT_DIR#./}" ;;
esac
if [ ! -d "${PROJECT_DIR}" ] || [ ! -f "${PROJECT_DIR}/Dockerfile" ] || [ ! -f "${PROJECT_DIR}/compose.yaml" ]; then
  printf '[错误] 项目根目录无效或缺少 Dockerfile / compose.yaml：%s\n' "${PROJECT_DIR}" >&2
  exit 1
fi
PROJECT_DIR="$(cd "${PROJECT_DIR}" && pwd)"
cd "${PROJECT_DIR}"

MODE="deploy"
BACKUP=1
GIT_PULL=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --check|--verify-only) MODE="check" ;;
    --deploy) MODE="deploy" ;;
    --no-backup) BACKUP=0 ;;
    --backup) BACKUP=1 ;;
    --git-pull) GIT_PULL=1 ;;
    --no-git-pull) GIT_PULL=0 ;;
    -h|--help) sed -n '1,100p' "$0"; exit 0 ;;
    *) printf '[错误] 未知参数：%s（使用 --help 查看用法）\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

DOWNLOAD_DIR="${HULUWA_DOWNLOAD_DIR:-/vol1/1000/downloads}"
FRP_DIR="${HULUWA_FRP_DIR:-/vol1/1000/docker/frpc}"
BACKUP_ROOT="${HULUWA_BACKUP_ROOT:-/vol1/1000/docker/huluwa-backups}"
CONTAINER="${HULUWA_CONTAINER:-huluwa-console}"
IMAGE="${HULUWA_IMAGE:-huluwa-console:latest}"
BIND_ADDRESS="${HULUWA_BIND_ADDRESS:-}"
HOST_PORT="${HULUWA_PORT:-}"
SOCKET_PATH="${HULUWA_DOCKER_SOCKET:-/var/run/docker.sock}"
ENV_FILE="${PROJECT_DIR}/.env"
STATE_FILE="${PROJECT_DIR}/.deploy-state"
BUILD_LOG="${TMPDIR:-/tmp}/huluwa-fnos-build.log"
COMPOSE_CONFIG="${TMPDIR:-/tmp}/huluwa-fnos-compose-config.yaml"
STARTED_AT="$(date +%Y%m%d-%H%M%S)"
ROLLBACK_TAG="${IMAGE%:*}:pre-deploy-${STARTED_AT}"
SNAPSHOT_DIR=""
OLD_IMAGE_ID=""
NEW_IMAGE_ID=""
OLD_CONTAINER_WAS_RUNNING=0
DEPLOY_SUCCEEDED=0

log()  { printf '\033[36m[%s] %s\033[0m\n' "$(date '+%H:%M:%S')" "$*"; }
warn() { printf '\033[33m[%s] %s\033[0m\n' "$(date '+%H:%M:%S')" "$*" >&2; }
fail() { printf '\033[31m[错误] %s\033[0m\n' "$*" >&2; exit 1; }

is_root() { [ "$(id -u)" -eq 0 ]; }

abs_path() {
  local p="$1"
  case "$p" in
    /*) printf '%s' "$p" ;;
    *) printf '%s/%s' "${PROJECT_DIR}" "${p#./}" ;;
  esac
}

DATA_DIR="$(abs_path "${HULUWA_DATA_DIR:-${PROJECT_DIR}/data}")"
MEDIA_DIR="$(abs_path "${HULUWA_MEDIA_DIR:-${DATA_DIR}/media}")"
FRP_DIR="$(abs_path "${FRP_DIR}")"
DOWNLOAD_DIR="$(abs_path "${DOWNLOAD_DIR}")"
BACKUP_ROOT="$(abs_path "${BACKUP_ROOT}")"

case "${PROJECT_DIR}${DATA_DIR}${MEDIA_DIR}${FRP_DIR}${DOWNLOAD_DIR}${BACKUP_ROOT}" in
  *$'\n'*|*$'\r'*) fail "目录路径不能包含换行符" ;;
esac
[ "${DATA_DIR}" != "/" ] || fail "数据目录不能是根目录 /"
[ "${DOWNLOAD_DIR}" != "/" ] || fail "下载目录不能是根目录 /"
[ "${FRP_DIR}" != "/" ] || fail "frpc 目录不能是根目录 /"
[ "${BACKUP_ROOT}" != "/" ] || fail "备份目录不能是根目录 /"
case "${DATA_DIR}${MEDIA_DIR}${DOWNLOAD_DIR}${FRP_DIR}${BACKUP_ROOT}" in
  "//"*|*/../*|*/..) fail "持久化目录不能包含 .. 路径段" ;;
esac

# 飞牛没有 SELinux；compose.yaml 中的 socket :z 只在 CentOS SELinux Enforcing 下有意义。
# 用临时 override 覆盖该挂载，避免修改受 Git 管理的 compose.yaml。
COMPOSE_BACKUP="${PROJECT_DIR}/.compose.yaml.fnos-backup"
COMPOSE_ORIGINAL_RESTORED=0
prepare_compose_override() {
  # 不依赖 Compose 的 !override 语法：备份原文件，临时去掉飞牛无意义的 :z，退出时恢复。
  [ ! -e "${COMPOSE_BACKUP}" ] || fail "残留 Compose 备份文件：${COMPOSE_BACKUP}"
  cp -p compose.yaml "${COMPOSE_BACKUP}"
  sed -i 's#/var/run/docker.sock:/var/run/docker.sock:z#/var/run/docker.sock:/var/run/docker.sock#' compose.yaml
}
restore_compose_override() {
  if [ -f "${COMPOSE_BACKUP}" ]; then
    mv -f "${COMPOSE_BACKUP}" compose.yaml
    COMPOSE_ORIGINAL_RESTORED=1
  fi
}
compose() { docker compose -p huluwa -f compose.yaml "$@"; }

env_value() {
  local key="$1" default="${2:-}"
  if [ -f "${ENV_FILE}" ]; then
    local value
    value="$(sed -n "s/^${key}=//p" "${ENV_FILE}" | tail -n 1)"
    if [ -n "${value}" ]; then printf '%s' "${value}"; return 0; fi
  fi
  printf '%s' "${default}"
}

set_env_value() {
  local key="$1" value="$2" tmp="${ENV_FILE}.tmp.$$"
  touch "${ENV_FILE}"
  awk -v key="${key}" -v value="${value}" '
    BEGIN { done=0 }
    index($0, key "=") == 1 {
      if (!done) { print key "=" value; done=1 }
      next
    }
    { print }
    END { if (!done) print key "=" value }
  ' "${ENV_FILE}" > "${tmp}"
  mv -f "${tmp}" "${ENV_FILE}"
}

ensure_env() {
  if [ ! -f "${ENV_FILE}" ]; then
    [ -f .env.example ] || fail "缺少 .env.example，无法生成 .env"
    log "未找到 .env，从 .env.example 生成"
    cp .env.example "${ENV_FILE}"
  fi
  chmod 600 "${ENV_FILE}" 2>/dev/null || true

  # 命令参数优先；未显式传入时保持 .env 既有值。
  local env_data env_media env_frp env_bind env_port env_gid
  env_data="$(env_value HULUWA_DATA_DIR '')"
  env_media="$(env_value HULUWA_MEDIA_DIR '')"
  env_frp="$(env_value HULUWA_FRP_DIR '')"
  env_bind="$(env_value HULUWA_BIND_ADDRESS '')"
  env_port="$(env_value HULUWA_PORT '')"
  env_gid="$(env_value DOCKER_GID '')"

  if [ -n "${HULUWA_DATA_DIR+x}" ]; then DATA_DIR="$(abs_path "${HULUWA_DATA_DIR}")"; fi
  if [ -n "${HULUWA_MEDIA_DIR+x}" ]; then MEDIA_DIR="$(abs_path "${HULUWA_MEDIA_DIR}")"; fi
  if [ -n "${HULUWA_FRP_DIR+x}" ]; then FRP_DIR="$(abs_path "${HULUWA_FRP_DIR}")"; else
    [ -n "${env_frp}" ] && FRP_DIR="$(abs_path "${env_frp}")"
  fi
  if [ -n "${HULUWA_BIND_ADDRESS+x}" ]; then BIND_ADDRESS="${HULUWA_BIND_ADDRESS}"; else
    [ -n "${env_bind}" ] && BIND_ADDRESS="${env_bind}"
  fi
  if [ -n "${HULUWA_PORT+x}" ]; then HOST_PORT="${HULUWA_PORT}"; else
    [ -n "${env_port}" ] && HOST_PORT="${env_port}"
  fi
  BIND_ADDRESS="${BIND_ADDRESS:-0.0.0.0}"
  HOST_PORT="${HOST_PORT:-8000}"
  case "${HOST_PORT}" in *[!0-9]*|'') fail "HULUWA_PORT 必须是数字：${HOST_PORT}" ;; esac

  # 数据目录必须由 .env 传给容器；显式参数与 .env 不一致时以参数为准并写回。
  set_env_value HULUWA_DATA_DIR "${DATA_DIR}"
  set_env_value HULUWA_MEDIA_DIR "${MEDIA_DIR}"
  set_env_value HULUWA_FRP_DIR "${FRP_DIR}"
  set_env_value HULUWA_BIND_ADDRESS "${BIND_ADDRESS}"
  set_env_value HULUWA_PORT "${HOST_PORT}"
  [ -n "$(env_value COMPOSE_PROJECT_NAME '')" ] || set_env_value COMPOSE_PROJECT_NAME huluwa

  local socket_gid
  socket_gid="$(stat -c '%g' "${SOCKET_PATH}" 2>/dev/null || true)"
  [ -n "${socket_gid}" ] || fail "无法读取 Docker socket 组 ID：${SOCKET_PATH}"
  if [ -n "${HULUWA_DOCKER_GID:-}" ]; then
    [ "${HULUWA_DOCKER_GID}" = "${socket_gid}" ] || fail "HULUWA_DOCKER_GID=${HULUWA_DOCKER_GID} 与 socket 实际 GID=${socket_gid} 不一致"
  fi
  set_env_value DOCKER_GID "${socket_gid}"
  SOCKET_GID="${socket_gid}"
  chmod 600 "${ENV_FILE}" 2>/dev/null || true
  # .env 由执行部署的账号维护；root 不应让它变成日后普通管理员无法修改的 root-only 文件。
  if [ -n "${SUDO_UID:-}" ] && [ -n "${SUDO_GID:-}" ]; then
    chown "${SUDO_UID}:${SUDO_GID}" "${ENV_FILE}" 2>/dev/null || true
  fi
}

check_environment() {
  command -v docker >/dev/null 2>&1 || fail "未检测到 docker"
  docker compose version >/dev/null 2>&1 || fail "未检测到 Docker Compose v2"
  docker info >/dev/null 2>&1 || fail "Docker 守护进程不可用，请先启动 Docker"
  [ -S "${SOCKET_PATH}" ] || fail "Docker socket 不存在：${SOCKET_PATH}"
  [ -f Dockerfile ] || fail "缺少 Dockerfile"
  [ -f compose.yaml ] || fail "缺少 compose.yaml"
  [ -f scripts/hls-download-dir.sh ] || fail "缺少 scripts/hls-download-dir.sh"
  if [ "$(uname -s)" != "Linux" ]; then fail "本脚本只能在飞牛 fnOS / Linux 上运行"; fi
  if [ -z "${CONTAINER}" ] || [ -z "${IMAGE}" ]; then fail "HULUWA_CONTAINER / HULUWA_IMAGE 不能为空"; fi
  if ! is_root; then
    fail "本机部署需要 root 权限处理飞牛目录属主和 TrimACL。请执行：sudo bash deploy-fnos.sh"
  fi
}

prepare_directories() {
  log "准备持久化目录"
  mkdir -p "${DATA_DIR}" "${MEDIA_DIR}" || fail "无法创建数据目录：${DATA_DIR} / ${MEDIA_DIR}，请使用 sudo"
  mkdir -p "${DOWNLOAD_DIR}" || fail "无法创建下载目录：${DOWNLOAD_DIR}，请使用 sudo"
  mkdir -p "${FRP_DIR}" || fail "无法创建 frpc 目录：${FRP_DIR}，请使用 sudo"
  mkdir -p "${BACKUP_ROOT}" || fail "无法创建备份目录：${BACKUP_ROOT}，请使用 sudo"
  [ ! -L "${DATA_DIR}" ] || fail "数据目录不能是符号链接：${DATA_DIR}"
  [ ! -L "${DOWNLOAD_DIR}" ] || fail "下载目录不能是符号链接：${DOWNLOAD_DIR}"
  [ ! -L "${FRP_DIR}" ] || fail "frpc 目录不能是符号链接：${FRP_DIR}"
  [ -d "${DATA_DIR}" ] && [ -d "${DOWNLOAD_DIR}" ] && [ -d "${FRP_DIR}" ] \
    || fail "持久化目录创建失败"
}

fix_data_permissions() {
  log "处理数据目录权限：${DATA_DIR}"
  if ! chown -R 10001:10001 "${DATA_DIR}" 2>/dev/null; then
    if [ -n "$(docker image inspect "${IMAGE}" --format '{{.Id}}' 2>/dev/null || true)" ]; then
      docker run --rm --network none --user 0:0 --entrypoint sh \
        -v "${DATA_DIR}:/data" "${IMAGE}" \
        -c 'chown -R 10001:10001 /data && chmod 755 /data && chmod -R u+rwX,g+rwX /data' \
        || fail "无法修正数据目录属主，请用 sudo 执行 deploy-fnos.sh"
    else
      fail "无法修正数据目录属主且本地没有 ${IMAGE}，请安装 sudo 后执行：sudo bash deploy-fnos.sh"
    fi
  fi
  chmod 755 "${DATA_DIR}"
  chmod -R u+rwX,g+rwX "${DATA_DIR}"
  local owner
  owner="$(stat -c '%u:%g' "${DATA_DIR}")"
  [ "${owner}" = "10001:10001" ] || fail "数据目录属主仍为 ${owner}，应为 10001:10001"
}

fix_frp_permissions() {
  log "处理 frpc 配置目录权限：${FRP_DIR}"
  local ini="${FRP_DIR}/frpc.ini"
  if ! chown 10001:1001 "${FRP_DIR}" 2>/dev/null; then
    chown 10001:10001 "${FRP_DIR}" 2>/dev/null \
      || fail "无法修改 frpc 目录属主，请用 sudo 执行 deploy-fnos.sh"
  fi
  chmod 2775 "${FRP_DIR}" 2>/dev/null || chmod 775 "${FRP_DIR}"
  if [ -f "${ini}" ]; then
    chown 10001:1001 "${ini}" 2>/dev/null || chown 10001:10001 "${ini}" 2>/dev/null || true
    chmod 664 "${ini}" 2>/dev/null || true
  else
    warn "未找到 ${ini}，Frp 管理页暂时无法读写配置；目录权限已准备好"
  fi
}

fix_download_permissions() {
  log "处理共享下载目录权限：${DOWNLOAD_DIR}"
  bash scripts/hls-download-dir.sh prepare || fail "下载目录 TrimACL / POSIX ACL 授权失败"
}

ensure_base_images() {
  local images=(
    node:22-bookworm-slim
    maven:3.9.9-eclipse-temurin-21
    eclipse-temurin:21-jre-noble
    ubuntu:24.04
  )
  local image
  for image in "${images[@]}"; do
    if docker image inspect "${image}" >/dev/null 2>&1; then
      log "基础镜像已存在：${image}"
    else
      log "拉取缺失基础镜像：${image}"
      docker pull "${image}" || fail "基础镜像拉取失败：${image}，请检查飞牛 Docker 镜像源"
    fi
  done
}

verify_compose_config() {
  log "校验 Compose 解析结果"
  compose config > "${COMPOSE_CONFIG}"
  grep -Fq "source: ${DATA_DIR}" "${COMPOSE_CONFIG}" \
    || fail "Compose 数据挂载不是预期路径 ${DATA_DIR}"
  grep -Fq "source: ${MEDIA_DIR}" "${COMPOSE_CONFIG}" \
    || fail "Compose 媒体挂载不是预期路径 ${MEDIA_DIR}"
  grep -Fq "source: ${DOWNLOAD_DIR}" "${COMPOSE_CONFIG}" \
    || fail "Compose 下载挂载不是预期路径 ${DOWNLOAD_DIR}"
  grep -Fq "source: ${FRP_DIR}" "${COMPOSE_CONFIG}" \
    || fail "Compose frpc 挂载不是预期路径 ${FRP_DIR}"
  grep -Fq "target: /app/data" "${COMPOSE_CONFIG}" \
    || fail "Compose 缺少 /app/data 挂载"
  grep -Fq "target: /media" "${COMPOSE_CONFIG}" \
    || fail "Compose 缺少 /media 挂载"
  grep -Fq "target: /vol1/1000/downloads" "${COMPOSE_CONFIG}" \
    || fail "Compose 缺少共享下载目录挂载"
  grep -Fq "target: /app/frp" "${COMPOSE_CONFIG}" \
    || fail "Compose 缺少 /app/frp 挂载"
  local gid
  gid="$(sed -n '/group_add:/,/^[^ ]/p' "${COMPOSE_CONFIG}" | sed -n 's/^[[:space:]]*- "\([0-9][0-9]*\)".*/\1/p' | head -n1)"
  [ -n "${gid}" ] && [ "${gid}" = "${SOCKET_GID}" ] \
    || fail "Compose group_add 与 socket GID 不一致（期望 ${SOCKET_GID}）"
}

record_old_image() {
  OLD_IMAGE_ID="$(docker inspect "${CONTAINER}" --format '{{.Image}}' 2>/dev/null || true)"
  if docker inspect "${CONTAINER}" --format '{{.State.Running}}' 2>/dev/null | grep -q '^true$'; then
    OLD_CONTAINER_WAS_RUNNING=1
  fi
  if [ -n "${OLD_IMAGE_ID}" ]; then
    docker tag "${OLD_IMAGE_ID}" "${ROLLBACK_TAG}" 2>/dev/null || true
    log "旧镜像：${ROLLBACK_TAG}"
  else
    log "未发现旧容器 ${CONTAINER}，将按首次部署处理"
  fi
}

build_image() {
  log "构建最新源码镜像（旧容器继续运行）"
  if ! docker build --progress=plain -t "${IMAGE}" . > "${BUILD_LOG}" 2>&1; then
    warn "镜像构建失败，线上容器未被改动。最近日志："
    tail -n 40 "${BUILD_LOG}" >&2 || true
    return 1
  fi
  NEW_IMAGE_ID="$(docker image inspect "${IMAGE}" --format '{{.Id}}')"
  [ -n "${NEW_IMAGE_ID}" ] || { warn "无法读取新镜像 ID"; return 1; }
  log "新镜像：${NEW_IMAGE_ID}"
}

stop_old_container() {
  if docker inspect "${CONTAINER}" >/dev/null 2>&1; then
    log "停止旧容器 ${CONTAINER}"
    docker stop --timeout 30 "${CONTAINER}" >/dev/null || fail "旧容器停止失败，未修改数据"
  fi
}

backup_data() {
  [ "${BACKUP}" -eq 1 ] || { warn "--no-backup：跳过数据快照"; return 0; }
  log "创建一致性数据快照"
  mkdir -p "${BACKUP_ROOT}"
  SNAPSHOT_DIR="${BACKUP_ROOT}/${STARTED_AT}-data"
  [ ! -e "${SNAPSHOT_DIR}" ] || fail "数据快照目录已存在，拒绝覆盖：${SNAPSHOT_DIR}"
  if docker inspect "${CONTAINER}" >/dev/null 2>&1; then
    docker cp -a "${CONTAINER}:/app/data" "${SNAPSHOT_DIR}" \
      || fail "无法从停止的旧容器导出 /app/data"
  else
    mkdir -p "${SNAPSHOT_DIR}"
    cp -a "${DATA_DIR}/." "${SNAPSHOT_DIR}/" || fail "无法复制数据目录做快照"
  fi
  tar -C "${BACKUP_ROOT}" -czf "${SNAPSHOT_DIR}.tar.gz" "$(basename "${SNAPSHOT_DIR}")" 2>/dev/null || true
  log "数据快照：${SNAPSHOT_DIR}"
}

recreate_container() {
  log "用新镜像重建容器 ${CONTAINER}"
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
  if ! compose up -d --force-recreate --no-build --remove-orphans; then
    warn "新容器创建失败，尝试停止并恢复旧容器"
    return 1
  fi
  return 0
}

wait_health() {
  local health="" i
  for i in $(seq 1 40); do
    health="$(docker inspect "${CONTAINER}" --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' 2>/dev/null || true)"
    [ "${health}" = "healthy" ] && return 0
    if [ "${health}" = "unhealthy" ]; then
      docker logs --tail 100 "${CONTAINER}" >&2 2>&1 || true
      return 1
    fi
    sleep 3
  done
  docker logs --tail 100 "${CONTAINER}" >&2 2>&1 || true
  return 1
}

verify_mounts() {
  local spec
  spec="$(docker inspect "${CONTAINER}" --format '{{range .Mounts}}{{println .Source "|" .Destination "|" .RW}}{{end}}')" \
    || fail "无法读取新容器挂载"
  printf '%s\n' "${spec}" | grep -Fq "${DATA_DIR}|/app/data|true" \
    || fail "数据目录挂载不正确：期望 ${DATA_DIR} -> /app/data 且可写"
  printf '%s\n' "${spec}" | grep -Fq "${MEDIA_DIR}|/media|false" \
    || fail "旧媒体目录挂载不正确：期望 ${MEDIA_DIR} -> /media 且只读"
  printf '%s\n' "${spec}" | grep -Fq "${DOWNLOAD_DIR}|${DOWNLOAD_DIR}|true" \
    || fail "下载目录挂载不正确：期望 ${DOWNLOAD_DIR} 双向一致且可写"
  printf '%s\n' "${spec}" | grep -Fq "${FRP_DIR}|/app/frp|true" \
    || fail "frpc 目录挂载不正确：期望 ${FRP_DIR} -> /app/frp 且可写"
  printf '%s\n' "${spec}" | grep -Fq "${SOCKET_PATH}|/var/run/docker.sock|true" \
    || fail "Docker socket 挂载不正确：期望 ${SOCKET_PATH} -> /var/run/docker.sock"
  log "容器挂载校验通过"
}

verify_permissions() {
  log "校验容器内权限"
  docker exec --user 10001:10001 "${CONTAINER}" \
    sh -c 'test -w /app/data && test -w /app/data/huluwa.db && echo data-writable' \
    || fail "容器用户 10001 无法写 /app/data"
  docker exec --user 10001:10001 "${CONTAINER}" \
    sh -c 'test -w /app/data && probe=/app/data/.write-check.$$ && (umask 077; : > "$probe") && rm -f "$probe"' \
    || fail "数据目录实际写入测试失败"
  docker exec --user 10001:10001 "${CONTAINER}" \
    sh -c 'test -w /app/frp && probe=/app/frp/.write-check.$$ && (umask 077; : > "$probe") && rm -f "$probe"' \
    || fail "frpc 目录不可写"
  docker exec --user 10001:10001 "${CONTAINER}" \
    sh -c 'test -r /var/run/docker.sock && test -w /var/run/docker.sock && echo docker-socket-writable' \
    || fail "Docker socket 不可读写，检查 DOCKER_GID 与 group_add"
  docker exec --user 10001:10001 "${CONTAINER}" \
    sh -c 'test -r /media && ! test -w /media && echo media-readonly' \
    || fail "/media 不是预期只读状态"
  bash scripts/hls-download-dir.sh check "${CONTAINER}" \
    || fail "下载目录挂载、TrimACL 或 UID 10001 写权限校验失败"
  local group_add
  group_add="$(docker inspect "${CONTAINER}" --format '{{json .HostConfig.GroupAdd}}')"
  printf '%s\n' "${group_add}" | grep -Eq "[[:space:],\"]${SOCKET_GID}([[:space:],\"]|$)" \
    || fail "容器 group_add 未包含 socket GID ${SOCKET_GID}"
}

restore_old_container() {
  [ -n "${OLD_IMAGE_ID}" ] || return 0
  warn "尝试用旧镜像恢复容器"
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
  docker tag "${OLD_IMAGE_ID}" "${IMAGE}" 2>/dev/null || true
  if compose up -d --force-recreate --no-build --remove-orphans >/dev/null 2>&1; then
    if wait_health >/dev/null 2>&1; then
      log "旧容器已恢复并通过健康检查"
      return 0
    fi
  fi
  warn "旧容器自动恢复失败，请执行 docker logs ${CONTAINER} 排查"
}

main() {
  log "葫芦娃助手 · 飞牛本机部署"
  log "项目目录：${PROJECT_DIR}"
  check_environment
  ensure_env
  prepare_directories
  fix_data_permissions
  fix_frp_permissions
  fix_download_permissions
  prepare_compose_override
  verify_compose_config

  if [ "${MODE}" = "check" ]; then
    log "预检完成：环境、目录、Compose 映射和权限配置正常（未构建、未重建、未拉取镜像）"
    exit 0
  fi

  ensure_base_images

  if [ "${GIT_PULL}" -eq 1 ]; then
    log "拉取最新代码（git pull --ff-only）"
    git pull --ff-only || fail "git pull 失败，未继续部署"
  fi

  record_old_image
  if ! build_image; then
    if [ "${OLD_CONTAINER_WAS_RUNNING}" -eq 1 ]; then
      docker start "${CONTAINER}" >/dev/null 2>&1 || true
    fi
    fail "镜像构建失败，旧容器未被替换；完整日志：${BUILD_LOG}"
  fi

  stop_old_container
  backup_data
  if ! recreate_container; then
    restore_old_container
    if [ "${OLD_CONTAINER_WAS_RUNNING}" -eq 1 ]; then
      docker start "${CONTAINER}" >/dev/null 2>&1 || true
    fi
    fail "新容器创建失败，已尝试恢复旧容器"
  fi

  if ! wait_health; then
    docker logs --tail 120 "${CONTAINER}" >&2 2>&1 || true
    restore_old_container
    fail "新容器未通过健康检查，已尝试回滚"
  fi
  if ! verify_mounts || ! verify_permissions; then
    restore_old_container
    fail "新容器挂载或权限校验失败，已尝试回滚"
  fi

  {
    printf 'project=%s\n' "${PROJECT_DIR}"
    printf 'data_dir=%s\n' "${DATA_DIR}"
    printf 'media_dir=%s\n' "${MEDIA_DIR}"
    printf 'download_dir=%s\n' "${DOWNLOAD_DIR}"
    printf 'frp_dir=%s\n' "${FRP_DIR}"
    printf 'docker_socket=%s\n' "${SOCKET_PATH}"
    printf 'image=%s\n' "${NEW_IMAGE_ID}"
    printf 'container=%s\n' "${CONTAINER}"
    printf 'deployed_at=%s\n' "$(date -Iseconds)"
  } > "${STATE_FILE}"
  chmod 600 "${STATE_FILE}" 2>/dev/null || true
  DEPLOY_SUCCEEDED=1

  log "部署完成"
  docker ps --filter "name=${CONTAINER}" --format '{{.Names}} | {{.Image}} | {{.Status}} | {{.Ports}}'
  log "数据目录：${DATA_DIR}"
  log "下载目录：${DOWNLOAD_DIR}"
  log "frpc 目录：${FRP_DIR}"
  log "Docker socket：${SOCKET_PATH}（GID ${SOCKET_GID}）"
  [ -n "${SNAPSHOT_DIR}" ] && log "本次数据快照：${SNAPSHOT_DIR}"
}

cleanup_override() {
  restore_compose_override
}
trap cleanup_override EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

main "$@"
