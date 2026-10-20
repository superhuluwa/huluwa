const fs=require('fs');const p='styles-task-c.css';fs.writeFileSync(p,String.raw`
[data-version="task"] .task-empty{padding:55px 22px;text-align:center;color:#9aa0a6}
[data-version="task"] .task-empty .empty-art{background:#f1f4f7;color:#9da9b4;font-size:30px;width:74px;height:74px}
[data-version="task"] .task-empty h4{font-size:14px;color:#4a5158;margin-bottom:5px}
[data-version="task"] .task-empty p{font-size:10px;margin-bottom:12px}
[data-version="task"] .task-empty button{background:#ff6000;font-size:10px;color:#fff}
[data-version="task"] .task-storage{margin:11px 16px 3px;padding:10px;border-radius:9px;background:#e9ecef;color:#899099;font-size:9px;display:flex;justify-content:space-between}
[data-version="task"] .task-storage b{color:#515960}
[data-version="task"] .detail-header{background:#fff;padding:0 16px 15px;border-bottom:1px solid #edf0f2}
[data-version="task"] .detail-top{display:flex;gap:11px;align-items:center}
[data-version="task"] .detail-top img{width:57px;height:73px;border-radius:7px;object-fit:cover}
[data-version="task"] .detail-top h3{font-size:14px;line-height:1.4;margin:0 0 6px;letter-spacing:-.03em}
[data-version="task"] .detail-top p{font-size:9px;color:#a2a7ad;margin:0}
[data-version="task"] .detail-toolbar{display:flex;justify-content:space-between;align-items:center;margin-top:12px;font-size:9px;color:#90979e}
[data-version="task"] .detail-toolbar button{background:none;font-size:10px;color:#ff6000;font-weight:700}
[data-version="task"] .lesson-block{margin:12px 16px;background:#fff;border-radius:10px;border:1px solid #edf0f2;overflow:hidden}
[data-version="task"] .lesson-block-head{display:flex;justify-content:space-between;align-items:center;padding:10px 11px;border-bottom:1px solid #f0f2f3;font-size:10px;font-weight:800}
[data-version="task"] .lesson-block-head span{font-size:9px;font-weight:400;color:#9ca3aa}
[data-version="task"] .lesson-line{display:flex;align-items:center;gap:8px;padding:10px 11px;border-bottom:1px solid #f4f5f6;font-size:10px;cursor:pointer}
[data-version="task"] .lesson-line:last-child{border:0}
[data-version="task"] .lesson-line .status-dot{width:17px;height:17px;border-radius:5px;background:#f0f3f5;display:flex;align-items:center;justify-content:center;font-size:9px;color:#84909a;flex:0 0 17px}
[data-version="task"] .lesson-line.done .status-dot{background:#eaf8f1;color:#188957}
[data-version="task"] .lesson-line .lesson-title{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
[data-version="task"] .lesson-line .duration{font-size:9px;color:#a2a7ad}
`); 
