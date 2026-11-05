#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
红果免费短剧 签名服务
=====================

在装有红果 App 的 Android 环境（容器内 redroid / 真机 / 模拟器）中，
通过 Frida 调用 App 内部的 NetworkParams.tryAddSecurityFactor()，
为任意 URL 生成签名头。

HTTP 接口
---------
  GET  /health   健康检查（会真实签一次名）
  GET  /info     环境信息（frida 版本、App 包名、设备型号、可用接口）
  POST /sign     生成签名
       Body:   {"url": "https://..."}
       Resp:   {"X-Gorgon":"...","X-Khronos":"...","X-Argus":"...",
                "X-Ladon":"...","X-Medusa":"...","X-Helios":"..."}
  POST /verify   端到端验证（签名 -> 真实请求 search/tab -> 校验 code=0）
       Body:   {"url": "..."}   可选，默认内置 search/tab URL
       Resp:   {"ok":true,"http_status":200,"bytes":68118,"code":0,...}

环境变量
--------
  FRIDA_HOST         frida-server 地址（默认 127.0.0.1:27042）
  TARGET_PKG         目标包名（默认 com.phoenix.read）
  PORT               HTTP 端口（默认 8901）
  BIND               监听地址（默认 127.0.0.1，容器内用 0.0.0.0）
  ADB_SERIAL         adb 设备序列号（多设备时必须）
  ADB_PATH           adb 可执行文件路径
  ADB_SERVER_SOCKET  adb server 地址（如 tcp:host:5037）
"""
import sys
import os
import json
import time
import argparse
import threading
import traceback
import urllib.request
import gzip

try:
    import frida
except ImportError:
    print("ERROR: 需要 frida。安装: pip install frida")
    sys.exit(1)

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# ---------------------------------------------------------------------------
# Frida 脚本：调用 App 内部签名方法
# ---------------------------------------------------------------------------
JS = r"""
Java.perform(function () {
    var SIG_KEYS = ['X-Gorgon','X-Khronos','X-Argus','X-Ladon','X-Medusa','X-Helios'];

    rpc.exports = {
        ping: function () { return 'pong'; },

        info: function () {
            var out = {};
            try {
                var NP = Java.use('com.bytedance.frameworks.baselib.network.http.NetworkParams');
                out.hasNetworkParams = true;
                out.className = String(NP.class.getName());
            } catch (e) {
                out.hasNetworkParams = false;
                out.err = String(e);
            }
            try {
                var AT = Java.use('android.app.ActivityThread');
                out.pkg = String(AT.currentPackageName());
            } catch (e) {}
            try {
                var Build = Java.use('android.os.Build');
                out.model = String(Build.MODEL.value);
                out.sdkInt = String(Build.VERSION.SDK_INT.value);
            } catch (e) {}
            return out;
        },

        sign: function (url) {
            try {
                var NP = Java.use('com.bytedance.frameworks.baselib.network.http.NetworkParams');
                var HM = Java.use('java.util.HashMap');
                var AL = Java.use('java.util.ArrayList');

                var m = HM.$new();
                var l = AL.$new();
                l.add('application/json; charset=utf-8');
                m.put('accept', l);

                var ret = NP.tryAddSecurityFactor(url, m);
                if (ret === null) return {__error: 'tryAddSecurityFactor returned null'};

                var out = {};
                for (var i = 0; i < SIG_KEYS.length; i++) {
                    var k = SIG_KEYS[i];
                    try {
                        var v = ret.get(k);
                        if (v !== null && v !== undefined) out[k] = String(v);
                    } catch (e) {}
                }
                return out;
            } catch (e) {
                return {__error: String(e)};
            }
        }
    };
});
"""

def build_verify_url(query="赘婿", offset=0):
    """构造一个参数完整的 search/tab URL
    （参数不全时服务端会返回空，所以内置完整参数集）"""
    import urllib.parse as _up
    p = {
        "device_platform": "android", "aid": "8662", "app_name": "novelread",
        "version_code": "73932", "version_name": "7.3.9.32",
        "manifest_version_code": "73932", "update_version_code": "73932",
        "channel": "vivo_8662_64", "os": "android", "ssmix": "a",
        "device_type": "SM-S9310", "device_brand": "SAMSUNG",
        "language": "zh", "os_api": "34", "os_version": "14",
        "resolution": "1440*2560", "dpi": "640", "ac": "wifi",
        "device_id": "305040037566937", "iid": "305040037571033",
        "cdid": "432b64a9-68af-457b-b8db-16032c6d6427",
        "host_abi": "arm64-v8a", "dragon_device_type": "phone",
        "user_is_login": "0", "bookshelf_search_plan": "4",
        "bookstore_tab": "-1", "search_source": "1",
        "use_correct": "true", "is_first_enter_search": "true",
        "only_feed": "false", "only_large_card": "false",
        "pad_column_cover": "0", "from_rs": "false",
        "from_half_screen": "false", "use_lynx": "false",
        "clicked_content": "auto", "need_personal_recommend": "1",
        "query": query, "offset": str(offset), "count": "0",
        "_rticket": str(int(time.time() * 1000)),
    }
    return ("https://api5-normal-sinfonlineb.fqnovel.com"
            "/reading/bookapi/search/tab/v?" + _up.urlencode(p))


# 兼容旧引用：惰性构造
DEFAULT_VERIFY_URL = build_verify_url()

UA = ("com.phoenix.read/73932 (Linux; U; Android 14; zh_CN; SM-S9310; "
      "Build/UQ1A.240205.06091433;tt-ok/3.12.13.20)")


# ---------------------------------------------------------------------------
# 签名器
# ---------------------------------------------------------------------------
class Signer:
    def __init__(self, host, pkg, serial=None):
        self.pkg = pkg
        self.serial = serial
        self.host = host
        self.dev = None
        self.session = None
        self.script = None
        self.lock = threading.Lock()

    def _connect_device(self):
        if self.dev is not None:
            return self.dev
        self.dev = frida.get_device_manager().add_remote_device(self.host)
        return self.dev

    def _find_pid(self):
        # ARM 翻译层会产生沿用 App 名称的子进程；签名必须注入 zygote
        # 创建的 App 主进程，不能只取 Frida 枚举或 pidof 的第一个结果。
        try:
            import subprocess
            adb = os.environ.get("ADB_PATH", "adb")
            sel = ["-s", self.serial] if self.serial else []
            result = subprocess.run(
                [adb] + sel + ["shell", "ps", "-A", "-o", "PID,PPID,NAME"],
                capture_output=True, timeout=15)
            processes = {}
            for line in result.stdout.decode("utf-8", "ignore").splitlines():
                columns = line.split(None, 2)
                if len(columns) == 3 and columns[0].isdigit() and columns[1].isdigit():
                    processes[int(columns[0])] = (int(columns[1]), columns[2].strip())
            for pid, (parent, name) in processes.items():
                parent_name = processes.get(parent, (0, ""))[1]
                if name == self.pkg and parent_name in ("zygote", "zygote64"):
                    return pid
            if any(name in ("zygote", "zygote64") for _, name in processes.values()):
                # 已读取到 Android 进程表时，主进程缺失就直接等待恢复，
                # 不再回退到同名子进程或触发额外的系统进程注入。
                return None
        except (OSError, subprocess.SubprocessError):
            pass

        # 1) frida 枚举
        try:
            for p in self._connect_device().enumerate_processes():
                if p.name == self.pkg:
                    return p.pid
        except Exception:
            pass
        # 2) adb 兜底（部分翻译环境 frida 枚举不到 app 进程）
        try:
            import subprocess
            adb = os.environ.get("ADB_PATH")
            sock = os.environ.get("ADB_SERVER_SOCKET")
            cands = [adb] if adb else []
            cands += ["adb", r"C:\leidian\LDPlayer14\adb.exe"]
            env2 = dict(os.environ)
            if sock:
                env2["ADB_SERVER_SOCKET"] = sock
            for cand in cands:
                if not cand:
                    continue
                sel = ["-s", self.serial] if self.serial else []
                try:
                    r = subprocess.run(
                        [cand] + sel + ["shell", "pidof", self.pkg],
                        capture_output=True, timeout=25, env=env2)
                    out = r.stdout.decode("utf-8", "ignore").strip()
                    if out:
                        return int(out.split()[0])
                except Exception:
                    continue
        except Exception:
            pass
        return None

    def attach(self):
        if self.script is not None:
            return
        pid = self._find_pid()
        if not pid:
            raise RuntimeError(
                "找不到 App 进程: %s（请确认 App 正在运行）" % self.pkg)
        self.session = self._connect_device().attach(pid)
        self.script = self.session.create_script(JS)
        self.script.on("message", self._on_msg)
        self.script.load()
        time.sleep(1.0)
        if self.script.exports_sync.ping() != "pong":
            raise RuntimeError("frida 脚本加载失败")
        print("[signer] attached pid=%d" % pid, flush=True)

    def _on_msg(self, msg, data):
        if msg.get("type") == "error":
            print("[frida-error] %s" % msg.get("description"), flush=True)

    def _call(self, name, *args, timeout=30):
        with self.lock:
            self.attach()
            box = {}

            def run():
                try:
                    box["r"] = getattr(self.script.exports_sync, name)(*args)
                except Exception as e:
                    box["e"] = repr(e)

            t = threading.Thread(target=run, daemon=True)
            t.start()
            t.join(timeout)
            if t.is_alive():
                self.reset()
                raise RuntimeError("%s 超时（%ds）" % (name, timeout))
            if "e" in box:
                self.reset()
                raise RuntimeError(box["e"])
            r = box.get("r")
            if isinstance(r, dict) and "__error" in r:
                raise RuntimeError(r["__error"])
            return r

    def info(self):
        return self._call("info", timeout=20)

    def sign(self, url, timeout=30):
        return self._call("sign", url, timeout=timeout)

    def reset(self):
        script, session = self.script, self.session
        self.script = None
        self.session = None
        self.dev = None

        # 失联时 Frida 的同步清理也可能卡住，不能阻塞 HTTP 超时响应。
        def cleanup():
            try:
                if session:
                    session.detach()
            except Exception:
                pass
            try:
                if script:
                    script.unload()
            except Exception:
                pass

        threading.Thread(target=cleanup, daemon=True).start()


# ---------------------------------------------------------------------------
# 端到端验证
# ---------------------------------------------------------------------------
def http_fetch(url, sig, timeout=40):
    h = {"User-Agent": UA, "Accept-Encoding": "gzip"}
    if sig:
        h.update(sig)
    req = urllib.request.Request(url, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw, st = r.read(), r.status
    except urllib.error.HTTPError as e:
        raw, st = e.read(), e.code
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    return st, raw


def _first_book_id(parsed):
    if not parsed:
        return None
    for t in (parsed.get("search_tabs") or []):
        for it in (t.get("data") or []):
            if it.get("book_id"):
                return str(it["book_id"])
    return None


def do_verify(signer, url=None):
    """签名 + 真实请求 + 校验，返回结构化结果"""
    url = url or build_verify_url()
    out = {"url": url, "steps": []}

    t0 = time.time()
    try:
        sig = signer.sign(url)
    except Exception as e:
        out.update(ok=False, stage="sign", error=str(e))
        return out
    t1 = time.time()

    keys = sorted(sig.keys()) if sig else []
    has_medusa = bool(sig and sig.get("X-Medusa"))
    has_helios = bool(sig and sig.get("X-Helios"))
    out["steps"].append({
        "step": "sign", "ok": bool(keys), "ms": int((t1 - t0) * 1000),
        "headers": keys, "has_medusa": has_medusa, "has_helios": has_helios,
    })
    if not keys:
        out.update(ok=False, stage="sign", error="未返回任何签名头")
        return out
    if not (has_medusa and has_helios):
        out.update(ok=False, stage="sign",
                   error="缺少关键头 X-Medusa/X-Helios（search/tab 必需）")
        return out

    try:
        st, raw = http_fetch(url, sig)
    except Exception as e:
        out.update(ok=False, stage="http", error=str(e))
        return out
    t2 = time.time()

    parsed = None
    code = None
    tabs = 0
    items = 0
    if raw:
        try:
            parsed = json.loads(raw)
            code = parsed.get("code")
            stabs = parsed.get("search_tabs") or []
            tabs = len(stabs)
            items = sum(len(t.get("data") or []) for t in stabs)
        except Exception:
            pass

    out["steps"].append({
        "step": "http", "ok": len(raw) > 0, "ms": int((t2 - t1) * 1000),
        "http_status": st, "bytes": len(raw), "code": code,
        "tabs": tabs, "items": items,
    })

    if not raw:
        out.update(ok=False, stage="http",
                   error="响应为空（签名未被服务端接受，或 App 上下文失效）")
        return out
    if code != 0:
        out.update(ok=False, stage="parse", error="code=%s（期望 0）" % code)
        return out

    out.update(ok=True, http_status=st, bytes=len(raw), code=code,
               tabs=tabs, items=items,
               first_book_id=_first_book_id(parsed),
               total_ms=int((t2 - t0) * 1000))
    return out


# ---------------------------------------------------------------------------
# HTTP 服务
# ---------------------------------------------------------------------------
class Handler(BaseHTTPRequestHandler):
    signer = None

    def log_message(self, fmt, *args):
        print("[http] %s %s" % (self.command, self.path), flush=True)

    def _send(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False, indent=2).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        try:
            self.wfile.write(body)
        except Exception:
            pass

    def _read_json(self):
        n = int(self.headers.get("Content-Length") or 0)
        if n <= 0:
            return {}
        try:
            return json.loads(self.rfile.read(n) or b"{}")
        except Exception:
            return {}

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/health":
            try:
                s = self.signer.sign(build_verify_url(), timeout=40)
                good = (bool(s) and bool(s.get("X-Medusa"))
                        and bool(s.get("X-Helios")))
                self._send(200 if good else 503,
                           {"ok": good,
                            "headers": sorted((s or {}).keys()),
                            "ts": int(time.time())})
            except Exception as e:
                self._send(503, {"ok": False, "error": str(e),
                                 "ts": int(time.time())})
        elif path == "/info":
            try:
                info = self.signer.info()
                info["frida_version"] = frida.__version__
                info["target_pkg"] = self.signer.pkg
                info["frida_host"] = self.signer.host
                info["endpoints"] = ["GET /health", "GET /info",
                                     "POST /sign", "POST /verify"]
                self._send(200, info)
            except Exception as e:
                self._send(503, {"error": str(e)})
        else:
            self._send(404, {"error": "not found",
                             "endpoints": ["GET /health", "GET /info",
                                           "POST /sign", "POST /verify"]})

    def do_POST(self):
        path = self.path.split("?")[0]
        if path == "/sign":
            data = self._read_json()
            url = data.get("url")
            if not url:
                self._send(400, {"error": "missing 'url' in JSON body"})
                return
            try:
                sig = self.signer.sign(url)
                if not sig:
                    self._send(500, {"error": "sign returned empty"})
                else:
                    self._send(200, sig)
            except Exception as e:
                self._send(500, {"error": str(e),
                                 "trace": traceback.format_exc()[-600:]})
        elif path == "/verify":
            data = self._read_json()
            try:
                res = do_verify(self.signer, data.get("url"))
                self._send(200 if res.get("ok") else 500, res)
            except Exception as e:
                self._send(500, {"ok": False, "error": str(e),
                                 "trace": traceback.format_exc()[-600:]})
        else:
            self._send(404, {"error": "not found"})


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(
        description="红果签名服务（Frida -> App 内部算法）")
    ap.add_argument("--host",
                    default=os.environ.get("FRIDA_HOST", "127.0.0.1:27042"),
                    help="frida-server 地址")
    ap.add_argument("--pkg",
                    default=os.environ.get("TARGET_PKG", "com.phoenix.read"),
                    help="目标包名")
    ap.add_argument("--port", type=int,
                    default=int(os.environ.get("PORT", "8901")),
                    help="HTTP 端口")
    ap.add_argument("--bind",
                    default=os.environ.get("BIND", "127.0.0.1"),
                    help="监听地址")
    ap.add_argument("--serial",
                    default=os.environ.get("ADB_SERIAL") or None,
                    help="adb 设备序列号（多设备时必须）")
    args = ap.parse_args()

    print("=" * 60, flush=True)
    print(" 红果签名服务", flush=True)
    print("=" * 60, flush=True)
    print(" frida-server : %s" % args.host, flush=True)
    print(" frida 客户端 : %s" % frida.__version__, flush=True)
    print(" 目标包名     : %s" % args.pkg, flush=True)
    if args.serial:
        print(" adb 序列号   : %s" % args.serial, flush=True)

    signer = Signer(args.host, args.pkg, args.serial)
    try:
        signer.attach()
    except Exception as e:
        print("\n[FATAL] 无法 attach: %s" % e, flush=True)
        print("排查建议：", flush=True)
        print("  1. App 是否在运行: adb shell pidof %s" % args.pkg, flush=True)
        print("  2. frida-server 是否在跑: adb shell pgrep -f frida-server",
              flush=True)
        print("  3. frida 版本是否一致（客户端与 server）", flush=True)
        sys.exit(1)

    print("\n[OK] 签名就绪", flush=True)
    print(" HTTP: http://%s:%d" % (args.bind, args.port), flush=True)
    print("   GET  /health", flush=True)
    print("   GET  /info", flush=True)
    print("   POST /sign    {\"url\":\"...\"}", flush=True)
    print("   POST /verify  (端到端验证)", flush=True)
    print("", flush=True)

    Handler.signer = signer
    srv = ThreadingHTTPServer((args.bind, args.port), Handler)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\n关闭", flush=True)


if __name__ == "__main__":
    main()
