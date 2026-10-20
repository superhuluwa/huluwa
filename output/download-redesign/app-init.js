
document.getElementById('versions').innerHTML=warmMain()+taskMain()+edMain();
$$(document,'.version').forEach(bind);
$$(document,'[data-screen-jump]').forEach(b=>b.addEventListener('click',()=>{const root=b.closest('.version');$$(root,'[data-screen-jump]').forEach(x=>x.classList.toggle('active',x===b))}));
