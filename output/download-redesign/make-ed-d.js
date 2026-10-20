const fs=require('fs');const p='styles-ed-d.css';fs.writeFileSync(p,String.raw`
[data-version="editorial"] .detail-cover-row{display:flex;gap:12px;padding:3px 16px 14px}
[data-version="editorial"] .detail-cover-row img{width:75px;height:98px;border-radius:2px;object-fit:cover;filter:saturate(.8)}
[data-version="editorial"] .detail-cover-row h3{font-family:"Songti SC","STSong","Noto Serif SC",serif;font-size:19px;line-height:1.4;margin:0 0 4px}
[data-version="editorial"] .detail-cover-row p{font-size:9px;color:#8d857a;line-height:1.65;margin:0}
[data-version="editorial"] .chapter{margin:0 16px 13px;border-top:2px solid #34302a;background:transparent}
[data-version="editorial"] .chapter-head{display:flex;justify-content:space-between;align-items:center;padding:9px 0 7px;font-family:"Songti SC","STSong","Noto Serif SC",serif;font-size:13px;font-weight:700;border-bottom:1px solid #dcd6cc}
[data-version="editorial"] .chapter-head span{font-family:"PingFang SC","Microsoft YaHei",sans-serif;font-size:9px;color:#978f85;font-weight:400}
[data-version="editorial"] .chapter-row{display:flex;gap:8px;align-items:center;padding:9px 0;border-bottom:1px solid #e2ddd5;font-size:10px;cursor:pointer}
[data-version="editorial"] .chapter-row .num{font-family:"Songti SC","STSong",serif;color:#a77a62;width:18px;flex:0 0 18px;font-size:11px}
[data-version="editorial"] .chapter-row .title{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
[data-version="editorial"] .chapter-row .play-mini{color:#a16f58;font-size:10px}
[data-version="editorial"] .editorial-bottom{height:14px}
[data-version="editorial"] .tabbar{background:#f4f1eb;border-color:#ded8cf}
[data-version="editorial"] .tabbar .tab.active{color:#9a5e3c}
`); 
