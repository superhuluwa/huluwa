import urllib.request, json, sys  
def get(n):  
    u = 'https://api.github.com/repos/openai/codex/issues/' + str(n)  
    rq = urllib.request.Request(u)  
    rq.add_header('User-Agent', 'probe')  
    return json.load(urllib.request.urlopen(rq, timeout=40))  
for n in (43028, 39843, 39711):  
    d = get(n)  
    sys.stdout.buffer.write((chr(10) + '===== ISSUE ' + str(n) + ' : ' + d['title'] + ' =====' + chr(10)).encode('ascii', 'replace'))  
    sys.stdout.buffer.write((d.get('body') or '')[:2500].encode('ascii', 'replace'))  
    sys.stdout.buffer.write((chr(10)).encode('ascii', 'replace'))  
