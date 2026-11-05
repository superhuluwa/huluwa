#!/usr/bin/env python3
"""给已有红果 sign_server.py 的 /info 增加当前签名 App 的设备资料。

只修改 Frida 的 info RPC；保留原服务的签名、进程恢复和 HTTP 实现。
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


def patch(source):
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
        print('已包含自动设备资料，无需重复修改:', args.source)


if __name__ == '__main__':
    main()
