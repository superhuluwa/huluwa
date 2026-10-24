#!/usr/bin/env python3
"""Write the private Docker agent enrollment without exposing its token."""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlsplit


def main():
    parser = argparse.ArgumentParser(description='配置 Debian Docker 打包节点')
    parser.add_argument('--server-url', help='葫芦娃主服务地址')
    parser.add_argument('--node-id', help='主服务分配的构建节点 ID')
    parser.add_argument('--token-stdin', action='store_true', help='从标准输入安全读取密钥，用于自动部署')
    parser.add_argument('--config', type=Path,
                        default=Path(__file__).resolve().parent.parent / 'data/debian-builder/config.json')
    args = parser.parse_args()
    previous = json.loads(args.config.read_text()) if args.config.exists() else {}
    server = (args.server_url or previous.get('serverUrl') or input('葫芦娃主服务地址：')).strip().rstrip('/')
    node = (args.node_id or previous.get('nodeId') or input('构建节点 ID：')).strip()
    parsed = urlsplit(server)
    if (parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username
            or parsed.password or parsed.query or parsed.fragment):
        parser.error('主服务地址必须为不含凭据、查询参数或片段的 HTTP/HTTPS 地址。')
    if not re.fullmatch(r'[a-f0-9]{32}', node):
        parser.error('节点 ID 格式无效。')
    same_node = previous.get('serverUrl') == server and previous.get('nodeId') == node
    token = sys.stdin.readline().strip() if args.token_stdin else (previous.get('token') if same_node else None)
    if not token and not args.token_stdin:
        token = getpass.getpass('构建节点密钥（不回显）：').strip()
    if not re.fullmatch(r'[a-f0-9]{64}', token):
        parser.error('节点密钥格式无效。')
    identity = hashlib.sha256((server + '/' + node).encode()).hexdigest()[:16]
    config = {
        'serverUrl': server, 'nodeId': node, 'token': token,
        'dataDirectory': '/build-data',
        'workDirectory': '/workspace/nodes/' + identity,
        'gradleHome': '/workspace/gradle',
        'signingDirectory': '/signing',
        'javaHome': '/opt/java/openjdk', 'androidHome': '/opt/android-sdk',
        'repository': 'https://gitee.com/wangjing71/huluwa.git', 'branch': 'master',
    }
    # Different enrollments must not share task IDs, but may keep Gradle caches.
    config['dataDirectory'] += '/nodes/' + identity
    args.config.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    # Docker installs a UID 10001-owned secret; the enrolling user can read it
    # through their group, within a private directory. No token enters argv/env.
    install = """import json,os,sys
from pathlib import Path
path=Path('/config')/sys.argv[1]
temporary=path.with_name(path.name+'.tmp')
fd=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
with os.fdopen(fd,'w') as output:
    json.dump(json.load(sys.stdin),output,ensure_ascii=False,indent=2)
    output.write('\\n')
os.chown(temporary,10001,int(sys.argv[2]))
temporary.chmod(0o640)
os.replace(temporary,path)
"""
    subprocess.run([
        'docker', 'run', '--rm', '-i', '--network', 'none', '--user', '0:0',
        '--entrypoint', 'python3', '--mount',
        'type=bind,source=' + str(args.config.parent.resolve()) + ',target=/config',
        'huluwa-android-builder:debian', '-c', install, args.config.name, str(os.getgid()),
    ], input=json.dumps(config), text=True, check=True)
    print('已保存 Debian 打包节点配置：' + str(args.config))


if __name__ == '__main__':
    main()
