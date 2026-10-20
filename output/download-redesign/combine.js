const fs=require('fs');
const files=['styles-base.css','styles-warm-a.css','styles-warm-b.css','styles-warm-c.css','styles-warm-d.css','styles-task-a.css','styles-task-b.css','styles-task-c.css','styles-task-d.css','styles-ed-a.css','styles-ed-b.css','styles-ed-c.css','styles-ed-d.css'];
fs.writeFileSync('styles.css',files.map(f=>'/* '+f+' */\n'+fs.readFileSync(f,'utf8')).join('\n'));
const sources=['app-1.js','app-warm.js','app-task.js','app-ed.js','app-core.js','app-init.js'];
let js='';
for(const f of sources){if(fs.existsSync(f)){const t=fs.readFileSync(f,'utf8');js+=t.replace('__DATA__','\n'+fs.readFileSync('data.js','utf8')+'\n')+'\n'}}
fs.writeFileSync('app.js',js);
console.log(JSON.stringify({css:fs.statSync('styles.css').size,js:fs.statSync('app.js').size}));
