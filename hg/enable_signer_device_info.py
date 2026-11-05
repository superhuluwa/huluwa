#!/usr/bin/env python3
"""给已有红果 sign_server.py 补充自动设备资料和播放状态恢复。

增加 /info 的设备字段和 POST /recover；保留 /sign 协议和原有进程恢复实现。
应用后需重启签名进程。默认备份原文件，已应用时不重复修改。
"""
import argparse
from datetime import datetime, timezone
from pathlib import Path
import shutil

MARKER = "// HULUWA_DEVICE_PROFILE_BEGIN"
DEVICE_INFO = r'''
            // HULUWA_DEVICE_PROFILE_BEGIN
            try {
                var Params = Java.use('com.bytedance.frameworks.baselib.network.http.NetworkParams');
                var Uri = Java.use('android.net.Uri');
                var BuildInfo = Java.use('android.os.Build');
                var VersionInfo = Java.use('android.os.Build$VERSION');
                var LocaleInfo = Java.use('java.util.Locale');
                var common = Params.addCommonParams.overload('java.lang.String', 'boolean').call(
                    Params, 'https://api5-normal-sinfonlineb.fqnovel.com/reading/bookapi/video_tab/video_model/v', false);
                var uri = Uri.parse(String(common));
                var keys = ['device_id', 'iid', 'cdid', 'device_type', 'device_brand', 'channel',
                    'aid', 'app_name', 'version_code', 'version_name', 'manifest_version_code',
                    'update_version_code', 'os', 'ssmix', 'language', 'os_api', 'os_version',
                    'resolution', 'dpi', 'ac', 'host_abi', 'device_platform',
                    'dragon_device_type', 'user_is_login'];
                var params = {};
                for (var i = 0; i < keys.length; i++) {
                    var value = uri.getQueryParameter(keys[i]);
                    if (value !== null) params[keys[i]] = String(value);
                }
                if (!params.device_id || !params.iid || !params.cdid) {
                    throw new Error('App device parameters are not initialized');
                }
                var agent = Params.getUserAgent();
                var userAgent = agent === null ? '' : String(agent);
                if (!userAgent || userAgent === 'null') {
                    userAgent = 'com.phoenix.read/' + (params.version_code || '73932') +
                        ' (Linux; U; Android ' + (params.os_version || String(VersionInfo.RELEASE.value)) +
                        '; ' + String(LocaleInfo.getDefault()) + '; ' +
                        (params.device_type || String(BuildInfo.MODEL.value)) + '; Build/' +
                        String(BuildInfo.ID.value) + ';tt-ok/3.12.13.20)';
                }
                out.deviceProfile = {params: params, user_agent: userAgent};
            } catch (e) {
                out.deviceProfileError = String(e);
            }
            // HULUWA_DEVICE_PROFILE_END
'''


def patch_device_info(source):
    if MARKER in source:
        begin = source.rfind('\n', 0, source.index(MARKER)) + 1
        end_marker = '// HULUWA_DEVICE_PROFILE_END'
        end = source.find(end_marker, begin)
        if end < 0:
            raise ValueError('已有设备资料补丁不完整；没有修改文件')
        line_end = source.find('\n', end + len(end_marker))
        if line_end < 0:
            raise ValueError('设备资料补丁结尾不完整；没有修改文件')
        return source[:begin] + DEVICE_INFO.lstrip('\n') + source[line_end + 1:]
    begin = source.find('        info: function () {')
    end = source.find('\n        sign: function', begin)
    if begin < 0 or end < 0:
        raise ValueError('未找到兼容的 Frida info RPC；没有修改文件')
    section = source[begin:end]
    target = '            return out;'
    if section.count(target) != 1:
        raise ValueError('info RPC 返回结构不匹配；没有修改文件')
    return source[:begin] + section.replace(target, DEVICE_INFO + target, 1) + source[end:]


RECOVERY_STATE = '''        # HULUWA_RECOVERY_STATE_BEGIN
        self._huluwa_recovery_at = 0.0
        self._huluwa_recovery_result = None
        # HULUWA_RECOVERY_STATE_END
'''

RECOVERY_METHOD = '''    # HULUWA_RECOVERY_METHOD_BEGIN
    def recover(self):
        """后台重新初始化 App，保留账号、设备资料和数据；一分钟最多恢复一次。"""
        import subprocess
        with self.lock:
            now = time.monotonic()
            if self._huluwa_recovery_result is not None and now - self._huluwa_recovery_at < 60:
                return dict(self._huluwa_recovery_result, reused=True)
            self._huluwa_recovery_at = now
            self._huluwa_recovery_result = {"ok": False, "error": "签名 App 暂未恢复"}
            try:
                self.reset()
                adb = [os.environ.get("ADB_PATH", "adb")]
                if self.serial:
                    adb += ["-s", self.serial]
                subprocess.run(adb + ["shell", "am", "force-stop", self.pkg],
                               check=True, capture_output=True, timeout=10)
                # 使用现有 ContentProvider 初始化方式，避免 redroid 前台 GPU 崩溃。
                subprocess.run(adb + ["shell", "content", "call", "--uri",
                               "content://" + self.pkg + ".file_provider", "--method", "huluwa_sign_init"],
                               check=True, capture_output=True, timeout=15)
                warmup = min(30, max(5, int(os.environ.get("APP_WARMUP", "30"))))
                time.sleep(warmup)
                info = self.info()
                profile = (info or {}).get("deviceProfile") or {}
                params = profile.get("params") or {}
                if not all(params.get(k) for k in ("device_id", "iid", "cdid")):
                    raise RuntimeError("App 的设备信息尚未就绪")
                signatures = self.sign(build_verify_url(), timeout=15)
                if not (signatures and signatures.get("X-Medusa") and signatures.get("X-Helios")):
                    raise RuntimeError("App 签名状态尚未就绪")
                self._huluwa_recovery_result = {"ok": True, "reused": False, "recoveredAt": int(time.time())}
                print("[signer] playback context recovered in background", flush=True)
            except Exception as error:
                self.reset()
                print("[signer] playback recovery failed: %r" % error, flush=True)
                self._huluwa_recovery_result = {"ok": False, "error": "签名 App 暂未恢复，请稍后重试"}
            return dict(self._huluwa_recovery_result)
    # HULUWA_RECOVERY_METHOD_END
'''

RECOVERY_ROUTE = '''        # HULUWA_RECOVERY_ROUTE_BEGIN
        if path == "/recover":
            self._read_json()
            result = self.signer.recover()
            self._send(200 if result.get("ok") else 503, result)
            return
        # HULUWA_RECOVERY_ROUTE_END
'''

RECOVERY_INFO = '''                # HULUWA_RECOVERY_INFO_BEGIN
                info["supportsRecovery"] = True
                # HULUWA_RECOVERY_INFO_END
'''


def replace_block(source, begin_marker, end_marker, block, anchor, after=True):
    if begin_marker in source:
        begin = source.rfind('\n', 0, source.index(begin_marker)) + 1
        end = source.find(end_marker, begin)
        line_end = source.find('\n', end + len(end_marker)) if end >= 0 else -1
        if line_end < 0:
            raise ValueError('已有恢复补丁不完整；没有修改文件')
        return source[:begin] + block + source[line_end + 1:]
    if source.count(anchor) != 1:
        raise ValueError('签名服务的恢复接入位置不匹配；没有修改文件')
    return source.replace(anchor, anchor + block if after else block + anchor, 1)


def patch(source):
    source = patch_device_info(source)
    source = source.replace('self.lock = threading.Lock()', 'self.lock = threading.RLock()', 1)
    if 'self.lock = threading.RLock()' not in source:
        raise ValueError('签名服务锁结构不匹配；没有修改文件')
    source = replace_block(source, '# HULUWA_RECOVERY_STATE_BEGIN', '# HULUWA_RECOVERY_STATE_END',
                           RECOVERY_STATE, '        self.lock = threading.RLock()\n')
    source = replace_block(source, '# HULUWA_RECOVERY_METHOD_BEGIN', '# HULUWA_RECOVERY_METHOD_END',
                           RECOVERY_METHOD, '    def reset(self):\n', after=False)
    source = replace_block(source, '# HULUWA_RECOVERY_ROUTE_BEGIN', '# HULUWA_RECOVERY_ROUTE_END',
                           RECOVERY_ROUTE, '    def do_POST(self):\n        path = self.path.split("?")[0]\n')
    source = replace_block(source, '# HULUWA_RECOVERY_INFO_BEGIN', '# HULUWA_RECOVERY_INFO_END',
                           RECOVERY_INFO, '                info = self.signer.info()\n')
    source = source.replace('"POST /sign", "POST /verify"]', '"POST /sign", "POST /verify", "POST /recover"]')
    return source


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='现有 sign_server.py')
    parser.add_argument('--output', type=Path, help='另存修改后的文件，不覆盖源文件')
    args = parser.parse_args()
    original = args.source.read_text(encoding='utf-8')
    updated = patch(original)
    if args.output:
        args.output.write_text(updated, encoding='utf-8')
        print('已生成:', args.output)
    elif updated != original:
        suffix = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        backup = args.source.with_name(args.source.name + '.before-device-info-' + suffix)
        shutil.copy2(args.source, backup)
        args.source.write_text(updated, encoding='utf-8')
        print('已更新:', args.source, '备份:', backup)
    else:
        print('已包含自动设备资料和恢复能力，无需重复修改:', args.source)


if __name__ == '__main__':
    main()
