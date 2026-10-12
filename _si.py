import urllib.request, json, sys, urllib.parse  
qq = chr(34) + 'fully support CET' + chr(34)  
base = 'https://api.github.com/search/issues?per_page=25'  
amp = chr(38) + 'q='  
u = base + amp + urllib.parse.quote(qq)  
rq = urllib.request.Request(u)  
rq.add_header('User-Agent', 'probe')  
d = json.load(urllib.request.urlopen(rq, timeout=40))  
print('TOTAL', d.get('total_count'))  
for it in d.get('items', [])[:25]:  
    sys.stdout.buffer.write((it['state'] + ' | ' + it['title'][:110] + ' | ' + it['html_url'] + chr(10)).encode('ascii', 'replace'))  
