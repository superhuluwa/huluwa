from pathlib import Path
import re
p=Path('research_spk/package/bin/bin/xunlei-pan-cli.3.23.5.amd64')
data=p.read_bytes()
strings=[(m.start(),m.group().decode('latin1')) for m in re.finditer(rb'[\x20-\x7e]{4,}',data)]
for needle in ['cmdListRunner','cmdListTask','commands.Download','doUploadFile','Usage of','Usage:','list task','--help','login','download']:
    print('\n###',needle)
    hits=[i for i,(_,s) in enumerate(strings) if needle.lower() in s.lower()]
    print('hits',len(hits))
    for i in hits[:10]:
        print('\n'.join(f'{off}: {s}' for off,s in strings[max(0,i-15):i+25]))
        print('---')
