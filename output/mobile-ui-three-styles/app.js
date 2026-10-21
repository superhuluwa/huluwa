(function () {
  'use strict';
  const directions = [
    { id: 'pine', number: '01', name: '松白', subtitle: '柔和分组 · 轻盈清单', note: '熟悉、亲切，适合日常使用', colors: ['#296a50', '#dce9d2', '#f5f8f3'], eyebrow: '葫芦娃 · 家庭服务' },
    { id: 'blue', number: '02', name: '晴蓝', subtitle: '清晰网格 · 利落排版', note: '工具感更强，操作更直接', colors: ['#3b60d7', '#dce4fb', '#ffffff'], eyebrow: '葫芦娃 · 随身工作台' },
    { id: 'sand', number: '03', name: '暖砂', subtitle: '暖白留白 · 安静书页', note: '弱化容器，让内容自然呼吸', colors: ['#8a653a', '#dfd4c0', '#f7f4ee'], eyebrow: '葫芦娃 / 日常' },
  ];
  const navItems = [['functions', '功能', 'grid'], ['system', '系统', 'shield'], ['downloads', '下载', 'download'], ['subscriptions', '订阅', 'bookmark'], ['mine', '我的', 'user']];
  const sessions = [
    { id: 'movie', title: '电影港', desc: '找到 6 条「山野来信」相关资源', short: '搜索影视资源', icon: 'film', tone: 'purple', time: '09:41', unread: 2 },
    { id: 'dytt', title: '电影天堂', desc: '你订阅的剧集已更新至第 12 集', short: '追踪剧集更新', icon: 'play', tone: 'mint', time: '09:38', unread: 1 },
    { id: 'magnet', title: '磁力下载', desc: '任务已添加，正在下载到家里', short: '提交磁力任务', icon: 'magnet', tone: 'blue', time: '09:32', unread: 0 },
    { id: 'video', title: '视频解析', desc: '视频解析完成，点击查看', short: '解析视频链接', icon: 'video', tone: 'coral', time: '昨天', unread: 0 },
    { id: 'xunlei', title: '迅雷下载', desc: '「海边的日记」已下载完成', short: '管理迅雷任务', icon: 'bolt', tone: 'amber', time: '昨天', unread: 0 },
    { id: 'system', title: '系统通知', desc: '家里的服务运行正常', short: '接收服务通知', icon: 'bell', tone: 'gray', time: '09:20', unread: 3 },
  ];
  const services = [
    { id: 'frp', title: 'Frp 管理', desc: '内网穿透与隧道管理', icon: 'network', tone: 'mint', tools: ['隧道管理', '配置文件', '运行日志'] },
    { id: 'docker', title: 'Docker 管理', desc: '容器与资源管理', icon: 'box', tone: 'blue', tools: ['容器管理', '镜像管理', '网络与存储卷'] },
    { id: 'android-build', title: 'App 构建管理', desc: '源码、安装包与执行日志', icon: 'android', tone: 'mint', tools: ['源码与构建', '安装包管理', '执行日志'] },
    { id: 'upgrade', title: '在线更新', desc: '查看与更新葫芦娃版本', icon: 'refresh', tone: 'purple', tools: ['检查更新', '更新记录'] },
  ];
  const initialTasks = [
    { id: 't1', title: '山野来信', episode: '第 12 集', source: 'hls', progress: 68, status: 'active', speed: 8.2, size: '1.36 / 2.00 GB' },
    { id: 't2', title: '星河回响', episode: '第 08 集', source: 'magnet', progress: 32, status: 'active', speed: 4.6, size: '1.12 / 3.50 GB' },
    { id: 't3', title: '海边的日记', episode: '第 03 集', source: 'win', progress: 0, status: 'waiting', speed: 0, size: '0 / 1.80 GB' },
    { id: 't4', title: '漫长的旅途', episode: '第 06 集', source: 'hls', progress: 100, status: 'done', speed: 0, size: '1.70 GB' },
    { id: 't5', title: '青山之间', episode: '第 10 集', source: 'magnet', progress: 100, status: 'done', speed: 0, size: '2.35 GB' },
    { id: 't6', title: '城市的晚风', episode: '第 05 集', source: 'hls', progress: 44, status: 'failed', speed: 0, size: '0.88 / 2.00 GB', error: '网络连接中断，请重试' },
  ];
  const initialSubscriptions = [
    { id: 's1', title: '山野来信', year: '2026', genre: '剧情', source: '电影天堂', count: 12, poster: 'mountain', enabled: true, last: '09:32', next: '10:00' },
    { id: 's2', title: '星河回响', year: '2026', genre: '科幻', source: '电影港', count: 8, poster: 'stars', enabled: true, last: '09:05', next: '10:05' },
    { id: 's3', title: '漫长的旅途', year: '2026', genre: '纪录片', source: '电影港', count: 6, poster: 'journey', enabled: false, last: '昨天 20:00', next: '' },
  ];
  const sourceNames = { all: '全部来源', hls: '离线剧集', magnet: '磁力下载', win: '迅雷【Win】' };
  const statusNames = { active: '正在下载', waiting: '等待开始', done: '已完成', failed: '下载失败', cancelled: '已取消' };
  const icons = {
    grid: '<rect x="3.5" y="3.5" width="6" height="6" rx="1.4"/><rect x="14.5" y="3.5" width="6" height="6" rx="1.4"/><rect x="3.5" y="14.5" width="6" height="6" rx="1.4"/><rect x="14.5" y="14.5" width="6" height="6" rx="1.4"/>',
    shield: '<path d="m12 3 8 3v5c0 5-3.8 8-8 10-4.2-2-8-5-8-10V6Z"/><path d="m8.5 11.5 2.4 2.4 4.6-4.8"/>',
    download: '<path d="M12 3v12m-4-4 4 4 4-4M4 16v4h16v-4"/>',
    bookmark: '<path d="M6 4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v17l-6-4-6 4Z"/><path d="M9 7h6"/>',
    user: '<circle cx="12" cy="8" r="3.5"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/>',
    film: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 4v16M17 4v16M3 9h4m-4 6h4m10-6h4m-4 6h4"/>',
    play: '<circle cx="12" cy="12" r="8.7"/><path d="m10 8 6 4-6 4Z"/>',
    magnet: '<path d="M5 4h5v9a2 2 0 0 0 4 0V4h5v9a7 7 0 0 1-14 0Z"/><path d="M5 8h5m4 0h5"/>',
    video: '<rect x="3" y="6" width="18" height="14" rx="2"/><path d="m10 10 5 3-5 3ZM3 6l3-3m4 3 3-3m4 3 3-3"/>',
    bolt: '<path d="m13 2-9 12h7l-1 8 10-13h-7Z"/>',
    bell: '<path d="M5 16h14l-2-3V9a5 5 0 0 0-10 0v4ZM9 20h6"/>',
    network: '<rect x="9" y="3" width="6" height="5" rx="1"/><rect x="2" y="16" width="6" height="5" rx="1"/><rect x="16" y="16" width="6" height="5" rx="1"/><path d="M12 8v4M5 16v-4h14v4"/>',
    box: '<path d="m12 3 9 5v9l-9 5-9-5V8Zm0 10v9M3 8l9 5 9-5M7.5 5.5l9 5"/>',
    android: '<path d="M5 12a7 7 0 0 1 14 0v7H5Zm2-7L5 2m12 3 2-3M2 12v6m20-6v6M8 19v3m8-3v3M8 8h.1m7.8 0h.1"/>',
    refresh: '<path d="M20 8a8 8 0 1 0 0 8M20 3v5h-5"/>',
    server: '<rect x="3" y="3" width="18" height="7" rx="2"/><rect x="3" y="14" width="18" height="7" rx="2"/><path d="M7 6.5h.1M7 17.5h.1M12 6.5h5M12 17.5h5"/>',
    more: '<circle cx="5" cy="12" r=".9"/><circle cx="12" cy="12" r=".9"/><circle cx="19" cy="12" r=".9"/>',
    plus: '<path d="M12 5v14M5 12h14"/>', arrow: '<path d="m9 5 7 7-7 7"/>', back: '<path d="m15 5-7 7 7 7"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>', logout: '<path d="M10 3H4v18h6M9 12h12m-5-5 5 5-5 5"/>',
    send: '<path d="m3 3 18 9-18 9 3-9Zm3 9h15"/>',
    document: '<path d="M14 3H5v18h14V8Zm0 0v5h5M8 12h8m-8 4h6"/>',
    wifi: '<path d="M2 8a16 16 0 0 1 20 0M5 12a11 11 0 0 1 14 0M8.5 15.5a5.6 5.6 0 0 1 7 0M12 19h.01"/>',
    signal: '<path d="M4 20v-3m5 3v-7m5 7V9m5 11V4"/>',
    battery: '<rect x="2" y="6" width="17" height="12" rx="2"/><path d="M22 10v4"/><rect x="4" y="8" width="10.4" height="8" rx=".8" fill="currentColor" stroke="none"/>',
    gourd: '<path d="M12 6C8 2 2 8 7 12c-7 3-5 10 5 10s12-7 5-10c5-4-1-10-5-6ZM12 6c0-4 2-5 5-5M9 13h6"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    chat: '<path d="M21 11.5A8.5 8.5 0 0 1 12.5 20H5l-3 2V10.5A8.5 8.5 0 0 1 21 11.5ZM7 9h10M7 13h6"/>',
  };
  const root = document.getElementById('directions');
  const states = Object.fromEntries(directions.map(d => [d.id, { page: 'functions', session: 'dytt', filter: 'active', source: 'all', empty: false, tasks: structuredClone(initialTasks), subscriptions: structuredClone(initialSubscriptions), sheet: null, toast: '', connected: true, messages: [] }]));
  const toastTimers = new Map();
  let nextTask = 10;
  function esc(value) { return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]); }
  function icon(name, size = 20) { return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name] || icons.chat}</svg>`; }
  function serviceIcon(row) { return `<span class="service-icon tone-${row.tone || 'mint'}">${icon(row.icon || 'play')}</span>`; }
  function iconButton(name, label, act, extra = '', className = '') { return `<button type="button" class="icon-button ${className}" data-act="${act}" aria-label="${esc(label)}" ${extra}>${icon(name)}</button>`; }
  function connection(s) { return `<span class="connection-line">${s.connected ? '<i class="live-dot"></i>' : ''}${s.connected ? '已连接' : '未连接'}</span>`; }
  function statusBar() { return `<div class="device-status" aria-label="安卓状态栏：时间 9 点 41 分，移动信号良好，已连接无线网络，电量百分之八十"><span>9:41</span><span class="device-camera" aria-hidden="true"></span><span class="status-icons">${icon('signal', 13)}${icon('wifi', 14)}<span class="battery-percentage">80%</span>${icon('battery', 20)}</span></div>`; }
  function appHeader(style, s) {
    if (s.page === 'chat') {
      const session = sessions.find(row => row.id === s.session) || sessions[1];
      return `<header class="app-header chat-header">${iconButton('back', '返回功能', 'back')}${serviceIcon(session)}<div class="chat-heading"><h2>${session.title}</h2><p class="app-subtitle">${s.connected ? '已连接 · 消息实时同步' : '连接已断开'}</p></div>${iconButton('more', '会话设置', 'chat-settings')}</header>`;
    }
    const title = navItems.find(n => n[0] === s.page)?.[1] || '功能';
    const subtitles = { functions: style === 'sand' ? '让家里的服务，安静地运转。' : style === 'blue' ? '常用工具，随时就绪。' : '一句话，搞定家里的事。', system: '家里的服务，都在掌握。', downloads: '每一份期待，都在路上。', subscriptions: '喜欢的故事，持续追更。', mine: '家里的服务，随身就好。' };
    const d = directions.find(d => d.id === style);
    let action = `<button type="button" class="header-avatar" data-act="account" aria-label="查看连接配置">${icon('gourd')}</button>`;
    if (s.page === 'downloads') action = iconButton('plus', '添加磁力下载', 'new-task', '', 'header-action');
    if (s.page === 'subscriptions') action = iconButton('refresh', '检查所有订阅', 'check-all', '', 'header-action');
    if (s.page === 'mine') action = '';
    return `<header class="app-header"><div><div class="app-eyebrow">${style === 'pine' ? icon('gourd', 11) : ''}${d.eyebrow}</div><h2>${title}</h2><p class="app-subtitle">${subtitles[s.page]}</p></div>${action}</header>`;
  }
  function appNav(s) { return `<nav class="app-nav" aria-label="应用主导航">${navItems.map(([id, label, glyph]) => `<button type="button" class="nav-item ${s.page === id ? 'selected' : ''}" data-act="page" data-page="${id}" ${s.page === id ? 'aria-current="page"' : ''}><span class="nav-icon">${icon(glyph)}${id === 'functions' ? '<i class="nav-dot" aria-label="有未读消息"></i>' : ''}</span><span>${label}</span></button>`).join('')}</nav>`; }
  function sessionRow(row, management = false) { return `<button type="button" class="session-row" data-act="${management ? 'service' : 'session'}" data-id="${row.id}" aria-label="${esc(row.title)}${row.unread ? `，${row.unread} 条未读消息` : ''}">${serviceIcon(row)}<span class="row-copy"><span class="row-title">${row.title}</span><span class="row-description">${row.desc}</span></span>${management ? `<span class="management-label">管理</span>${icon('arrow', 15)}` : `<span class="row-tail"><span class="row-time">${row.time}</span>${row.unread ? `<span class="unread-badge">${row.unread}</span>` : '<span></span>'}</span>`}</button>`; }
  function serviceTile(row, management = false) { return `<button type="button" class="service-tile" data-act="${management ? 'service' : 'session'}" data-id="${row.id}" aria-label="${esc(row.title)}${row.unread ? `，${row.unread} 条未读消息` : ''}"><span class="tile-top">${serviceIcon(row)}${row.unread ? `<span class="unread-badge">${row.unread}</span>` : management ? icon('arrow', 13) : ''}</span><span class="row-title">${row.title}</span><span class="row-description">${management ? row.desc : row.short}</span></button>`; }
  function functionsPage(style, s) {
    if (style === 'blue') return `<div class="section-line"><strong>常用服务</strong>${connection(s)}</div><div class="service-grid">${sessions.slice(0, 4).map(row => serviceTile(row)).join('')}</div><div class="section-line"><strong>最近消息</strong><span>2 个会话</span></div><div class="group-list">${[sessions[4], sessions[5]].map(row => sessionRow(row)).join('')}</div>`;
    return `<div class="section-line"><strong>${style === 'sand' ? '六项服务，随时待命' : '我的会话'}</strong>${connection(s)}</div><div class="group-list">${sessions.map(row => sessionRow(row)).join('')}</div>${style === 'sand' ? '' : '<p class="end-note">服务像聊天一样简单。</p>'}`;
  }
  function systemPage(style, s) {
    const summary = `<div class="system-summary">${serviceIcon({ icon: 'server' })}<div><strong>家庭服务器</strong><p>${s.connected ? '连接正常 · 实时同步中' : '连接已断开 · 请检查连接配置'}</p></div>${icon('check', 15)}</div>`;
    if (style === 'blue') return `${summary}<div class="section-line"><strong>服务管理</strong><span>4 项服务</span></div><div class="service-grid system-grid">${services.map(row => serviceTile(row, true)).join('')}</div><p class="end-note">管理好服务，享受简单的日常。</p>`;
    if (style === 'sand') return `${summary}<section class="system-section"><div class="section-line"><strong>基础服务</strong><span>01 — 02</span></div><div class="group-list">${services.slice(0, 2).map(row => sessionRow(row, true)).join('')}</div></section><section class="system-section"><div class="section-line"><strong>应用维护</strong><span>03 — 04</span></div><div class="group-list">${services.slice(2).map(row => sessionRow(row, true)).join('')}</div></section>`;
    return `${summary}<div class="section-line"><strong>服务管理</strong><span>4 项服务</span></div><div class="group-list">${services.map(row => sessionRow(row, true)).join('')}</div><p class="end-note">家里的事，随时照看。</p>`;
  }
  function filteredTasks(s) { return s.tasks.filter(t => (s.source === 'all' || t.source === s.source) && (s.filter === 'active' ? ['active', 'waiting'].includes(t.status) : s.filter === 'done' ? t.status === 'done' : true)); }
  function taskCard(t) { return `<article class="task-card ${t.status === 'failed' ? 'failed' : ''}"><div class="task-top"><span class="task-icon">${icon(t.source === 'hls' ? 'document' : 'download')}</span><div class="row-copy"><span class="row-title">${esc(t.title)}</span><span class="row-description">${esc(t.episode)} · ${sourceNames[t.source]}</span></div>${iconButton('more', `查看${t.title}下载详情`, 'task', `data-id="${t.id}"`)}</div><div class="task-progress" role="progressbar" aria-label="${esc(t.title)}下载进度" aria-valuenow="${t.progress}" aria-valuemin="0" aria-valuemax="100"><span style="width:${t.progress}%"></span></div><div class="task-bottom"><span class="${t.status === 'failed' ? 'task-error' : ''}">${t.status === 'failed' ? '连接中断 · 可重试' : t.status === 'waiting' ? '等待开始' : t.status === 'cancelled' ? '已取消' : t.size}</span><span class="task-speed">${t.status === 'active' ? `${t.speed.toFixed(1)} MB/s` : t.status === 'done' ? '已完成' : t.status === 'failed' ? '下载失败' : t.status === 'cancelled' ? '已取消' : '等待中'}</span><span class="task-percent">${t.progress}%</span></div></article>`; }
  function downloadsPage(style, s) {
    const sourceTasks = s.tasks.filter(t => s.source === 'all' || t.source === s.source);
    const active = sourceTasks.filter(t => ['active', 'waiting'].includes(t.status)).length, done = sourceTasks.filter(t => t.status === 'done').length;
    const totalSpeed = s.tasks.filter(t => t.status === 'active').reduce((n, t) => n + t.speed, 0), tasks = filteredTasks(s);
    return `<section class="download-summary" aria-label="下载概览"><div class="summary-top"><div><span class="summary-caption">当前下载速度</span><div class="speed-value">${totalSpeed.toFixed(1)}<small>MB/s</small></div></div><div class="summary-symbol">${icon('download')}</div></div><div class="summary-foot"><span><b>${s.tasks.filter(t => t.status === 'active').length}</b>正在下载</span><span><b>${s.tasks.filter(t => t.status === 'waiting').length}</b>等待开始</span><span>家庭服务器</span></div></section><div class="download-filter" role="group" aria-label="下载状态筛选">${[['active', `进行中 ${active}`], ['done', `已完成 ${done}`], ['all', `全部 ${sourceTasks.length}`]].map(([id, label]) => `<button type="button" data-act="filter" data-filter="${id}" class="${s.filter === id ? 'selected' : ''}" aria-pressed="${s.filter === id}">${label}</button>`).join('')}</div><div class="source-control"><label class="source-leading">下载来源<select class="source-select" data-act="source" aria-label="筛选下载来源">${Object.entries(sourceNames).map(([value, label]) => `<option value="${value}" ${s.source === value ? 'selected' : ''}>${label}</option>`).join('')}</select></label>${iconButton('refresh', '刷新下载任务', 'refresh-downloads')}</div><div class="task-list">${tasks.length ? tasks.map(taskCard).join('') : emptyState('downloads', true)}</div>`;
  }
  function subscriptionCard(sub){const countLabel=sub.source==='电影天堂'?`已收录 ${sub.count} 集`:`${sub.count} 条可用磁力`;return `<article class="subscription-card ${sub.enabled?'':'subscription-paused'}"><img class="poster" src="assets/${sub.poster}.svg" alt="${esc(sub.title)}示意封面"><div class="subscription-copy"><h3 class="subscription-title">${esc(sub.title)}</h3><p class="subscription-meta">${sub.year} · ${sub.genre} · ${sub.source}</p><p class="subscription-update">${countLabel}</p><p class="subscription-check"><span class="subscription-status">${sub.enabled?'自动追更中':'暂停追更'}</span></p><p class="subscription-check">${sub.enabled?`下次检查 ${sub.next}`:`上次检查 ${sub.last}`}</p></div>${iconButton('more',`管理${sub.title}订阅`,'subscription',`data-id="${sub.id}"`,'more-button')}</article>`;}
  function subscriptionsPage(style, s) { return `<div class="subscription-overview"><span><b>${s.subscriptions.filter(sub => sub.enabled).length}</b> 部追更中</span><span>喜欢的内容，不错过。</span></div><div class="subscription-list">${s.subscriptions.length ? s.subscriptions.map(subscriptionCard).join('') : emptyState('subscriptions')}</div>`; }
  function minePage(style, s) { return `<section class="profile-card"><div class="profile-avatar">${icon('gourd', 28)}</div><div><h3 class="profile-name">小葫芦</h3><p class="profile-caption">我的家庭服务器</p>${connection(s)}</div></section><div class="section-line"><strong>连接与账户</strong></div><div class="group-list"><button type="button" class="settings-row" data-act="account">${icon('server')}<span class="row-copy"><span class="row-title">连接配置</span><span class="row-description">服务器、账号与实时连接</span></span>${icon('arrow', 13)}</button><button type="button" class="settings-row danger" data-act="logout">${icon('logout')}<span class="row-copy"><span class="row-title">退出登录 / 切换服务器</span><span class="row-description">退出后，服务器任务继续运行</span></span>${icon('arrow', 13)}</button></div><div class="brand-signature">${icon('gourd', 24)}<p>家里的服务，随身就好。</p><span>葫芦娃助手</span></div>`; }
  function filmResult(s, title = '山野来信') {
    const subscribed = s.subscriptions.some(sub => sub.title === title);
    return `<div class="message-result"><div class="film-intro"><img class="poster" src="assets/mountain.svg" alt="影视示意封面"><div><h3>${esc(title)}</h3><p>2026 · 剧情</p><p>${s.session === 'movie' ? '找到 6 条相关资源' : '更新至第 12 集'}</p><p>来源：${s.session === 'movie' ? '电影港' : '电影天堂'}</p></div></div><p class="film-summary">山川之间，藏着平凡生活里的温柔与回响。</p><div class="film-actions"><button type="button" class="small-button" data-act="film-resources" data-title="${esc(title)}">资源列表</button><button type="button" class="small-button primary" data-act="${subscribed ? 'goto-subscriptions' : 'subscribe-film'}" data-title="${esc(title)}">${subscribed ? '查看订阅' : '订阅追更'}</button></div></div>`;
  }
  function chatPage(style, s) {
    const session = sessions.find(row => row.id === s.session) || sessions[1];
    const initialText = ['movie', 'dytt'].includes(session.id) ? '把想看的片名发给我，我来帮你找。' : session.id === 'magnet' ? '发送磁力链接，把下载任务交给我。' : session.id === 'video' ? '发送视频链接，我来帮你解析。' : session.id === 'xunlei' ? '发送链接，随时查看迅雷下载进度。' : '家里的服务运行正常，新通知会出现在这里。';
    const defaultFilm = ['movie', 'dytt'].includes(session.id);
    return `<div class="chat-date">今天 09:38</div><div class="chat-message">${serviceIcon(session)}<div class="message-bubble">${initialText}</div></div>${defaultFilm ? `<div class="chat-message user"><div class="message-bubble">山野来信</div></div><div class="chat-message">${serviceIcon(session)}${filmResult(s)}</div>` : ''}${s.messages.map(m => m.mine ? `<div class="chat-message user"><div class="message-bubble">${esc(m.text)}</div></div>` : `<div class="chat-message">${serviceIcon(session)}${m.film ? filmResult(s, m.text) : `<div class="message-bubble">${esc(m.text)}</div>`}</div>`).join('')}`;
  }
  function composer(s) {
    return s.session === 'system' ? `<div class="chat-compose"><p class="app-subtitle" style="margin:6px auto 9px">此会话用于接收服务通知</p></div>` : `<form class="chat-compose" data-form="message"><input aria-label="消息内容" name="message" autocomplete="off" placeholder="${['movie', 'dytt'].includes(s.session) ? '输入片名，开始搜索…' : '输入消息或粘贴链接…'}" maxlength="240" required><button type="submit" class="send-button" aria-label="发送消息">${icon('send')}</button></form>`;
  }
  function emptyState(page, filtered = false) {
    const map = { functions: ['chat', '还没有会话消息', '服务已就绪，从第一句对话开始。', '查看服务'], system: ['server', '暂时没有系统服务', '连接家庭服务器后，在这里管理服务。', '连接配置'], downloads: ['download', filtered ? '没有符合条件的任务' : '下载列表很安静', filtered ? '试试其他状态或下载来源。' : '找到喜欢的内容，就把它带回家。', filtered ? '查看全部任务' : '添加磁力任务'], subscriptions: ['bookmark', '好故事，值得等待', '从会话中找到喜欢的剧集，开始第一次订阅。', '去找点好看的'], chat: ['chat', '发条消息，开始吧', '搜索影视、提交下载，都可以从这里开始。', '查看示例消息'] };
    const [glyph, title, desc, label] = map[page] || map.functions;
    return `<div class="empty-state"><div class="empty-illustration">${icon(glyph)}</div><h3>${title}</h3><p>${desc}</p><button type="button" class="small-button" data-act="empty-action" data-target="${page}" data-filtered="${filtered}">${label}</button></div>`;
  }
  function detailRow(label, value) { return `<div class="detail-row"><span>${esc(label)}</span><span>${esc(value)}</span></div>`; }
  function action(label, act, extra = '', kind = '') { return `<button type="button" class="action-button ${kind}" data-act="${act}" ${extra}>${label}</button>`; }
  function sheetMarkup(s) {
    if (!s.sheet) return '';
    const sh = s.sheet;
    let title = '', content = '';
    if (sh.type === 'task') {
      const t = s.tasks.find(t => t.id === sh.id); if (!t) return '';
      title = '下载详情'; content = `<p>${esc(t.title)} · ${esc(t.episode)}</p>${detailRow('状态', statusNames[t.status])}${detailRow('下载来源', sourceNames[t.source])}${detailRow('下载进度', `${t.progress}%`)}${detailRow('当前速度', `${t.speed.toFixed(1)} MB/s`)}${detailRow('保存位置', t.source === 'hls' ? '/volume1/downloads' : '由下载器管理')}${t.error ? `<p class="form-error">${esc(t.error)}</p>` : ''}<div class="sheet-actions">${t.source === 'hls' ? action('查看日志', 'task-log', `data-id="${t.id}"`, 'secondary') : ''}${t.source === 'hls' && ['active', 'waiting'].includes(t.status) ? action('取消下载', 'cancel-task', `data-id="${t.id}"`, 'danger') : ''}${t.source === 'hls' && ['failed', 'cancelled'].includes(t.status) ? action('重新下载', 'retry-task', `data-id="${t.id}"`) : ''}${action('返回列表', 'close', '', 'secondary')}</div>`;
    } else if (sh.type === 'task-log') {
      const t = s.tasks.find(t => t.id === sh.id); title = '任务日志';
      content = `<p>${esc(t?.title)} · 示例日志</p><pre class="log-block">09:32:00  收到下载任务\n09:32:01  已解析剧集资源\n09:32:02  开始下载媒体片段\n09:41:00  ${t?.status === 'failed' ? '网络连接中断，等待重试' : '下载进度同步完成'}</pre><div class="sheet-actions">${action('返回详情', 'task', `data-id="${sh.id}"`, 'secondary')}</div>`;
    } else if (sh.type === 'new-task') {
      title = '添加磁力任务'; content = `<p>把链接交给下载器，回家就能看。</p><form class="sheet-form" data-form="new-task"><label class="field-label">磁力链接<textarea name="magnet" placeholder="magnet:?xt=urn:btih:…" required>${esc(sh.magnet || '')}</textarea></label><label class="field-label">任务名称<input name="title" placeholder="手动磁力任务" maxlength="60" value="${esc(sh.title || '')}"></label>${sh.error ? `<p class="form-error" role="alert">${esc(sh.error)}</p>` : ''}<button type="submit" class="action-button">添加到下载队列</button></form>`;
    } else if (sh.type === 'subscription') {
      const sub = s.subscriptions.find(sub => sub.id === sh.id); if (!sub) return '';
      title = sub.title; content = `<p>${sub.year} · ${sub.genre} · ${sub.source}</p>${detailRow('追更状态', sub.enabled ? '自动追更中' : '已暂停')}${detailRow('已收录', sub.source === '电影天堂' ? `${sub.count} 集` : `${sub.count} 条磁力`)}${detailRow('上次检查', sub.last)}${sub.enabled ? detailRow('下次检查', sub.next) : ''}<div class="sheet-actions">${sub.enabled ? action('立即检查', 'check-sub', `data-id="${sub.id}"`) : ''}${action(sub.enabled ? '暂停追更' : '恢复追更', 'toggle-sub', `data-id="${sub.id}"`, 'secondary')}${action('资源列表', 'resources', `data-id="${sub.id}"`, 'secondary')}${action('删除订阅', 'delete-sub-confirm', `data-id="${sub.id}"`, 'danger')}</div>`;
    } else if (sh.type === 'delete-sub') {
      const sub = s.subscriptions.find(sub => sub.id === sh.id); title = '删除订阅'; content = `<p>确定删除「${esc(sub?.title)}」的订阅吗？已下载的文件会保留。</p><div class="sheet-actions">${action('确认删除', 'delete-sub', `data-id="${sh.id}"`, 'danger')}${action('保留订阅', 'subscription', `data-id="${sh.id}"`, 'secondary')}</div>`;
    } else if (sh.type === 'resources') {
      const sub = s.subscriptions.find(sub => sub.id === sh.id); title = '资源列表'; const titleText = sub?.title || sh.title || '山野来信';
      const isMagnet = sub ? sub.source === '电影港' : s.session === 'movie';
      content = `<p>${esc(titleText)} · ${isMagnet ? '选择想下载的资源' : '选择想下载的剧集'}</p>${(isMagnet ? [1, 2, 3] : [12, 11, 10]).map(ep => `<div class="resource-row"><span>${isMagnet ? `资源 ${ep}` : `第 ${ep} 集`}<small>${isMagnet ? '高清 · 磁力资源' : '高清 · 离线剧集'}</small></span><button type="button" class="small-button" data-act="resource-download" data-title="${esc(titleText)}" data-episode="${ep}" data-source="${isMagnet ? 'magnet' : 'hls'}">下载</button></div>`).join('')}`;
    } else if (sh.type === 'account') {
      title = '连接配置'; content = `<p>与家里的服务保持连接。</p><div class="sheet-form"><label class="field-label">服务器地址<input value="http://192.168.31.10:8000" readonly aria-label="示例服务器地址"></label><label class="field-label">账号<input value="admin" readonly aria-label="示例账号"></label></div>${detailRow('实时连接', s.connected ? '已连接 · 实时同步中' : '连接已断开')}<div class="sheet-actions">${!s.connected ? action('重新连接', 'reconnect') : ''}${action('修改密码', 'password', '', 'secondary')}${action('完成', 'close', '', 'secondary')}</div>`;
    } else if (sh.type === 'password') {
      title = '修改密码'; content = `<form class="sheet-form" data-form="password"><label class="field-label">当前密码<input type="password" name="old" autocomplete="current-password" required></label><label class="field-label">新密码<input type="password" name="password" minlength="6" autocomplete="new-password" placeholder="至少 6 位" required></label><label class="field-label">确认新密码<input type="password" name="confirm" minlength="6" autocomplete="new-password" required></label>${sh.error ? `<p class="form-error" role="alert">${esc(sh.error)}</p>` : ''}<button type="submit" class="action-button">保存密码</button></form>`;
    } else if (sh.type === 'logout') {
      title = '退出登录'; content = '<p>退出后可以切换服务器或账号，服务器上的任务会继续运行。</p><div class="sheet-actions">' + action('退出演示账户', 'confirm-logout', '', 'danger') + action('继续使用', 'close', '', 'secondary') + '</div>';
    } else if (sh.type === 'service') {
      const service = services.find(row => row.id === sh.id); title = service.title;
      content = `<p>${service.desc}</p>${detailRow('家庭服务器', s.connected ? '已连接' : '未连接')}<div class="group-list" style="margin-top:16px">${service.tools.map((tool, i) => `<button type="button" class="settings-row" data-act="service-tool" data-id="${service.id}" data-tool="${i}"><span class="row-copy"><span class="row-title">${tool}</span></span>${icon('arrow', 13)}</button>`).join('')}</div>`;
    } else if (sh.type === 'service-tool') {
      const service = services.find(row => row.id === sh.id); title = service.tools[sh.tool];
      if (service.id === 'frp' && sh.tool === 0) content = `<p>家庭服务器 · 内网穿透</p>${detailRow('家庭云盘', '运行中 · :18000')}${detailRow('远程桌面', '运行中 · :13389')}${detailRow('影视服务', '已停用 · :18096')}`;
      else if (service.id === 'docker' && sh.tool === 0) content = `<p>家庭服务器 · 容器概览</p>${detailRow('huluwa', '运行中')}${detailRow('qbittorrent', '运行中')}${detailRow('frpc', '运行中')}`;
      else content = `<p>${service.title} · ${service.tools[sh.tool]}</p><div class="log-block">此处展示二级页面的整体层次。<br>返回即可继续对比三个主界面方案。</div>`;
      content += `<div class="sheet-actions">${action('返回服务', 'service', `data-id="${service.id}"`, 'secondary')}</div>`;
    } else if (sh.type === 'chat-settings') {
      const session = sessions.find(row => row.id === s.session) || sessions[1]; title = '会话设置'; content = `<p>${session.title}</p>${detailRow('消息同步', s.connected ? '实时连接' : '未连接')}${detailRow('服务状态', s.connected ? '可用' : '等待连接')}<div class="sheet-actions">${action('连接配置', 'account', '', 'secondary')}${action('返回会话', 'close', '', 'secondary')}</div>`;
    }
    return `<div class="sheet-layer"><button type="button" class="sheet-scrim" data-act="close" aria-label="关闭弹层"></button><section class="sheet-panel" role="dialog" aria-modal="true" aria-label="${esc(title)}"><div class="sheet-handle"></div><div class="sheet-heading"><h3>${esc(title)}</h3>${iconButton('close', '关闭弹层', 'close')}</div><div class="sheet-body">${content}</div></section></div>`;
  }
  function renderPhone(style, preserveScroll = false) {
    const s = states[style], phone = root.querySelector(`.direction-card[data-style="${style}"] .phone`), scroll = preserveScroll ? phone.querySelector('.app-content')?.scrollTop || 0 : 0;
    const pageContent = s.empty && s.page !== 'mine' ? emptyState(s.page) : ({ functions: functionsPage, system: systemPage, downloads: downloadsPage, subscriptions: subscriptionsPage, mine: minePage, chat: chatPage }[s.page])(style, s);
    phone.dataset.page = s.page; phone.innerHTML = `<div class="phone-inner">${statusBar()}${appHeader(style, s)}<div class="app-content ${s.page === 'chat' ? 'chat-content' : ''}">${pageContent}</div>${s.page === 'chat' ? composer(s) : appNav(s)}<div class="home-indicator" aria-hidden="true"></div>${sheetMarkup(s)}${s.toast ? `<div class="phone-toast" role="status">${esc(s.toast)}</div>` : ''}</div>`;
    if (preserveScroll) phone.querySelector('.app-content').scrollTop = scroll;
    syncControls();
  }
  function renderAll() {
    root.innerHTML = directions.map(d => `<article class="direction-card" data-style="${d.id}"><header class="direction-heading"><div class="direction-name"><span class="direction-number">${d.number}</span><div><h2>${d.name}</h2><p>${d.subtitle}</p></div></div><div class="palette" aria-label="${d.name}配色">${d.colors.map(c => `<i style="background:${c};border:1px solid #2836260d"></i>`).join('')}</div></header><div class="phone theme-${d.id}" aria-label="${d.name}手机端预览"></div><div class="direction-note"><strong>${d.note}</strong><span>五个入口 · 完整会话</span></div></article>`).join(''); directions.forEach(d => renderPhone(d.id));
  }
  function syncControls() {
    const pages = Object.values(states).map(s => s.page);
    document.querySelectorAll('[data-global-page]').forEach(button => { const isCurrent = pages.every(page => page === button.dataset.globalPage); button.classList.toggle('selected', isCurrent); button.setAttribute('aria-pressed', String(isCurrent)); });
    const emptyValues = Object.values(states).map(s => s.empty), emptyInput = document.getElementById('empty-preview'); emptyInput.checked = emptyValues.every(Boolean); emptyInput.indeterminate = emptyValues.some(Boolean) && !emptyValues.every(Boolean);
  }
  function toast(style, message) {
    const s = states[style]; s.toast = message; clearTimeout(toastTimers.get(style));
    const inner = root.querySelector(`[data-style="${style}"] .phone-inner`); inner.querySelector('.phone-toast')?.remove();
    const notice = document.createElement('div'); notice.className = 'phone-toast'; notice.setAttribute('role', 'status'); notice.textContent = message; inner.appendChild(notice);
    toastTimers.set(style, setTimeout(() => { s.toast = ''; root.querySelector(`[data-style="${style}"] .phone-toast`)?.remove(); }, 2600));
  }
  function setSheet(style, sheet) { states[style].sheet = sheet; renderPhone(style, true); queueMicrotask(() => root.querySelector(`[data-style="${style}"] .sheet-heading button`)?.focus({ preventScroll: true })); }
  function setPage(style, page) { const s = states[style]; s.page = page; s.sheet = null; s.toast = ''; renderPhone(style); }
  function addResourceTask(style, title, episode, source = 'hls') {
    const s = states[style]; s.tasks.unshift({ id: `t${++nextTask}`, title, episode: source === 'magnet' ? `资源 ${episode}` : `第 ${episode} 集`, source, progress: 0, status: 'waiting', speed: 0, size: '等待下载' }); s.empty = false; s.filter = 'active'; s.source = 'all'; s.sheet = null; renderPhone(style, true); toast(style, '已加入示例下载队列');
  }
  root.addEventListener('click', event => {
    const button = event.target.closest('button[data-act]'); if (!button || !root.contains(button)) return;
    const style = button.closest('.direction-card').dataset.style, s = states[style], act = button.dataset.act, id = button.dataset.id;
    if (act === 'page') setPage(style, button.dataset.page);
    else if (act === 'back') setPage(style, 'functions');
    else if (act === 'session') { s.session = id; s.messages = []; setPage(style, 'chat'); }
    else if (act === 'service') setSheet(style, { type: 'service', id });
    else if (act === 'service-tool') setSheet(style, { type: 'service-tool', id, tool: Number(button.dataset.tool) });
    else if (act === 'filter') { s.filter = button.dataset.filter; renderPhone(style); }
    else if (act === 'refresh-downloads') toast(style, '下载状态已同步');
    else if (act === 'task' || act === 'task-log') setSheet(style, { type: act, id });
    else if (act === 'cancel-task') { const t = s.tasks.find(t => t.id === id); t.status = 'cancelled'; t.speed = 0; s.sheet = null; renderPhone(style, true); toast(style, '任务已取消'); }
    else if (act === 'retry-task') { const t = s.tasks.find(t => t.id === id); t.status = 'waiting'; t.progress = 0; t.speed = 0; t.error = ''; s.sheet = null; s.filter = 'active'; renderPhone(style); toast(style, '已重新加入下载队列'); }
    else if (act === 'new-task') setSheet(style, { type: 'new-task' });
    else if (act === 'subscription') setSheet(style, { type: 'subscription', id });
    else if (act === 'check-sub') { const sub = s.subscriptions.find(sub => sub.id === id); sub.last = '刚刚'; renderPhone(style, true); toast(style, '检查完成，当前收录已同步'); }
    else if (act === 'check-all') { s.subscriptions.forEach(sub => { if (sub.enabled) sub.last = '刚刚'; }); renderPhone(style, true); toast(style, '追更中的订阅已检查'); }
    else if (act === 'toggle-sub') { const sub = s.subscriptions.find(sub => sub.id === id); sub.enabled = !sub.enabled; if (sub.enabled) sub.next = '10:00'; renderPhone(style, true); toast(style, sub.enabled ? '已恢复追更' : '已暂停追更'); }
    else if (act === 'delete-sub-confirm') setSheet(style, { type: 'delete-sub', id });
    else if (act === 'delete-sub') { s.subscriptions = s.subscriptions.filter(sub => sub.id !== id); s.sheet = null; renderPhone(style, true); toast(style, '订阅已删除，下载文件已保留'); }
    else if (act === 'resources') setSheet(style, { type: 'resources', id });
    else if (act === 'film-resources') setSheet(style, { type: 'resources', title: button.dataset.title });
    else if (act === 'resource-download') addResourceTask(style, button.dataset.title, button.dataset.episode, button.dataset.source);
    else if (act === 'goto-subscriptions') { s.empty = false; setPage(style, 'subscriptions'); }
    else if (act === 'subscribe-film') { const title = button.dataset.title; if (!s.subscriptions.some(sub => sub.title === title)) s.subscriptions.unshift({ ...structuredClone(initialSubscriptions[0]), id: `s${Date.now()}`, title, source: s.session === 'movie' ? '电影港' : '电影天堂', count: s.session === 'movie' ? 6 : 12 }); renderPhone(style, true); toast(style, '已添加订阅，后续更新自动追踪'); }
    else if (['account', 'password', 'logout', 'chat-settings'].includes(act)) setSheet(style, { type: act });
    else if (act === 'confirm-logout') { s.connected = false; s.sheet = null; setPage(style, 'mine'); toast(style, '已退出演示账户'); }
    else if (act === 'reconnect') { s.connected = true; renderPhone(style, true); toast(style, '已连接到演示服务器'); }
    else if (act === 'close') { s.sheet = null; renderPhone(style, true); }
    else if (act === 'empty-action') {
      const page = button.dataset.target;
      if (page === 'system') setSheet(style, { type: 'account' });
      else if (page === 'downloads' && button.dataset.filtered !== 'true') setSheet(style, { type: 'new-task' });
      else if (page === 'subscriptions') { s.empty = false; setPage(style, 'functions'); }
      else { s.empty = false; if (page === 'downloads') { s.filter = 'all'; s.source = 'all'; } renderPhone(style); }
    }
  });
  root.addEventListener('change', event => { if (!event.target.matches('select[data-act="source"]')) return; const style = event.target.closest('.direction-card').dataset.style; states[style].source = event.target.value; renderPhone(style); });
  root.addEventListener('submit', event => {
    const form = event.target.closest('form[data-form]'); if (!form) return; event.preventDefault();
    const style = form.closest('.direction-card').dataset.style, s = states[style], data = new FormData(form);
    if (form.dataset.form === 'message') {
      const text = String(data.get('message') || '').trim(); if (!text) return;
      s.empty = false; s.messages.push({ mine: true, text });
      if (['movie', 'dytt'].includes(s.session)) s.messages.push({ film: true, text }); else s.messages.push({ mine: false, text: s.session === 'video' ? '已收到链接，解析结果将显示在这里。' : '已收到消息，任务状态会同步到这里。' });
      renderPhone(style); const content = root.querySelector(`[data-style="${style}"] .app-content`); content.scrollTop = content.scrollHeight; root.querySelector(`[data-style="${style}"] .chat-compose input`)?.focus({ preventScroll: true });
    } else if (form.dataset.form === 'new-task') {
      const magnet = String(data.get('magnet') || '').trim(), title = String(data.get('title') || '').trim() || '手动磁力任务';
      if (!/^magnet:\?xt=urn:btih:(?:[a-f0-9]{40}|[a-z2-7]{32})(?:&|$)/i.test(magnet)) { setSheet(style, { type: 'new-task', magnet, title, error: '请输入有效的磁力链接，包含 32 或 40 位资源标识。' }); return; }
      s.tasks.unshift({ id: `t${++nextTask}`, title, episode: '手动添加', source: 'magnet', progress: 0, status: 'waiting', speed: 0, size: '等待下载' }); s.empty = false; s.filter = 'active'; s.source = 'all'; setPage(style, 'downloads'); toast(style, '已添加示例任务');
    } else if (form.dataset.form === 'password') {
      if (data.get('password') !== data.get('confirm')) { setSheet(style, { type: 'password', error: '两次输入的新密码不一致，请重新输入。' }); return; } s.sheet = null; renderPhone(style, true); toast(style, '密码表单验证通过，演示设置已保存');
    }
  });
  root.addEventListener('keydown', event => {
    const article = event.target.closest('.direction-card'); if (!article) return; const style = article.dataset.style, s = states[style]; if (!s.sheet) return;
    if (event.key === 'Escape') { s.sheet = null; renderPhone(style, true); }
    if (event.key === 'Tab') {
      const focusable = Array.from(article.querySelectorAll('.sheet-panel button:not(:disabled), .sheet-panel input, .sheet-panel textarea, .sheet-panel select')).filter(el => el.offsetParent !== null), first = focusable[0], last = focusable.at(-1);
      if (event.shiftKey && event.target === first) { event.preventDefault(); last?.focus(); } else if (!event.shiftKey && event.target === last) { event.preventDefault(); first?.focus(); }
    }
  });
  document.querySelectorAll('[data-global-page]').forEach(button => button.addEventListener('click', () => { Object.values(states).forEach(s => { s.page = button.dataset.globalPage; s.sheet = null; s.toast = ''; if (s.page === 'chat') s.session = 'dytt'; }); directions.forEach(d => renderPhone(d.id)); }));
  document.querySelectorAll('.style-switch button').forEach(button => button.addEventListener('click', () => { root.dataset.view = button.dataset.style; document.querySelectorAll('.style-switch button').forEach(b => { const current = b.dataset.style === button.dataset.style; b.classList.toggle('selected', current); b.setAttribute('aria-pressed', String(current)); }); }));
  document.getElementById('empty-preview').addEventListener('change', event => { const empty = event.target.checked; Object.values(states).forEach(s => { s.empty = empty; s.sheet = null; }); directions.forEach(d => renderPhone(d.id)); });
  document.querySelector('.board-brand').addEventListener('click', event => { event.preventDefault(); Object.values(states).forEach(s => { s.page = 'functions'; s.empty = false; s.sheet = null; }); directions.forEach(d => renderPhone(d.id)); });
  renderAll();
  const narrowScreen = window.matchMedia('(max-width:600px)');
  function adaptStyleSelection() {
    if (narrowScreen.matches && root.dataset.view === 'all') document.querySelector('.style-switch [data-style="pine"]').click();
  }
  narrowScreen.addEventListener('change', adaptStyleSelection);
  adaptStyleSelection();
})();
