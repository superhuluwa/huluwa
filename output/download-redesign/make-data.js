const fs=require('fs');
const data=`
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
module.exports={courses,lessons};
`;
fs.writeFileSync('data.js',data.trim());
