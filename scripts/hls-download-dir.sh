#!/usr/bin/env bash
# 共享下载目录准备、容器检查与在线权限修复；不构建镜像或重建容器。
# 用法：sudo bash scripts/hls-download-dir.sh [fix|prepare|check] [容器名]
set -euo pipefail

MODE="${1:-fix}"
CONTAINER="${2:-huluwa-console}"
DOWNLOAD_DIR="/vol1/1000/downloads"
# 飞牛 fnOS 在 POSIX ACL 之上叠加自家的 TrimACL：/vol1 下的目录以 system.trim_acl
# 扩展属性为准，setfacl 写入的 POSIX ACL 在该卷上不参与权限判定，必须用 trimacl 授权。
TRIMACL_BIN="/usr/trim/bin/trimacl"

log() { printf '%s\n' "$*"; }
fail() { printf '错误：%s\n' "$*" >&2; exit 1; }

grant_uid_10001() {
  local dir="$1"
  if [ -x "${TRIMACL_BIN}" ]; then
    # sfd = 自身、文件、子目录均继承，等价于 CentOS 上的默认 ACL。
    "${TRIMACL_BIN}" -m a:u:10001:rwxpdDaAeEc:sfd "${dir}" \
      || fail "TrimACL 授权失败（飞牛）：${dir}"
    return 0
  fi
  command -v setfacl >/dev/null 2>&1 \
    || fail "未找到 trimacl 或 setfacl，无法授予 UID 10001 下载目录权限"
  setfacl -m u:10001:rwx,d:u:10001:rwx "${dir}" \
    || fail "POSIX ACL 授权失败：${dir}"
}

prepare_directory() {
  [ "$(id -u)" -eq 0 ] || fail "目录授权需要 root 权限，请通过 sudo 运行"
  [ ! -L "${DOWNLOAD_DIR}" ] || fail "下载根目录不能是符号链接：${DOWNLOAD_DIR}"
  mkdir -p "${DOWNLOAD_DIR}" || fail "无法创建下载目录：${DOWNLOAD_DIR}"
  [ -d "${DOWNLOAD_DIR}" ] || fail "下载路径不是目录：${DOWNLOAD_DIR}"
  # 仅增加应用用户的访问和继承权限，保留迅雷/qBittorrent 的目录属主。
  grant_uid_10001 "${DOWNLOAD_DIR}"
  local dir
  while IFS= read -r -d '' dir; do
    grant_uid_10001 "${dir}"
  done < <(find "${DOWNLOAD_DIR}" -mindepth 1 -maxdepth 1 -type d -print0)
  if [ -x "${TRIMACL_BIN}" ]; then
    log "已通过 TrimACL 授予 UID 10001 下载根目录及第一层目录的写权限：${DOWNLOAD_DIR}"
  else
    log "已通过 POSIX ACL 授予 UID 10001 下载根目录及第一层目录的写权限：${DOWNLOAD_DIR}"
  fi
}

check_mount() {
  command -v docker >/dev/null 2>&1 || fail "未找到 docker"
  local mount root
  mount="$(docker inspect "${CONTAINER}" --format '{{range .Mounts}}{{if eq .Destination "/vol1/1000/downloads"}}{{.Source}}|{{.RW}}{{end}}{{end}}')" \
    || fail "无法检查容器 ${CONTAINER}，请确认容器存在且有 Docker 访问权限"
  case "${mount}" in
    "${DOWNLOAD_DIR}|true") ;;
    "${DOWNLOAD_DIR}|false") fail "容器下载目录挂载为只读，请将 ${DOWNLOAD_DIR} 挂载为可写并重新创建容器" ;;
    "") fail "容器缺少 ${DOWNLOAD_DIR}:${DOWNLOAD_DIR} 挂载，重启容器不会新增挂载，需要按最新 compose.yaml 重新创建容器" ;;
    *) fail "容器下载目录映射不一致：${mount}；应为 ${DOWNLOAD_DIR}:${DOWNLOAD_DIR} 且可写" ;;
  esac
  root="$(docker exec "${CONTAINER}" sh -c 'printf "%s" "${HULUWA_HLS_MEDIA_ROOT:-/vol1/1000/downloads}"')" \
    || fail "无法读取容器下载目录配置，请确认容器已启动"
  [ "${root}" = "${DOWNLOAD_DIR}" ] \
    || fail "容器 HULUWA_HLS_MEDIA_ROOT=${root}，应设置为 ${DOWNLOAD_DIR}"
}

check_write() {
  # 使用应用的 UID/GID 创建、硬链接并删除临时文件，验证实际下载归档所需权限。
  # 只清理本次 mktemp 创建的文件，不接触已有媒体或下载任务。
  if ! docker exec --user 10001:10001 "${CONTAINER}" sh -eu -c '
    root=/vol1/1000/downloads
    probe=
    cleanup() {
      if [ -n "$probe" ]; then
        rm -f -- "$probe/media" "$probe/link"
        rmdir -- "$probe"
      fi
    }
    trap cleanup EXIT
    trap "exit 1" HUP INT TERM
    test -d "$root"
    for dir in "$root" "$root/.huluwa-work"; do
      test ! -L "$dir"
      if test -e "$dir"; then
        test -d "$dir"
        test -w "$dir"
        probe=$(mktemp -d "$dir/.huluwa-check.XXXXXX")
        printf "%s\n" check > "$probe/media"
        ln "$probe/media" "$probe/link"
        cleanup
        probe=
      fi
    done
  '; then
    fail "容器 UID 10001 无法在 ${DOWNLOAD_DIR} 创建或归档文件；请检查 TrimACL/POSIX ACL、挂载及磁盘状态"
  fi
  log "下载目录挂载及 UID 10001 实际写入检查通过：${DOWNLOAD_DIR}"
}

[ "$#" -le 2 ] || fail "用法：sudo bash $0 [fix|prepare|check] [容器名]"
case "${MODE}" in
  prepare) prepare_directory ;;
  check) check_mount; check_write ;;
  fix) check_mount; prepare_directory; check_write ;;
  *) fail "未知操作 ${MODE}，可用操作：fix、prepare、check" ;;
esac
