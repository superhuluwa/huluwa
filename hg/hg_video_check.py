#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""红果视频链路自检：搜索、分集、播放模型、CDN、可选完整下载/解码。

主机只使用 Python 标准库。--device-from-container 复用签名容器内的 Frida。
默认从签名服务 /info 自动读取当前设备，不能复用其他模拟器的 device_id/iid。
退出码：0=所选检查通过，1=接口/下载失败，2=签名或设备资料不可用，
3=搜索失败，4=加密流尚未验证可播放，5=文件/解码检查失败。
"""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

HOST = "api5-normal-sinfonlineb.fqnovel.com"
UA = ("com.phoenix.read/73932 (Linux; U; Android 14; zh_CN; SM-S9310; "
      "Build/UQ1A.240205.06091433;tt-ok/3.12.13.20)")
DEVICE = {}
COMMON = {
    "device_platform": "android", "aid": "8662", "app_name": "novelread",
    "version_code": "73932", "version_name": "7.3.9.32",
    "manifest_version_code": "73932", "update_version_code": "73932",
    "os": "android", "ssmix": "a", "language": "zh",
    "os_api": "34", "os_version": "14",
    "resolution": "1440*2560", "dpi": "640", "ac": "wifi",
    "host_abi": "arm64-v8a", "dragon_device_type": "phone",
    "user_is_login": "0",
}
DEVICE_KEYS = {"device_id", "iid", "cdid", "device_type", "device_brand", "channel"}
SIGN_KEYS = {"x-gorgon", "x-khronos", "x-argus", "x-ladon", "x-medusa", "x-helios"}
SERVER = None

# 在已有签名容器内运行；不改 App 属性、不启动 Activity、不重启签名服务。
PROFILE_READER = r'''
import json, os, subprocess, threading, urllib.parse
import frida
pkg = os.environ.get("TARGET_PKG", "com.phoenix.read")
adb = [os.environ.get("ADB_PATH", "adb")]
if os.environ.get("ADB_SERIAL"):
    adb += ["-s", os.environ["ADB_SERIAL"]]
pid = int(subprocess.check_output(adb + ["shell", "pidof", "-s", pkg],
                                  text=True, timeout=15).strip())
session = frida.get_device_manager().add_remote_device(
    os.environ.get("FRIDA_HOST", "127.0.0.1:27042")).attach(pid)
done, box = threading.Event(), {}
def receive(message, data):
    if message.get("type") == "send":
        box.update(message["payload"])
    else:
        box["error"] = message.get("description", str(message))
    done.set()
try:
    script = session.create_script(r"""
Java.perform(function () {
    try {
        var N = Java.use('com.bytedance.frameworks.baselib.network.http.NetworkParams');
        var B = Java.use('android.os.Build');
        var L = Java.use('java.util.Locale');
        var url = N.addCommonParams.overload('java.lang.String', 'boolean').call(
            N, 'https://api5-normal-sinfonlineb.fqnovel.com/reading/bookapi/video_tab/video_model/v', false);
        var ua = N.getUserAgent();
        send({url: String(url), user_agent: ua === null ? null : String(ua),
              build_id: String(B.ID.value), locale: String(L.getDefault())});
    } catch (e) { send({error: String(e)}); }
});
""")
    script.on("message", receive)
    script.load()
    if not done.wait(20):
        raise RuntimeError("读取 App 公共参数超时")
    if box.get("error"):
        raise RuntimeError(box["error"])
    params = dict(urllib.parse.parse_qsl(urllib.parse.urlsplit(box["url"]).query))
    if not box.get("user_agent"):
        box["user_agent"] = (
            "com.phoenix.read/{version_code} (Linux; U; Android {os_version}; "
            "{locale}; {device_type}; Build/{build_id};tt-ok/3.12.13.20)"
        ).format(**dict(params, locale=box["locale"], build_id=box["build_id"]))
    print(json.dumps({"params": params, "user_agent": box["user_agent"]}))
finally:
    session.detach()
'''


class SignError(RuntimeError):
    pass


def log(message=""):
    print(message, flush=True)


def hr(title):
    log("\n" + "=" * 76 + "\n" + title + "\n" + "=" * 76)


def load_device(args):
    global UA
    if args.device_from_container:
        result = subprocess.run(
            ["docker", "exec", "-i", args.device_from_container, "python3", "-"],
            input=PROFILE_READER, capture_output=True, text=True, timeout=45)
        if result.returncode:
            raise ValueError("容器设备参数读取失败: " + result.stderr.strip()[-1000:])
        profile = json.loads(result.stdout)
    elif args.device_profile:
        profile = json.loads(Path(args.device_profile).read_text(encoding="utf-8"))
    else:
        with urllib.request.urlopen(SERVER + "/info", timeout=30) as response:
            info = json.loads(response.read())
        profile = info.get("deviceProfile")
        if not isinstance(profile, dict):
            raise ValueError("签名服务尚未提供自动设备信息，请更新签名服务或显式指定设备来源")
    if not isinstance(profile, dict):
        raise ValueError("设备资料必须是 JSON 对象")
    params = profile.get("params", profile)
    if not isinstance(params, dict):
        raise ValueError("设备资料 params 必须是 JSON 对象")
    # 仅使用设备/应用参数，不导出会话令牌、播放参数或旧 _rticket。
    allowed = set(COMMON) | DEVICE_KEYS
    params = {k: str(v) for k, v in params.items() if k in allowed and v is not None}
    if any(not params.get(k) for k in ("device_id", "iid", "cdid")):
        raise ValueError("设备资料必须包含当前签名 App 的 device_id、iid、cdid")
    COMMON.update({k: v for k, v in params.items() if k in COMMON})
    DEVICE.update({k: v for k, v in params.items() if k in DEVICE_KEYS})
    if profile.get("user_agent"):
        UA = str(profile["user_agent"])
    if args.save_device_profile:
        fd = os.open(args.save_device_profile, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump({"params": params, "user_agent": UA}, f, ensure_ascii=False, indent=2)
        os.chmod(args.save_device_profile, 0o600)
    log("设备资料: model=%s，已加载当前 device_id / iid / cdid" % DEVICE.get("device_type"))


def sign_url(url):
    request = urllib.request.Request(
        SERVER + "/sign", data=json.dumps({"url": url}).encode(),
        headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            headers = json.loads(response.read())
        if (not isinstance(headers, dict) or
                not SIGN_KEYS.issubset({k.lower() for k in headers}) or
                any(not isinstance(v, str) or not v for v in headers.values())):
            raise ValueError("签名响应缺少有效的 6 个签名头")
        return headers
    except (ValueError, OSError, urllib.error.URLError) as exc:
        raise SignError(str(exc)) from exc


def http_get(url, headers=None, limit=None):
    """返回 HTTP 状态、响应字节、响应头；limit 限制实际读取量。"""
    h = {"User-Agent": UA, "Accept-Encoding": "gzip"}
    h.update(headers or {})
    request = urllib.request.Request(url, headers=h)
    try:
        response = urllib.request.urlopen(request, timeout=45)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        raw = response.read() if limit is None else response.read(limit)
        status = response.code
        response_headers = {k.lower(): v for k, v in response.headers.items()}
    if raw.startswith(b"\x1f\x8b"):
        raw = gzip.decompress(raw)
    return status, raw, response_headers


def build_url(path, extra):
    params = dict(COMMON, **DEVICE)
    params.update(extra)
    params["_rticket"] = str(int(time.time() * 1000))
    return "https://%s%s?%s" % (HOST, path, urllib.parse.urlencode(params))


def call(path, extra, sign_headers=None):
    url = build_url(path, extra)
    signatures = sign_url(url)
    if sign_headers is not None:
        signatures = {k: v for k, v in signatures.items() if k.lower() in sign_headers}
    try:
        status, raw, headers = http_get(url, signatures)
    except (OSError, ValueError, urllib.error.URLError) as exc:
        log("  请求异常 %s: %s" % (path, exc))
        return 0, None, None
    if status != 200 or not raw:
        log("  HTTP %s, %d 字节, path=%s, logid=%s" %
            (status, len(raw), path, headers.get("x-tt-logid", "-")))
        return len(raw), None, None
    try:
        result = json.loads(raw)
        if not isinstance(result, dict):
            raise ValueError("响应不是 JSON 对象")
        return len(raw), result.get("code"), result
    except ValueError:
        log("  非 JSON 响应: HTTP %s, %d 字节, path=%s" % (status, len(raw), path))
        return len(raw), None, None


def check_health():
    hr("检查 1: 签名服务")
    try:
        with urllib.request.urlopen(SERVER + "/health", timeout=60) as r:
            health = json.loads(r.read())
        log("  /health ok=%s headers=%s" % (health.get("ok"), health.get("headers")))
        if not health.get("ok"):
            return False
        with urllib.request.urlopen(SERVER + "/info", timeout=30) as r:
            info = json.loads(r.read())
        log("  /info pkg=%s model=%s frida=%s" %
            (info.get("pkg"), info.get("model"), info.get("frida_version")))
        return True
    except (OSError, ValueError, urllib.error.URLError) as exc:
        log("  签名服务不可用: %s" % exc)
        return False


def search(query):
    n, code, result = call("/reading/bookapi/search/tab/v", {
        "query": query, "offset": "0", "count": "0", "bookshelf_search_plan": "4",
        "bookstore_tab": "-1", "search_source": "1", "use_correct": "true",
        "is_first_enter_search": "true",
    })
    videos = []
    if result and code == 0:
        for tab in result.get("search_tabs") or []:
            for item in tab.get("data") or []:
                videos.extend(v for v in item.get("video_data") or [] if v.get("vid"))
    log("  搜索 %s: %d 字节, code=%s, 视频 %d 个" % (query, n, code, len(videos)))
    return videos


def parse_model(response):
    data = response.get("data") or {}
    model = data.get("video_model") or {}
    if isinstance(model, str):
        model = json.loads(model)
    if not isinstance(model, dict):
        raise ValueError("video_model 不是 JSON 对象")
    streams = model.get("video_list") or []
    if isinstance(streams, dict):
        streams = list(streams.values())
    direct_url = data.get("mainUrl") or data.get("main_url")
    if direct_url and not any(s.get("main_url") == direct_url for s in streams):
        streams.append({"main_url": direct_url})
    return data, model, streams


def check_sign_headers(vid, sid, query):
    hr("检查 2: 签名头组合对照")
    search_params = {"query": query, "offset": "0", "count": "0",
                     "bookshelf_search_plan": "4", "bookstore_tab": "-1",
                     "search_source": "1", "use_correct": "true",
                     "is_first_enter_search": "true"}
    targets = [("search/tab", "/reading/bookapi/search/tab/v", search_params)]
    if sid:
        targets.append(("video_detail", "/reading/bookapi/video_tab/video_detail/v",
                        {"video_series_id": sid, "source": "0",
                         "screen_width_px": COMMON["resolution"].split("*")[0],
                         "is_horizontal_screen": "false"}))
    targets.append(("video_model", "/reading/bookapi/video_tab/video_model/v",
                    {"video_id": vid, "source": "0"}))
    combinations = [("无签名", set()), ("Medusa+Helios", {"x-medusa", "x-helios"}),
                    ("Gorgon 四个", {"x-gorgon", "x-khronos", "x-argus", "x-ladon"}),
                    ("全部六个", None)]
    full_sign_ok = False
    for name, keys in combinations:
        cells = []
        successes = []
        for label, path, params in targets:
            n, code, _ = call(path, params, keys)
            successes.append(code == 0)
            cells.append("%s=%dB/code:%s" % (label, n, code))
        if keys is None:
            full_sign_ok = all(successes)
        log("  %s: %s" % (name, ", ".join(cells)))
    return full_sign_ok


def probe_video_model(vid):
    hr("检查 3: video_model 参数对照")
    combinations = [
        ("最小参数", {}), ("UGC platform=2", {"video_platform": "2"}),
        ("Outer platform=3", {"video_platform": "3"}),
        ("device_level=3", {"device_level": "3"}),
        ("多清晰度", {"need_all_video_definition": "true"}),
        ("video_id_type=0", {"video_id_type": "0"}),
        ("source=1", {"source": "1"}), ("source=2", {"source": "2"}),
    ]
    hit = None
    for name, extra in combinations:
        params = {"video_id": vid, "source": "0", **extra}
        n, code, result = call("/reading/bookapi/video_tab/video_model/v", params)
        valid = False
        if result and code == 0:
            try:
                _, _, streams = parse_model(result)
                valid = any(s.get("main_url") for s in streams)
            except ValueError:
                pass
        log("  %s: %d 字节, code=%s, 地址=%s" % (name, n, code, valid))
        if valid and hit is None:
            hit = params
    if hit is None:
        log("  本次组合均未取得地址，仍需检查设备资料、请求参数和 App 状态")
    return hit


def check_media(stream, args):
    url = stream["main_url"]
    if urllib.parse.urlsplit(url).scheme not in ("http", "https"):
        log("  播放地址不是 HTTP(S) URL")
        return 1
    meta = stream.get("video_meta") or {}
    encryption = stream.get("encrypt_info") or {}
    encrypted = encryption.get("encrypt") in (True, 1, "1", "true")
    log("  清晰度=%s %sx%s codec=%s size=%s" %
        (meta.get("definition"), meta.get("vwidth"), meta.get("vheight"),
         meta.get("codec_type"), meta.get("size")))
    log("  加密=%s method=%s" % (encrypted, encryption.get("encryption_method")))
    status, chunk, headers = http_get(
        url, {"Range": "bytes=0-1023", "Accept-Encoding": "identity"}, limit=1024)
    log("  CDN: HTTP %s, %d 字节, Content-Range=%s" %
        (status, len(chunk), headers.get("content-range")))
    # 只取有限字节，避免服务器忽略 Range 时读取整个视频。
    if status not in (200, 206) or len(chunk) < 12 or chunk[4:8] != b"ftyp":
        log("  响应不是可识别的 MP4 文件头")
        return 1
    if status == 206 and not headers.get("content-range", "").startswith("bytes 0-"):
        log("  CDN 返回的范围不是文件开头")
        return 1
    if args.download:
        path = Path(args.download)
        digest = hashlib.sha256()
        total = 0
        request = urllib.request.Request(url, headers={"Accept-Encoding": "identity"})
        with urllib.request.urlopen(request, timeout=120) as response, path.open("wb") as f:
            if response.status != 200:
                log("  完整下载失败: HTTP %s" % response.status)
                return 1
            content_length = response.headers.get("Content-Length")
            while True:
                block = response.read(1024 * 1024)
                if not block:
                    break
                f.write(block)
                digest.update(block)
                total += len(block)
        log("  完整下载: %s, %d 字节, SHA256=%s" % (path, total, digest.hexdigest()))
        for expected in (content_length, meta.get("size")):
            if expected is not None and total != int(expected):
                log("  文件大小不匹配: expected=%s actual=%d" % (expected, total))
                return 5
        if args.decode:
            probe = subprocess.run(
                ["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", str(path)],
                capture_output=True, text=True, timeout=60)
            if probe.returncode:
                log("  ffprobe 失败: " + probe.stderr[-1500:])
                return 5
            info = json.loads(probe.stdout)
            log("  ffprobe: duration=%s streams=%s" %
                ((info.get("format") or {}).get("duration"),
                 [s.get("codec_name") for s in info.get("streams", [])]))
            if not any(s.get("codec_type") == "video" for s in info.get("streams", [])):
                log("  文件中没有视频轨道")
                return 5
            decoded = subprocess.run(
                ["ffmpeg", "-v", "error", "-xerror", "-i", str(path),
                 "-map", "0:v:0", "-map", "0:a:0?", "-f", "null", "-"],
                capture_output=True, text=True, timeout=args.decode_timeout)
            log("  ffmpeg 完整音视频解码: exit=%s" % decoded.returncode)
            if decoded.returncode:
                log(decoded.stderr[:1500])
                return 4 if encrypted else 5
            log("  完整音视频解码通过")
            return 0
    if encrypted:
        log("  地址和 MP4 下载可用，但这是加密流；需支持该模型的播放器，尚未验证可播放")
        return 4
    log("  地址及 MP4 文件头检查通过" + ("，完整文件已下载" if args.download else ""))
    return 0


def check_chain(vid, sid, args, model_params=None):
    hr("检查 4: 详情 → 播放模型 → CDN → 下载/解码")
    log("  vid=%s series_id=%s" % (vid, sid))
    if sid:
        n, code, result = call("/reading/bookapi/video_tab/video_detail/v", {
            "video_series_id": sid, "source": "0",
            "screen_width_px": COMMON["resolution"].split("*")[0],
            "is_horizontal_screen": "false",
        })
        if not result or code != 0:
            log("  详情失败: %d 字节, code=%s" % (n, code))
            return 1
        data = result.get("data") or {}
        items = (data.get("dir_data") or {}).get("item_list") or []
        video = data.get("video_data") or {}
        log("  详情: %d 字节, code=%s, 标题=%s, 总集数=%s, 分集=%d, vid在目录=%s" %
            (n, code, video.get("series_title") or video.get("seriesTitle"),
             video.get("episode_cnt") or video.get("episodeCnt"), len(items),
             any(str(i.get("video_id")) == str(vid) for i in items)))
    else:
        log("  未提供 series_id，跳过可选的分集详情检查")
    n, code, result = call("/reading/bookapi/video_tab/video_model/v",
                           model_params or {"video_id": vid, "source": "0"})
    log("  播放模型: %d 字节, code=%s" % (n, code))
    if not result or code != 0:
        if result:
            log("  message=%s" % result.get("message"))
        return 1
    data, model, streams = parse_model(result)
    stream = next((s for s in streams if s.get("main_url")), None)
    if not stream:
        log("  响应没有播放地址")
        return 1
    expiry = data.get("expire_time") or model.get("url_expire")
    log("  模型时长=%s, 地址过期时间=%s, 清晰度数=%d" %
        (model.get("video_duration"), expiry, len(streams)))
    return check_media(stream, args)


def main():
    global SERVER
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", required=True, help="签名服务地址")
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--device-profile", help="当前签名 App 的设备资料 JSON")
    source.add_argument("--device-from-container", help="在该签名容器内读取设备资料")
    parser.add_argument("--save-device-profile", help="保存设备资料，供其他主机复用")
    parser.add_argument("--query", default="赘婿")
    parser.add_argument("--vid", help="指定单集 ID，跳过搜索")
    parser.add_argument("--series-id", help="指定剧集 ID，与 --vid 配合检查详情")
    parser.add_argument("--mode", choices=("all", "chain", "probe", "sign"), default="all")
    parser.add_argument("--download", help="将一集完整 MP4 保存到此路径")
    parser.add_argument("--decode", action="store_true", help="使用 ffprobe/ffmpeg 完整解码，需要 --download")
    parser.add_argument("--decode-timeout", type=int, default=600, help="完整解码超时秒数")
    args = parser.parse_args()
    if args.decode and not args.download:
        parser.error("--decode 必须与 --download 一起使用")
    if args.download and args.mode not in ("all", "chain"):
        parser.error("--download 需要 --mode all 或 chain")
    if args.decode and not all(shutil.which(tool) for tool in ("ffprobe", "ffmpeg")):
        parser.error("--decode 需要在运行主机上安装 ffprobe 和 ffmpeg")
    SERVER = args.server.rstrip("/")
    log("红果视频链路自检 | 时间=" + time.strftime("%Y-%m-%d %H:%M:%S%z"))
    if not check_health():
        return 2
    try:
        load_device(args)
    except (OSError, ValueError, subprocess.SubprocessError) as exc:
        log("设备资料不可用: %s" % exc)
        return 2
    try:
        vid, sid = args.vid, args.series_id
        if not vid:
            videos = search(args.query)
            if not videos:
                return 3
            vid, sid = str(videos[0]["vid"]), videos[0].get("series_id")
        if args.mode in ("all", "sign"):
            sign_ok = check_sign_headers(vid, sid, args.query)
            if args.mode == "sign":
                return 0 if sign_ok else 1
        model_params = None
        if args.mode in ("all", "probe"):
            model_params = probe_video_model(vid)
            if args.mode == "probe":
                return 0 if model_params else 1
        if args.mode in ("all", "chain"):
            rc = check_chain(vid, sid, args, model_params)
            hr("结论")
            log({0: ("完整下载及音视频解码通过" if args.decode else
                     "所选检查通过；完整播放验证需启用 --download 和 --decode"),
                 1: "视频接口或 CDN 检查失败", 4: "下载地址可用，加密视频尚未验证可播放",
                 5: "文件完整性或普通解码检查失败"}[rc])
            return rc
        return 0
    except SignError as exc:
        log("签名失败: %s" % exc)
        return 2
    except (OSError, ValueError, subprocess.SubprocessError, urllib.error.URLError) as exc:
        log("链路检查异常: %s" % exc)
        return 1


if __name__ == "__main__":
    sys.exit(main())
