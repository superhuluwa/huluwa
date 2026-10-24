"""Outbound-only bridge between a native worker and the Huluwa build mailbox."""
import argparse
import base64
import http.client
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
import threading
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

import worker


class Bridge:
    def __init__(self, config):
        self.config = config
        self.base = config['serverUrl'].rstrip('/') + config.get('apiPath', '/api/android').rstrip('/') + '/agents/' + config['nodeId']
        self.data = Path(config['dataDirectory'])
        self.pending = []
        self.offsets = {}
        self.uploaded = set()

    def poll(self, payload):
        request = Request(self.base + '/poll', json.dumps(payload).encode(),
                          {'Content-Type': 'application/json', 'X-Build-Token': self.config['token']})
        with urlopen(request, timeout=30) as response:
            result = json.load(response)
        if result['code'] != 200:
            raise RuntimeError(result.get('message', '构建节点同步失败'))
        return result['data']['tasks']

    def upload(self, task_id, path):
        # Stream large APKs without copying them into memory.
        url = urlsplit(self.base + f'/tasks/{task_id}/files/{path.name}')
        connection_type = http.client.HTTPSConnection if url.scheme == 'https' else http.client.HTTPConnection
        connection = connection_type(url.hostname, url.port, timeout=120)
        try:
            with path.open('rb') as source:
                connection.request('PUT', url.path, body=source, headers={
                    'X-Build-Token': self.config['token'], 'Content-Type': 'application/octet-stream',
                    'Content-Length': str(path.stat().st_size),
                })
                response = connection.getresponse()
                body = response.read()
                if response.status != 200:
                    raise RuntimeError(f'构建产物回传失败：HTTP {response.status}')
                if json.loads(body)['code'] != 200:
                    raise RuntimeError('构建产物回传失败')
        finally:
            connection.close()

    def sync(self):
        payload = worker.read_json(self.data / 'worker.json', {})
        if time.time() - payload.get('heartbeat', 0) >= 20:
            raise RuntimeError('构建进程心跳尚未就绪')
        payload['source'] = worker.read_json(self.data / 'source.json', {})
        for task in self.pending:
            task_id = task['id']
            directory = self.data / 'tasks' / str(task_id)
            payload['taskId'] = task_id
            state = worker.read_json(directory / 'state.json')
            if state:
                payload['state'] = state
            log = directory / 'build.log'
            offset = self.offsets.get(task_id, task.get('logOffset', 0))
            payload['logOffset'] = offset
            remaining = False
            if log.is_file():
                with log.open('rb') as source:
                    source.seek(offset)
                    payload['log'] = base64.b64encode(source.read(65536)).decode()
                    remaining = source.tell() < log.stat().st_size
            result = worker.read_json(directory / 'result.json')
            if result and not remaining:
                for name in (result.get('artifactName'), 'mapping.txt'):
                    if name and (directory / name).is_file() and (task_id, name) not in self.uploaded:
                        self.upload(task_id, directory / name)
                        self.uploaded.add((task_id, name))
                payload['result'] = result
            break  # The central mailbox serializes builds and version allocation.
        tasks = self.poll(payload)
        self.pending = tasks
        for task in tasks:
            task_id = task['id']
            self.offsets[task_id] = task['logOffset']
            directory = self.data / 'tasks' / str(task_id)
            directory.mkdir(parents=True, exist_ok=True)
            if task.get('cancel'):
                (directory / 'cancel').touch()
            if not any((directory / name).exists() for name in ('request.json', 'running.json', 'result.json')):
                worker.atomic_json(directory / 'request.json', task)
        # Keep recent diagnostic output locally; central retention remains authoritative.
        completed = sorted((p for p in (self.data / 'tasks').glob('*') if (p / 'result.json').exists()
                            and int(p.name) not in {t['id'] for t in tasks}), key=lambda p: int(p.name), reverse=True)
        import shutil
        for directory in completed[10:]:
            shutil.rmtree(directory)


def run(config_path):
    config = json.loads(Path(config_path).read_text(encoding='utf-8-sig'))
    parsed = urlsplit(config['serverUrl'])
    if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError('主服务地址无效')
    env = os.environ.copy()
    env.update({
        'HULUWA_ANDROID_DATA': config['dataDirectory'], 'HULUWA_ANDROID_WORK': config['workDirectory'],
        'HULUWA_ANDROID_SIGNING': config['signingDirectory'], 'HULUWA_ANDROID_NODE_ID': config['nodeId'],
        'HULUWA_ANDROID_REPOSITORY': config.get('repository', worker.REPOSITORY),
        'HULUWA_ANDROID_BRANCH': config.get('branch', 'master'), 'PYTHONUNBUFFERED': '1', 'PYTHONUTF8': '1',
        'JAVA_HOME': config['javaHome'], 'ANDROID_HOME': config['androidHome'],
        'ANDROID_SDK_ROOT': config['androidHome'],
        'HULUWA_ANDROID_PACKAGE': config.get('packageName', 'com.huluwa.android'),
        'HULUWA_ANDROID_ARTIFACT_PREFIX': config.get('artifactPrefix', 'huluwa'),
    })
    env['PATH'] = str(Path(config['javaHome']) / 'bin') + os.pathsep + env['PATH']
    if config.get('gradleHome'):
        env['GRADLE_USER_HOME'] = config['gradleHome']
    Path(config['dataDirectory'], 'tasks').mkdir(parents=True, exist_ok=True)
    process = subprocess.Popen([sys.executable, str(Path(__file__).with_name('worker.py'))], env=env,
                               creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name == 'nt' else 0)
    bridge = Bridge(config)
    stopping = threading.Event()
    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, lambda *_: stopping.set())
    print('葫芦娃构建节点已启动，正在连接主服务。', flush=True)
    last_error = None
    try:
        while not stopping.is_set() and process.poll() is None:
            try:
                bridge.sync()
                if last_error:
                    print('已恢复与主服务的连接。', flush=True)
                last_error = None
            except Exception as error:
                # Never include credentials or response bodies in diagnostic output.
                if last_error != type(error).__name__:
                    print(f'同步暂时失败（{type(error).__name__}），每 2 秒自动重试。', flush=True)
                last_error = type(error).__name__
            stopping.wait(2)
        if not stopping.is_set():
            raise RuntimeError(f'构建进程已退出：{process.returncode}')
    finally:
        if process.poll() is None:
            process.send_signal(signal.CTRL_BREAK_EVENT if os.name == 'nt' else signal.SIGTERM)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', required=True)
    args = parser.parse_args()
    run(args.config)
