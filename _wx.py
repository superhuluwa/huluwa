import json, os, subprocess  
D = r'C:\Users\Jing\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell'  
P = D + r'\pwsh.runtimeconfig.json'  
W = D + r'\pwsh.exe'  
B = P + '.codexbak'  
print('CONFIG_EXISTS', os.path.exists(P))  
s = open(P, encoding='utf-8').read()  
open(B, 'w', encoding='utf-8').write(s)  
d = json.loads(s)  
cp = d['runtimeOptions'].setdefault('configProperties', dict())  
cp['System.Runtime.EnableWriteXorExecute'] = False  
open(P, 'w', encoding='utf-8').write(json.dumps(d, indent=2))  
print('PATCHED')  
