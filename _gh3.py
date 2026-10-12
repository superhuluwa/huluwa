import urllib.request, json, sys  
def get(u):  
    rq = urllib.request.Request(u)  
    rq.add_header('User-Agent', 'probe')  
    return json.load(urllib.request.urlopen(rq, timeout=40))  
d = get('https://api.github.com/repos/dotnet/runtime/issues/128540')  
sys.stdout.buffer.write(('TITLE: ' + d['title'] + chr(10)).encode('ascii','replace'))  
