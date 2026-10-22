(() => {
'use strict';
const directions = [
  { id: 'pine', number: '01', name: '松柏极简', tag: '延续品牌', note: '柔和分组 · 自然段回复 · 轻量输入栏', palette: ['#206c54','#e7eee3','#f5f7f3'] },
  { id: 'editorial', number: '02', name: '黑白编辑', tag: '阅读优先', note: '编号清单 · 细分割线 · 更宽的文字区', palette: ['#242424','#c5c5bf','#ffffff'] },
  { id: 'terminal', number: '03', name: '深色终端', tag: '开发氛围', note: '命令式层级 · 深色消息块 · 日志折叠', palette: ['#8ab4f8','#273b55','#10151d'] },
];
const glyphs = {
  back:'<path d="m14 5-7 7 7 7M7 12h13"/>',
  chevron:'<path d="m9 5 7 7-7 7"/>', down:'<path d="m6 9 6 6 6-6"/>',
  more:'<circle cx="5" cy="12" r=".8"/><circle cx="12" cy="12" r=".8"/><circle cx="19" cy="12" r=".8"/>',
  plus:'<path d="M12 5v14M5 12h14"/>', close:'<path d="m6 6 12 12M18 6 6 18"/>',
  spark:'<path d="m12 3 2.6 6.4L21 12l-6.4 2.6L12 21l-2.6-6.4L3 12l6.4-2.6ZM20 2v4m-2-2h4"/>',
  terminal:'<rect x="3" y="4" width="18" height="16" rx="2"/><path d="m7 9 3 3-3 3m6 0h4"/>',
  send:'<path d="M12 19V5m-5 5 5-5 5 5"/>', stop:'<rect x="7" y="7" width="10" height="10" rx="1" fill="currentColor" stroke="none"/>',
  folder:'<path d="M3 7V5h7l2 3h9v12H3Zm0 3h18"/>',
  tune:'<path d="M5 3v18M12 3v18M19 3v18M2 8h6m1 8h6m1-10h6"/>',
  server:'<rect x="3" y="3" width="18" height="7" rx="2"/><rect x="3" y="14" width="18" height="7" rx="2"/><path d="M7 6.5h.1M7 17.5h.1M12 6.5h5M12 17.5h5"/>',
  refresh:'<path d="M20 8a8 8 0 1 0 0 8M20 3v5h-5"/>',
  chat:'<path d="M21 12a8 8 0 0 1-8 8H7l-4 2V12a9 9 0 0 1 18 0Z"/><path d="M7 10h10M7 14h6"/>',
  grid:'<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
  shield:'<path d="m12 3 8 3v5c0 5-4 8-8 10-4-2-8-5-8-10V6Z"/><path d="m8 11 3 3 5-5"/>',
  download:'<path d="M12 3v12m-4-4 4 4 4-4M4 17v4h16v-4"/>',
  user:'<circle cx="12" cy="8" r="3.5"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/>',
  lock:'<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 4v3"/>',
  link:'<path d="m10 14 4-4m-7 6-1 1a4 4 0 0 1-6-6l4-4a4 4 0 0 1 6 0m4 1 1-1a4 4 0 0 1 6 6l-4 4a4 4 0 0 1-6 0" transform="translate(2 0)"/>',
  check:'<path d="m5 12 4 4L19 6"/>', eye:'<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>',
  info:'<circle cx="12" cy="12" r="9"/><path d="M12 10v7m0-10h.01"/>',
  trash:'<path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/>',
  wifi:'<path d="M2 8a16 16 0 0 1 20 0M5 12a11 11 0 0 1 14 0m-10 4a5 5 0 0 1 6 0m-3 4h.01"/>',
  signal:'<path d="M4 20v-3m5 3v-7m5 7V9m5 11V4"/>',
};
const icon = (name, extra = '') => `<svg class="icon ${extra}" viewBox="0 0 24 24" aria-hidden="true">${glyphs[name] || glyphs.spark}</svg>`;
const escape = value => String(value).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const projects = [
  {id:'huluwa',title:'葫芦娃',subtitle:'C:\\Users\\wiggins.wang\\Desktop\\huluwa'},
  {id:'android',title:'安卓客户端',subtitle:'huluwa/android · Kotlin + Jetpack Compose'},
  {id:'frontend',title:'网页管理台',subtitle:'huluwa/frontend · Vue 3'},
  {id:'backend',title:'服务端接口',subtitle:'huluwa/src · Spring Boot'},
];
const providers = [{id:'codex',title:'Codex · 默认供应商',subtitle:'服务端已配置 · 3 个示例模型'}, {id:'claude',title:'Claude · 默认供应商',subtitle:'服务端已配置 · 3 个示例模型'}];
const models = [{id:'default',title:'服务端默认模型',subtitle:'沿用当前供应商的默认设置'}, {id:'fast',title:'快速响应模型',subtitle:'服务端预设 · 侧重响应速度'}, {id:'reasoning',title:'深度推理模型',subtitle:'服务端预设 · 较高推理等级'}];
const baseMessages = [
 {id:'1',role:'user',text:'把 AI 对话页改得更紧凑，能多看几条消息。',time:'10:24'},
 {id:'2',role:'assistant',text:'可以。先压缩导航和输入区，把主要空间留给消息。',time:'10:24'},
 {id:'3',role:'tool',text:'已读取 AiScreen.kt 与 Theme.kt',detail:'已读取界面文件\nui/AiScreen.kt\nui/Theme.kt\n\n已确认：项目选择、模型选择、对话列表与输入栏。\n本原型仅演示排版，不执行命令。'},
 {id:'4',role:'user',text:'字号别太小，执行日志默认收起。',time:'10:25'},
 {id:'5',role:'assistant',text:'按这个方向调整：',bullets:['标题改为两行，合并模型和状态。','减少气泡留白，移除重复标签。','执行日志折叠，点击再看完整过程。'],time:'10:25'},
 {id:'6',role:'user',text:'项目和模型也要方便切换。',time:'10:26'},
 {id:'7',role:'assistant',text:'首页保留项目选择，供应商与模型并排；二级页面展示完整名称。',time:'10:26'},
 {id:'8',role:'user',text:'按这个结构，给我三种不同的风格。',time:'10:27'},
 {id:'9',role:'assistant',text:'三套方案都保留发送、中断和操作确认入口，可以直接对比。',time:'10:27'},
];
const baseSessions = [
 {id:'s1',title:'AI 页面紧凑布局设计',project:'葫芦娃',agent:'codex',time:'10:27',active:true},
 {id:'s2',title:'优化首页的项目与模型选择',project:'安卓客户端',agent:'claude',time:'09:52'},
 {id:'s3',title:'检查消息自动跟随逻辑',project:'安卓客户端',agent:'codex',time:'09:16'},
 {id:'s4',title:'整理服务连接页面',project:'葫芦娃',agent:'codex',time:'昨天'},
 {id:'s5',title:'对话输入栏排版',project:'安卓客户端',agent:'claude',time:'昨天'},
 {id:'s6',title:'梳理移动端页面层级',project:'网页管理台',agent:'codex',time:'周二'},
 {id:'s7',title:'服务端模型配置',project:'服务端接口',agent:'claude',time:'周二'},
 {id:'s8',title:'消息列表的间距调整',project:'安卓客户端',agent:'codex',time:'周一'},
];
const makeState = () => ({page:'chat',overlay:null,pickerTab:'project',project:'huluwa',provider:'codex',model:'default',session:'s1',title:baseSessions[0].title,server:'http://192.168.1.10:9600',username:'admin',draft:'',messages:structuredClone(baseMessages),sessions:structuredClone(baseSessions),expanded:new Set(),pending:false,running:false,ticket:0});
let states = Object.fromEntries(directions.map(direction => [direction.id, makeState()]));
let serial = 0;
const gallery = document.getElementById('gallery');
const agent = ui => ui.provider === 'claude' ? 'Claude' : 'Codex';
const modelName = ui => models.find(model => model.id === ui.model).title;
const projectName = ui => projects.find(project => project.id === ui.project).title;
const iconButton = (name,label,action,extra='') => `<button type="button" class="icon-button" data-action="${action}" aria-label="${label}" ${extra}>${icon(name)}</button>`;
function topbar(ui) {
 return `<header class="screen-topbar">${iconButton('back','返回 AI 首页','home')}<div class="topbar-copy"><h3 class="topbar-title">${escape(ui.title)}</h3><div class="topbar-meta"><i class="online-dot"></i><span class="meta-name">${agent(ui)} · ${modelName(ui)}</span><span>· ${ui.running?'正在回复':ui.pending?'等待确认':'实时同步'}</span></div></div>${iconButton('more','对话操作','menu')}</header>`;
}
function message(ui, msg) {
 if (msg.role === 'tool') {
  const expanded=ui.expanded.has(msg.id);
  return `<div class="tool-message"><button type="button" class="tool-toggle" data-action="tool" data-id="${escape(msg.id)}" aria-expanded="${expanded}">${icon('terminal')}<span>${escape(msg.text)}</span><small class="tool-state">${expanded?'收起':'展开'}</small>${icon('down')}</button>${expanded?`<pre class="tool-code">${escape(msg.detail)}</pre>`:''}</div>`;
 }
 return `<article class="message ${msg.role}">${msg.role==='assistant'?`<span class="assistant-mark" aria-label="${agent(ui)} 回复">${icon('spark')}</span>`:''}<div class="bubble">${msg.role==='assistant'?`<div class="message-header"><span>${agent(ui)}</span><span>${escape(msg.time||'刚刚')}</span></div>`:''}<p>${escape(msg.text)}</p>${msg.bullets?`<ul>${msg.bullets.map(text=>`<li>${escape(text)}</li>`).join('')}</ul>`:''}</div></article>`;
}
function approval() {
 return `<section class="approval-card" aria-label="待确认的 AI 操作"><div class="approval-heading">${icon('shield')}<span>需要你确认</span></div><p>允许 AI 调整对话页面的布局吗？</p><div class="approval-command">AiScreen.kt · 修改消息与输入栏样式</div><div class="approval-actions"><button type="button" data-action="decline">拒绝</button><button type="button" class="accept" data-action="accept">允许本次</button></div></section>`;
}
function chat(ui) {
 return `${topbar(ui)}<div class="messages" aria-label="AI 对话消息"><div class="day-divider">今天 10:24</div>${ui.messages.length?ui.messages.map(msg=>message(ui,msg)).join(''):`<div class="empty-state">${icon('spark')}<strong>开始新的对话</strong>发送第一条消息，继续你的想法。</div>`}${ui.running?'<div class="typing" role="status" aria-label="正在生成演示回复"><i></i><i></i><i></i><span>正在回复…</span></div>':''}</div>${ui.pending?approval():''}<div class="composer"><form class="composer-form" data-form="message"><textarea name="message" rows="1" aria-label="消息输入框" placeholder="给 ${agent(ui)} 发消息…">${escape(ui.draft)}</textarea><button type="${ui.running?'button':'submit'}" class="send-button" ${ui.running?'data-action="stop"':''} aria-label="${ui.running?'中断回复':'发送消息'}" ${!ui.running&&!ui.draft.trim()?'disabled':''}><span>${icon(ui.running?'stop':'send')}</span></button></form></div>`;
}
function choice(ui,kind) {
 const config={project:['folder','项目',projectName(ui)],provider:['spark','供应商',agent(ui)],model:['tune','模型',modelName(ui)]}[kind];
 return `<button type="button" class="choice-button" data-action="picker" data-kind="${kind}" aria-label="选择${config[1]}">${icon(config[0])}<span class="choice-copy"><small>${config[1]}</small><strong>${escape(config[2])}</strong></span>${icon('down')}</button>`;
}
function navigation() {
 return `<nav class="bottom-nav" aria-label="主导航">${[['grid','功能'],['shield','系统'],['download','下载'],['spark','AI'],['user','我的']].map(([name,label])=>`<button type="button" class="${label==='AI'?'selected':''}" data-action="${label==='AI'?'home':'other-tab'}" aria-label="${label}" ${label==='AI'?'aria-current="page"':''}><span class="nav-icon">${icon(name)}</span>${label}</button>`).join('')}</nav>`;
}
function home(ui,style) {
 return `<header class="home-heading"><h3>${style==='editorial'?'对话':style==='terminal'?'AI 控制台':'AI 工作台'}</h3>${iconButton('more','服务操作','menu')}</header><div class="home-scroll"><div class="connection-strip">${icon('server')}<div class="server-label"><strong>${escape(ui.username)} <span class="small-label">· 已登录</span></strong><span>${escape(ui.server)}</span></div>${iconButton('refresh','刷新项目与模型','refresh')}</div><section class="launch-card"><div class="launch-title"><h4>${style==='terminal'?'当前环境':style==='editorial'?'开始一段新对话':'新对话'}</h4><button type="button" class="primary-button" data-action="create">${icon('plus')}开始对话</button></div>${choice(ui,'project')}<div class="choice-pair">${choice(ui,'provider')}${choice(ui,'model')}</div></section><div class="section-heading"><h4>最近对话</h4><span>${ui.sessions.length} 段对话</span></div><div class="recent-list">${ui.sessions.length?ui.sessions.map((session,index)=>`<button type="button" class="session-row" data-action="open" data-id="${session.id}" aria-label="打开对话：${escape(session.title)}"><span class="session-icon">${icon(session.agent==='codex'?'terminal':'spark')}</span><span class="session-number">${style==='terminal'?'#':''}${String(index+1).padStart(2,'0')}</span><span class="session-copy"><strong>${escape(session.title)}</strong><small>${escape(session.project)} · ${session.agent==='codex'?'Codex':'Claude'}</small></span><span class="session-side"><span>${escape(session.time)}</span>${session.active?'<span class="active">进行中</span>':''}</span></button>`).join(''):`<div class="empty-state">${icon('chat')}<strong>还没有对话</strong>选好项目和模型，开始第一段对话。</div>`}</div></div>${navigation()}`;
}
function connection(ui) {
 return `<header class="screen-topbar">${iconButton('back','返回 AI 首页','home')}<div class="topbar-copy"><h3 class="topbar-title">服务连接</h3></div></header><div class="connection-scroll"><div class="connection-intro"><span class="connection-symbol">${icon('link')}</span><div><strong>连接你的 AI</strong><p>继续服务器上的 Claude 与 Codex 对话</p></div></div><form class="connection-form" data-form="connection"><label class="field-label">服务地址<input name="server" aria-label="服务地址" type="url" required value="${escape(ui.server)}" placeholder="http://192.168.1.10:9600"></label><label class="field-label">服务账号<input name="username" aria-label="服务账号" required autocomplete="off" value="${escape(ui.username)}"></label><label class="field-label">服务密码<span class="password-field"><input name="password" aria-label="服务密码" type="password" required autocomplete="off" placeholder="输入服务密码">${iconButton('eye','显示或隐藏密码','password')}</span></label><button class="primary-button" type="submit">连接并登录${icon('chevron')}</button></form><p class="privacy-note">${icon('lock')}<span>使用 TerminalBuddy 服务账号。密码不会保存。此处仅演示连接流程，不发送登录请求。</span></p><p class="connection-caption">同一个入口，两种助手</p><div class="agent-chips"><span>Codex</span><span>Claude</span></div></div>`;
}
function sheetFrame(title,content,extra='') {
 return `<div class="overlay" data-overlay="true"><section class="sheet" role="dialog" aria-label="${title}"><div class="sheet-handle"></div><header class="sheet-header"><h4>${title}</h4>${extra}${iconButton('close','关闭二级页面','close')}</header>${content}</section></div>`;
}
function overlay(ui) {
 if (ui.overlay==='picker') {
  const kind=ui.pickerTab;
  const options=kind==='project'?projects:kind==='provider'?providers:models;
  const titles={project:'项目',provider:'供应商',model:'模型'};
  return sheetFrame(`选择${titles[kind]}`,`<div class="sheet-tabs" role="group" aria-label="切换选择类型">${Object.entries(titles).map(([id,title])=>`<button type="button" data-action="picker-tab" data-kind="${id}" class="${kind===id?'selected':''}" aria-pressed="${kind===id}">${title}</button>`).join('')}</div><div class="sheet-body">${options.map(option=>`<button type="button" class="sheet-choice ${ui[kind]===option.id?'selected':''}" data-action="choose" data-id="${option.id}" aria-label="选择${escape(option.title)}" aria-pressed="${ui[kind]===option.id}"><span class="choice-main"><strong>${escape(option.title)}</strong><small>${escape(option.subtitle)}</small></span>${ui[kind]===option.id?icon('check'):''}</button>`).join('')}</div>`,`<span>${options.length} 项</span>`);
 }
 if (ui.overlay==='info') return sheetFrame('对话信息',`<div class="sheet-body information"><h5>对话标题</h5><p>${escape(ui.title)}</p><h5>项目</h5><p>${escape(projectName(ui))}</p><p><code>${escape(projects.find(p=>p.id===ui.project).subtitle)}</code></p><h5>供应商与模型</h5><p>${agent(ui)} · ${modelName(ui)}</p><h5>同步状态</h5><p>已连接 · 实时同步</p><h5>消息数量</h5><p>${ui.messages.length} 条</p><button type="button" class="primary-button" data-action="close">完成</button></div>`);
 if (ui.overlay==='delete') return sheetFrame('删除这段对话？',`<div class="sheet-body information"><p>将从当前方案的示例列表中移除「${escape(ui.title)}」，项目文件不会受影响。</p><button type="button" class="primary-button" data-action="confirm-delete">删除对话</button><button type="button" class="menu-row" data-action="close">取消</button></div>`);
 if (ui.overlay==='menu') {
  const rows=ui.page==='chat'?[['info','对话信息','info'],['shield','查看操作确认','approval'],['folder','返回首页选择模型','change-target'],['trash','删除对话','delete']]:[['link','服务连接','connection'],['refresh','刷新项目与模型','refresh'],['chat','查看对话页面','show-chat']];
  return sheetFrame(ui.page==='chat'?'对话操作':'服务操作',`<div class="sheet-body">${rows.map(([name,title,action])=>`<button type="button" class="menu-row ${action==='delete'?'danger':''}" data-action="${action}">${icon(name)}${title}</button>`).join('')}</div>`);
 }
 return '';
}
function render(style,follow=true) {
 const ui=states[style], container=gallery.querySelector(`[data-style="${style}"] .screen-content`);
 const previousScroll=container.querySelector('.messages')?.scrollTop||0;
 container.innerHTML=(ui.page==='home'?home(ui,style):ui.page==='connection'?connection(ui):chat(ui))+overlay(ui);
 const messages=container.querySelector('.messages');
 if(messages) messages.scrollTop=follow?messages.scrollHeight:previousScroll;
}
function initialize() {
 gallery.innerHTML=directions.map(direction=>`<section class="direction" data-style="${direction.id}" aria-label="${direction.name}设计"><header class="direction-head"><div class="direction-name"><span class="direction-index">${direction.number}</span><h2>${direction.name}</h2></div><span class="direction-tag">${direction.tag}</span></header><div class="phone ${direction.id}"><div class="phone-screen"><div class="status-bar"><span>10:27</span><div class="system-icons">${icon('signal')}${icon('wifi')}<span>80</span><i class="battery"></i></div></div><div class="screen-content"></div><div class="home-indicator"></div></div></div><p class="direction-caption"><span>${direction.note}</span><span class="palette" aria-label="方案配色">${direction.palette.map(color=>`<i style="background:${color}"></i>`).join('')}</span></p></section>`).join('');
 directions.forEach(direction=>render(direction.id));
}
function toast(style,text) {
 const phone=gallery.querySelector(`[data-style="${style}"] .phone`);
 phone.querySelector('.toast')?.remove();
 const item=document.createElement('div'); item.className='toast'; item.setAttribute('role','status'); item.textContent=text;phone.append(item);
 setTimeout(()=>item.remove(),2200);
}
function switchPage(style,page) {
 const ui=states[style];ui.overlay=null;
 if(page==='picker'){ui.page='home';ui.overlay='picker';ui.pickerTab='project';}
 else if(page==='approval'){ui.page='chat';ui.pending=true;}
 else {ui.page=page;ui.pending=false;}
 render(style);
}
function send(style,form) {
 const ui=states[style],text=form.elements.message.value.trim();
 if(!text||ui.running)return;
 ui.messages.push({id:`demo-${++serial}`,role:'user',text,time:'刚刚'});
 ui.draft='';ui.running=true;const ticket=++ui.ticket;render(style);
 setTimeout(()=>{
  if(states[style]!==ui||ticket!==ui.ticket||!ui.running)return;
  ui.running=false;ui.messages.push({id:`demo-${++serial}`,role:'assistant',text:'收到，这条消息已加入演示会话。这里是本地模拟回复，选定风格后可按现有接口接入真实对话。',time:'刚刚'});render(style);
 },1100);
}
gallery.addEventListener('click',event=>{
 const direction=event.target.closest('.direction');if(!direction)return;
 const style=direction.dataset.style,ui=states[style];
 if(event.target.matches('[data-overlay]')){ui.overlay=null;render(style,false);return;}
 const button=event.target.closest('[data-action]');if(!button||button.disabled)return;
 const action=button.dataset.action;
 if(action==='home'){switchPage(style,'home');return;}
 if(action==='show-chat'){switchPage(style,'chat');return;}
 if(action==='connection'){switchPage(style,'connection');return;}
 if(action==='approval'){switchPage(style,'approval');return;}
 if(action==='other-tab'){toast(style,'本次展示 AI 模块的设计');return;}
 if(action==='refresh'){ui.overlay=null;render(style,false);toast(style,'项目与模型已同步（演示）');return;}
 if(action==='menu'){ui.overlay='menu';render(style,false);return;}
 if(action==='close'){ui.overlay=null;render(style,false);return;}
 if(action==='info'){ui.overlay='info';render(style,false);return;}
 if(action==='delete'){ui.overlay='delete';render(style,false);return;}
 if(action==='confirm-delete'){ui.sessions=ui.sessions.filter(session=>session.id!==ui.session);switchPage(style,'home');toast(style,'已移除当前示例对话');return;}
 if(action==='picker'){ui.overlay='picker';ui.pickerTab=button.dataset.kind;render(style,false);return;}
 if(action==='picker-tab'){ui.pickerTab=button.dataset.kind;render(style,false);return;}
 if(action==='choose'){ui[ui.pickerTab]=button.dataset.id;if(ui.pickerTab==='provider')ui.model='default';ui.overlay=null;render(style,false);toast(style,'已更新'+({project:'项目',provider:'供应商',model:'模型'}[ui.pickerTab]));return;}
 if(action==='change-target'){ui.page='home';ui.overlay='picker';ui.pickerTab='model';render(style);return;}
 if(action==='tool'){const id=button.dataset.id;ui.expanded.has(id)?ui.expanded.delete(id):ui.expanded.add(id);render(style,false);return;}
 if(action==='stop'){ui.running=false;ui.ticket++;render(style);toast(style,'已中断演示回复');return;}
 if(action==='accept'||action==='decline'){ui.pending=false;ui.messages.push({id:`demo-${++serial}`,role:'assistant',text:action==='accept'?'已允许本次操作。这是原型演示，没有修改实际文件。':'已拒绝本次操作，可以继续对话。',time:'刚刚'});render(style);return;}
 if(action==='password'){const input=button.parentElement.querySelector('input');input.type=input.type==='password'?'text':'password';button.setAttribute('aria-label',input.type==='password'?'显示密码':'隐藏密码');button.setAttribute('aria-pressed',String(input.type==='text'));return;}
 if(action==='open'){const session=ui.sessions.find(session=>session.id===button.dataset.id);ui.running=false;ui.ticket++;ui.session=session.id;ui.title=session.title;ui.provider=session.agent;ui.model=session.model||'default';ui.project=projects.find(project=>project.title===session.project)?.id||'huluwa';ui.messages=structuredClone(baseMessages);ui.draft='';switchPage(style,'chat');return;}
 if(action==='create'){ui.running=false;ui.ticket++;const id=`new-${++serial}`,title='新的对话';ui.session=id;ui.title=title;ui.sessions.unshift({id,title,project:projectName(ui),agent:ui.provider,model:ui.model,time:'刚刚'});ui.messages=[];ui.draft='';switchPage(style,'chat');return;}
});
gallery.addEventListener('input',event=>{
 const textarea=event.target.closest('textarea[name="message"]');if(!textarea)return;
 const style=textarea.closest('.direction').dataset.style;const ui=states[style];ui.draft=textarea.value;
 textarea.style.height='40px';textarea.style.height=Math.min(100,Math.max(40,textarea.scrollHeight))+'px';
 textarea.form.querySelector('.send-button').disabled=!ui.running&&!ui.draft.trim();
});
gallery.addEventListener('keydown',event=>{
 if(event.key==='Escape'){const style=event.target.closest('.direction')?.dataset.style;if(style&&states[style].overlay){states[style].overlay=null;render(style,false);}}
});
gallery.addEventListener('submit',event=>{
 const form=event.target;const style=form.closest('.direction').dataset.style;event.preventDefault();
 if(form.dataset.form==='message'){send(style,form);return;}
 if(form.dataset.form==='connection'){
  const ui=states[style];let url;try{url=new URL(form.elements.server.value);}catch{toast(style,'请输入有效的服务地址');return;}
  if(!['http:','https:'].includes(url.protocol)){toast(style,'服务地址需使用 http 或 https');return;}
  ui.server=form.elements.server.value;ui.username=form.elements.username.value;switchPage(style,'home');toast(style,'已完成连接流程演示，未发送请求');
 }
});
document.querySelectorAll('[data-view]').forEach(button=>{
 if(button.tagName!=='BUTTON')return;
 button.addEventListener('click',()=>{
  gallery.dataset.view=button.dataset.view;
  document.querySelectorAll('.view-switch button').forEach(item=>item.setAttribute('aria-pressed',String(item===button)));
 });
});
document.querySelectorAll('[data-page]').forEach(button=>button.addEventListener('click',()=>{
 document.querySelectorAll('[data-page]').forEach(item=>item.setAttribute('aria-pressed',String(item===button)));
 directions.forEach(direction=>switchPage(direction.id,button.dataset.page));
}));
document.getElementById('reset-demo').addEventListener('click',()=>{
 states=Object.fromEntries(directions.map(direction=>[direction.id,makeState()]));
 document.querySelectorAll('[data-page]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.page==='chat')));
 directions.forEach(direction=>render(direction.id));
});
initialize();
})();
