#!/usr/bin/env python3
"""恢复 Docker 配置，在 Android 管理的服务进程组内启动签名服务。"""
import os
from pathlib import Path


environment = {}
for item in Path('/run/container.env').read_bytes().split(b'\0'):
    if b'=' in item:
        key, value = item.split(b'=', 1)
        environment[os.fsdecode(key)] = os.fsdecode(value)
environment.update({
    'PATH': '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin',
    'HOME': '/root',
    'PYTHONUNBUFFERED': '1',
    'PYTHONIOENCODING': 'utf-8',
    'ADB_TARGET': '127.0.0.1:5555',
    'FRIDA_DIRECT': '1',
    'APK_PATH': '/apk/base.apk',
})
# tini 回收 ADB 和健康检查退出后的孤儿进程，并向整组子进程转发停止信号。
os.execve('/usr/bin/tini', ['tini', '-s', '-g', '--', '/app/entrypoint.sh'], environment)
