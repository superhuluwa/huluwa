import urllib.request, json, sys  
u = 'https://api.github.com/repos/PowerShell/PowerShell/releases/tags/v7.4.20'  
rq = urllib.request.Request(u)  
rq.add_header('User-Agent', 'probe')  
rq.add_header('Accept', 'application/vnd.github+json')  
d = json.load(urllib.request.urlopen(rq, timeout=30))  
print('TAG', d.get('tag_name'))  
for a in d.get('assets', []):  
    n = a['name']  
    if n.endswith('.zip') or n.endswith('.msi'):  
        print(a['id'], n, a['size'], a['browser_download_url'][:80])  
