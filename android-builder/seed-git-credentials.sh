#!/usr/bin/env bash
# Copy the saved credential for exactly https://gitee.com into the read-only worker volume.
set -euo pipefail
python3 - <<'PY'
import json
import os
import subprocess

env = os.environ.copy()
env['GIT_TERMINAL_PROMPT'] = '0'
result = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=gitee.com\n\n',
                        text=True, capture_output=True, env=env)
credential = dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)
if result.returncode or not credential.get('username') or not credential.get('password'):
    raise SystemExit('未找到可用的 Gitee 已保存凭据，请先配置本机 Git 凭据。')
if credential.get('host') != 'gitee.com' or credential.get('protocol') != 'https':
    raise SystemExit('凭据主机不匹配，未导入。')
subprocess.run(['docker', 'volume', 'create', '--label', 'com.docker.compose.project=huluwa',
                '--label', 'com.docker.compose.volume=android-credentials', 'huluwa-android-credentials'],
               check=True, stdout=subprocess.DEVNULL)
script = '''import json,os,sys
from pathlib import Path
root=Path('/credentials')
path=root/'git-credentials.json.tmp'
path.write_text(json.dumps(json.load(sys.stdin)),encoding='utf-8')
path.chmod(0o600)
os.chown(path,10001,10001)
os.replace(path,root/'git-credentials.json')
'''
subprocess.run(['docker', 'run', '--rm', '-i', '--network', 'none', '--user', '0:0',
                '--entrypoint', 'python3', '--mount', 'type=volume,source=huluwa-android-credentials,target=/credentials',
                os.environ.get('HULUWA_ANDROID_BUILDER_IMAGE', 'huluwa-android-builder:latest'), '-c', script],
               input=json.dumps({'host': 'gitee.com', 'username': credential['username'], 'password': credential['password']}),
               text=True, check=True)
print('已将 Gitee 已保存凭据导入构建凭据卷，凭据未写入源码或日志。')
PY
