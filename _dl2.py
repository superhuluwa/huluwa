import urllib.request, os, sys, time  
u = 'https://api.github.com/repos/PowerShell/PowerShell/releases/assets/551206814'  
out = r'C:\Users\Jing\Desktop\huluwa\ps7420.zip'  
rq = urllib.request.Request(u)  
rq.add_header('User-Agent', 'probe')  
rq.add_header('Accept', 'application/octet-stream')  
r = urllib.request.urlopen(rq, timeout=120)  
total = int(r.headers.get('Content-Length') or 0)  
print('TOTAL', total)  
got = 0  
t0 = time.time()  
with open(out, 'wb') as f:  
    while True:  
        c = r.read(1024 * 512)  
        if not c: break  
        f.write(c); got = got + len(c)  
        if got % (10 * 1024 * 1024) == 0:  
            sys.stdout.write('  ' + str(got // 1048576) + ' MiB ' + str(round(time.time()-t0,1)) + 's' + chr(10))  
print('DONE', got, os.path.getsize(out))  
