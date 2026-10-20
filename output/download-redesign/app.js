const courses=[
 {id:'interview',title:'面试必问的 30 个高频问题',sub:'回答逻辑与实战演练',type:'视频课',lessons:30,size:'92.6 MB',cover:'interview',accent:'#d46b3c',last:'第 8 节',kind:'video'},
 {id:'communication',title:'高效沟通：把话说到点子上',sub:'职场表达与关系管理',type:'音频课',lessons:20,size:'68.4 MB',cover:'communication',accent:'#3e6872',last:'第 3 节',kind:'audio'},
 {id:'graduate',title:'应届生求职第一课',sub:'从定位到拿到心仪 offer',type:'视频课',lessons:18,size:'124.8 MB',cover:'graduate',accent:'#d4b083',last:'从头开始',kind:'video'}
];
const lessons=[
 {ch:'第一章 · 面试底层逻辑',count:8,size:'24.6 MB',items:[['01 面试官到底在考察什么？','06:42','已学完'],['02 用 STAR 结构讲清一段经历','08:12','上次学到 03:45'],['03 如何回答你的优缺点','07:03','已缓存']]},
 {ch:'第二章 · 高频问题拆解',count:12,size:'38.2 MB',items:[['04 为什么选择我们公司？','09:10','已缓存'],['05 你的职业规划是什么？','07:46','已缓存'],['06 如何讲好一次项目复盘','10:22','已缓存']]},
 {ch:'第三章 · 临场表达训练',count:10,size:'29.8 MB',items:[['07 面试紧张时如何快速稳定','05:58','已缓存'],['08 反问环节怎样提问加分','08:35','已缓存']]}
];

const covers={
 interview:"data:image/svg+xml;charset=UTF-8,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 width=%27100%27 height=%27130%27%3E%3Crect width=%27100%27 height=%27130%27 fill=%27%23d46b3c%27/%3E%3Ccircle cx=%2776%27 cy=%2725%27 r=%2734%27 fill=%27%23f4b171%27 opacity=%27.55%27/%3E%3Cpath d=%27M0 117 L100 83 V130 H0Z%27 fill=%27%234e3028%27/%3E%3Ctext x=%2711%27 y=%2751%27 fill=%27%23fff8f0%27 font-size=%2721%27 font-weight=%27700%27%3E面试%3C/text%3E%3Ctext x=%2711%27 y=%2775%27 fill=%27%23fff8f0%27 font-size=%2715%27%3E实战课%3C/text%3E%3C/svg%3E",
 communication:"data:image/svg+xml;charset=UTF-8,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 width=%27100%27 height=%27130%27%3E%3Crect width=%27100%27 height=%27130%27 fill=%27%233e6872%27/%3E%3Ccircle cx=%2718%27 cy=%27106%27 r=%2745%27 fill=%27%2389b9b5%27 opacity=%27.35%27/%3E%3Cpath d=%27M0 15 H100 V48 H0Z%27 fill=%27%23d8b06a%27 opacity=%27.5%27/%3E%3Ctext x=%2711%27 y=%2754%27 fill=%27%23f6fffc%27 font-size=%2721%27 font-weight=%27700%27%3E沟通%3C/text%3E%3Ctext x=%2711%27 y=%2779%27 fill=%27%23f6fffc%27 font-size=%2715%27%3E表达课%3C/text%3E%3C/svg%3E",
 graduate:"data:image/svg+xml;charset=UTF-8,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 width=%27100%27 height=%27130%27%3E%3Crect width=%27100%27 height=%27130%27 fill=%27%23d4b083%27/%3E%3Ccircle cx=%2750%27 cy=%2743%27 r=%2729%27 fill=%27%23fff4dc%27 opacity=%27.45%27/%3E%3Cpath d=%27M0 100 L100 71 V130 H0Z%27 fill=%27%23855b42%27/%3E%3Ctext x=%2711%27 y=%2755%27 fill=%27%23fffaf1%27 font-size=%2721%27 font-weight=%27700%27%3E求职%3C/text%3E%3Ctext x=%2711%27 y=%2779%27 fill=%27%23fffaf1%27 font-size=%2715%27%3E规划课%3C/text%3E%3C/svg%3E"
};
function statusBar(theme){return '<div class="statusbar"><span>9:41</span><span class="right">◒ 5G ▰</span></div>'}
function tabs(theme){return '<div class="tabbar"><div class="tab"><span class="mini-icon">⌂</span><span>首页</span></div><div class="tab"><span class="mini-icon">▤</span><span>课程</span></div><div class="tab active"><span class="mini-icon">⇩</span><span>下载</span></div><div class="tab"><span class="mini-icon">♙</span><span>我的</span></div></div>'}
function note(kind,text){return '<p class="design-note"><b>设计意图：</b>'+text+'</p><div class="review-links"><button data-screen-jump="main" class="active">主页</button><button data-screen-jump="detail">课节目录</button><button data-screen-jump="empty">空状态</button></div>'}


function warmMain(){
 const c=courses;
 return '<section class="version" data-version="warm" data-screen="main"><div class="version-head"><div class="version-title"><strong>版本一 · 温暖学习空间</strong><span>方案 A</span></div><div class="tag orange">高情绪价值</div></div>'
 +'<div class="phone">'+statusBar('warm')+'<div class="screen main-screen"><div class="topbar"><button class="back icon-btn" aria-label="返回">‹</button><h2>我的下载</h2><button class="action" data-action="warm-edit">管理</button></div><div class="scroll">'
 +'<div class="hero"><p class="hello">晚上好，今天也继续学一点</p><h3 class="hero-title">把课程装进口袋，<br><em>随时接着学。</em></h3><p class="hero-sub">已下载后无需网络，通勤路上也能轻松上课</p></div>'
 +'<div class="hero-card"><div class="hero-card-bg"></div><div class="hero-card-inner"><div class="hero-topline"><span>上次学到 · 第 8 节</span><span class="hero-tag">继续学习</span></div><div class="hero-course" data-open="interview"><img class="hero-cover" src="'+covers.interview+'" alt="面试实战课封面"><div><p class="hero-course-name">'+c[0].title+'<br>'+c[0].sub+'</p><p class="hero-course-meta">视频 · 12 / 30 节已下载 · 92.6 MB</p></div></div><div class="hero-progress"><div class="progress"><i style="width:40%"></i></div><span>已完成 40%</span></div><div class="continue-row"><button class="continue-main" data-toast="将从第 8 节「如何讲好项目经历」继续播放">继续第 8 节</button><button class="continue-secondary" data-open="interview">课程目录</button></div></div></div>'
 +'<div class="warm-section"><div class="section-head"><h3>正在下载</h3><span class="count">1 个任务 · 预计 4 分钟</span></div><div class="downloading-card"><div class="downloading-thumb"><img src="'+covers.communication+'" alt="沟通表达课封面"><div class="play-badge">↓</div></div><div class="dl-copy"><div class="line"><b>职场沟通表达训练营</b><span>72%</span></div><div class="progress" data-progress="72"><i style="width:72%"></i></div><div class="meta"><span>第 15 / 20 节</span><span>2.8 MB/s · 还剩 4 分钟</span></div></div><button class="icon-circle" data-toast="下载已暂停">Ⅱ</button></div></div>'
 +'<div class="warm-section"><div class="section-head"><h3>已下载课程</h3><span class="count">3 门 · 286 MB</span></div><div class="filter-row"><button class="filter-chip active">全部 3</button><button class="filter-chip">视频 2</button><button class="filter-chip">音频 1</button><button class="filter-chip">最近学习</button></div><div class="course-list">'
 +warmCourse(c[0],'刚学过','继续第 8 节','video')+warmCourse(c[1],'','第 3 节继续','audio')+warmCourse(c[2],'','从头开始','video')
 +'</div><div class="storage-line"><span><b>存储空间</b> · 已用 286 MB</span><span>管理空间 ›</span></div></div><div class="bottom-safe"></div></div>'
 +'<div class="editbar"><span class="selected-count">已选 0 项</span><div class="edit-actions"><button class="select-all">全选</button><button class="delete-action" data-toast="请选择要删除的课程">删除</button></div></div>'+tabs('warm')+'<div class="homebar"></div></div>'
 +'<div class="screen detail-screen"><div class="topbar"><button class="back icon-btn" data-back>‹</button><h2>课程目录</h2><button class="action" data-action="warm-detail-edit">管理</button></div><div class="scroll"><div class="detail-head"><div class="breadcrumb">我的下载 / <b>面试实战课</b></div><div class="detail-course"><img src="'+covers.interview+'" alt=""><div><h3>'+c[0].title+'</h3><p>已下载 30 节 · 共 92.6 MB · 视频课</p></div></div></div>'+warmLessons()+'<div class="storage-line"><span><b>本课程已占用</b> · 92.6 MB</span><span>空间足够</span></div><div class="bottom-safe"></div></div><div class="editbar"><span class="selected-count">已选 0 节</span><div class="edit-actions"><button class="select-all">全选</button><button class="delete-action" data-toast="请选择要删除的课节">删除</button></div></div><div class="homebar"></div></div>'
 +'<div class="screen empty-screen"><div class="topbar"><button class="back icon-btn" data-back>‹</button><h2>我的下载</h2><span class="action"></span></div><div class="scroll"><div class="warm-empty"><div class="empty-art">⇩</div><h4>还没有下载课程</h4><p>把感兴趣的课程下载到本地<br>没有网络也能继续学习</p><button data-toast="去课程中心看看">去发现课程</button></div></div><div class="homebar"></div></div><div class="toast"></div></div>'
 +note('温暖','把已下载内容提升为学习资产。用学习进度、继续学习入口和温暖封面，让下载页从工具页变成愿意反复打开的学习空间。')+'</section>';
}
function warmCourse(c,extra,action,kind){
 return '<article class="course-item" data-course="'+c.id+'"><div class="warm-cover-wrap"><img class="warm-cover" src="'+covers[c.cover]+'" alt=""><span class="video-mark">'+(kind==='audio'?'音频':'视频')+'</span><span class="checkbox">✓</span></div><div class="course-content"><div><div class="course-meta-top"><span class="type-label">'+c.type+'</span>'+(extra?'<span class="orange-pill">'+extra+'</span>':'')+'</div><p class="course-title">'+c.title+'</p><p class="course-sub">'+c.sub+'</p></div><div class="course-actions"><div class="tiny-info">'+c.lessons+' 节 · '+c.size+'</div><button class="resume-btn">'+action+'</button></div></div></article>';
}
function warmLessons(){
 return lessons.map(g=>'<div class="lesson-group"><div class="lesson-group-head">'+g.ch+'<span>'+g.count+' 节 · '+g.size+'</span></div>'+g.items.map((it,i)=>'<div class="lesson-row'+(i===1?' checked':'')+'" data-lesson><span class="lesson-play">▶</span><div class="lesson-main"><b>'+it[0]+'</b><small>'+it[1]+' · '+it[2]+'</small></div><span class="tiny-check">✓</span></div>').join('')+'</div>').join('');
}


function taskMain(){
 const c=courses;
 return '<section class="version" data-version="task" data-screen="main"><div class="version-head"><div class="version-title"><strong>版本二 · 轻量任务中心</strong><span>方案 B</span></div><div class="tag">高效率管理</div></div><div class="phone">'+statusBar('task')+'<div class="screen main-screen"><div class="topbar"><button class="back icon-btn" aria-label="返回">‹</button><h2>下载中心</h2><button class="action" data-action="task-edit">管理</button></div><div class="scroll"><div class="task-overview"><div class="overview-head"><div><h3>下载空间</h3><p>离线内容集中管理 · 自动续传已开启</p></div><div class="ring"><span>72%</span></div></div><div class="metrics"><div class="metric hot"><b>1</b><span>进行中</span></div><div class="metric"><b>3</b><span>已完成</span></div><div class="metric"><b>286 <small>MB</small></b><span>已占用</span></div></div></div>'
 +'<div class="segmented"><button class="active" data-task-tab="all">全部</button><button data-task-tab="doing">进行中 1</button><button data-task-tab="done">已完成 3</button></div>'
 +'<div class="task-panel task-doing-panel"><div class="queue-group"><div class="queue-head"><b>正在下载</b><span>自动续传中</span></div><div class="queue-card"><div class="queue-line"><div class="queue-name">职场沟通表达训练营 · 第 15 节</div><span class="queue-pct">72%</span></div><div class="queue-sub"><span>第 15 / 20 节</span><span>2.8 MB/s · 剩 4 分钟</span></div><div class="progress"><i style="width:72%"></i></div><div class="queue-actions"><button data-toast="任务已暂停">暂停</button><button data-toast="已取消该下载任务">取消</button><button class="resume" data-toast="将在 Wi-Fi 环境优先下载">Wi-Fi 优先</button></div></div></div></div>'
 +'<div class="library" id="task-library"><div class="library-head"><h3>已下载</h3><button data-toast="已按最近使用排序">最近下载⌄</button></div><div class="list-tools"><span class="sort">3 门课程 · 98 个文件</span><span class="sort">总大小 286 MB</span></div>'
 +taskCourse(c[0],'orange','继续学习')+taskCourse(c[1],'blue','继续学习')+taskCourse(c[2],'','查看课程')
 +'</div><div class="task-storage"><span><b>286 MB</b> / 2.4 GB 可用空间</span><span>清理建议 ›</span></div><div class="bottom-safe"></div></div>'
 +'<div class="task-editbar"><span>已选择 <b class="task-count">0</b> 项</span><div><button class="task-select-all">全选</button>　<button class="task-delete" data-toast="请选择要删除的课程">删除</button></div></div>'+tabs('task')+'<div class="homebar"></div></div>'
 +'<div class="screen detail-screen"><div class="topbar"><button class="back icon-btn" data-back>‹</button><h2>课程详情</h2><button class="action" data-action="task-detail-edit">管理</button></div><div class="scroll"><div class="detail-header"><div class="detail-top"><img src="'+covers.interview+'" alt=""><div><h3>'+c[0].title+'</h3><p>视频课 · 30 节 · 92.6 MB</p></div></div><div class="detail-toolbar"><span>已缓存全部课节 · 可离线播放</span><button data-toast="正在按顺序播放">全部播放 →</button></div></div>'+taskLessons()+'<div class="task-storage"><span><b>92.6 MB</b> · 课程已完整下载</span><span>删除课程</span></div><div class="bottom-safe"></div></div><div class="task-editbar"><span>已选择 <b class="task-count">0</b> 节</span><div><button class="task-select-all">全选</button>　<button class="task-delete" data-toast="请选择要删除的课节">删除</button></div></div><div class="homebar"></div></div>'
 +'<div class="screen empty-screen"><div class="topbar"><button class="back icon-btn" data-back>‹</button><h2>下载中心</h2><span class="action"></span></div><div class="scroll"><div class="task-empty"><div class="empty-art">⇩</div><h4>暂无下载内容</h4><p>下载课程后，即使没有网络也能继续学习</p><button data-toast="去课程中心看看">浏览课程</button></div></div><div class="task-storage"><span><b>0 B</b> / 2.4 GB 可用空间</span><span>空间充足</span></div><div class="homebar"></div></div><div class="menu-pop"><button data-menu-detail>查看课程目录</button><button data-menu-delete>删除下载</button></div><div class="toast"></div></div>'
 +note('任务','信息优先、状态明确。以任务队列和容量仪表盘为中心，解决课程较多时的查找、暂停、续传和清理效率问题。')+'</section>';
}
function taskCourse(c,color,action){
 return '<div class="course-row" data-course="'+c.id+'"><img class="task-cover" src="'+covers[c.cover]+'" alt=""><div class="course-body"><div class="course-top"><b>'+c.title+'</b><button class="more" data-more>⋯</button></div><div class="course-tags"><span class="'+(c.kind==='audio'?'blue-pill':'orange-pill')+'">'+c.type+'</span><span class="green-pill">已缓存</span></div><div class="course-bottom"><div class="left">'+c.lessons+' 节 · '+c.size+'<br>'+(c.last==='从头开始'?'尚未开始学习':'上次学至 '+c.last)+'</div><button class="task-resume '+color+'">'+action+'</button></div></div><span class="task-check">✓</span></div>';
}
function taskLessons(){
 return lessons.map(g=>'<div class="lesson-block"><div class="lesson-block-head">'+g.ch+'<span>'+g.count+' 节</span></div>'+g.items.map((it,i)=>'<div class="lesson-line'+(i===0?' done':'')+'" data-lesson><span class="status-dot">'+(i===0?'✓':'▶')+'</span><span class="lesson-title">'+it[0]+'</span><span class="duration">'+it[1]+'</span></div>').join('')+'</div>').join('');
}


function edMain(){
 const c=courses;
 return '<section class="version" data-version="editorial" data-screen="main"><div class="version-head"><div class="version-title"><strong>版本三 · 内容杂志感</strong><span>方案 C</span></div><div class="tag">品牌差异化</div></div><div class="phone">'+statusBar('editorial')+'<div class="screen main-screen"><div class="topbar"><button class="back icon-btn" aria-label="返回">‹</button><h2>我的下载</h2><button class="action" data-action="editorial-edit">管理</button></div><div class="scroll"><div class="editorial-copy"><div class="kicker">MY OFFLINE LIBRARY</div><h3>把好课，留在身边。</h3><p>离线收藏不是文件，是下一次专注学习的入口。</p></div><div class="hero-article"><img src="'+covers.interview+'" alt=""><div class="hero-content"><div class="hero-badge">本期推荐 · 继续学习</div><h4>'+c[0].title+'</h4><p>'+c[0].sub+'<br>第 8 节 · 共 30 节 · 92.6 MB</p><button class="hero-button" data-toast="将从第 8 节继续播放">继续学习 →</button></div></div><div class="ed-section"><div class="ed-header"><h3>下载中</h3><span>1 个任务 · 自动续传</span></div><div class="ed-task"><img class="task-cover" src="'+covers.communication+'" alt=""><div class="task-copy"><div class="name">职场沟通表达训练营 · 第 15 节</div><div class="meta"><span>72% · 2.8 MB/s</span><span>还剩 4 分钟</span></div><div class="progress"><i style="width:72%"></i></div></div><button class="icon-btn" data-toast="下载已暂停">Ⅱ</button></div><div class="ed-header"><h3>我的离线书架</h3><span>3 门课程 · 286 MB</span></div><div class="ed-toolbar"><span>按最近学习排序</span><button data-toast="已切换为按下载时间排序">切换排序</button></div><div class="ed-list">'+edCourse(c[0],'视频课 · 最近学习','继续学习')+edCourse(c[1],'音频课 · 通勤听','继续听')+edCourse(c[2],'视频课 · 未开始','打开课程')+'</div><div class="ed-storage"><span><b>存储空间</b>　286 MB 已用</span><span>管理空间 →</span></div></div><div class="editorial-bottom"></div></div><div class="ed-editbar"><span class="selected-count">已选 0 门课程</span><div><button class="select-all">全选</button>　<button class="delete" data-toast="请选择要删除的课程">删除</button></div></div>'+tabs('editorial')+'<div class="homebar"></div></div>'
 +'<div class="screen detail-screen"><div class="topbar"><button class="back icon-btn" data-back>‹</button><h2>课程目录</h2><button class="action" data-action="editorial-detail-edit">管理</button></div><div class="scroll"><div class="editorial-copy"><div class="kicker">OFFLINE COURSE · 30 LESSONS</div></div><div class="detail-cover-row"><img src="'+covers.interview+'" alt=""><div><h3>'+c[0].title+'</h3><p>'+c[0].sub+'<br>30 节 · 92.6 MB · 全部可离线播放</p></div></div>'+edLessons()+'<div class="ed-storage"><span><b>本课程已完整下载</b>　92.6 MB</span><span>离线播放 →</span></div><div class="editorial-bottom"></div></div><div class="ed-editbar"><span class="selected-count">已选 0 节</span><div><button class="select-all">全选</button>　<button class="delete" data-toast="请选择要删除的课节">删除</button></div></div><div class="homebar"></div></div>'
 +'<div class="screen empty-screen"><div class="topbar"><button class="back icon-btn" data-back>‹</button><h2>我的下载</h2><span class="action"></span></div><div class="scroll"><div class="editorial-copy"><div class="kicker">MY OFFLINE LIBRARY</div></div><div class="ed-empty"><div class="empty-art">⌑</div><h4>书架还空着</h4><p>挑一门喜欢的课，留在这里<br>在需要的时候，随时打开。</p><button data-toast="去课程中心看看">去挑一门课 →</button></div></div><div class="homebar"></div></div><div class="toast"></div></div>'
 +note('书架','以“离线书架”取代“下载列表”，用杂志化排版和内容推荐强化品牌辨识度，适合更重视课程内容氛围的年轻求职用户。')+'</section>';
}
function edCourse(c,kicker,action){
 return '<article class="ed-course" data-course="'+c.id+'"><img class="ed-cover" src="'+covers[c.cover]+'" alt=""><div class="ed-course-body"><div class="mini-kicker">'+kicker+'</div><h4>'+c.title+'</h4><div class="ed-meta"><span>'+c.lessons+' 节 · '+c.size+'</span><span>'+c.last+'</span></div><span class="mini-link">'+action+'　→</span></div><span class="ed-checkbox">✓</span></article>';
}
function edLessons(){
 return lessons.map((g,gi)=>'<div class="chapter"><div class="chapter-head">'+g.ch+'<span>'+g.count+' 节 / '+g.size+'</span></div>'+g.items.map((it,i)=>'<div class="chapter-row" data-lesson><span class="num">'+String(gi*10+i+1).padStart(2,'0')+'</span><span class="title">'+it[0].replace(/^\d+\s*/,'')+'</span><span class="play-mini">▶</span></div>').join('')+'</div>').join('');
}


function bind(root){
 root.addEventListener('click',e=>{
  const el=e.target.closest('[data-screen-jump],[data-open],[data-course],[data-back],[data-action],[data-lesson],[data-toast],[data-task-tab],[data-more],[data-menu-delete],[data-menu-detail],[data-select-all],.select-all,.task-select-all');
  if(!el)return;
  if(el.dataset.screenJump){root.dataset.screen=el.dataset.screenJump;$$(root,'[data-screen-jump]').forEach(b=>b.classList.toggle('active',b===el));return}
  if(el.dataset.open){openCourse(root);return}
  if(el.dataset.course){if(root.classList.contains('edit-mode')){el.classList.toggle('selected');updateCount(root);return}openCourse(root);return}
  if(el.dataset.back){if(root.classList.contains('edit-mode')){setEdit(root,false);return}root.dataset.screen='main';return}
  if(el.dataset.action){setEdit(root,!root.classList.contains('edit-mode'));return}
  if(el.dataset.lesson){if(root.classList.contains('edit-mode')){el.classList.toggle('checked');updateCount(root,true);return}toast(root,'正在离线播放：'+(el.querySelector('b,.lesson-title,.title')?.textContent||'该课节'));return}
  if(el.dataset.taskTab){filterTask(root,el.dataset.taskTab);return}
  if(el.dataset.more){const pop=root.querySelector('.menu-pop');pop.classList.toggle('show');e.stopPropagation();return}
  if(el.dataset.menuDetail){root.querySelector('.menu-pop').classList.remove('show');openCourse(root);return}
  if(el.dataset.menuDelete){root.querySelector('.menu-pop').classList.remove('show');toast(root,'已从下载列表移除以释放空间');return}
  if(el.dataset.toast){toast(root,el.dataset.toast);return}
  if(el.classList.contains('select-all')||el.classList.contains('task-select-all')){selectAll(root);return}
 });
 root.addEventListener('click',e=>{
  const chip=e.target.closest('.filter-chip');if(chip){const row=chip.parentElement;$$(row,'.filter-chip').forEach(x=>x.classList.toggle('active',x===chip));toast(root,'已筛选：'+chip.textContent);}
 });
}
function openCourse(root){root.dataset.screen='detail';toast(root,'已打开离线课程目录')}
function setEdit(root,on){root.classList.toggle('edit-mode',on);if(!on){$$(root,'.selected').forEach(x=>x.classList.remove('selected'));$$(root,'.checked').forEach(x=>{if(x.matches('.lesson-row'))x.classList.remove('checked')})}updateCount(root)}
function updateCount(root,isLesson){const n=$$(root,isLesson?'.lesson-row.checked':'.course-item.selected,.course-row.selected,.ed-course.selected').length;const a=root.querySelector('.selected-count');if(a)a.textContent='已选 '+n+(isLesson?' 节':root.dataset.version==='editorial'?' 门课程':' 项');const b=root.querySelector('.task-count');if(b)b.textContent=n}
function selectAll(root){if(root.dataset.screen==='detail'){$$(root,'.lesson-row').forEach(x=>x.classList.add('checked'));updateCount(root,true)}else{$$(root,'.course-item,.course-row,.ed-course').forEach(x=>x.classList.add('selected'));updateCount(root)}toast(root,'已全选当前列表')}
function filterTask(root,tab){$$(root,'.segmented button').forEach(x=>x.classList.toggle('active',x.dataset.taskTab===tab));const q=root.querySelector('.task-doing-panel');const lib=root.querySelector('#task-library');if(q)q.hidden=tab==='done';if(lib)lib.hidden=tab==='doing';}
function toast(root,text){const t=root.querySelector('.toast');if(!t)return;t.textContent=text;t.classList.add('show');clearTimeout(root._t);root._t=setTimeout(()=>t.classList.remove('show'),1700)}
const $$=(root,sel)=>Array.from(root.querySelectorAll(sel));


document.getElementById('versions').innerHTML=warmMain()+taskMain()+edMain();
$$(document,'.version').forEach(bind);
$$(document,'[data-screen-jump]').forEach(b=>b.addEventListener('click',()=>{const root=b.closest('.version');$$(root,'[data-screen-jump]').forEach(x=>x.classList.toggle('active',x===b))}));
