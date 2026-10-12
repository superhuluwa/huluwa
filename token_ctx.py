from pathlib import Path
D=Path('research_spk/package/bin/bin/xunlei-pan-cli.3.23.5.amd64').read_bytes()
def clean(s):
    out=[]
    for ch in s:
        o=ord(ch)
        if 32<=o<127 or o>=128: out.append(ch)
        else: out.append('\n')
    return '\n'.join(x.strip() for x in ''.join(out).split('\n') if x.strip())
for needle in [b'handelCreateTask',b'CreateTask',b'createTask',b'task/create',b'manual_task',b'manualTask',b'NewCommandCreateTask',b'NewCommandTask',b'Usage of',b'COMMANDS',b'create-task',b'list-task']:
    poss=[]; p=0
    while True:
        p=D.find(needle,p)
        if p<0: break
        poss.append(p); p+=len(needle)
    print('\n###',needle.decode(),len(poss),poss[:20])
    for x in poss[:8]:
        chunk=D[max(0,x-500):min(len(D),x+1500)]
        print('offset',x)
        print(clean(chunk.decode('utf-8','replace'))[-5000:])
        print('---')
