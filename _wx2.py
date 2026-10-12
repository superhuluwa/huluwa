import subprocess, sys  
W = r'C:\Users\Jing\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'  
r = subprocess.run([W, '-NoProfile', '-Command', 'Write-Output WX_OFF_OK'], capture_output=True, text=True)  
sys.stdout.write('rc=' + str(r.returncode) + ' out=' + repr(r.stdout[:80]) + ' err=' + repr(r.stderr[:200]) + chr(10))  
