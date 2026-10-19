#!/usr/bin/env bash
# Import the existing release signing once; never replace an installed signing volume.
set -euo pipefail
ANDROID_SOURCE="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../android" && pwd)}"
docker volume create --label com.docker.compose.project=huluwa --label com.docker.compose.volume=android-signing huluwa-android-signing >/dev/null
docker run --rm --user 0:0 --network none --entrypoint python3 \
  --mount "type=bind,source=${ANDROID_SOURCE},target=/seed,readonly" \
  --mount type=volume,source=huluwa-android-signing,target=/signing \
  huluwa-android-builder:latest -c '
from pathlib import Path
import os, re, shutil
root = Path("/signing")
config = root / "keystore.properties"
key = root / "release.keystore"
if config.exists() and key.exists():
    print("已有持久化 Release 签名，继续复用。")
    raise SystemExit(0)
if config.exists() or key.exists():
    raise SystemExit("签名卷文件不完整，请先恢复原有密钥和配置。")
source = Path("/seed")
text = (source / "keystore.properties").read_text(encoding="utf-8-sig")
setting = re.search(r"(?m)^\s*storeFile\s*=\s*(.+)$", text)
if not setting:
    raise SystemExit("签名配置缺少 storeFile。")
source_key = (source / setting.group(1).strip()).resolve()
if not source_key.is_relative_to(source) or not source_key.is_file():
    raise SystemExit("storeFile 必须指向安卓目录内现有的签名文件。")
shutil.copyfile(source_key, key)
config.write_text(re.sub(r"(?m)^\s*storeFile\s*=.*$", "storeFile=release.keystore", text), encoding="utf-8")
for item in (key, config):
    os.chown(item, 10001, 10001)
    item.chmod(0o600)
os.chown(root, 10001, 10001)
root.chmod(0o700)
print("已导入现有 Release 签名，后续 APK 继续使用同一证书。")
'
