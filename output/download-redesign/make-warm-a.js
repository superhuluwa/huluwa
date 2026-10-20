const fs=require('fs');const p='styles-warm-a.css';fs.writeFileSync(p,String.raw`
[data-version="warm"] .phone{background:#fffaf7}
[data-version="warm"] .statusbar,[data-version="warm"] .topbar{background:#fffaf7}
[data-version="warm"] .hero{padding:4px 16px 15px;background:linear-gradient(180deg,#fffaf7 0%,#fff6f1 65%,#fffaf7 100%)}
[data-version="warm"] .hello{font-size:10px;color:#9b8580;margin:0 0 3px}
[data-version="warm"] .hero-title{font-size:22px;font-weight:850;letter-spacing:-.05em;line-height:1.25;color:#2a2421;margin:0}
[data-version="warm"] .hero-title em{font-style:normal;color:#ff6000}
[data-version="warm"] .hero-sub{font-size:10px;color:#9d8f8b;margin:6px 0 0}
[data-version="warm"] .hero-card{margin:0 16px;position:relative;overflow:hidden;border-radius:17px;background:#252122;color:#fff;box-shadow:0 8px 18px rgba(76,42,24,.14)}
[data-version="warm"] .hero-card-bg{position:absolute;right:-30px;top:-44px;width:145px;height:145px;border-radius:50%;background:radial-gradient(circle,#ff6e26 0%,#a33b0d 50%,rgba(71,30,18,.1) 72%);opacity:.77}
[data-version="warm"] .hero-card-bg:after{content:"";position:absolute;left:22px;top:46px;width:70px;height:70px;border-radius:50%;border:1px solid rgba(255,210,187,.32)}
[data-version="warm"] .hero-card-inner{position:relative;z-index:1;padding:15px 15px 13px}
[data-version="warm"] .hero-topline{display:flex;justify-content:space-between;align-items:center;font-size:9px;color:#ffb48b}
[data-version="warm"] .hero-tag{font-size:9px;padding:4px 7px;border-radius:99px;background:rgba(255,255,255,.12);color:#ffd5bd}
[data-version="warm"] .hero-course{display:flex;gap:11px;align-items:center;margin-top:12px;cursor:pointer}
[data-version="warm"] .hero-cover{width:52px;height:68px;border-radius:8px;object-fit:cover;box-shadow:0 4px 9px rgba(0,0,0,.2)}
[data-version="warm"] .hero-course-name{font-size:13px;font-weight:700;line-height:1.4;margin:0 0 5px;max-width:190px}
[data-version="warm"] .hero-course-meta{font-size:9px;color:#bdb1aa}
[data-version="warm"] .hero-progress{align-items:center;display:flex;gap:8px;margin-top:12px}
[data-version="warm"] .hero-progress .progress{flex:1;background:rgba(255,255,255,.15)}
[data-version="warm"] .hero-progress .progress>i{background:#ff8f52}
[data-version="warm"] .hero-progress span{font-size:9px;color:#ffb48b;min-width:40px;text-align:right}
[data-version="warm"] .continue-row{display:flex;gap:8px;margin-top:12px}
[data-version="warm"] .continue-row button{border-radius:8px;padding:9px 12px;font-size:10px;font-weight:700}
[data-version="warm"] .continue-main{background:#ff6000;color:#fff;flex:1}
[data-version="warm"] .continue-secondary{background:rgba(255,255,255,.1);color:#f7dad0}
[data-version="warm"] .warm-section{padding:0 16px}
[data-version="warm"] .section-head{display:flex;align-items:center;justify-content:space-between;margin:18px 0 9px}
[data-version="warm"] .section-head h3{font-size:14px;margin:0;letter-spacing:-.02em}
[data-version="warm"] .section-head .count{font-size:10px;color:#a09b98}
`); 
