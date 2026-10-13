import{e as G,o,f as n,g as i,h as N,i as h,n as P,F as W,E as w,I as ee,l as D,al as te,am as se,an as ie,ao as re,ap as pe,a as O,c as y,b as I,u as oe,aq as E,j as ge,k as fe,ar as he,as as X,Y as le,Z as j,R as k,U as g,T as p,W as b,a2 as B,$ as z,y as A,ak as ne,aa as ae,S as de}from"./index-DfCfUgVf.js";import{D as ce}from"./DataTable-B-emD_eH.js";import{S as F}from"./Spin-BUYNpv1p.js";import{T as me,a as ye}from"./Tabs-GmPS5et3.js";import{f as q,I as ve}from"./Input-CICm00Gu.js";import{d as be,a as _e,R as ke,e as xe,f as we,g as Se}from"./hls-BEwkVreR.js";import{S as Ce}from"./Select-BxRvcaiF.js";import{F as Pe,a as $e}from"./FormItem-Det-31zC.js";import{I as ze}from"./InputNumber-D5OnhaiN.js";import{_ as V}from"./_plugin-vue_export-helper-DlAUqK2U.js";import{n as Ne,o as Be}from"./chat-tn-fcz_7.js";import"./create-B-uH1d1w.js";import"./use-compitable-7tceJLWJ.js";import"./Dropdown-Cqg_bv0o.js";import"./CheckboxGroup-DboLnXP4.js";import"./Tooltip-DG5FnygA.js";import"./get-slot-VaplJ9hC.js";import"./Add-DRs9pgw6.js";const De=["id"],Te=["stop-color"],Re=["stop-color"],Ie=["viewBox"],Ae=["d","stroke-width"],We=["d","stroke-width"],Le={success:(o(),w(re)),error:(o(),w(ie)),warning:(o(),w(se)),info:(o(),w(te))};var Me=G({name:"ProgressCircle",props:{clsPrefix:{type:String,required:!0},status:{type:String,required:!0},strokeWidth:{type:Number,required:!0},fillColor:[String,Object],railColor:String,railStyle:[String,Object],percentage:{type:Number,default:0},offsetDegree:{type:Number,default:0},showIndicator:{type:Boolean,required:!0},indicatorTextColor:String,unit:String,viewBoxWidth:{type:Number,required:!0},gapDegree:{type:Number,required:!0},gapOffsetDegree:{type:Number,default:0}},setup(e,{slots:t}){const x=D(()=>{const l="gradient",{fillColor:a}=e;return typeof a=="object"?`${l}-${pe(JSON.stringify(a))}`:l});function S(l,a,c,f){const{gapDegree:m,viewBoxWidth:v,strokeWidth:_}=e,s=50,C=0,d=s,u=0,L=100,T=50+_/2,$=`M ${T},${T} m ${C},${d}
      a ${s},${s} 0 1 1 ${u},-100
      a ${s},${s} 0 1 1 0,${L}`,R=Math.PI*2*s;return{pathString:$,pathStyle:{stroke:f==="rail"?c:typeof e.fillColor=="object"?`url(#${x.value})`:c,strokeDasharray:`${Math.min(l,100)/100*(R-m)}px ${v*8}px`,strokeDashoffset:`-${m/2}px`,transformOrigin:a?"center":void 0,transform:a?`rotate(${a}deg)`:void 0}}}const r=()=>{const l=typeof e.fillColor=="object",a=l?e.fillColor.stops[0]:"",c=l?e.fillColor.stops[1]:"";return l&&(o(),n("defs",null,[i("linearGradient",{id:x.value,x1:"0%",y1:"100%",x2:"100%",y2:"0%"},[i("stop",{offset:"0%","stop-color":a},null,8,Te),i("stop",{offset:"100%","stop-color":c},null,8,Re)],8,De)]))};return()=>{const{fillColor:l,railColor:a,strokeWidth:c,offsetDegree:f,status:m,percentage:v,showIndicator:_,indicatorTextColor:s,unit:C,gapOffsetDegree:d,clsPrefix:u}=e,{pathString:L,pathStyle:T}=S(100,0,a,"rail"),{pathString:$,pathStyle:R}=S(v,f,l,"fill"),M=100+c;return o(),n("div",{class:h(`${u}-progress-content`),role:"none"},[i("div",{class:h(`${u}-progress-graph`),"aria-hidden":!0},[i("div",{class:h(`${u}-progress-graph-circle`),style:N({transform:d?`rotate(${d}deg)`:void 0})},[(o(),n("svg",{viewBox:`0 0 ${M} ${M}`},[P(()=>r()),i("g",null,[i("path",{class:h(`${u}-progress-graph-circle-rail`),d:L,"stroke-width":c,"stroke-linecap":"round",fill:"none",style:N(T)},null,14,Ae)]),i("g",null,[i("path",{class:h([`${u}-progress-graph-circle-fill`,v===0&&`${u}-progress-graph-circle-fill--empty`]),d:$,"stroke-width":c,"stroke-linecap":"round",fill:"none",style:N(R)},null,14,We)])],8,Ie))],6)],2),_?(o(),n("div",{key:0},[t.default?(o(),n("div",{key:0,class:h(`${u}-progress-custom-content`),role:"none"},[P(()=>t.default())],2)):(o(),n(W,{key:1},[m!=="default"?(o(),n("div",{key:0,class:h(`${u}-progress-icon`),"aria-hidden":!0},[(o(),w(ee,{clsPrefix:u},{default:()=>Le[m]},1032,["clsPrefix"]))],2)):(o(),n("div",{key:1,class:h(`${u}-progress-text`),style:N({color:s}),role:"none"},[i("span",{class:h(`${u}-progress-text__percentage`)},[P(()=>v)],2),i("span",{class:h(`${u}-progress-text__unit`)},[P(()=>C)],2)],6))],64))])):P(()=>null)],2)}}});const qe={success:(o(),w(re)),error:(o(),w(ie)),warning:(o(),w(se)),info:(o(),w(te))};var He=G({name:"ProgressLine",props:{clsPrefix:{type:String,required:!0},percentage:{type:Number,default:0},railColor:String,railStyle:[String,Object],fillColor:[String,Object],status:{type:String,required:!0},indicatorPlacement:{type:String,required:!0},indicatorTextColor:String,unit:{type:String,default:"%"},processing:{type:Boolean,required:!0},showIndicator:{type:Boolean,required:!0},height:[String,Number],railBorderRadius:[String,Number],fillBorderRadius:[String,Number]},setup(e,{slots:t}){const x=D(()=>q(e.height)),S=D(()=>typeof e.fillColor=="object"?`linear-gradient(to right, ${e.fillColor?.stops[0]} , ${e.fillColor?.stops[1]})`:e.fillColor),r=D(()=>e.railBorderRadius!==void 0?q(e.railBorderRadius):e.height!==void 0?q(e.height,{c:.5}):""),l=D(()=>e.fillBorderRadius!==void 0?q(e.fillBorderRadius):e.railBorderRadius!==void 0?q(e.railBorderRadius):e.height!==void 0?q(e.height,{c:.5}):"");return()=>{const{indicatorPlacement:a,railColor:c,railStyle:f,percentage:m,unit:v,indicatorTextColor:_,status:s,showIndicator:C,processing:d,clsPrefix:u}=e;return o(),n("div",{class:h(`${u}-progress-content`),role:"none"},[i("div",{class:h(`${u}-progress-graph`),"aria-hidden":!0},[i("div",{class:h([`${u}-progress-graph-line`,{[`${u}-progress-graph-line--indicator-${a}`]:!0}])},[i("div",{class:h(`${u}-progress-graph-line-rail`),style:N([{backgroundColor:c,height:x.value,borderRadius:r.value},f])},[i("div",{class:h([`${u}-progress-graph-line-fill`,d&&`${u}-progress-graph-line-fill--processing`]),style:N({maxWidth:`${e.percentage}%`,background:S.value,height:x.value,lineHeight:x.value,borderRadius:l.value})},[a==="inside"?(o(),n("div",{key:0,class:h(`${u}-progress-graph-line-indicator`),style:N({color:_})},[t.default?(o(),n(W,{key:0},[P(()=>t.default())],64)):(o(),n(W,{key:1},[P(()=>`${m}${v}`)],64))],6)):P(()=>null)],6)],6)],2)],2),C&&a==="outside"?(o(),n("div",{key:0},[t.default?(o(),n("div",{key:0,class:h(`${u}-progress-custom-content`),style:N({color:_}),role:"none"},[P(()=>t.default())],6)):(o(),n(W,{key:1},[s==="default"?(o(),n("div",{key:0,role:"none",class:h(`${u}-progress-icon ${u}-progress-icon--as-text`),style:N({color:_})},[P(()=>m),P(()=>v)],6)):(o(),n("div",{key:1,class:h(`${u}-progress-icon`),"aria-hidden":!0},[(o(),w(ee,{clsPrefix:u},{default:()=>qe[s]},1032,["clsPrefix"]))],2))],64))])):P(()=>null)],2)}}});const je=["id"],Oe=["stop-color"],Ge=["stop-color"],Ue=["d","stroke-width"],Ee=["d","stroke-width"],Fe=["viewBox"];function Y(e,t,x=100){return`m ${x/2} ${x/2-e} a ${e} ${e} 0 1 1 0 ${2*e} a ${e} ${e} 0 1 1 0 -${2*e}`}var Ve=G({name:"ProgressMultipleCircle",props:{clsPrefix:{type:String,required:!0},viewBoxWidth:{type:Number,required:!0},percentage:{type:Array,default:[0]},strokeWidth:{type:Number,required:!0},circleGap:{type:Number,required:!0},showIndicator:{type:Boolean,required:!0},fillColor:{type:Array,default:()=>[]},railColor:{type:Array,default:()=>[]},railStyle:{type:Array,default:()=>[]}},setup(e,{slots:t}){const x=D(()=>e.percentage.map((r,l)=>`${Math.PI*r/100*(e.viewBoxWidth/2-e.strokeWidth/2*(1+2*l)-e.circleGap*l)*2}, ${e.viewBoxWidth*8}`)),S=(r,l)=>{const a=e.fillColor[l],c=typeof a=="object"?a.stops[0]:"",f=typeof a=="object"?a.stops[1]:"";return typeof e.fillColor[l]=="object"&&(o(),n("linearGradient",{id:`gradient-${l}`,x1:"100%",y1:"0%",x2:"0%",y2:"100%"},[i("stop",{offset:"0%","stop-color":c},null,8,Oe),i("stop",{offset:"100%","stop-color":f},null,8,Ge)],8,je))};return()=>{const{viewBoxWidth:r,strokeWidth:l,circleGap:a,showIndicator:c,fillColor:f,railColor:m,railStyle:v,percentage:_,clsPrefix:s}=e;return o(),n("div",{class:h(`${s}-progress-content`),role:"none"},[i("div",{class:h(`${s}-progress-graph`),"aria-hidden":!0},[i("div",{class:h(`${s}-progress-graph-circle`)},[(o(),n("svg",{viewBox:`0 0 ${r} ${r}`},[i("defs",null,[P(()=>_.map((C,d)=>S(C,d)))]),P(()=>_.map((C,d)=>(o(),n("g",{key:d},[i("path",{class:h(`${s}-progress-graph-circle-rail`),d:Y(r/2-l/2*(1+2*d)-a*d,l,r),"stroke-width":l,"stroke-linecap":"round",fill:"none",style:N([{strokeDashoffset:0,stroke:m[d]},v[d]])},null,14,Ue),i("path",{class:h([`${s}-progress-graph-circle-fill`,C===0&&`${s}-progress-graph-circle-fill--empty`]),d:Y(r/2-l/2*(1+2*d)-a*d,l,r),"stroke-width":l,"stroke-linecap":"round",fill:"none",style:N({strokeDasharray:x.value[d],strokeDashoffset:0,stroke:typeof f[d]=="object"?`url(#gradient-${d})`:f[d]})},null,14,Ee)]))))],8,Fe))],2)],2),c&&t.default?(o(),n("div",{key:0},[i("div",{class:h(`${s}-progress-text`)},[P(()=>t.default())],2)])):P(()=>null)],2)}}}),Xe=O([y("progress",{display:"inline-block"},[y("progress-icon",`
 color: var(--n-icon-color);
 transition: color .3s var(--n-bezier);
 `),I("line",`
 width: 100%;
 display: block;
 `,[y("progress-content",`
 display: flex;
 align-items: center;
 `,[y("progress-graph",{flex:1})]),y("progress-custom-content",{marginLeft:"14px"}),y("progress-icon",`
 width: 30px;
 padding-left: 14px;
 height: var(--n-icon-size-line);
 line-height: var(--n-icon-size-line);
 font-size: var(--n-icon-size-line);
 `,[I("as-text",`
 color: var(--n-text-color-line-outer);
 text-align: center;
 width: 40px;
 font-size: var(--n-font-size);
 padding-left: 4px;
 transition: color .3s var(--n-bezier);
 `)])]),I("circle, dashboard",{width:"120px"},[y("progress-custom-content",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 justify-content: center;
 `),y("progress-text",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 color: inherit;
 font-size: var(--n-font-size-circle);
 color: var(--n-text-color-circle);
 font-weight: var(--n-font-weight-circle);
 transition: color .3s var(--n-bezier);
 white-space: nowrap;
 `),y("progress-icon",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 color: var(--n-icon-color);
 font-size: var(--n-icon-size-circle);
 `)]),I("multiple-circle",`
 width: 200px;
 color: inherit;
 `,[y("progress-text",`
 font-weight: var(--n-font-weight-circle);
 color: var(--n-text-color-circle);
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 justify-content: center;
 transition: color .3s var(--n-bezier);
 `)]),y("progress-content",{position:"relative"}),y("progress-graph",{position:"relative"},[y("progress-graph-circle",[O("svg",{verticalAlign:"bottom"}),y("progress-graph-circle-fill",`
 stroke: var(--n-fill-color);
 transition:
 opacity .3s var(--n-bezier),
 stroke .3s var(--n-bezier),
 stroke-dasharray .3s var(--n-bezier);
 `,[I("empty",{opacity:0})]),y("progress-graph-circle-rail",`
 transition: stroke .3s var(--n-bezier);
 overflow: hidden;
 stroke: var(--n-rail-color);
 `)]),y("progress-graph-line",[I("indicator-inside",[y("progress-graph-line-rail",`
 height: 16px;
 line-height: 16px;
 border-radius: 10px;
 `,[y("progress-graph-line-fill",`
 height: inherit;
 border-radius: 10px;
 `),y("progress-graph-line-indicator",`
 background: #0000;
 white-space: nowrap;
 text-align: right;
 margin-left: 14px;
 margin-right: 14px;
 height: inherit;
 font-size: 12px;
 color: var(--n-text-color-line-inner);
 transition: color .3s var(--n-bezier);
 `)])]),I("indicator-inside-label",`
 height: 16px;
 display: flex;
 align-items: center;
 `,[y("progress-graph-line-rail",`
 flex: 1;
 transition: background-color .3s var(--n-bezier);
 `),y("progress-graph-line-indicator",`
 background: var(--n-fill-color);
 font-size: 12px;
 transform: translateZ(0);
 display: flex;
 vertical-align: middle;
 height: 16px;
 line-height: 16px;
 padding: 0 10px;
 border-radius: 10px;
 position: absolute;
 white-space: nowrap;
 color: var(--n-text-color-line-inner);
 transition:
 right .2s var(--n-bezier),
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 `)]),y("progress-graph-line-rail",`
 position: relative;
 overflow: hidden;
 height: var(--n-rail-height);
 border-radius: 5px;
 background-color: var(--n-rail-color);
 transition: background-color .3s var(--n-bezier);
 `,[y("progress-graph-line-fill",`
 background: var(--n-fill-color);
 position: relative;
 border-radius: 5px;
 height: inherit;
 width: 100%;
 max-width: 0%;
 transition:
 background-color .3s var(--n-bezier),
 max-width .2s var(--n-bezier);
 `,[I("processing",[O("&::after",`
 content: "";
 background-image: var(--n-line-bg-processing);
 animation: progress-processing-animation 2s var(--n-bezier) infinite;
 `)])])])])])]),O("@keyframes progress-processing-animation",`
 0% {
 position: absolute;
 left: 0;
 top: 0;
 bottom: 0;
 right: 100%;
 opacity: 1;
 }
 66% {
 position: absolute;
 left: 0;
 top: 0;
 bottom: 0;
 right: 0;
 opacity: 0;
 }
 100% {
 position: absolute;
 left: 0;
 top: 0;
 bottom: 0;
 right: 0;
 opacity: 0;
 }
 `)]);const Ye=["aria-valuenow","role"],Ke={...oe.props,processing:Boolean,type:{type:String,default:"line"},gapDegree:Number,gapOffsetDegree:Number,status:{type:String,default:"default"},railColor:[String,Array],railStyle:[String,Array],color:[String,Array,Object],viewBoxWidth:{type:Number,default:100},strokeWidth:{type:Number,default:7},percentage:[Number,Array],unit:{type:String,default:"%"},showIndicator:{type:Boolean,default:!0},indicatorPosition:{type:String,default:"outside"},indicatorPlacement:{type:String,default:"outside"},indicatorTextColor:String,circleGap:{type:Number,default:1},height:Number,borderRadius:[String,Number],fillBorderRadius:[String,Number],offsetDegree:Number};var K=G({name:"Progress",props:Ke,setup(e){const t=D(()=>e.indicatorPlacement||e.indicatorPosition),x=D(()=>{if(e.gapDegree||e.gapDegree===0)return e.gapDegree;if(e.type==="dashboard")return 75}),{mergedClsPrefixRef:S,inlineThemeDisabled:r}=ge(e),l=oe("Progress","-progress",Xe,he,e,S),a=D(()=>{const{status:f}=e,{common:{cubicBezierEaseInOut:m},self:{fontSize:v,fontSizeCircle:_,railColor:s,railHeight:C,iconSizeCircle:d,iconSizeLine:u,textColorCircle:L,textColorLineInner:T,textColorLineOuter:$,lineBgProcessing:R,fontWeightCircle:M,[X("iconColor",f)]:U,[X("fillColor",f)]:H}}=l.value;return{"--n-bezier":m,"--n-fill-color":H,"--n-font-size":v,"--n-font-size-circle":_,"--n-font-weight-circle":M,"--n-icon-color":U,"--n-icon-size-circle":d,"--n-icon-size-line":u,"--n-line-bg-processing":R,"--n-rail-color":s,"--n-rail-height":C,"--n-text-color-circle":L,"--n-text-color-line-inner":T,"--n-text-color-line-outer":$}}),c=r?fe("progress",D(()=>e.status[0]),a,e):void 0;return{mergedClsPrefix:S,mergedIndicatorPlacement:t,gapDeg:x,cssVars:r?void 0:a,themeClass:c?.themeClass,onRender:c?.onRender}},render(){const{type:e,cssVars:t,indicatorTextColor:x,showIndicator:S,status:r,railColor:l,railStyle:a,color:c,percentage:f,viewBoxWidth:m,strokeWidth:v,mergedIndicatorPlacement:_,unit:s,borderRadius:C,fillBorderRadius:d,height:u,processing:L,circleGap:T,mergedClsPrefix:$,gapDeg:R,gapOffsetDegree:M,themeClass:U,$slots:H,onRender:ue}=this;return ue?.(),o(),n("div",{class:h([U,`${$}-progress`,`${$}-progress--${e}`,`${$}-progress--${r}`]),style:N(t),"aria-valuemax":100,"aria-valuemin":0,"aria-valuenow":f,role:e==="circle"||e==="line"||e==="dashboard"?"progressbar":"none"},[e==="circle"||e==="dashboard"?(o(),w(Me,{key:0,clsPrefix:$,status:r,showIndicator:S,indicatorTextColor:x,railColor:l,fillColor:c,railStyle:a,offsetDegree:this.offsetDegree,percentage:f,viewBoxWidth:m,strokeWidth:v,gapDegree:R===void 0?e==="dashboard"?75:0:R,gapOffsetDegree:M,unit:s},E(H),1032,["clsPrefix","status","showIndicator","indicatorTextColor","railColor","fillColor","railStyle","offsetDegree","percentage","viewBoxWidth","strokeWidth","gapDegree","gapOffsetDegree","unit"])):(o(),n(W,{key:1},[e==="line"?(o(),w(He,{key:0,clsPrefix:$,status:r,showIndicator:S,indicatorTextColor:x,railColor:l,fillColor:c,railStyle:a,percentage:f,processing:L,indicatorPlacement:_,unit:s,fillBorderRadius:d,railBorderRadius:C,height:u},E(H),1032,["clsPrefix","status","showIndicator","indicatorTextColor","railColor","fillColor","railStyle","percentage","processing","indicatorPlacement","unit","fillBorderRadius","railBorderRadius","height"])):(o(),n(W,{key:1},[e==="multiple-circle"?(o(),w(Ve,{key:0,clsPrefix:$,strokeWidth:v,railColor:l,fillColor:c,railStyle:a,viewBoxWidth:m,percentage:f,showIndicator:S,circleGap:T},E(H),1032,["clsPrefix","strokeWidth","railColor","fillColor","railStyle","viewBoxWidth","percentage","showIndicator","circleGap"])):P(()=>null)],64))],64))],14,Ye)}});const Ze={components:{NButton:j,NForm:$e,NFormItem:Pe,NInput:ve,NInputNumber:ze,NModal:le,NSelect:Ce,NSpin:F},props:{show:Boolean},emits:["update:show"],data(){return{form:null,loading:!1,saving:!1,formats:[{label:"MP4",value:"mp4"},{label:"MKV",value:"mkv"}]}},computed:{visible:{get(){return this.show},set(e){this.$emit("update:show",e)}}},watch:{show(e){e&&this.load()}},methods:{async load(){this.loading=!0,this.form=null;try{this.form=await _e()}catch{}finally{this.loading=!1}},async save(){this.saving=!0;try{await be(this.form),window.$message?.success("离线下载设置已保存"),this.visible=!1}catch{}finally{this.saving=!1}}}},Je={class:"tip"},Qe={class:"footer"};function et(e,t,x,S,r,l){const a=k("n-input"),c=k("n-form-item"),f=k("n-input-number"),m=k("n-select"),v=k("n-form"),_=k("n-spin"),s=k("n-button"),C=k("n-modal");return o(),w(C,{show:l.visible,"onUpdate:show":t[6]||(t[6]=d=>l.visible=d),preset:"card",title:"离线下载设置",style:{width:"500px","max-width":"calc(100vw - 32px)"}},{footer:g(()=>[i("div",Qe,[p(s,{onClick:t[5]||(t[5]=d=>l.visible=!1)},{default:g(()=>[...t[8]||(t[8]=[z("关闭",-1)])]),_:1}),p(s,{type:"primary",disabled:r.loading||!r.form,loading:r.saving,onClick:l.save},{default:g(()=>[...t[9]||(t[9]=[z("保存",-1)])]),_:1},8,["disabled","loading","onClick"])])]),default:g(()=>[p(_,{show:r.loading},{default:g(()=>[r.form?(o(),w(v,{key:0,"label-placement":"top"},{default:g(()=>[p(c,{label:"下载根目录"},{default:g(()=>[p(a,{value:r.form.mediaRoot,readonly:""},null,8,["value"])]),_:1}),i("p",Je,b(r.form.toolsReady?"下载工具已就绪":"缺少下载工具，需要更新 Docker 镜像")+"；"+b(r.form.mediaWritable?"媒体目录可写":"媒体目录不可写，请检查挂载与权限")+"。",1),p(c,{label:"分片线程数"},{default:g(()=>[p(f,{value:r.form.threadCount,"onUpdate:value":t[0]||(t[0]=d=>r.form.threadCount=d),min:1,max:32},null,8,["value"])]),_:1}),p(c,{label:"最大尝试次数"},{default:g(()=>[p(f,{value:r.form.maxAttempts,"onUpdate:value":t[1]||(t[1]=d=>r.form.maxAttempts=d),min:1,max:10},null,8,["value"])]),_:1}),p(c,{label:"每次任务超时（分钟）"},{default:g(()=>[p(f,{value:r.form.timeoutMinutes,"onUpdate:value":t[2]||(t[2]=d=>r.form.timeoutMinutes=d),min:5,max:1440},null,8,["value"])]),_:1}),p(c,{label:"封装格式"},{default:g(()=>[p(m,{value:r.form.format,"onUpdate:value":t[3]||(t[3]=d=>r.form.format=d),options:r.formats},null,8,["value","options"])]),_:1}),p(c,{label:"限速"},{default:g(()=>[p(a,{value:r.form.maxSpeed,"onUpdate:value":t[4]||(t[4]=d=>r.form.maxSpeed=d),placeholder:"如 15M 或 500K，留空不限速"},null,8,["value"])]),_:1}),t[7]||(t[7]=i("p",{class:"tip"},"同时下载一集，保留原始编码。失败后自动退避重试；已运行的任务沿用启动时的设置。",-1))]),_:1})):B("",!0)]),_:1},8,["show"])]),_:1},8,["show"])}const tt=V(Ze,[["render",et],["__scopeId","data-v-b5a4a2f0"]]),Z={pending:"等待下载",downloading:"下载中",verifying:"检查中",completed:"已完成",failed:"失败",skipped:"首次跳过"},J={parsing:"正在解析资源",downloading:"下载中",merging:"正在合并",verifying:"检查并归档中"},st={pending:"warn",downloading:"accent",verifying:"accent",completed:"ok",failed:"danger",skipped:"off"},it={components:{NButton:j,NDataTable:ce,NModal:le,NProgress:K,NSpin:F,HlsSettingsDialog:tt},setup(){return{isMobile:ne()}},data(){return{rows:[],loading:!1,fetching:!1,settings:!1,retrying:0,clearing:!1,timer:null,logDialog:!1,logLoading:!1,logContent:""}},computed:{clearable(){return this.rows.some(e=>["completed","failed","skipped"].includes(e.status))},columns(){return[{title:"剧名",key:"seriesTitle",width:180,ellipsis:{tooltip:!0}},{title:"季集",key:"episode",width:90,render:e=>`S${String(e.season).padStart(2,"0")}E${String(e.episode).padStart(2,"0")}`},{title:"状态",key:"status",width:110,render:e=>e.paused&&e.status==="pending"?"订阅已暂停":Z[e.status]||e.status},{title:"下载进度",key:"progressPercent",width:210,render:e=>{const t=typeof e.progressPercent=="number"&&Number.isFinite(e.progressPercent);return["downloading","verifying","completed"].includes(e.status)?A("div",{class:"progress-cell"},[A(K,{type:"line",percentage:t?e.progressPercent:0,processing:e.status!=="completed",showIndicator:t,height:6,status:e.status==="completed"?"success":"default"}),A("div",{class:"progress-detail"},[A("span",e.status==="completed"?"已归档":J[e.phase]||"等待进度"),e.downloadSpeed&&e.phase==="downloading"?A("span",e.downloadSpeed):null])]):e.status==="pending"?"等待开始":"—"}},{title:"尝试次数",key:"attempts",width:90},{title:"下次尝试",key:"nextAttemptAt",width:160},{title:"归档文件",key:"savePath",width:240,ellipsis:{tooltip:!0}},{title:"提示",key:"message",width:200,ellipsis:{tooltip:!0}},{title:"操作",key:"action",width:140,render:e=>A("div",{style:"display:flex;gap:6px"},[A(j,{size:"tiny",onClick:()=>this.showLog(e)},{default:()=>"日志"}),["failed","skipped"].includes(e.status)?A(j,{size:"tiny",disabled:!!e.paused,loading:this.retrying===e.id,onClick:()=>this.retry(e)},{default:()=>e.status==="skipped"?"补下载":"重试"}):null])}]}},mounted(){this.load(),this.start()},activated(){this.timer||(this.load(),this.start())},deactivated(){this.stop()},beforeUnmount(){this.stop()},methods:{statusText(e){return e.paused&&e.status==="pending"?"订阅已暂停":Z[e.status]||e.status},statusTone(e){return st[e.status]||"off"},episodeText(e){return`S${String(e.season).padStart(2,"0")}E${String(e.episode).padStart(2,"0")}`},hasPercent(e){return typeof e.progressPercent=="number"&&Number.isFinite(e.progressPercent)},progressPercent(e){return this.hasPercent(e)?e.progressPercent:0},showProgress(e){return["downloading","verifying","completed"].includes(e.status)},phaseText(e){return e.status==="completed"?"已归档":J[e.phase]||"等待进度"},async showLog(e){this.logDialog=!0,this.logLoading=!0,this.logContent="";try{this.logContent=(await Se(e.id)).content}catch{}finally{this.logLoading=!1}},start(){this.timer||(this.timer=setInterval(()=>this.load(!0),3e3))},stop(){clearInterval(this.timer),this.timer=null},async load(e=!1){if(!this.fetching){this.fetching=!0,e||(this.loading=!0);try{this.rows=await we()}catch{}finally{this.loading=!1,this.fetching=!1}}},clearRecords(){window.$dialog?.warning({title:"清空下载记录",content:"将清空已完成 / 失败 / 跳过的历史记录，等待与下载中的任务保留。已归档的视频文件不受影响，确认清空？",positiveText:"清空",negativeText:"取消",onPositiveClick:async()=>{this.clearing=!0;try{const e=await xe();window.$message?.success(e?.message||"已清空下载记录"),await this.load()}catch{}finally{this.clearing=!1}}})},async retry(e){this.retrying=e.id;try{await ke(e.id),window.$message?.success("已重新加入下载队列"),await this.load()}catch{}finally{this.retrying=0}}}},rt={class:"head"},ot={key:0,class:"empty"},lt={key:1,class:"cards"},nt={class:"task-head"},at={class:"task-title"},dt={class:"task-tags"},ct={class:"tag"},ut={class:"tag"},pt={key:0,class:"task-progress"},gt={class:"progress-detail"},ft={key:0},ht={class:"task-fields"},mt={key:0,class:"field"},yt={key:1,class:"field"},vt={class:"field-value"},bt={key:2,class:"field"},_t={class:"field-value mono"},kt={key:3,class:"field"},xt={class:"field-value"},wt={class:"task-actions"},St={class:"log"};function Ct(e,t,x,S,r,l){const a=k("n-button"),c=k("n-progress"),f=k("n-data-table"),m=k("n-spin"),v=k("HlsSettingsDialog"),_=k("n-modal");return o(),n("div",null,[i("div",rt,[t[6]||(t[6]=i("span",null,"离线剧集任务（最近 500 条）",-1)),p(a,{size:"small",type:"error",ghost:"",disabled:!l.clearable,loading:r.clearing,onClick:l.clearRecords},{default:g(()=>[...t[3]||(t[3]=[z("清空下载记录",-1)])]),_:1},8,["disabled","loading","onClick"]),p(a,{size:"small",onClick:t[0]||(t[0]=s=>r.settings=!0)},{default:g(()=>[...t[4]||(t[4]=[z("下载设置",-1)])]),_:1}),p(a,{size:"small",loading:r.loading,onClick:l.load},{default:g(()=>[...t[5]||(t[5]=[z("刷新",-1)])]),_:1},8,["loading","onClick"])]),p(m,{show:r.loading},{default:g(()=>[r.rows.length?S.isMobile?(o(),n("div",lt,[(o(!0),n(W,null,ae(r.rows,s=>(o(),n("article",{key:s.id,class:"task-card"},[i("div",nt,[i("span",at,b(s.seriesTitle||"未知剧集"),1),i("span",{class:de(["pill",l.statusTone(s)])},[t[7]||(t[7]=i("span",{class:"dot"},null,-1)),z(b(l.statusText(s)),1)],2)]),i("div",dt,[i("span",ct,b(l.episodeText(s)),1),i("span",ut,"尝试 "+b(s.attempts)+" 次",1)]),l.showProgress(s)?(o(),n("div",pt,[p(c,{type:"line",percentage:l.progressPercent(s),"show-indicator":l.hasPercent(s),processing:s.status!=="completed",status:s.status==="completed"?"success":"default",height:6},null,8,["percentage","show-indicator","processing","status"]),i("div",gt,[i("span",null,b(l.phaseText(s)),1),s.downloadSpeed&&s.phase==="downloading"?(o(),n("span",ft,b(s.downloadSpeed),1)):B("",!0)])])):B("",!0),i("div",ht,[s.status==="pending"?(o(),n("div",mt,[...t[8]||(t[8]=[i("span",{class:"field-label"},"进度",-1),i("span",{class:"field-value"},"等待开始",-1)])])):B("",!0),s.nextAttemptAt?(o(),n("div",yt,[t[9]||(t[9]=i("span",{class:"field-label"},"下次尝试",-1)),i("span",vt,b(s.nextAttemptAt),1)])):B("",!0),s.savePath?(o(),n("div",bt,[t[10]||(t[10]=i("span",{class:"field-label"},"归档文件",-1)),i("span",_t,b(s.savePath),1)])):B("",!0),s.message?(o(),n("div",kt,[t[11]||(t[11]=i("span",{class:"field-label"},"提示",-1)),i("span",xt,b(s.message),1)])):B("",!0)]),i("div",wt,[p(a,{size:"tiny",onClick:C=>l.showLog(s)},{default:g(()=>[...t[12]||(t[12]=[z("日志",-1)])]),_:1},8,["onClick"]),["failed","skipped"].includes(s.status)?(o(),w(a,{key:0,size:"tiny",type:"primary",disabled:!!s.paused,loading:r.retrying===s.id,onClick:C=>l.retry(s)},{default:g(()=>[z(b(s.status==="skipped"?"补下载":"重试"),1)]),_:2},1032,["disabled","loading","onClick"])):B("",!0)])]))),128))])):(o(),w(f,{key:2,columns:l.columns,data:r.rows,"row-key":s=>s.id,bordered:!1,"max-height":560,"scroll-x":1350,size:"small"},null,8,["columns","data","row-key"])):(o(),n("p",ot,"还没有离线下载任务。可在电影天堂订阅追更或下载单集。"))]),_:1},8,["show"]),t[13]||(t[13]=i("p",{class:"tip"},"页面显示期间每 3 秒更新进度；下载后还需合并、检查和归档，完成后显示 100%。",-1)),p(v,{show:r.settings,"onUpdate:show":t[1]||(t[1]=s=>r.settings=s)},null,8,["show"]),p(_,{show:r.logDialog,"onUpdate:show":t[2]||(t[2]=s=>r.logDialog=s),preset:"card",title:"下载日志",style:{width:"760px","max-width":"calc(100vw - 32px)"}},{default:g(()=>[p(m,{show:r.logLoading},{default:g(()=>[i("pre",St,b(r.logContent),1)]),_:1},8,["show"])]),_:1},8,["show"])])}const Pt=V(it,[["render",Ct],["__scopeId","data-v-ab3bd2d9"]]),Q={pending:"待提交",added:"已提交",downloading:"下载中",completed:"已完成",failed:"失败",removed:"已移除"},$t={pending:"warn",added:"off",downloading:"accent",completed:"ok",failed:"danger",removed:"off"},zt={name:"MovieDownloads",components:{HlsDownloadsPanel:Pt,NTabs:ye,NTabPane:me,NButton:j,NDataTable:ce,NSpin:F},setup(){return{isMobile:ne()}},data(){return{kind:"hls",loading:!1,clearing:!1,firstActivate:!0,rows:[],columns:[{title:"剧名",key:"seriesTitle",ellipsis:{tooltip:!0}},{title:"集号",key:"episode",width:70},{title:"清晰度",key:"quality",width:90},{title:"工具",key:"downloader",width:100,render:e=>e.downloader==="xunlei"?"迅雷":"qBittorrent"},{title:"状态",key:"status",width:90,render:e=>Q[e.status]||e.status},{title:"保存目录",key:"savePath",ellipsis:{tooltip:!0}},{title:"时间",key:"createdAt",width:160}]}},computed:{clearable(){return this.rows.some(e=>["completed","failed","removed"].includes(e.status))}},async mounted(){await this.load()},async activated(){if(this.firstActivate){this.firstActivate=!1;return}await this.load()},methods:{statusLabel(e){return Q[e]||e},statusTone(e){return $t[e]||"off"},clearRecords(){window.$dialog?.warning({title:"清空下载记录",content:"将清空已完成 / 失败的历史记录，待提交与下载中的任务保留。下载器里已提交的任务不受影响，确认清空？",positiveText:"清空",negativeText:"取消",onPositiveClick:async()=>{this.clearing=!0;try{const e=await Be();window.$message?.success(e?.message||"已清空下载记录"),await this.load()}catch{}finally{this.clearing=!1}}})},async load(){this.loading=!0;try{const e=await Ne(0);this.rows=Array.isArray(e)?e:[]}finally{this.loading=!1}}}},Nt={class:"downloads-page"},Bt={key:1,class:"downloads"},Dt={class:"downloads-head"},Tt={key:0,class:"empty"},Rt={key:1,class:"cards"},It={class:"task-head"},At={class:"task-title"},Wt={class:"task-tags"},Lt={key:0,class:"tag"},Mt={key:1,class:"tag"},qt={class:"tag"},Ht={class:"task-fields"},jt={class:"field"},Ot={class:"field-value mono"},Gt={class:"field"},Ut={class:"field-value"},Et={key:0,class:"field"},Ft={class:"field-value"};function Vt(e,t,x,S,r,l){const a=k("n-tab-pane"),c=k("n-tabs"),f=k("HlsDownloadsPanel"),m=k("n-button"),v=k("n-data-table"),_=k("n-spin");return o(),n("div",Nt,[p(c,{value:r.kind,"onUpdate:value":t[0]||(t[0]=s=>r.kind=s),type:"line"},{default:g(()=>[p(a,{name:"hls"},{tab:g(()=>[...t[1]||(t[1]=[z("离线剧集下载",-1)])]),_:1}),p(a,{name:"magnet"},{tab:g(()=>[...t[2]||(t[2]=[z("磁力下载",-1)])]),_:1})]),_:1},8,["value"]),r.kind==="hls"?(o(),w(f,{key:0})):(o(),n("div",Bt,[i("div",Dt,[t[7]||(t[7]=i("h3",null,"下载任务",-1)),p(m,{size:"tiny",type:"error",ghost:"",disabled:!l.clearable,loading:r.clearing,onClick:l.clearRecords},{icon:g(()=>[...t[3]||(t[3]=[i("i",{class:"fa-solid fa-trash-can"},null,-1)])]),default:g(()=>[t[4]||(t[4]=z(" 清空下载记录 ",-1))]),_:1},8,["disabled","loading","onClick"]),p(m,{size:"tiny",loading:r.loading,onClick:l.load},{icon:g(()=>[...t[5]||(t[5]=[i("i",{class:"fa-solid fa-rotate"},null,-1)])]),default:g(()=>[t[6]||(t[6]=z(" 刷新 ",-1))]),_:1},8,["loading","onClick"])]),p(_,{show:r.loading},{default:g(()=>[r.rows.length?S.isMobile?(o(),n("div",Rt,[(o(!0),n(W,null,ae(r.rows,s=>(o(),n("article",{key:s.id,class:"task-card"},[i("div",It,[i("span",At,b(s.seriesTitle||"未知剧集"),1),i("span",{class:de(["pill",l.statusTone(s.status)])},b(l.statusLabel(s.status)),3)]),i("div",Wt,[s.episode?(o(),n("span",Lt,"第 "+b(s.episode)+" 集",1)):B("",!0),s.quality?(o(),n("span",Mt,b(s.quality),1)):B("",!0),i("span",qt,b(s.downloader==="xunlei"?"迅雷":"qBittorrent"),1)]),i("div",Ht,[i("div",jt,[t[8]||(t[8]=i("span",{class:"field-label"},"保存目录",-1)),i("span",Ot,b(s.savePath||"—"),1)]),i("div",Gt,[t[9]||(t[9]=i("span",{class:"field-label"},"时间",-1)),i("span",Ut,b(s.createdAt||"—"),1)]),s.message?(o(),n("div",Et,[t[10]||(t[10]=i("span",{class:"field-label"},"提示",-1)),i("span",Ft,b(s.message),1)])):B("",!0)])]))),128))])):(o(),w(v,{key:2,size:"small",columns:r.columns,data:r.rows,bordered:!1,"max-height":560},null,8,["columns","data"])):(o(),n("div",Tt,"还没有订阅触发的下载任务"))]),_:1},8,["show"])]))])}const ps=V(zt,[["render",Vt],["__scopeId","data-v-c10112cd"]]);export{ps as default};
