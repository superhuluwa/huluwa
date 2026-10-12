from pathlib import Path
p=Path('research_spk/package/bin/bin/xunlei-pan-cli.3.23.5.amd64')
d=p.read_bytes()
for needle in ['创建任务','任务列表','登录','新建任务','下载任务','新建','迅雷云盘','使用帮助','命令行']:
    b=needle.encode()
    poss=[]; start=0
    while True:
        i=d.find(b,start)
        if i<0: break
        poss.append(i); start=i+1
    print('\n###',needle,len(poss),poss[:20])
    for i in poss[:10]:
        lo=max(0,i-500); hi=min(len(d),i+1000)
        chunk=d[lo:hi]
        # replace non-utf8 friendly and NUL with newline
        s=chunk.decode('utf-8','backslashreplace')
        s=''.join(c if c.isprintable() else '\n' for c in s)
        # compact empty
        lines=[x.strip() for x in s.split('\n') if x.strip()]
        print('\n'.join(lines[-60:]))
        print('---')
