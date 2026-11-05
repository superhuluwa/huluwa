#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""红果视频播放链路 服务器端自检工具

用途：部署到服务器后运行，快速定位问题所在。
零依赖，只用标准库。

用法：
    # 完整链路测试
    python3 hg_video_check.py --server http://127.0.0.1:8901 --query 赘婿

    # 只测播放地址接口（多种参数组合）
    python3 hg_video_check.py --server http://127.0.0.1:8901 --mode probe

    # 指定 vid
    python3 hg_video_check.py --server http://127.0.0.1:8901 --vid 7611102984516160574

退出码：
    0 = 全链路成功拿到 main_url
    1 = 部分成功（搜索/详情通，播放地址不通）
    2 = 签名服务不可用
    3 = 搜索失败
"""
import argparse
import gzip
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

HOST = "api5-normal-sinfonlineb.fqnovel.com"
UA = ("com.phoenix.read/73932 (Linux; U; Android 14; zh_CN; SM-S9310; "
      "Build/UQ1A.240205.06091433;tt-ok/3.12.13.20)")

DEVICE = {
    "device_id": "305040037566937",
    "iid": "305040037571033",
    "cdid": "432b64a9-68af-457b-b8db-16032c6d6427",
    "device_type": "SM-S9310",
    "device_brand": "SAMSUNG",
    "channel": "vivo_8662_64",
}
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

SERVER = None
_SID = None


def log(msg=""):
    print(msg, flush=True)


def hr(title="", ch="=", w=76):
    if title:
        log("\n" + ch * w)
        log(title)
        log(ch * w)
    else:
        log(ch * w)


# ---------------------------------------------------------------- 签名

def sign_url(url, timeout=90):
    """调签名服务，返回 6 个签名字典"""
    body = json.dumps({"url": url}).encode()
    req = urllib.request.Request(
        SERVER + "/sign", data=body,
        headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read())


def http_get(url, headers, timeout=45):
    """GET，返回 (status, raw_bytes)，自动解 gzip"""
    h = {"User-Agent": UA, "Accept-Encoding": "gzip"}
    h.update(headers or {})
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=h),
                                    timeout=timeout) as r:
            raw, st = r.read(), r.status
    except urllib.error.HTTPError as e:
        raw, st = e.read(), e.code
    except Exception as e:
        return None, ("EXC:" + str(e)[:60]).encode()
    if raw[:2] == b"\x1f\x8b":
        try:
            raw = gzip.decompress(raw)
        except Exception:
            pass
    return st, raw


def build_url(path, extra):
    p = dict(COMMON)
    p.update(DEVICE)
    p.update(extra)
    p["_rticket"] = str(int(time.time() * 1000))
    return "https://%s%s?%s" % (HOST, path, urllib.parse.urlencode(p))


def call(path, extra, sign_headers=None):
    """签名 + 请求 + 解析，返回 (len, code, dict_or_None)"""
    url = build_url(path, extra)
    sig = sign_url(url)
    if sign_headers is not None:
        sig = {k: v for k, v in sig.items() if k in sign_headers}
    st, raw = http_get(url, sig)
    if not raw:
        return 0, None, None
    try:
        j = json.loads(raw)
        return len(raw), j.get("code"), j
    except Exception:
        return len(raw), None, None


# ---------------------------------------------------------------- 检查项

def check_health():
    hr("检查 1: 签名服务可用性")
    ok = True
    for ep in ("/health", "/info"):
        try:
            with urllib.request.urlopen(SERVER + ep, timeout=60) as r:
                d = json.loads(r.read())
            log("  %-8s OK" % ep)
            if ep == "/health":
                log("           ok=%s headers=%s"
                    % (d.get("ok"), d.get("headers")))
                if not d.get("ok"):
                    ok = False
            else:
                for k in ("pkg", "model", "frida_version", "target_pkg"):
                    if k in d:
                        log("           %-13s %s" % (k, d.get(k)))
        except Exception as e:
            log("  %-8s 失败: %r" % (ep, e))
            ok = False
    return ok


def check_sign_headers():
    """测各接口需要哪些签名头"""
    hr("检查 2: 各接口需要哪些签名头")
    log("  (这能证明问题不是签名引起的)\n")

    # 先拿一个 vid
    n, code, j = call("/reading/bookapi/search/tab/v",
                      {"query": "赘婿", "offset": "0", "count": "0",
                       "bookshelf_search_plan": "4", "bookstore_tab": "-1",
                       "search_source": "1", "use_correct": "true",
                       "is_first_enter_search": "true"})
    if not j:
        log("  搜索失败，无法继续")
        return None

    vid = sid = None
    for tab in (j.get("search_tabs") or []):
        for it in (tab.get("data") or []):
            for vd in (it.get("video_data") or []):
                if vd.get("vid"):
                    vid, sid = vd["vid"], vd.get("series_id")
                    break
            if vid:
                break
        if vid:
            break
    log("  测试目标: vid=%s series_id=%s\n" % (vid, sid))

    COMBOS = [
        ("不带任何头", []),
        ("只 Medusa+Helios", ["X-Medusa", "X-Helios"]),
        ("只 Gorgon 4 个", ["X-Gorgon", "X-Khronos", "X-Argus", "X-Ladon"]),
        ("只 Khronos+Argus", ["X-Khronos", "X-Argus"]),
        ("全 6 个", None),
    ]

    TARGETS = [
        ("search/tab", "/reading/bookapi/search/tab/v",
         {"query": "赘婿", "offset": "0", "count": "0",
          "bookshelf_search_plan": "4", "bookstore_tab": "-1",
          "search_source": "1", "use_correct": "true",
          "is_first_enter_search": "true"}),
        ("video_detail", "/reading/bookapi/video_tab/video_detail/v",
         {"video_series_id": str(sid), "source": "0",
          "screen_width_px": "1440", "is_horizontal_screen": "false"}),
        ("video_model", "/reading/bookapi/video_tab/video_model/v",
         {"video_id": str(vid), "source": "0"}),
    ]

    log("  %-20s %-18s %-18s %-18s" % ("头组合", "search/tab",
                                       "video_detail", "video_model"))
    log("  " + "-" * 74)
    for cname, keys in COMBOS:
        cells = []
        for label, path, extra in TARGETS:
            n, code, jj = call(path, extra, keys)
            okk = n > 200 and code == 0
            cells.append("%s %d" % ("OK" if okk else "--", n))
        log("  %-20s %-18s %-18s %-18s"
            % (cname, cells[0], cells[1], cells[2]))
    return vid, sid


def check_chain(vid=None, query="赘婿", sid=None):
    hr("检查 3: 完整链路（搜索 → 详情 → 播放地址）")

    if not vid:
        n, code, j = call("/reading/bookapi/search/tab/v",
                          {"query": query, "offset": "0", "count": "0",
                           "bookshelf_search_plan": "4", "bookstore_tab": "-1",
                           "search_source": "1", "use_correct": "true",
                           "is_first_enter_search": "true"})
        if not j:
            log("  步骤1 搜索失败: %d 字节" % n)
            return 3
        vids = []
        for tab in (j.get("search_tabs") or []):
            for it in (tab.get("data") or []):
                for vd in (it.get("video_data") or []):
                    if vd.get("vid"):
                        vids.append(vd)
        log("  步骤1 搜索: %d 字节, code=%s, 视频 %d 个" % (n, code, len(vids)))
        if not vids:
            log("  没有视频结果（该 query 可能无短剧）")
            return 3
        v = vids[0]
        vid, sid = v["vid"], v.get("series_id")
    else:
        if not sid:
            # 传了 vid 但没 sid，尝试从搜索里找匹配
            try:
                n, code, j = call("/reading/bookapi/search/tab/v",
                                  {"query": query, "offset": "0", "count": "0",
                                   "bookshelf_search_plan": "4",
                                   "bookstore_tab": "-1", "search_source": "1",
                                   "use_correct": "true",
                                   "is_first_enter_search": "true"})
                if j:
                    for tab in (j.get("search_tabs") or []):
                        for it in (tab.get("data") or []):
                            for vd in (it.get("video_data") or []):
                                if str(vd.get("vid")) == str(vid):
                                    sid = vd.get("series_id")
                                    break
            except Exception:
                pass

    log("\n  步骤2 video_detail:")
    if sid:
        n2, c2, j2 = call("/reading/bookapi/video_tab/video_detail/v",
                          {"video_series_id": str(sid), "source": "0",
                           "screen_width_px": "1440",
                           "is_horizontal_screen": "false"})
        log("     %d 字节, code=%s" % (n2, c2))
        if j2 and j2.get("code") == 0:
            d = j2.get("data") or {}
            vd = d.get("video_data") or {}
            items = ((d.get("dir_data") or {}).get("item_list") or [])
            log("     seriesTitle=%s episodeCnt=%s 分集数=%d"
                % (vd.get("seriesTitle"), vd.get("episodeCnt"), len(items)))
    else:
        log("     跳过（无 series_id）")

    log("\n  步骤3 video_model:  ★关键")
    n3, c3, j3 = call("/reading/bookapi/video_tab/video_model/v",
                      {"video_id": str(vid), "source": "0"})
    log("     %d 字节, code=%s" % (n3, c3))

    main_url = None
    if j3 and j3.get("code") == 0:
        d3 = j3.get("data") or {}
        model = d3.get("video_model")
        if isinstance(model, str):
            try:
                model = json.loads(model)
            except Exception:
                model = {}
        model = model or {}
        # 兼容两种字段
        if d3.get("mainUrl"):
            main_url = d3["mainUrl"]
        else:
            vl = model.get("video_list") or []
            if vl:
                main_url = vl[0].get("main_url")

        if main_url:
            log("\n  ★ 成功拿到 main_url:")
            log("     %s" % main_url[:150])
            for item in (model.get("video_list") or []):
                meta = item.get("video_meta") or {}
                if meta:
                    log("     清晰度=%s %sx%s size=%.1fMB codec=%s"
                        % (meta.get("definition"), meta.get("vwidth"),
                           meta.get("vheight"),
                           (meta.get("size") or 0) / 1024 / 1024,
                           meta.get("codec_type")))
            log("     过期时间: %s"
                % (d3.get("expire_time") or model.get("url_expire")))

            # 验证可下载
            log("\n  验证 main_url 可下载（只取 1KB）:")
            st, chunk = http_get(main_url, {"Range": "bytes=0-1023"})
            log("     HTTP %s, 取到 %d 字节, 前4字节=%s"
                % (st, len(chunk), chunk[:4].hex() if chunk else "-"))
            if len(chunk) > 100:
                log("     ✓ MP4 可下载")
                return 0
            else:
                log("     ✗ 下载异常")
                return 1
        else:
            log("\n  code=0 但响应里没有 main_url")
            log("  data 字段: %s" % list(d3.keys()))
            log("  video_model 类型: %s" % type(d3.get("video_model")).__name__)
            return 1
    else:
        log("\n  ✗ video_model 未返回有效数据")
        if n3 == 0:
            log("     空响应 —— 签名被拒 或 服务端静默拒绝")
            log("     这不是参数错误（参数错误会返回带 code 的 JSON）")
        return 1


def probe_video_model(vid=None):
    """穷举 video_model 参数组合"""
    hr("检查 4: video_model 参数穷举")

    if not vid:
        n, code, j = call("/reading/bookapi/search/tab/v",
                          {"query": "赘婿", "offset": "0", "count": "0",
                           "bookshelf_search_plan": "4", "bookstore_tab": "-1",
                           "search_source": "1", "use_correct": "true",
                           "is_first_enter_search": "true"})
        if not j:
            log("  搜索失败")
            return
        for tab in (j.get("search_tabs") or []):
            for it in (tab.get("data") or []):
                for vd in (it.get("video_data") or []):
                    if vd.get("vid"):
                        vid = vd["vid"]
                        break
                if vid:
                    break
            if vid:
                break

    log("  目标 vid = %s\n" % vid)

    COMBOS = [
        ("仅 video_id + source", {"video_id": vid, "source": "0"}),
        ("+video_platform=2", {"video_id": vid, "source": "0",
                               "video_platform": "2"}),
        ("+video_platform=3", {"video_id": vid, "source": "0",
                               "video_platform": "3"}),
        ("+device_level=3", {"video_id": vid, "source": "0",
                             "device_level": "3"}),
        ("源码完整", {"video_id": vid, "source": "0",
                      "video_platform": "2", "device_level": "3",
                      "need_all_video_definition": "true",
                      "need_mp4_align": "true",
                      "use_os_player": "false"}),
        ("多清晰度", {"video_id": vid, "source": "0",
                      "need_all_video_definition": "true"}),
        ("video_id_type=0", {"video_id": vid, "source": "0",
                             "video_id_type": "0"}),
        ("user_is_login=1", {"video_id": vid, "source": "0",
                             "user_is_login": "1"}),
        ("source=1", {"video_id": vid, "source": "1"}),
        ("source=2", {"video_id": vid, "source": "2"}),
    ]

    hit = None
    for label, extra in COMBOS:
        n, code, j = call("/reading/bookapi/video_tab/video_model/v", extra)
        has_main = False
        if j and j.get("code") == 0:
            d = j.get("data") or {}
            if d.get("mainUrl"):
                has_main = True
            else:
                m = d.get("video_model")
                if isinstance(m, str):
                    try:
                        m = json.loads(m)
                    except Exception:
                        m = {}
                vl = (m or {}).get("video_list") or []
                has_main = bool(vl and vl[0].get("main_url"))
        flag = "★ HIT" if has_main else "  ---"
        log("  %-24s len=%-6d code=%-8s %s"
            % (label, n, code, flag))
        if has_main and not hit:
            hit = label

    log("")
    if hit:
        log("  ★ 成功组合: %s" % hit)
    else:
        log("  全部失败 —— 确认不是参数问题")
        log("  下一步：抓 App 真实请求对比，或检查设备指纹")


def main():
    global SERVER
    ap = argparse.ArgumentParser(description="红果视频播放链路自检")
    ap.add_argument("--server", required=True,
                    help="签名服务地址，如 http://127.0.0.1:8901")
    ap.add_argument("--query", default="赘婿", help="搜索关键词")
    ap.add_argument("--vid", default=None, help="指定 vid（跳过搜索）")
    ap.add_argument("--mode", default="all",
                    choices=["all", "chain", "probe", "sign"],
                    help="all=全部检查, chain=只测链路, probe=穷举参数, sign=只测签名头")
    args = ap.parse_args()

    SERVER = args.server.rstrip("/")

    log("红果视频播放链路自检")
    log("签名服务: %s" % SERVER)
    log("时间: %s" % time.strftime("%Y-%m-%d %H:%M:%S"))

    if not check_health():
        log("\n签名服务不可用，终止")
        return 2

    if args.mode in ("all", "sign"):
        r = check_sign_headers()
        if r and not args.vid:
            args.vid = r[0]
        if r:
            global _SID
            _SID = r[1]

    if args.mode in ("all", "probe"):
        probe_video_model(args.vid)

    if args.mode in ("all", "chain"):
        rc = check_chain(args.vid, args.query, _SID)
        hr("结论")
        if rc == 0:
            log("  ✓ 全链路成功，已拿到可下载的 MP4 地址")
        elif rc == 1:
            log("  ⚠ 部分成功：搜索/详情通，播放地址未通")
            log("    请查看「检查 4」的穷举结果，并参考文档第 4 节排查方向")
        elif rc == 3:
            log("  ✗ 搜索失败")
        return rc

    return 0


if __name__ == "__main__":
    sys.exit(main())
