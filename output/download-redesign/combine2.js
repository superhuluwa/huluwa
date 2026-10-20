const fs=require('fs');
fs.writeFileSync('app.js',fs.readFileSync('data.js','utf8')+'\n'+fs.readFileSync('app-warm.js','utf8')+'\n'+fs.readFileSync('app-task.js','utf8')+'\n'+fs.readFileSync('app-ed.js','utf8')+'\n'+fs.readFileSync('app-core.js','utf8')+'\n'+fs.readFileSync('app-init.js','utf8'));
console.log(fs.statSync('app.js').size);
