"""Install missing pinned SDK components using Python's HTTPS stack on Windows."""
import argparse
import hashlib
import ipaddress
from pathlib import Path
import shutil
import socket
import tempfile
from urllib.request import urlopen
import xml.etree.ElementTree as ET
import zipfile


BASE = 'https://dl.google.com/android/repository/'


def provision(sdk):
    sdk = Path(sdk).resolve()
    packages = [('platforms;android-36', sdk / 'platforms/android-36', 'android.jar'),
                ('build-tools;35.0.0', sdk / 'build-tools/35.0.0', 'aapt.exe')]
    missing = [(name, target, marker) for name, target, marker in packages if not (target / marker).is_file()]
    if not missing:
        print('SDK 36 和 Build Tools 35.0.0 已就绪。', flush=True)
        return
    if not (sdk / 'licenses/android-sdk-license').is_file():
        raise RuntimeError('请先通过 Android Studio SDK Manager 安装 SDK 并接受 Android SDK 许可证。')
    with urlopen(BASE + 'repository2-3.xml', timeout=60) as source:
        manifest = ET.fromstring(source.read())
    for name, target, marker in missing:
        package = next((p for p in manifest.findall('remotePackage') if p.get('path') == name), None)
        if package is None:
            raise RuntimeError(f'官方 SDK 清单中没有找到 {name}')
        archive = next(a for a in package.findall('./archives/archive') if a.findtext('host-os') in (None, 'windows'))
        filename = archive.findtext('./complete/url')
        checksum = archive.findtext('./complete/checksum')
        if not filename or '/' in filename or not checksum:
            raise RuntimeError('官方 SDK 归档信息无效')
        target.parent.mkdir(parents=True, exist_ok=True)
        print(f'安装 {name}……', flush=True)
        with tempfile.TemporaryDirectory(prefix='huluwa-sdk-', dir=target.parent) as temporary:
            stage = Path(temporary)
            download = stage / 'download.zip'
            for attempt in range(3):
                try:
                    with urlopen(BASE + filename, timeout=60) as response, download.open('wb') as output:
                        shutil.copyfileobj(response, output, 1024 * 1024)
                    break
                except OSError:
                    if attempt == 2:
                        raise
            digest = hashlib.sha1()
            with download.open('rb') as source:
                for chunk in iter(lambda: source.read(1024 * 1024), b''):
                    digest.update(chunk)
            if digest.hexdigest().lower() != checksum.lower():
                raise RuntimeError('官方 SDK 归档校验失败')
            extracted = stage / 'extracted'
            with zipfile.ZipFile(download) as zip_file:
                for entry in zip_file.infolist():
                    if not (extracted / entry.filename).resolve().is_relative_to(extracted.resolve()):
                        raise RuntimeError('SDK 归档包含越界路径')
                zip_file.extractall(extracted)
            children = list(extracted.iterdir())
            if len(children) != 1 or not (children[0] / marker).is_file():
                raise RuntimeError('SDK 归档结构无效')
            if target.exists():
                raise RuntimeError(f'已存在不完整的 SDK 目录，请使用 SDK Manager 修复：{target}')
            shutil.move(str(children[0]), target)
        print(f'{name} 安装完成。', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--sdk', required=True)
    parser.add_argument('--download-address', help='Optional reachable IP for dl.google.com; TLS hostname verification stays enabled')
    args = parser.parse_args()
    if args.download_address:
        address = str(ipaddress.ip_address(args.download_address))
        original_resolver = socket.getaddrinfo
        def resolve(host, *arguments, **kwargs):
            return original_resolver(address if host == 'dl.google.com' else host, *arguments, **kwargs)
        socket.getaddrinfo = resolve
    provision(args.sdk)
