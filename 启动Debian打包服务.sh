#!/usr/bin/env bash
# Start the enrolled Debian Docker builder without compiling an APK.
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"
if [ -f "$PROJECT_DIR/data/debian-builder/migrated-to" ]; then
  IFS= read -r target < "$PROJECT_DIR/data/debian-builder/migrated-to"
  printf '此目录的打包节点已迁移至 %s，请在目标服务器的部署目录运行本脚本。\n' "$target" >&2
  exit 1
fi
if [ ! -f "${HULUWA_ANDROID_AGENT_CONFIG:-$PROJECT_DIR/data/debian-builder/config.json}" ]; then
  printf '%s\n' '尚未配置 Debian 打包节点，请先按 android-builder/README.debian.md 完成部署。' >&2
  exit 1
fi
compose=(docker compose)
if [ -f "$PROJECT_DIR/.env" ]; then
  compose+=(--env-file "$PROJECT_DIR/.env")
fi
"${compose[@]}" -f "$PROJECT_DIR/android-builder/compose.debian.yaml" up -d --no-build
