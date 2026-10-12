import os, shutil  
D = r'C:\Users\Jing\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell'  
P = D + r'\pwsh.runtimeconfig.json'  
B = P + '.codexbak'  
print('BAK_EXISTS', os.path.exists(B))  
if os.path.exists(B):  
    shutil.copyfile(B, P)  
    os.remove(B)  
    print('RESTORED')  
print(open(P, encoding='utf-8').read()[:400])  
