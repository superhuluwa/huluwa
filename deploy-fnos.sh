#!/usr/bin/env bash
# 葫芦娃助手 · 一键部署到飞牛 fnOS（Linux / macOS 版，等价于 deploy_fnos.bat）
#
# 与 .bat 的差异：
#   - 用本机 ssh/scp 直接连飞牛，不再依赖 Git for Windows 自带的 ssh；
#   - 飞牛的 TrimACL 允许管理员账号免 root 完成目录授权，因此默认不加 sudo，
#     需要提权时设置 HULUWA_FNOS_SUDO_PW 即可（脚本会自动改用 sudo）。
#
# 用法：bash deploy-fnos.sh
set -euo pipefail

# ==================== CONFIG ====================
# 均可通过同名环境变量覆盖
SERVER="${HULUWA_FNOS_HOST:-192.168.3.77}"
SSH_PORT="${HULUWA_FNOS_PORT:-22}"
DEPLOY_USER="${HULUWA_FNOS_USER:-397383523}"
APP_DIR="${HULUWA_FNOS_APP_DIR:-/vol1/@appdata/huluwa}"
DATA_DIR="${APP_DIR}/data"
# frpc 容器的实际配置目录（frpc compose 的挂载源），葫芦娃与 frpc 共用，改完即时生效
FRP_DIR="${HULUWA_FNOS_FRP_DIR:-/vol1/1000/docker/frpc}"
CONTAINER="${HULUWA_FNOS_CONTAINER:-huluwa-console}"
IMAGE="${HULUWA_FNOS_IMAGE:-huluwa-console:latest}"
HOST_PORT="${HULUWA_FNOS_HTTP_PORT:-8000}"
SSH_RETRY="${HULUWA_FNOS_SSH_RETRY:-3}"
SSH_WAIT="${HULUWA_FNOS_SSH_WAIT:-3}"
SSH_KEY="${HULUWA_FNOS_KEY:-${HOME}/.ssh/terminal_buddy_fnos_deploy}"
KNOWN_HOSTS="${TMPDIR:-/tmp}/huluwa_fnos_known_hosts"
# ================================================

cd "$(dirname "$0")"
PROJECT_DIR="$(pwd)"
TARBALL="$(mktemp -t huluwa-src-XXXXXX.tar.gz)"
REMOTE_SH="/tmp/huluwa-fnos-deploy.sh"
REMOTE_TARBALL="/tmp/huluwa-src.tar.gz"
# 提权密码只经环境变量传入；未设置时以普通账号部署（飞牛管理员账号足够）
SUDO_PW="${HULUWA_FNOS_SUDO_PW:-}"

log()  { printf '\033[36m[%s] %s\033[0m\n' "$(date '+%H:%M:%S')" "$*"; }
warn() { printf '\033[33m[%s] %s\033[0m\n' "$(date '+%H:%M:%S')" "$*" >&2; }
fail() { printf '\033[31m[错误] %s\033[0m\n' "$*" >&2; exit 1; }

cleanup() { rm -f "${TARBALL}"; }
trap cleanup EXIT

SSH_OPTS=(-i "${SSH_KEY}" -o BatchMode=yes -o StrictHostKeyChecking=no
          -o UserKnownHostsFile="${KNOWN_HOSTS}" -o LogLevel=ERROR -p "${SSH_PORT}")
remote() { ssh "${SSH_OPTS[@]}" "${DEPLOY_USER}@${SERVER}" "$@"; }

# ---------- 0. 拉取最新代码 ----------
log "正在拉取最新代码..."
git pull --ff-only || fail "拉取最新代码失败，请检查 Git 凭据、网络或本地仓库状态"
echo

echo "========== 葫芦娃助手 一键部署（飞牛 fnOS） =========="
echo "目标: ${DEPLOY_USER}@${SERVER}:${SSH_PORT}   应用目录: ${APP_DIR}"
echo "提权: ${SUDO_PW:+sudo（已提供密码）}${SUDO_PW:-普通账号，免 sudo}"
echo

# ---------- 0b. 环境检查 ----------
[ -f Dockerfile ] || fail "当前目录缺少 Dockerfile，请把 deploy-fnos.sh 放在项目根目录"
[ -f compose.yaml ] || fail "当前目录缺少 compose.yaml，请把 deploy-fnos.sh 放在项目根目录"
[ -f scripts/deploy-fnos-remote.sh ] || fail "缺少 scripts/deploy-fnos-remote.sh（飞牛远端部署脚本）"
command -v ssh >/dev/null 2>&1 || fail "未找到 ssh 命令"
command -v scp >/dev/null 2>&1 || fail "未找到 scp 命令"
command -v tar >/dev/null 2>&1 || fail "未找到 tar 命令"
[ -f "${SSH_KEY}" ] || fail "未找到部署密钥 ${SSH_KEY}
       请先安装公钥到飞牛 ${DEPLOY_USER} 账号，或用 HULUWA_FNOS_KEY 指定其它密钥"

# ---------- 1. 免密认证 ----------
if remote "echo ok" >/dev/null 2>&1; then
  log "[1/5] 免密认证通过 (${DEPLOY_USER}@${SERVER})"
else
  fail "免密登录失败：${SSH_KEY}
       已确认飞牛可达但密钥未生效时，请把 ${SSH_KEY}.pub 追加到
       ${DEPLOY_USER}@${SERVER}:~/.ssh/authorized_keys"
fi
echo

# ---------- 2. 打包本机源码（仅代码，不含 .git / 构建产物 / 数据） ----------
log "[2/5] 打包源码..."
tar -czf "${TARBALL}" \
  --exclude=./.git --exclude=./node_modules --exclude='*/node_modules' \
  --exclude=./target --exclude=./data --exclude=./output \
  --exclude=./research_spk --exclude=./research_xunlei --exclude=./ps7420.zip \
  --exclude=./src/main/resources/static \
  -C "${PROJECT_DIR}" . || fail "打包源码失败"
log "      源码包: $(du -h "${TARBALL}" | cut -f1)"

# ---------- 3. 上传源码包与远端部署脚本 ----------
log "[3/5] 上传到飞牛 /tmp ..."
UPLOAD_OK=0
for i in $(seq 1 "${SSH_RETRY}"); do
  log "      尝试 ${i}/${SSH_RETRY}..."
  if scp -i "${SSH_KEY}" -o StrictHostKeyChecking=no -o UserKnownHostsFile="${KNOWN_HOSTS}" \
       -o LogLevel=ERROR -P "${SSH_PORT}" \
       "${TARBALL}" scripts/deploy-fnos-remote.sh \
       "${DEPLOY_USER}@${SERVER}:/tmp/" >/dev/null 2>&1; then
    UPLOAD_OK=1
    break
  fi
  warn "      上传失败，${SSH_WAIT} 秒后重试..."
  sleep "${SSH_WAIT}"
done
[ "${UPLOAD_OK}" -eq 1 ] || fail "源码上传失败（${SSH_RETRY} 次重试均失败）"
remote "mv -f /tmp/deploy-fnos-remote.sh ${REMOTE_SH} && mv -f /tmp/$(basename "${TARBALL}") ${REMOTE_TARBALL}" \
  || fail "远端整理上传文件失败"

# ---------- 4. 远端构建 + 重建容器 ----------
log "[4/5] 在飞牛上构建镜像并重建容器（首次构建较慢，请耐心等待）..."
BACKUP_ARG=""
[ "${HULUWA_BACKUP_BASE_IMAGES:-1}" = "0" ] && BACKUP_ARG="--no-base-image-backup"

DEPLOY_CMD="sed -i 's/\r$//' ${REMOTE_SH} && bash ${REMOTE_SH} ${APP_DIR} ${DATA_DIR} ${FRP_DIR} ${HOST_PORT} ${CONTAINER} ${IMAGE} ${DEPLOY_USER} ${BACKUP_ARG}"
if [ -n "${SUDO_PW}" ]; then
  # 密码经 stdin 交给 sudo -S，不出现在命令行与日志里
  printf '%s\n' "${SUDO_PW}" | ssh "${SSH_OPTS[@]}" "${DEPLOY_USER}@${SERVER}" \
    "tr -d '\r' | sudo -S -p '' bash -c $(printf '%q' "${DEPLOY_CMD}")" \
    || fail "飞牛上的构建或启动失败，详见上方输出"
else
  remote "${DEPLOY_CMD}" || fail "飞牛上的构建或启动失败，详见上方输出"
fi

# ---------- 5. 完成 ----------
echo
echo "========== 部署完成 =========="
echo "访问地址: http://${SERVER}:${HOST_PORT}"
echo "数据目录: ${DATA_DIR}  (容器内 /app/data)"
echo "下载目录: /vol1/1000/downloads  (迅雷、qBittorrent、电影天堂共用)"
echo "源码目录: ${APP_DIR}/src"
echo "基础镜像缓存: ${APP_DIR}/base-images (设 HULUWA_BACKUP_BASE_IMAGES=0 可关闭导出)"
echo "Frp 配置: ${FRP_DIR}/frpc.ini"
echo "查看日志: ssh -i ${SSH_KEY} -p ${SSH_PORT} ${DEPLOY_USER}@${SERVER} 'docker logs -f ${CONTAINER}'"
remote "docker ps -a --filter name=huluwa --format '{{.Names}} | {{.Status}} | {{.Ports}}'"
