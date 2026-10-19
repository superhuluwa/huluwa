"""Single Android build worker. The shared directory is its durable job mailbox."""
import codecs
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import shutil
import signal
import subprocess
import threading
import time
from datetime import datetime, timezone
from urllib.parse import urlsplit

DATA = Path(os.environ.get('HULUWA_ANDROID_DATA', '/build-data'))
WORK = Path('/workspace')
TASKS = DATA / 'tasks'
REPO = WORK / 'repository.git'
REPOSITORY = os.environ.get('HULUWA_ANDROID_REPOSITORY', 'https://gitee.com/wangjing71/huluwa.git')
BRANCH = os.environ.get('HULUWA_ANDROID_BRANCH', 'master')
SIGNING = Path('/signing')
STOP = threading.Event()
CURRENT = None
SECRET_VALUES = []


def now():
    return datetime.now(timezone.utc).isoformat(timespec='seconds')


def atomic_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False), encoding='utf-8')
    os.replace(temporary, path)


def read_json(path, default=None):
    try:
        return json.loads(path.read_text(encoding='utf-8'))
    except (OSError, ValueError):
        return default


def redact(text):
    for secret in SECRET_VALUES:
        if secret:
            text = text.replace(secret, '***')
    return text


def log(directory, text):
    with (directory / 'build.log').open('a', encoding='utf-8') as output:
        output.write(redact(text))
    print(redact(text), end='', flush=True)


class TaskStopped(Exception):
    def __init__(self, status, message):
        self.status = status
        super().__init__(message)


class Job:
    def __init__(self, directory, request):
        self.directory = directory
        self.request = request
        self.started = now()
        # The deadline also includes time spent waiting for an unavailable worker.
        self.deadline = float(request['deadline'])
        self.meta = {}

    def check(self):
        if STOP.is_set():
            raise TaskStopped('INTERRUPTED', '构建容器停止，任务已中断，请重新提交。')
        if (self.directory / 'cancel').exists():
            raise TaskStopped('CANCELLED', '任务已取消。')
        if time.time() > self.deadline:
            raise TaskStopped('TIMED_OUT', '任务超过时间限制，已终止。')

    def stage(self, name, message):
        self.check()
        atomic_json(self.directory / 'state.json', {
            'status': 'RUNNING', 'stage': name, 'startedAt': self.started, **self.meta,
        })
        log(self.directory, f'\n[{now()}] {message}\n')

    def run(self, arguments, cwd=None, env=None, capture=False, check=True):
        self.check()
        process_env = os.environ.copy()
        process_env.update({
            'GIT_TERMINAL_PROMPT': '0', 'GIT_ASKPASS': '/opt/builder/git-askpass.py',
            'GRADLE_USER_HOME': '/workspace/gradle',
        })
        if env:
            process_env.update(env)
        process = subprocess.Popen(arguments, cwd=cwd, env=process_env,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        decoder = codecs.getincrementaldecoder('utf-8')('replace')
        output = []
        selector = selectors.DefaultSelector()
        selector.register(process.stdout, selectors.EVENT_READ)
        try:
            while selector.get_map():
                self.check()
                for key, _ in selector.select(timeout=0.3):
                    chunk = os.read(key.fileobj.fileno(), 16384)
                    if not chunk:
                        selector.unregister(key.fileobj)
                        continue
                    text = decoder.decode(chunk)
                    # Secret-bearing tools are captured privately; only their validated result is logged.
                    if capture:
                        output.append(text)
                    else:
                        log(self.directory, text)
            tail = decoder.decode(b'', final=True)
            if capture:
                output.append(tail)
            elif tail:
                log(self.directory, tail)
            code = process.wait(timeout=5)
            if check and code != 0:
                raise RuntimeError(f'{Path(arguments[0]).name} 执行失败（退出码 {code}），请查看上方日志。')
            return ''.join(output) if capture else code
        finally:
            selector.close()
            # The Gradle launcher can exit before its descendants. Stop the entire
            # group even when the launcher itself has already exited.
            with contextlib.suppress(ProcessLookupError):
                os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                pass
            with contextlib.suppress(ProcessLookupError):
                os.killpg(process.pid, signal.SIGKILL)
            process.wait()
            process.stdout.close()

    def update(self):
        self.stage('FETCHING', '从 Gitee 更新安卓源码……')
        parsed = urlsplit(REPOSITORY)
        if parsed.scheme != 'https' or parsed.username or parsed.password:
            raise RuntimeError('仓库地址必须使用不含凭据的 HTTPS URL。')
        if not REPO.exists():
            self.run(['git', 'init', '--bare', str(REPO)])
        self.run(['git', '-C', str(REPO), 'config', 'remote.origin.url', REPOSITORY])
        ref = 'refs/remotes/origin/' + BRANCH
        self.run(['git', '-C', str(REPO), 'fetch', '--prune', '--no-tags', 'origin',
                  '+refs/heads/' + BRANCH + ':' + ref])
        commit = self.run(['git', '-C', str(REPO), 'rev-parse', ref], capture=True).strip()
        if not re.fullmatch(r'[0-9a-f]{40}', commit):
            raise RuntimeError('无法确定源码提交号。')
        gradle = self.run(['git', '-C', str(REPO), 'show', commit + ':android/app/build.gradle.kts'], capture=True)
        version_code = re.search(r'versionCode\s*=\s*(\d+)', gradle)
        version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
        subject = self.run(['git', '-C', str(REPO), 'show', '-s', '--format=%s', commit], capture=True).strip()
        self.meta.update({
            'commit': commit, 'commitSubject': subject,
            'versionCode': int(version_code.group(1)) if version_code else 1,
            'versionName': version_name.group(1) if version_name else '1.0.0',
        })
        self.check()
        atomic_json(DATA / 'source.json', {**self.meta, 'branch': BRANCH, 'updatedAt': now()})
        log(self.directory, f'源码更新完成：{commit[:12]} {subject}\n')

    def prepare_gradle(self, android):
        wrapper = android / 'gradle/wrapper/gradle-wrapper.properties'
        properties = read_properties(wrapper)
        url = properties.get('distributionUrl', '')
        expected = properties.get('distributionSha256Sum', '').lower()
        parsed = urlsplit(url)
        if parsed.scheme != 'https' or not re.fullmatch(r'[0-9a-f]{64}', expected):
            raise RuntimeError('Gradle Wrapper 必须配置 HTTPS 发行地址及 SHA-256 校验值。')
        filename = Path(parsed.path).name
        if not filename.endswith('.zip'):
            raise RuntimeError('Gradle 发行文件格式无效。')
        distribution = WORK / 'distributions' / expected / filename
        distribution.parent.mkdir(parents=True, exist_ok=True)
        if not distribution.is_file() or file_sha256(distribution) != expected:
            self.stage('PREPARING', '下载并校验 Gradle 发行包，下载结果将持久缓存……')
            temporary = distribution.with_suffix('.zip.part')
            self.run(['curl', '--fail', '--location', '--retry', '3', '--connect-timeout', '20',
                      '--output', str(temporary), url])
            if file_sha256(temporary) != expected:
                temporary.unlink(missing_ok=True)
                raise RuntimeError('Gradle 发行包 SHA-256 校验失败。')
            os.replace(temporary, distribution)
            log(self.directory, 'Gradle 发行包下载完成，SHA-256 校验通过。\n')
        else:
            log(self.directory, '复用已校验的 Gradle 发行包缓存。\n')
        # Keep the repository-pinned checksum; only the disposable worktree uses
        # the verified local download, avoiding the slow JVM network downloader.
        text = wrapper.read_text(encoding='utf-8')
        local_url = distribution.as_uri().replace(':', '\\:', 1)
        text = re.sub(r'(?m)^distributionUrl=.*$', lambda _: 'distributionUrl=' + local_url, text)
        wrapper.write_text(text, encoding='utf-8')

    def build(self):
        commit = self.request['commit']
        if not re.fullmatch(r'[0-9a-f]{40}', commit):
            raise RuntimeError('源码提交号无效，请先更新代码。')
        self.meta.update({k: self.request[k] for k in ['commit', 'commitSubject', 'versionCode', 'versionName']})
        self.stage('PREPARING', f'准备源码 {commit[:12]}，版本代码 {self.request["versionCode"]}……')
        if shutil.disk_usage(WORK).free < 3 * 1024 ** 3:
            raise RuntimeError('构建目录可用空间不足 3GB。')
        signing = read_properties(SIGNING / 'keystore.properties')
        for key in ['storeFile', 'storePassword', 'keyAlias', 'keyPassword']:
            if not signing.get(key):
                raise RuntimeError(f'签名配置缺少 {key}，请恢复现有 Release 签名。')
        keystore = (SIGNING / signing['storeFile']).resolve()
        if not keystore.is_relative_to(SIGNING.resolve()) or not keystore.is_file():
            raise RuntimeError('签名文件不存在，storeFile 必须指向签名卷内的文件。')
        SECRET_VALUES.extend([signing['storePassword'], signing['keyPassword']])
        token = Path('/credentials/git-token')
        if token.is_file():
            SECRET_VALUES.append(token.read_text().strip())
        job_dir = WORK / 'jobs' / str(self.request['id'])
        source = job_dir / 'source'
        job_dir.mkdir(parents=True, exist_ok=True)
        self.run(['git', '-C', str(REPO), 'worktree', 'add', '--detach', str(source), commit])
        try:
            android = source / 'android'
            # The shared release signing is authoritative, including for old Git revisions.
            # Remove machine-specific SDK paths; use ANDROID_HOME from this image.
            (android / 'local.properties').unlink(missing_ok=True)
            self.prepare_gradle(android)
            env = {
                'HULUWA_VERSION_CODE': str(self.request['versionCode']),
                'HULUWA_VERSION_NAME': self.request['versionName'],
                'HULUWA_KEYSTORE_FILE': str(keystore),
                'HULUWA_STORE_PASSWORD': signing['storePassword'],
                'HULUWA_KEY_ALIAS': signing['keyAlias'],
                'HULUWA_KEY_PASSWORD': signing['keyPassword'],
                'LC_ALL': 'C.UTF-8',
            }
            self.stage('COMPILING', '执行 Gradle Release 编译，首次运行需要下载依赖……')
            self.run(['bash', './gradlew', ':app:assembleRelease', '--no-daemon', '--console=plain',
                      '--stacktrace', '--build-cache', '--max-workers=2',
                      '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8',
                      '-Pkotlin.compiler.execution.strategy=in-process',
                      '--init-script', '/opt/builder/android.init.gradle'], cwd=android, env=env)
            self.stage('VERIFYING', '读取 APK 信息并校验 Release 签名……')
            candidates = list((android / 'app/build/outputs/apk/release').glob('*.apk'))
            if len(candidates) != 1:
                raise RuntimeError('预期生成一个 Release APK，实际产物数量不符。')
            apk = candidates[0]
            signature_output = self.run(['apksigner', 'verify', '--print-certs', str(apk)], capture=True)
            digest = re.search(r'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)', signature_output)
            if not digest:
                raise RuntimeError('APK 未通过签名校验。')
            key_output = self.run(['keytool', '-exportcert', '-rfc', '-keystore', str(keystore),
                                   '-alias', signing['keyAlias'], '-storepass:env', 'HULUWA_STORE_PASSWORD'],
                                  env=env, capture=True)
            import base64
            certificate = re.search(r'-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----', key_output, re.S)
            if not certificate:
                raise RuntimeError('无法读取 Release 签名证书。')
            expected = hashlib.sha256(base64.b64decode(certificate.group(1))).hexdigest()
            if digest.group(1).lower() != expected:
                raise RuntimeError('APK 签名与配置的 Release 证书不一致。')
            badging = self.run(['aapt', 'dump', 'badging', str(apk)], capture=True)
            package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
            if not package or package.group(1) != 'com.huluwa.android' or int(package.group(2)) != self.request['versionCode']:
                raise RuntimeError('APK 包名或版本代码不符合本次构建参数。')
            self.stage('ARCHIVING', '归档已签名 APK……')
            version = re.sub(r'[^A-Za-z0-9._-]', '_', package.group(3))[:60]
            filename = f'huluwa-{version}-{self.request["versionCode"]}-{commit[:8]}.apk'
            temporary = self.directory / (filename + '.tmp')
            shutil.copyfile(apk, temporary)
            self.check()
            os.replace(temporary, self.directory / filename)
            self.meta.update({
                'versionName': package.group(3), 'artifactName': filename,
                'packageName': package.group(1),
                'minSdk': int(re.search(r"(?m)^sdkVersion:'(\d+)'", badging).group(1)),
                'apkSize': apk.stat().st_size, 'sha256': file_sha256(apk),
                'signatureSha256': expected,
            })
            # Retain the R8 mapping for diagnosing crashes in this minified release.
            mapping = android / 'app/build/outputs/mapping/release/mapping.txt'
            if mapping.is_file():
                shutil.copyfile(mapping, self.directory / 'mapping.txt')
            log(self.directory, f'APK 已归档：{filename}\nSHA-256：{self.meta["sha256"]}\n')
        finally:
            # Only disposable worktrees are removed; Git and Gradle caches persist.
            subprocess.run(['git', '-C', str(REPO), 'worktree', 'remove', '--force', str(source)],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
            shutil.rmtree(job_dir, ignore_errors=True)

    def execute(self):
        credential = read_json(Path('/credentials/git-credentials.json'), {})
        SECRET_VALUES[:] = [credential.get('password', '')]
        token_file = Path('/credentials/git-token')
        if token_file.is_file():
            SECRET_VALUES.append(token_file.read_text().strip())
        result = {'status': 'SUCCESS', 'stage': 'DONE'}
        try:
            self.check()
            if self.request['type'] == 'UPDATE':
                self.update()
            elif self.request['type'] == 'BUILD':
                self.build()
            else:
                raise RuntimeError('不支持的任务类型。')
        except TaskStopped as error:
            result.update(status=error.status, stage='DONE', error=str(error))
        except Exception as error:
            result.update(status='FAILED', stage='DONE', error=redact(str(error)))
        result.update(startedAt=self.started, completedAt=now(), **self.meta)
        if result['status'] != 'SUCCESS':
            log(self.directory, f'\n[{now()}] {result.get("error", "任务失败")}\n')
            # Incomplete or cancelled files never become downloadable.
            for path in self.directory.glob('*.apk*'):
                path.unlink(missing_ok=True)
            result.pop('artifactName', None)
        atomic_json(self.directory / 'result.json', result)
        SECRET_VALUES.clear()


def file_sha256(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def read_properties(path):
    if not path.is_file():
        raise RuntimeError(f'未找到配置文件：{path.name}')
    properties = {}
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        line = line.strip()
        if not line or line.startswith(('#', '!')):
            continue
        match = re.match(r'([^:=\s]+)\s*[:=]\s*(.*)', line)
        if match:
            value = re.sub(r'\\u([0-9a-fA-F]{4})', lambda m: chr(int(m.group(1), 16)), match.group(2))
            value = re.sub(r'\\(.)', lambda m: {'t': '\t', 'n': '\n', 'r': '\r', 'f': '\f'}.get(m.group(1), m.group(1)), value)
            properties[match.group(1)] = value
    return properties


def heartbeat():
    while not STOP.is_set():
        atomic_json(DATA / 'worker.json', {
            'heartbeat': time.time(), 'currentTask': CURRENT,
            'repository': REPOSITORY, 'branch': BRANCH,
            'signingReady': (SIGNING / 'keystore.properties').is_file(),
        })
        STOP.wait(2)


def main():
    global CURRENT
    TASKS.mkdir(parents=True, exist_ok=True)
    WORK.mkdir(parents=True, exist_ok=True)
    lock = (WORK / 'worker.lock').open('w')
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, lambda *_: STOP.set())
    # A prior process may have died mid-build. Never silently rerun a started job.
    for directory in TASKS.iterdir():
        if directory.is_dir() and ((directory / 'state.json').is_file() or (directory / 'running.json').is_file()) and not (directory / 'result.json').exists():
            state = read_json(directory / 'state.json', {})
            atomic_json(directory / 'result.json', {
                **state, 'status': 'INTERRUPTED', 'stage': 'DONE', 'completedAt': now(),
                'error': '构建容器重启，原任务已中断，请重新提交。',
            })
    # Remove only orphaned disposable sources after an interrupted job.
    shutil.rmtree(WORK / 'jobs', ignore_errors=True)
    if REPO.exists():
        subprocess.run(['git', '-C', str(REPO), 'worktree', 'prune'], check=False)
    thread = threading.Thread(target=heartbeat, daemon=True)
    thread.start()
    print('葫芦娃 Android 构建服务已启动，等待网页任务。', flush=True)
    while not STOP.is_set():
        directories = sorted((p for p in TASKS.iterdir() if p.is_dir() and p.name.isdigit()), key=lambda p: int(p.name))
        for directory in directories:
            request = read_json(directory / 'request.json')
            if request and not (directory / 'result.json').exists():
                # Claim once before running. A history cleanup must never turn an
                # already-completed request back into an executable queue entry.
                try:
                    os.replace(directory / 'request.json', directory / 'running.json')
                except FileNotFoundError:
                    continue
                CURRENT = request['id']
                Job(directory, request).execute()
                CURRENT = None
                if STOP.is_set():
                    break
        STOP.wait(0.5)
    thread.join(timeout=3)
    (DATA / 'worker.json').unlink(missing_ok=True)


if __name__ == '__main__':
    main()
