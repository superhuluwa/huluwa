'use strict';
// 独立设计原型：所有数据及操作仅保存在当前页面内存中。
const symbols = {
 download:'<path d="M12 3v12m-4-4 4 4 4-4M5 16v4h14v-4"/>',
 refresh:'<path d="M20 7v5h-5M4 17v-5h5"/><path d="M6.1 7a7 7 0 0 1 11.5-1L20 9M4 15l2.4 3A7 7 0 0 0 18 17"/>',
 settings:'<path d="m10 3-.6 2.1-2 .9-2-.6-2 3.5 1.5 1.5v2.2L3.4 14l2 3.5 2-.6 2 .9.6 2.2h4l.6-2.2 2-.9 2 .6 2-3.5-1.5-1.4v-2.2l1.5-1.5-2-3.5-2 .6-2-.9L14 3z"/><circle cx="12" cy="11.5" r="3"/>',
 search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/>',
 more:'<circle cx="5" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1" fill="currentColor" stroke="none"/>',
 film:'<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 3v18M16 3v18M4 8h4m-4 8h4m8-8h4m-4 8h4"/>',
 magnet:'<path d="M5 4v9a7 7 0 0 0 14 0V4h-5v9a2 2 0 0 1-4 0V4zM5 8h5m4 0h5"/>',
 cloud:'<path d="M7 18H6a4 4 0 0 1-.5-8A6.5 6.5 0 0 1 18 8.5a4.8 4.8 0 0 1 0 9.5h-1M12 12v9m-3-3 3 3 3-3"/>',
 grid:'<rect x="3.5" y="3.5" width="6" height="6" rx="1.3"/><rect x="14.5" y="3.5" width="6" height="6" rx="1.3"/><rect x="3.5" y="14.5" width="6" height="6" rx="1.3"/><rect x="14.5" y="14.5" width="6" height="6" rx="1.3"/>',
 system:'<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4M7 9h3m-3 4h7"/>',
 user:'<circle cx="12" cy="7.5" r="3.5"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/>',
 chevron:'<path d="m9 5 7 7-7 7"/>',
 down:'<path d="m7 10 5 5 5-5"/>',
 sort:'<path d="M4 6h16M4 12h11M4 18h6"/>',
 folder:'<path d="M3 7a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v9H3z"/>',
 check:'<path d="m5 12 4 4L19 6"/>',
 alert:'<circle cx="12" cy="12" r="9"/><path d="M12 7v6m0 4h.01"/>',
 clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
 pause:'<path d="M8 5v14M16 5v14"/>',
 close:'<path d="m6 6 12 12M6 18 18 6"/>',
 log:'<path d="M5 3h10l4 4v14H5zM15 3v5h4M9 12h6m-6 4h6"/>',
 trash:'<path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"/>',
 copy:'<rect x="8" y="8" width="12" height="13" rx="2"/><path d="M16 8V3H3v13h5"/>',
 wifi:'<path d="M3 9a14 14 0 0 1 18 0M6 12a9 9 0 0 1 12 0m-9 3a5 5 0 0 1 6 0m-3 3h.01"/>',
 signal:'<path d="M4 20v-4m5 4v-8m5 8V8m5 12V4" stroke-width="2.5"/>',
 battery:'<rect x="2" y="6" width="18" height="12" rx="2"/><path d="M22 10v4"/><rect x="4.5" y="8.5" width="12.5" height="7" rx=".5" fill="currentColor" stroke="none"/>'
};
const icon = (name, cls='') => '<svg class="'+cls+'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+(symbols[name]||symbols.download)+'</svg>';
const safe = value => String(value).replace(/[&<>"']/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const sources = {hls:{name:'离线剧集',short:'剧集',icon:'film'},magnet:{name:'磁力下载',short:'磁力',icon:'magnet'},xunlei:{name:'迅雷【Win】',short:'迅雷',icon:'cloud'}};
const originalTasks = [
 {id:'t1',title:'凡人修仙传',episode:'第 128 集',source:'hls',state:'active',progress:68,speed:2.4,received:'856 MB',total:'1.26 GB',path:'/media/剧集/凡人修仙传/第128集.mp4'},
 {id:'t2',title:'星际穿越',episode:'2160P · 蓝光原盘',source:'magnet',state:'active',progress:42,speed:8.6,received:'17.8 GB',total:'42.3 GB',path:'/media/电影/星际穿越/Interstellar.mkv'},
 {id:'t3',title:'沙丘：第二部',episode:'4K · 杜比视界',source:'xunlei',state:'active',progress:84,speed:3.8,received:'15.7 GB',total:'18.7 GB',path:'/media/电影/沙丘：第二部/Dune.Part.Two.mkv'},
 {id:'t4',title:'漫长的季节',episode:'第 08 集',source:'hls',state:'error',progress:31,speed:0,received:'328 MB',total:'1.06 GB',message:'连接超时，请重试',path:'/media/剧集/漫长的季节/第08集.mp4'},
 {id:'t5',title:'庆余年 第二季',episode:'第 09 集',source:'hls',state:'waiting',progress:0,speed:0,received:'0 MB',total:'1.42 GB',path:'/media/剧集/庆余年 第二季/第09集.mp4'},
 {id:'t6',title:'繁花',episode:'第 12 集 · 1080P',source:'magnet',state:'paused',progress:56,speed:0,received:'980 MB',total:'1.75 GB',path:'/media/剧集/繁花/第12集.mkv'},
 {id:'t7',title:'流浪地球 2',episode:'4K · 蓝光',source:'xunlei',state:'done',progress:100,speed:0,received:'23.6 GB',total:'23.6 GB',path:'/media/电影/流浪地球2/The.Wandering.Earth.2.mkv'},
 {id:'t8',title:'宇宙探索编辑部',episode:'1080P · 高清',source:'magnet',state:'done',progress:100,speed:0,received:'3.8 GB',total:'3.8 GB',path:'/media/电影/宇宙探索编辑部/Journey.to.the.West.mkv'}
];
const specs = [
 {id:'a',num:'01',title:'极简清单',subtitle:'连续列表 · 优先浏览效率',tag:'推荐',caption:'去掉大卡片，用轻分隔线串起任务。标题、进度、速度固定对齐，一眼扫过所有下载。',fit:'日常使用'},
 {id:'b',num:'02',title:'状态分组',subtitle:'分组列表 · 优先处理任务',caption:'下载中、异常、等待、已完成各归一组。组内保持连续列表，优先看到需要关注的任务。',fit:'任务较多'},
 {id:'c',num:'03',title:'来源分栏',subtitle:'双栏列表 · 优先切换来源',caption:'左侧固定来源，右侧专注任务列表。三个下载器各自查看，查找与切换更有方向。',fit:'多下载器'}
];
let states = {};
const newState = () => ({tasks:structuredClone(originalTasks),filter:'all',source:'all',query:'',sort:'default',groups:{active:true,error:true,waiting:true,done:true}});
const counts = tasks => ({all:tasks.length,active:tasks.filter(t=>t.state==='active').length,done:tasks.filter(t=>t.state==='done').length,error:tasks.filter(t=>t.state==='error').length});
const totalSpeed = tasks => tasks.reduce((sum,t)=>sum+(t.state==='active'?t.speed:0),0).toFixed(1);
const statusText = task => ({active:'下载中',error:'下载失败',waiting:'等待中',paused:'已暂停',done:'已完成',cancelled:'已取消'}[task.state]||'待处理');
const stateClass = task => ({error:'error-text',done:'done-text',waiting:'waiting-text'}[task.state]||'');
const stateIcon = task => ({error:'alert',done:'check',waiting:'clock',paused:'pause'}[task.state]||'');
const sourceTag = task => '<span class="source-tag '+task.source+'">'+sources[task.source].name+'</span>';
const progress = task => '<div class="progress-track '+task.state+'" role="progressbar" aria-label="'+safe(task.title)+'下载进度" aria-valuemin="0" aria-valuemax="100" aria-valuenow="'+task.progress+'"><i style="--progress:'+task.progress+'%"></i></div>';
const statusNode = task => '<span class="task-state '+stateClass(task)+'">'+(stateIcon(task)?icon(stateIcon(task)):'')+statusText(task)+'</span>';
const titleLine = task => '<div class="task-title-line"><button class="title-button" data-action="detail" data-task="'+task.id+'" aria-label="查看'+safe(task.title)+'详细信息"><span class="task-title">'+safe(task.title)+'</span></button><button class="more-button" data-action="menu" data-task="'+task.id+'" aria-label="'+safe(task.title)+'的更多操作">'+icon('more')+'</button></div>';
function row(task, variant, index) {
 const meta=sourceTag(task)+'<span class="meta-divider">·</span><span>'+safe(task.episode)+'</span>';
 const sizes=task.received+' / '+task.total;
 const right=task.state==='active'?'<strong>'+task.progress+'% · '+task.speed.toFixed(1)+' MB/s</strong>':statusNode(task);
 if(variant==='b') return '<article class="group-row" data-task-row="'+task.id+'"><span class="row-index">'+String(index+1).padStart(2,'0')+'</span><div class="task-content">'+titleLine(task)+'<div class="task-meta">'+meta+'</div>'+progress(task)+'<div class="task-footer"><span class="'+stateClass(task)+'">'+(task.state==='error'?safe(task.message):sizes)+'</span>'+(task.state==='active'?'<strong>'+task.progress+'% · '+task.speed.toFixed(1)+' MB/s</strong>':statusNode(task))+'</div></div></article>';
 if(variant==='c') return '<article class="task-row" data-task-row="'+task.id+'"><div class="task-content"><div class="row-heading">'+sourceTag(task)+statusNode(task)+'</div>'+titleLine(task)+'<div class="task-meta">'+safe(task.episode)+'<span class="meta-divider">·</span><span>'+task.total+'</span></div>'+progress(task)+'<div class="task-footer"><span class="'+stateClass(task)+'">'+(task.state==='error'?safe(task.message):task.received+' · '+task.progress+'%')+'</span>'+(task.state==='active'?'<strong>'+task.speed.toFixed(1)+' MB/s</strong>':'<span>'+((task.state==='done')?'已保存至目录':'')+'</span>')+'</div></div></article>';
 return '<article class="task-row" data-task-row="'+task.id+'"><div class="task-icon '+task.source+'">'+icon(sources[task.source].icon)+'</div><div class="task-content">'+titleLine(task)+'<div class="task-meta">'+meta+'</div>'+progress(task)+'<div class="task-footer"><span>'+((task.state==='error')?'<span class="error-text">'+safe(task.message)+'</span>':sizes)+'</span>'+right+'</div></div></article>';
}
const filteredTasks = state => {
 let tasks=state.tasks.filter(t=>(state.source==='all'||t.source===state.source)&&(state.filter==='all'||t.state===state.filter)&&(!state.query||(t.title+' '+t.episode+' '+sources[t.source].name).toLowerCase().includes(state.query.toLowerCase())));
 if(state.sort==='name') tasks.sort((a,b)=>a.title.localeCompare(b.title,'zh-CN'));
 return tasks;
};
const empty = () => '<div class="empty-state">'+icon('folder')+'<strong>没有符合条件的任务</strong><span>试试其他关键词或筛选条件</span><br><button data-action="clear-filter">查看全部任务</button></div>';
function searchInput(id){return '<label class="search">'+icon('search')+'<input type="search" placeholder="搜索名称或剧集" aria-label="搜索'+specs.find(s=>s.id===id).title+'中的下载任务" data-search="'+id+'" autocomplete="off"></label>';}
function toolbar(count,state,label='全部任务') { return '<div class="list-toolbar"><span>'+label+' <span data-list-count>'+count+'</span></span><button class="sort-button" data-action="sort">'+(state.sort==='name'?'名称排序':'默认排序')+icon('sort')+'</button></div>'; }
function listContent(id){
 const state=states[id],tasks=filteredTasks(state);
 if(id==='a') return '<div class="list-scroll">'+(tasks.length?tasks.map((t,i)=>row(t,'a',i)).join(''):empty())+'</div>';
 if(id==='b') {
  const groups=[{key:'active',name:'正在下载',note:totalSpeed(tasks)+' MB/s'},{key:'error',name:'需要处理',note:'重试或查看日志'},{key:'waiting',name:'等待与暂停',note:''},{key:'done',name:'已完成',note:''}];
  const body=groups.map(group=>{
   const items=tasks.filter(t=>group.key==='waiting'?['waiting','paused','cancelled'].includes(t.state):t.state===group.key);
   if(!items.length) return '';
   return '<details class="task-group" data-group="'+group.key+'" '+(state.groups[group.key]?'open':'')+'><summary><span class="group-label"><i class="group-dot '+group.key+'"></i>'+group.name+'<span class="group-count">'+items.length+'</span></span><span class="group-note">'+group.note+icon('down')+'</span></summary><div class="group-rows">'+items.map((t,i)=>row(t,'b',i)).join('')+'</div></details>';
  }).join('');
  return '<div class="list-scroll groups">'+(body||empty())+'</div>';
 }
 const name=state.source==='all'?'全部任务':sources[state.source].name;
 return '<div class="list-toolbar"><strong>'+name+'<span>'+tasks.length+' 项</span></strong><button class="sort-button" data-action="sort" aria-label="切换排序">'+icon('sort')+'</button></div><div class="list-scroll">'+(tasks.length?tasks.map((t,i)=>row(t,'c',i)).join(''):empty())+'</div><div class="mini-stat">'+icon('download')+'<span>'+tasks.filter(t=>t.state==='active').length+' 项下载中</span><strong>'+totalSpeed(tasks)+' MB/s</strong></div>';
}
function sourceRail(state) {return '<aside class="source-rail" aria-label="按下载来源筛选">'+['all','hls','magnet','xunlei'].map(s=>'<button class="source-button '+(state.source===s?'active':'')+'" data-action="source" data-source="'+s+'" aria-pressed="'+(state.source===s)+'">'+icon(s==='all'?'grid':sources[s].icon)+'<span>'+(s==='all'?'全部':sources[s].short)+'</span><small>'+state.tasks.filter(t=>s==='all'||t.source===s).length+'</small></button>').join('')+'<div class="rail-bottom" aria-hidden="true">'+icon('folder')+'</div></aside>';}
function nav(){return '<nav class="bottom-nav" aria-label="底部导航">'+[{icon:'grid',name:'功能'},{icon:'system',name:'系统'},{icon:'download',name:'下载'},{icon:'user',name:'我的'}].map(n=>'<button class="nav-item '+(n.name==='下载'?'selected':'')+'" data-action="nav" data-name="'+n.name+'" '+(n.name==='下载'?'aria-current="page"':'')+'><span class="nav-icon">'+icon(n.icon)+'</span><span>'+n.name+'</span></button>').join('')+'</nav><div class="gesture-area" aria-hidden="true"></div>';}
function renderPhone(id){
 const state=states[id],c=counts(state.tasks);
 const status='<div class="status-line"><i class="live-dot"></i><span>'+c.active+' 项下载中</span><span>·</span><strong>'+totalSpeed(state.tasks)+' MB/s</strong><span class="right-label">服务器在线</span></div>';
 let middle='';
 if(id==='a') middle=status+searchInput(id)+'<div class="filters" role="group" aria-label="按状态筛选">'+[{key:'all',name:'全部'},{key:'active',name:'下载中'},{key:'done',name:'已完成'},{key:'error',name:'异常'}].map(f=>'<button class="filter '+(state.filter===f.key?'active':'')+'" aria-pressed="'+(state.filter===f.key)+'" data-action="filter" data-filter="'+f.key+'">'+f.name+'<span class="count">'+c[f.key]+'</span></button>').join('')+'<button class="sort-button filter-sort" data-action="sort" aria-label="切换任务排序">'+icon('sort')+'</button></div><div class="content-slot" style="display:contents">'+listContent(id)+'</div>';
 if(id==='b') middle='<div class="group-summary"><div class="summary-item"><strong>'+c.active+'</strong><span>正在下载</span></div><div class="summary-item"><strong>'+c.done+'</strong><span>已经完成</span></div><div class="summary-item"><strong>'+c.error+'</strong><span>需要处理</span></div></div>'+searchInput(id)+'<div class="content-slot" style="display:contents">'+listContent(id)+'</div>';
 if(id==='c') middle=status+searchInput(id)+'<div class="source-workspace">'+sourceRail(state)+'<section class="source-main content-slot" aria-label="来源任务列表">'+listContent(id)+'</section></div>';
 const phone=document.querySelector('[data-phone="'+id+'"]');
 phone.innerHTML='<div class="status-bar"><span>09:41</span><div class="status-icons">'+icon('signal')+icon('wifi')+icon('battery','battery')+'</div></div><header class="app-header"><h3 class="app-title">下载</h3><div class="header-tools"><button class="icon-button" data-action="refresh" aria-label="刷新下载">'+icon('refresh')+'</button><button class="icon-button" data-action="settings" aria-label="下载设置">'+icon('settings')+'</button></div></header>'+middle+nav();
 const input=phone.querySelector('input');if(input)input.value=state.query;
 bindGroups(id);
}
function bindGroups(id){document.querySelectorAll('[data-phone="'+id+'"] details[data-group]').forEach(el=>el.addEventListener('toggle',()=>{if(el.isConnected)states[id].groups[el.dataset.group]=el.open;}));}
function renderList(id){const phone=document.querySelector('[data-phone="'+id+'"]');phone.querySelector('.content-slot').innerHTML=listContent(id);bindGroups(id);}
function init(){states={a:newState(),b:newState(),c:newState()};document.getElementById('layouts').innerHTML=specs.map(spec=>'<section class="layout layout-'+spec.id+'" aria-label="方案'+spec.num+'：'+spec.title+'"><div class="layout-heading"><span class="layout-number">'+spec.num+'</span><div><h2>'+spec.title+(spec.tag?'<span class="recommended">'+spec.tag+'</span>':'')+'</h2><p>'+spec.subtitle+'</p></div></div><div class="phone" data-phone="'+spec.id+'"></div><div class="layout-caption"><span class="caption-label">'+spec.fit+'</span><p>'+spec.caption+'</p></div></section>').join('');specs.forEach(s=>renderPhone(s.id));}
const toastTimers=new Map();
function toast(id,message){const phone=document.querySelector('[data-phone="'+id+'"]');phone.querySelector('.toast')?.remove();clearTimeout(toastTimers.get(id));const el=document.createElement('div');el.className='toast';el.setAttribute('role','status');el.textContent=message;phone.append(el);toastTimers.set(id,setTimeout(()=>el.remove(),2400));}
function closeSheet(id){document.querySelector('[data-phone="'+id+'"] .overlay')?.remove();document.querySelector('[data-phone="'+id+'"] .sheet-return-focus')?.focus();}
function showSheet(id,title,content,taskId=''){
 const phone=document.querySelector('[data-phone="'+id+'"]');phone.querySelector('.overlay')?.remove();phone.querySelectorAll('.sheet-return-focus').forEach(el=>el.classList.remove('sheet-return-focus'));if(document.activeElement&&phone.contains(document.activeElement))document.activeElement.classList.add('sheet-return-focus');
 const overlay=document.createElement('div');overlay.className='overlay';overlay.dataset.task=taskId;overlay.innerHTML='<section class="sheet" role="dialog" aria-modal="true" aria-label="'+safe(title)+'"><div class="sheet-handle"></div><div class="sheet-header"><h3>'+safe(title)+'</h3><button class="icon-button close-button" data-action="close" aria-label="关闭弹层">'+icon('close')+'</button></div>'+content+'</section>';phone.append(overlay);overlay.querySelector('.close-button').focus();overlay.addEventListener('click',event=>{if(event.target===overlay)closeSheet(id);});
}
function taskDetail(id,task){const pairs=[['来源',sources[task.source].name],['剧集 / 规格',task.episode],['当前状态',statusText(task)],['已下载',task.received+' / '+task.total],['下载速度',task.state==='active'?task.speed.toFixed(1)+' MB/s':'—'],['创建时间','2026-10-23 09:20']];showSheet(id,'任务详情','<h4 class="sheet-title">'+safe(task.title)+'</h4><p class="sheet-subtitle">'+safe(task.episode)+'</p>'+progress(task)+'<div class="detail-box">'+pairs.map(p=>'<div class="detail-row"><span>'+p[0]+'</span><span>'+safe(p[1])+'</span></div>').join('')+'</div>'+(task.message?'<p class="error-note">'+safe(task.message)+'</p>':'')+'<p class="settings-label">保存目录 · 点击复制</p><button class="detail-path" data-action="copy-path">'+safe(task.path)+'</button><div class="sheet-actions"><button class="secondary-button" data-action="menu">更多操作</button><button class="primary-button" data-action="close">完成</button></div>',task.id);}
function menu(id,task){const item=(action,name,symbol,cls='')=>'<button class="menu-action '+cls+'" data-action="'+action+'" data-task="'+task.id+'">'+icon(symbol)+'<span>'+name+'</span></button>';showSheet(id,'任务操作','<p class="sheet-subtitle">'+safe(task.title)+' · '+safe(task.episode)+'</p>'+item('detail','详细信息','log')+item('copy-path','复制保存路径','copy')+(task.source==='hls'?item('logs','查看下载日志','log'):'')+(task.source==='hls'&&['error','cancelled'].includes(task.state)?item('retry','重新下载','refresh'):'')+(task.source==='hls'&&['active','waiting'].includes(task.state)?item('confirm-cancel','取消下载','close','danger'):'')+'<p class="sheet-explainer">演示操作仅影响本页面中的示例任务。</p>',task.id);}
function settings(id){showSheet(id,'下载设置','<p class="sheet-subtitle">三个下载来源，沿用现有设置入口。</p>'+Object.keys(sources).map(s=>'<button class="settings-item" style="width:100%;text-align:left" data-action="setting-info" data-source="'+s+'"><span>'+sources[s].name+'设置</span>'+icon('chevron')+'</button>').join('')+'<p class="settings-label">记录管理</p>'+Object.keys(sources).map(s=>'<button class="settings-item" style="width:100%;text-align:left" data-action="confirm-clear" data-source="'+s+'"><span>清空'+sources[s].name+'记录</span>'+icon('chevron')+'</button>').join('')+'<p class="sheet-explainer">仅展示设置入口。本原型使用独立示例数据。</p>');}
function act(id,action,button){
 const state=states[id],phone=document.querySelector('[data-phone="'+id+'"]');
 const taskId=button.dataset.task||button.closest('.overlay')?.dataset.task;
 const task=state.tasks.find(t=>t.id===taskId);
 if(action==='close')return closeSheet(id);
 if(action==='filter'){state.filter=button.dataset.filter;renderPhone(id);return;}
 if(action==='source'){state.source=button.dataset.source;renderPhone(id);return;}
 if(action==='sort'){state.sort=state.sort==='default'?'name':'default';renderList(id);toast(id,state.sort==='name'?'已按任务名称排序':'已恢复默认排序');return;}
 if(action==='clear-filter'){state.filter='all';state.source='all';state.query='';renderPhone(id);return;}
 if(action==='refresh'){button.classList.add('refreshing');setTimeout(()=>{button.classList.remove('refreshing');toast(id,'示例任务已刷新');},650);return;}
 if(action==='settings')return settings(id);
 if(action==='nav'){if(button.dataset.name!=='下载')toast(id,'当前为下载页布局预览');return;}
 if(action==='setting-info'){toast(id,'此处进入'+sources[button.dataset.source].name+'设置');return;}
 if(action==='confirm-clear'){const s=button.dataset.source;showSheet(id,'清空记录','<p class="confirm-note">清空'+sources[s].name+'的示例记录？这只影响当前方案，不会操作实际下载任务或文件。</p><div class="sheet-actions"><button class="secondary-button" data-action="close">保留记录</button><button class="primary-button" data-action="clear" data-source="'+s+'">清空示例记录</button></div>');return;}
 if(action==='clear'){state.tasks=state.tasks.filter(t=>t.source!==button.dataset.source);renderPhone(id);toast(id,'示例记录已清空');return;}
 if(!task)return;
 if(action==='detail')return taskDetail(id,task);
 if(action==='menu')return menu(id,task);
 if(action==='logs'){showSheet(id,'下载日志','<p class="sheet-subtitle">'+safe(task.title)+' · '+safe(task.episode)+'</p><div class="log-lines">09:20:01 已创建下载任务<br>09:20:02 开始解析剧集资源<br>09:20:04 正在下载分片<br>'+(task.state==='error'?'<span class="error-text">09:28:32 连接超时，任务下载失败</span>':'09:28:32 已同步任务进度：'+task.progress+'%')+'</div><p class="sheet-explainer">以上为示例日志，用于展示布局。</p>',task.id);return;}
 if(action==='copy-path'){if(navigator.clipboard?.writeText){navigator.clipboard.writeText(task.path).then(()=>toast(id,'保存路径已复制')).catch(()=>toast(id,'浏览器未允许复制，请在详情中查看路径'));}else toast(id,'浏览器未允许复制，请在详情中查看路径');return;}
 if(action==='retry'){task.state='active';task.message='';task.speed=1.6;renderPhone(id);toast(id,'示例任务已重新开始');return;}
 if(action==='confirm-cancel'){showSheet(id,'取消下载','<p class="confirm-note">取消“'+safe(task.title)+'”的示例下载？当前只演示取消后的列表状态。</p><div class="sheet-actions"><button class="secondary-button" data-action="close">继续下载</button><button class="primary-button" data-action="cancel">取消示例下载</button></div>',task.id);return;}
 if(action==='cancel'){task.state='cancelled';task.speed=0;renderPhone(id);toast(id,'示例任务已取消');}
}
document.addEventListener('click',event=>{const button=event.target.closest('button[data-action]');if(!button)return;if(button.dataset.action==='reset'){init();return;}const phone=button.closest('[data-phone]');if(phone)act(phone.dataset.phone,button.dataset.action,button);});
document.addEventListener('input',event=>{const id=event.target.dataset.search;if(!id)return;states[id].query=event.target.value;renderList(id);});
document.addEventListener('keydown',event=>{const overlay=document.activeElement?.closest('[data-phone]')?.querySelector('.overlay');if(event.key==='Escape'&&overlay){closeSheet(overlay.closest('[data-phone]').dataset.phone);return;}if(event.key==='Tab'&&overlay){const nodes=[...overlay.querySelectorAll('button:not([disabled]),input,[tabindex="0"]')];if(!nodes.length)return;const first=nodes[0],last=nodes[nodes.length-1];if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}}});
init();
