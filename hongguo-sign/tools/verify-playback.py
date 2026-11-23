#!/usr/bin/env python3
"""通过指定 Frida 服务真实取址，并读取少量 CDN 数据，验证完整播放取址链路。"""
import argparse
import base64
import gzip
import hashlib
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


SIGNATURE_KEYS = {'x-gorgon', 'x-khronos', 'x-argus', 'x-ladon', 'x-medusa', 'x-helios'}
HOSTS = ('api5-normal-sinfonlineb.fqnovel.com', 'reading.snssdk.com')
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def fetch_json(url, payload=None, headers=None):
    request_headers = {'Accept-Encoding': 'gzip', 'Accept': 'application/json'}
    request_headers.update(headers or {})
    body = None
    if payload is not None:
        body = json.dumps(payload).encode()
        request_headers['Content-Type'] = 'application/json; charset=utf-8'
    request = urllib.request.Request(url, data=body, headers=request_headers)
    with OPENER.open(request, timeout=45) as response:
        raw = response.read()
    if raw.startswith(b'\x1f\x8b'):
        raw = gzip.decompress(raw)
    if not raw:
        raise RuntimeError('接口返回空响应')
    return json.loads(raw)


def parse_model(value):
    if isinstance(value, str):
        return json.loads(value)
    return value


def playback_urls(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if isinstance(child, str) and key in ('main_url', 'backup_url', 'backup_url_1', 'backup_url_2', 'url'):
                candidate = child
                if not candidate.startswith(('http://', 'https://')):
                    try:
                        candidate = base64.b64decode(candidate + '=' * (-len(candidate) % 4)).decode()
                    except (ValueError, UnicodeError):
                        continue
                if candidate.startswith(('http://', 'https://')):
                    yield candidate
            elif isinstance(child, (dict, list)):
                yield from playback_urls(child)
    elif isinstance(value, list):
        for child in value:
            yield from playback_urls(child)


def verify(endpoint, episode):
    profile = fetch_json(endpoint + '/info')['deviceProfile']
    identity = profile['params']
    if not all(identity.get(key) for key in ('device_id', 'iid', 'cdid')) or not profile.get('user_agent'):
        raise RuntimeError('设备档案尚未就绪')
    failures = []
    for host in HOSTS:
        try:
            ticket = int(time.time() * 1000)
            params = dict(identity)
            params.update({
                'video_id': episode, 'source': '0', 'need_all_video_definition': 'true',
                'device_level': '1', 'ts': str(ticket // 1000), '_rticket': str(ticket),
            })
            url = 'https://' + host + '/reading/bookapi/video_tab/video_model/v?' + urllib.parse.urlencode(params)
            signatures = fetch_json(endpoint + '/sign', {'url': url})
            if not SIGNATURE_KEYS.issubset({key.lower() for key, value in signatures.items() if value}):
                raise RuntimeError('服务未返回完整六头签名')
            headers = {
                'User-Agent': profile['user_agent'], 'x-xs-from-web': '0',
                'x-ss-req-ticket': str(ticket), 'x-tt-request-tag': 't=0;n=0',
                'sdk-version': '2', 'passport-sdk-version': '50561',
                'x-vc-bdturing-sdk-version': '3.7.2.cn',
            }
            headers.update(signatures)
            root = fetch_json(url, headers=headers)
            if root.get('code', root.get('Code', -1)) != 0:
                raise RuntimeError('播放接口错误码: ' + str(root.get('code', root.get('Code'))))
            model = parse_model(root.get('data', {}).get('video_model'))
            urls = list(dict.fromkeys(playback_urls(model)))
            if not urls:
                raise RuntimeError('播放模型没有可用媒体地址')
            cdn_failures = []
            for media_url in urls:
                try:
                    request = urllib.request.Request(media_url, headers={
                        'User-Agent': profile['user_agent'], 'Range': 'bytes=0-1023',
                    })
                    with OPENER.open(request, timeout=30) as response:
                        chunk = response.read(1024)
                        status = response.status
                    if status not in (200, 206) or not chunk:
                        raise RuntimeError('CDN 未返回媒体数据')
                    return {
                        'ok': True, 'endpoint': endpoint, 'episode': episode, 'api_host': host,
                        'playback_urls': len(urls), 'cdn_host': urllib.parse.urlsplit(media_url).hostname,
                        'cdn_status': status, 'cdn_bytes': len(chunk),
                        'device_fingerprint': hashlib.sha256(str(identity['device_id']).encode()).hexdigest()[:12],
                    }
                except (OSError, ValueError, RuntimeError) as error:
                    cdn_failures.append(type(error).__name__ + ': ' + str(error)[:120])
            raise RuntimeError('CDN 读取失败: ' + '; '.join(cdn_failures[:2]))
        except (OSError, ValueError, KeyError, TypeError, RuntimeError) as error:
            failures.append(host + ': ' + str(error)[:250])
    raise RuntimeError('; '.join(failures))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--endpoint', required=True)
    parser.add_argument('--episode', required=True)
    parser.add_argument('--count', type=int, default=1)
    args = parser.parse_args()
    if args.count < 1:
        parser.error('--count 必须大于零')
    try:
        for index in range(args.count):
            result = verify(args.endpoint.rstrip('/'), args.episode)
            result['round'] = index + 1
            print(json.dumps(result, ensure_ascii=False), flush=True)
    except (OSError, ValueError, KeyError, TypeError, RuntimeError) as error:
        print(json.dumps({'ok': False, 'error': str(error)}, ensure_ascii=False), flush=True)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
