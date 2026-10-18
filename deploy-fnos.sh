#!/usr/bin/env bash
# 葫芦娃助手 · 飞牛 fnOS 本机一键部署
#
# 在飞牛上的项目根目录直接执行：
#   bash deploy-fnos.sh
#
# 脚本会完成：
#   - 校验 Docker / Compose / socket
#   - 构建当前工作区最新源码镜像
#   - 处理 data / downloads / frpc / docker.sock 权限
#   - 停机备份整个持久化数据目录
#   - 重建容器并等待健康检查、校验挂载与权限
#   - 新版本失败时尝试回滚旧镜像
#
# 可选参数：
#   --check        只检查环境和 Compose 配置，不构建、不重建
#   --no-backup    跳过停机数据快照（不建议）
#   --git-pull     部署前先执行 git pull --ff-only
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE="${SCRIPT_DIR}/scripts/deploy-fnos-local.sh"

if [ ! -f "${SCRIPT_DIR}/Dockerfile" ] || [ ! -f "${SCRIPT_DIR}/compose.yaml" ]; then
  echo "[错误] 当前目录不是葫芦娃项目根目录：${SCRIPT_DIR}" >&2
  exit 1
fi
if [ ! -f "${CORE}" ]; then
  echo "[错误] 缺少本机部署核心脚本：${CORE}" >&2
  exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "[错误] 未检测到 docker" >&2
  exit 1
fi
if [ ! -S /var/run/docker.sock ]; then
  echo "[错误] 未检测到 /var/run/docker.sock，请确认飞牛 Docker 服务已启动" >&2
  exit 1
fi

# 部署核心会自行执行 docker info / compose version 检查。
exec bash "${CORE}" "$@"
