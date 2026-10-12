import urllib.request, sys, os  
u = 'https://api.github.com/repos/PowerShell/PowerShell/releases/assets/551206814'  
rq = urllib.request.Request(u)  
rq.add_header('User-Agent', 'probe')  
rq.add_header('Accept', 'application/octet-stream')  
r = urllib.request.urlopen(rq, timeout=60)  
print('STATUS', r.status, 'LEN', r.headers.get('Content-Length'), 'URL', r.geturl()[:120])  
b = r.read(200000)  
print('GOT_BYTES', len(b), b[:4])  
