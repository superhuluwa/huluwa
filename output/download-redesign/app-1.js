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
