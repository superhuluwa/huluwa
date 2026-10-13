#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为飞牛部署脚本准备 sudo 凭据。

飞牛 fnOS 默认禁止 root 登录（PermitRootLogin no），deploy_fnos.bat 需要以普通
管理员账号执行 sudo 来完成目录属主调整等特权操作。本脚本按以下顺序取得密码并
写入临时文件，随后由 ssh 通过 stdin 交给远端 `sudo -S`：

  1. 环境变量 HULUWA_FNOS_SUDO_PW
  2. --config 指定的本机凭据 JSON（含 host / username / password 字段）
  3. 交互式隐藏输入

密码不会出现在命令行参数、日志或聊天记录里；临时文件由调用方在部署结束后删除。
"""
from __future__ import annotations

import argparse
import getpass
import json
import os
import sys
from pathlib import Path


def load_from_config(path: str, host: str, user: str) -> str:
    """从本机凭据文件读取密码；主机或账号不匹配时返回空串。"""
    cfg_path = Path(path)
    if not cfg_path.exists():
        return ""
    try:
        cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
    except Exception:
        return ""
    if host and cfg.get("host") and cfg.get("host") != host:
        return ""
    if user and cfg.get("username") and cfg.get("username") != user:
        return ""
    return (cfg.get("password") or "").strip()


def main() -> int:
    ap = argparse.ArgumentParser(description="准备飞牛部署所需的 sudo 密码临时文件")
    ap.add_argument("outfile", help="密码临时文件路径")
    ap.add_argument("--config", default="", help="本机凭据 JSON（含 host/username/password）")
    ap.add_argument("--host", default="", help="远端主机，用于核对凭据文件")
    ap.add_argument("--user", default="", help="远端账号，用于核对凭据文件")
    args = ap.parse_args()

    password = os.environ.get("HULUWA_FNOS_SUDO_PW", "").strip()

    if not password and args.config:
        password = load_from_config(args.config, args.host, args.user)

    if not password:
        prompt = "请输入 %s@%s 的 sudo 密码（仅本次使用，不会保存）: " % (
            args.user or "飞牛账号", args.host or "")
        try:
            password = (getpass.getpass(prompt) or "").strip()
        except (EOFError, KeyboardInterrupt):
            print("", file=sys.stderr)
            return 3

    if not password:
        print("未提供 sudo 密码", file=sys.stderr)
        return 2

    # 写入临时文件；远端会用 tr -d '\r' 兜底，这里统一 LF 结尾。
    Path(args.outfile).write_bytes(password.encode("utf-8") + b"\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())