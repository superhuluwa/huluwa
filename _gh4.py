import urllib.request, json, sys  
def get(u):  
    rq = urllib.request.Request(u)  
    rq.add_header('User-Agent', 'probe')  
    return json.load(urllib.request.urlopen(rq, timeout=40))  
c = get('https://api.github.com/repos/openai/codex/issues/43028/comments')  
print('COMMENTS', len(c))  
for x in c[:12]:  
    sys.stdout.buffer.write((chr(10) + '--- ' + x['user']['login'] + chr(10)).encode('ascii','replace'))  
    sys.stdout.buffer.write(((x.get('body') or '')[:1200] + chr(10)).encode('ascii','replace'))  
