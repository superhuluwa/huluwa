"""Kill the complete command tree, including descendants of an exited launcher."""
import os
import signal
import subprocess
import contextlib


class ProcessTree:
    def __init__(self, process):
        self.process = process
        self.handle = None
        if os.name == 'nt':
            import ctypes
            from ctypes import wintypes
            self.api = ctypes.WinDLL('kernel32', use_last_error=True)
            self.api.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
            self.api.CreateJobObjectW.restype = wintypes.HANDLE
            self.api.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
            self.api.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
            self.api.TerminateJobObject.argtypes = [wintypes.HANDLE, wintypes.UINT]
            self.api.CloseHandle.argtypes = [wintypes.HANDLE]
            self.handle = self.api.CreateJobObjectW(None, None)
            class BasicLimits(ctypes.Structure):
                _fields_ = [('processTime', ctypes.c_longlong), ('jobTime', ctypes.c_longlong),
                            ('flags', wintypes.DWORD), ('minimum', ctypes.c_size_t), ('maximum', ctypes.c_size_t),
                            ('processLimit', wintypes.DWORD), ('affinity', ctypes.c_size_t),
                            ('priority', wintypes.DWORD), ('scheduling', wintypes.DWORD)]
            class ExtendedLimits(ctypes.Structure):
                _fields_ = [('basic', BasicLimits), ('io', ctypes.c_ulonglong * 6),
                            ('processMemory', ctypes.c_size_t), ('jobMemory', ctypes.c_size_t),
                            ('peakProcessMemory', ctypes.c_size_t), ('peakJobMemory', ctypes.c_size_t)]
            limits = ExtendedLimits()
            limits.basic.flags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            if (not self.handle or not self.api.SetInformationJobObject(self.handle, 9, ctypes.byref(limits), ctypes.sizeof(limits))
                    or not self.api.AssignProcessToJobObject(self.handle, int(process._handle))):
                if self.handle:
                    self.api.CloseHandle(self.handle)
                process.kill()
                process.wait()
                raise OSError(ctypes.get_last_error(), '无法创建 Windows 构建进程作业组')

    def close(self):
        if os.name == 'nt':
            if self.handle:
                self.api.TerminateJobObject(self.handle, 1)
                self.api.CloseHandle(self.handle)
                self.handle = None
        else:
            with contextlib.suppress(ProcessLookupError):
                os.killpg(self.process.pid, signal.SIGKILL)
