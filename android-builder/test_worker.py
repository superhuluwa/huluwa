import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import unittest

import worker


class WorkerTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary.name)
        worker.STOP.clear()
        self.job = worker.Job(self.directory, {'id': 1, 'deadline': time.time() + 20})

    def tearDown(self):
        self.temporary.cleanup()

    def test_output_and_nonzero_exit(self):
        self.assertEqual('hello', self.job.run([sys.executable, '-c', 'print("hello")'], capture=True).strip())
        with self.assertRaises(RuntimeError):
            self.job.run([sys.executable, '-c', 'raise SystemExit(7)'])

    def test_cancel_terminates_child_processes(self):
        pid_file = self.directory / 'child.pid'
        program = ('import subprocess,sys,time,pathlib; '
                   'p=subprocess.Popen([sys.executable,"-c","import time; time.sleep(120)"]); '
                   f'pathlib.Path({str(pid_file)!r}).write_text(str(p.pid)); time.sleep(120)')
        def cancel():
            deadline = time.time() + 10
            while not pid_file.exists() and time.time() < deadline:
                time.sleep(.05)
            (self.directory / 'cancel').touch()
        thread = threading.Thread(target=cancel)
        thread.start()
        with self.assertRaises(worker.TaskStopped) as stopped:
            self.job.run([sys.executable, '-c', program])
        self.assertEqual('CANCELLED', stopped.exception.status)
        thread.join()
        pid = int(pid_file.read_text())
        if os.name == 'nt':
            import ctypes
            handle = ctypes.windll.kernel32.OpenProcess(0x1000, False, pid)
            if handle:
                code = ctypes.c_ulong()
                ctypes.windll.kernel32.GetExitCodeProcess(handle, ctypes.byref(code))
                ctypes.windll.kernel32.CloseHandle(handle)
                self.assertNotEqual(259, code.value)
        else:
            with self.assertRaises(ProcessLookupError):
                os.kill(pid, 0)

    def test_deadline_and_stop(self):
        self.job.deadline = time.time() - 1
        with self.assertRaises(worker.TaskStopped) as stopped:
            self.job.run([sys.executable, '-c', 'print(1)'])
        self.assertEqual('TIMED_OUT', stopped.exception.status)
        self.job.deadline = time.time() + 20
        worker.STOP.set()
        with self.assertRaises(worker.TaskStopped) as stopped:
            self.job.check()
        self.assertEqual('INTERRUPTED', stopped.exception.status)
        worker.STOP.clear()

    def test_running_command_times_out(self):
        self.job.deadline = time.time() + .4
        started = time.monotonic()
        with self.assertRaises(worker.TaskStopped) as stopped:
            self.job.run([sys.executable, '-c', 'import time; time.sleep(120)'])
        self.assertEqual('TIMED_OUT', stopped.exception.status)
        self.assertLess(time.monotonic() - started, 5)

    def test_local_worker_does_not_claim_or_interrupt_remote_tasks(self):
        data = self.directory / 'data'
        remote = data / 'tasks' / '77'
        remote.mkdir(parents=True)
        worker.atomic_json(remote / 'request.json', {'id': 77, 'serverId': 'remote', 'type': 'UPDATE', 'deadline': time.time() + 120})
        worker.atomic_json(remote / 'state.json', {'status': 'RUNNING'})
        env = os.environ.copy()
        env.update(HULUWA_ANDROID_DATA=str(data), HULUWA_ANDROID_WORK=str(self.directory / 'work'))
        process = subprocess.Popen([sys.executable, str(Path(worker.__file__))], env=env, stdout=subprocess.DEVNULL,
                                   creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name == 'nt' else 0)
        try:
            until = time.time() + 10
            while not (data / 'worker.json').exists() and time.time() < until:
                time.sleep(.05)
            self.assertTrue((data / 'worker.json').exists())
            time.sleep(.7)
            self.assertTrue((remote / 'request.json').exists())
            self.assertFalse((remote / 'result.json').exists())
        finally:
            import signal
            process.send_signal(signal.CTRL_BREAK_EVENT if os.name == 'nt' else signal.SIGTERM)
            process.wait(timeout=10)

    def test_windows_batch_path_with_spaces(self):
        if os.name != 'nt':
            self.skipTest('Windows batch execution')
        batch = self.directory / 'path with spaces.cmd'
        batch.write_text('@echo off\r\necho batch-ok\r\n', encoding='ascii')
        self.assertIn('batch-ok', self.job.run([str(batch)], capture=True))

    def test_streaming_hash(self):
        path = self.directory / 'hash.bin'
        path.write_bytes(b'abc')
        self.assertEqual('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', worker.file_sha256(path))


if __name__ == '__main__':
    unittest.main()
