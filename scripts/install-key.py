#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""一次性把本机部署公钥安装到服务器 ~/.ssh/authorized_keys。

只在 deploy.bat 首次运行(或更换密钥)时被调用, 需要输入一次服务器密码。
密码不会被保存; 装好公钥后, 后续部署全部免密自动认证。
"""
from __future__ import annotations

import argparse
import getpass
import json
import sys
from pathlib import Path

try:
    import paramiko
except ImportError:
    print("缺少 paramiko, 执行: pip install paramiko", file=sys.stderr)
    raise SystemExit(2)

DEFAULT_CONFIG = Path.home() / ".codex" / "private" / "centos-server" / "config.json"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", required=True)
    ap.add_argument("--port", type=int, default=22)
    ap.add_argument("--user", required=True)
    ap.add_argument("--pubkey", required=True)
    ap.add_argument("--config", default=str(DEFAULT_CONFIG))
    args = ap.parse_args()

    pub = Path(args.pubkey).read_text(encoding="utf-8").strip()
    if not pub:
        print("公钥文件为空: %s" % args.pubkey, file=sys.stderr)
        return 2

    # 优先复用本机已保存的服务器密码, 免去手动输入
    password = None
    cfg_path = Path(args.config)
    if cfg_path.exists():
        try:
            cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
            if cfg.get("host") == args.host and cfg.get("username") == args.user:
                password = cfg.get("password")
        except Exception:
            password = None

    if not password:
        password = getpass.getpass("请输入 %s@%s 的 SSH 密码(仅本次使用, 不会保存): " % (args.user, args.host))

    client = paramiko.SSHClient()
    client.load_system_host_keys()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect(
            hostname=args.host, port=args.port, username=args.user,
            password=password, timeout=20, banner_timeout=20, auth_timeout=20,
            look_for_keys=False, allow_agent=False,
        )
    except paramiko.AuthenticationException:
        print("认证失败: 密码错误", file=sys.stderr)
        return 3
    except Exception as exc:
        print("连接失败: %s" % exc, file=sys.stderr)
        return 4

    try:
        quoted = "'" + pub.replace("'", "'\\''") + "'"
        cmd = (
            "mkdir -p /root/.ssh && chmod 700 /root/.ssh && "
            "touch /root/.ssh/authorized_keys && chmod 600 /root/.ssh/authorized_keys && "
            "grep -qF %s /root/.ssh/authorized_keys || echo %s >> /root/.ssh/authorized_keys; "
            "restorecon -R /root/.ssh 2>/dev/null || true; "
            "grep -cF %s /root/.ssh/authorized_keys"
        ) % (quoted, quoted, quoted)
        _, stdout, stderr = client.exec_command(cmd, timeout=30)
        out = stdout.read().decode("utf-8", "replace").strip()
        err = stderr.read().decode("utf-8", "replace").strip()
        status = stdout.channel.recv_exit_status()
        if status != 0:
            print("安装公钥失败: %s" % (err or out), file=sys.stderr)
            return 5
        print("公钥已安装到 %s@%s" % (args.user, args.host))
        return 0
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
