const fs=require('fs');const p='styles-task-d.css';fs.writeFileSync(p,String.raw`
[data-version="task"] .task-sort-sheet{background:#f4f6f8;padding:14px 16px}
[data-version="task"] .task-sort-sheet .sheet-title{font-size:14px;font-weight:800;margin:0 0 12px}
[data-version="task"] .task-sort-sheet .sort-option{display:flex;justify-content:space-between;align-items:center;padding:12px 0;border-bottom:1px solid #edf0f2;font-size:11px;cursor:pointer;background:none;width:100%;text-align:left}
[data-version="task"] .task-sort-sheet .sort-option.active{color:#ff6000;font-weight:700}
[data-version="task"] .task-doing-panel[hidden],#task-library[hidden]{display:none}
[data-version="task"] .task-overview[hidden]{display:none}
`); 
