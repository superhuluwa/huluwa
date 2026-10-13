import{e as j,o,f as c,g as n,h as P,i as h,n as x,F as q,E as k,I as Y,l as z,al as K,am as Z,an as J,ao as Q,ap as ie,a as H,c as y,b as R,u as ee,aq as O,j as se,ar as le,k as ne,as as E,Y as te,Z as M,R as _,U as f,T as g,W as U,a2 as ae,$ as B,y as I}from"./index-D-Ieq7DB.js";import{D as re}from"./DataTable-zr_hYUez.js";import{S as F}from"./Spin-Ckis65XR.js";import{T as de,a as ce}from"./Tabs-D9Vt7aq8.js";import{f as A,I as ue}from"./Input-DGDWrra5.js";import{c as ge,a as pe,R as fe,d as he,e as me,f as ye}from"./hls-BygQrQNB.js";import{S as ve}from"./Select-B8btiZ61.js";import{F as be,a as we}from"./FormItem-a6lP6KgW.js";import{I as _e}from"./InputNumber-1trzg9dK.js";import{_ as V}from"./_plugin-vue_export-helper-DlAUqK2U.js";import{o as ke,p as xe}from"./chat-CsoqRoT7.js";import"./create-DrGbyF8A.js";import"./use-compitable-DrrpCM3q.js";import"./Dropdown-f_X_CApl.js";import"./CheckboxGroup-B0iHVYj6.js";import"./Tooltip-4cvTcWg9.js";import"./get-slot-VaplJ9hC.js";import"./Add-Dt2k97Ft.js";const Ce=["id"],Se=["stop-color"],$e=["stop-color"],Pe=["viewBox"],ze=["d","stroke-width"],Be=["d","stroke-width"],Ne={success:(o(),k(Q)),error:(o(),k(J)),warning:(o(),k(Z)),info:(o(),k(K))};var De=j({name:"ProgressCircle",props:{clsPrefix:{type:String,required:!0},status:{type:String,required:!0},strokeWidth:{type:Number,required:!0},fillColor:[String,Object],railColor:String,railStyle:[String,Object],percentage:{type:Number,default:0},offsetDegree:{type:Number,default:0},showIndicator:{type:Boolean,required:!0},indicatorTextColor:String,unit:String,viewBoxWidth:{type:Number,required:!0},gapDegree:{type:Number,required:!0},gapOffsetDegree:{type:Number,default:0}},setup(e,{slots:t}){const w=z(()=>{const i="gradient",{fillColor:s}=e;return typeof s=="object"?`${i}-${ie(JSON.stringify(s))}`:i});function C(i,s,a,p){const{gapDegree:v,viewBoxWidth:b,strokeWidth:m}=e,u=50,S=0,l=u,d=0,T=100,N=50+m/2,$=`M ${N},${N} m ${S},${l}
      a ${u},${u} 0 1 1 ${d},-100
      a ${u},${u} 0 1 1 0,${T}`,D=Math.PI*2*u;return{pathString:$,pathStyle:{stroke:p==="rail"?a:typeof e.fillColor=="object"?`url(#${w.value})`:a,strokeDasharray:`${Math.min(i,100)/100*(D-v)}px ${b*8}px`,strokeDashoffset:`-${v/2}px`,transformOrigin:s?"center":void 0,transform:s?`rotate(${s}deg)`:void 0}}}const r=()=>{const i=typeof e.fillColor=="object",s=i?e.fillColor.stops[0]:"",a=i?e.fillColor.stops[1]:"";return i&&(o(),c("defs",null,[n("linearGradient",{id:w.value,x1:"0%",y1:"100%",x2:"100%",y2:"0%"},[n("stop",{offset:"0%","stop-color":s},null,8,Se),n("stop",{offset:"100%","stop-color":a},null,8,$e)],8,Ce)]))};return()=>{const{fillColor:i,railColor:s,strokeWidth:a,offsetDegree:p,status:v,percentage:b,showIndicator:m,indicatorTextColor:u,unit:S,gapOffsetDegree:l,clsPrefix:d}=e,{pathString:T,pathStyle:N}=C(100,0,s,"rail"),{pathString:$,pathStyle:D}=C(b,p,i,"fill"),W=100+a;return o(),c("div",{class:h(`${d}-progress-content`),role:"none"},[n("div",{class:h(`${d}-progress-graph`),"aria-hidden":!0},[n("div",{class:h(`${d}-progress-graph-circle`),style:P({transform:l?`rotate(${l}deg)`:void 0})},[(o(),c("svg",{viewBox:`0 0 ${W} ${W}`},[x(()=>r()),n("g",null,[n("path",{class:h(`${d}-progress-graph-circle-rail`),d:T,"stroke-width":a,"stroke-linecap":"round",fill:"none",style:P(N)},null,14,ze)]),n("g",null,[n("path",{class:h([`${d}-progress-graph-circle-fill`,b===0&&`${d}-progress-graph-circle-fill--empty`]),d:$,"stroke-width":a,"stroke-linecap":"round",fill:"none",style:P(D)},null,14,Be)])],8,Pe))],6)],2),m?(o(),c("div",{key:0},[t.default?(o(),c("div",{key:0,class:h(`${d}-progress-custom-content`),role:"none"},[x(()=>t.default())],2)):(o(),c(q,{key:1},[v!=="default"?(o(),c("div",{key:0,class:h(`${d}-progress-icon`),"aria-hidden":!0},[(o(),k(Y,{clsPrefix:d},{default:()=>Ne[v]},1032,["clsPrefix"]))],2)):(o(),c("div",{key:1,class:h(`${d}-progress-text`),style:P({color:u}),role:"none"},[n("span",{class:h(`${d}-progress-text__percentage`)},[x(()=>b)],2),n("span",{class:h(`${d}-progress-text__unit`)},[x(()=>S)],2)],6))],64))])):x(()=>null)],2)}}});const Re={success:(o(),k(Q)),error:(o(),k(J)),warning:(o(),k(Z)),info:(o(),k(K))};var Ie=j({name:"ProgressLine",props:{clsPrefix:{type:String,required:!0},percentage:{type:Number,default:0},railColor:String,railStyle:[String,Object],fillColor:[String,Object],status:{type:String,required:!0},indicatorPlacement:{type:String,required:!0},indicatorTextColor:String,unit:{type:String,default:"%"},processing:{type:Boolean,required:!0},showIndicator:{type:Boolean,required:!0},height:[String,Number],railBorderRadius:[String,Number],fillBorderRadius:[String,Number]},setup(e,{slots:t}){const w=z(()=>A(e.height)),C=z(()=>typeof e.fillColor=="object"?`linear-gradient(to right, ${e.fillColor?.stops[0]} , ${e.fillColor?.stops[1]})`:e.fillColor),r=z(()=>e.railBorderRadius!==void 0?A(e.railBorderRadius):e.height!==void 0?A(e.height,{c:.5}):""),i=z(()=>e.fillBorderRadius!==void 0?A(e.fillBorderRadius):e.railBorderRadius!==void 0?A(e.railBorderRadius):e.height!==void 0?A(e.height,{c:.5}):"");return()=>{const{indicatorPlacement:s,railColor:a,railStyle:p,percentage:v,unit:b,indicatorTextColor:m,status:u,showIndicator:S,processing:l,clsPrefix:d}=e;return o(),c("div",{class:h(`${d}-progress-content`),role:"none"},[n("div",{class:h(`${d}-progress-graph`),"aria-hidden":!0},[n("div",{class:h([`${d}-progress-graph-line`,{[`${d}-progress-graph-line--indicator-${s}`]:!0}])},[n("div",{class:h(`${d}-progress-graph-line-rail`),style:P([{backgroundColor:a,height:w.value,borderRadius:r.value},p])},[n("div",{class:h([`${d}-progress-graph-line-fill`,l&&`${d}-progress-graph-line-fill--processing`]),style:P({maxWidth:`${e.percentage}%`,background:C.value,height:w.value,lineHeight:w.value,borderRadius:i.value})},[s==="inside"?(o(),c("div",{key:0,class:h(`${d}-progress-graph-line-indicator`),style:P({color:m})},[t.default?(o(),c(q,{key:0},[x(()=>t.default())],64)):(o(),c(q,{key:1},[x(()=>`${v}${b}`)],64))],6)):x(()=>null)],6)],6)],2)],2),S&&s==="outside"?(o(),c("div",{key:0},[t.default?(o(),c("div",{key:0,class:h(`${d}-progress-custom-content`),style:P({color:m}),role:"none"},[x(()=>t.default())],6)):(o(),c(q,{key:1},[u==="default"?(o(),c("div",{key:0,role:"none",class:h(`${d}-progress-icon ${d}-progress-icon--as-text`),style:P({color:m})},[x(()=>v),x(()=>b)],6)):(o(),c("div",{key:1,class:h(`${d}-progress-icon`),"aria-hidden":!0},[(o(),k(Y,{clsPrefix:d},{default:()=>Re[u]},1032,["clsPrefix"]))],2))],64))])):x(()=>null)],2)}}});const Te=["id"],We=["stop-color"],Ae=["stop-color"],qe=["d","stroke-width"],Le=["d","stroke-width"],Me=["viewBox"];function X(e,t,w=100){return`m ${w/2} ${w/2-e} a ${e} ${e} 0 1 1 0 ${2*e} a ${e} ${e} 0 1 1 0 -${2*e}`}var He=j({name:"ProgressMultipleCircle",props:{clsPrefix:{type:String,required:!0},viewBoxWidth:{type:Number,required:!0},percentage:{type:Array,default:[0]},strokeWidth:{type:Number,required:!0},circleGap:{type:Number,required:!0},showIndicator:{type:Boolean,required:!0},fillColor:{type:Array,default:()=>[]},railColor:{type:Array,default:()=>[]},railStyle:{type:Array,default:()=>[]}},setup(e,{slots:t}){const w=z(()=>e.percentage.map((r,i)=>`${Math.PI*r/100*(e.viewBoxWidth/2-e.strokeWidth/2*(1+2*i)-e.circleGap*i)*2}, ${e.viewBoxWidth*8}`)),C=(r,i)=>{const s=e.fillColor[i],a=typeof s=="object"?s.stops[0]:"",p=typeof s=="object"?s.stops[1]:"";return typeof e.fillColor[i]=="object"&&(o(),c("linearGradient",{id:`gradient-${i}`,x1:"100%",y1:"0%",x2:"0%",y2:"100%"},[n("stop",{offset:"0%","stop-color":a},null,8,We),n("stop",{offset:"100%","stop-color":p},null,8,Ae)],8,Te))};return()=>{const{viewBoxWidth:r,strokeWidth:i,circleGap:s,showIndicator:a,fillColor:p,railColor:v,railStyle:b,percentage:m,clsPrefix:u}=e;return o(),c("div",{class:h(`${u}-progress-content`),role:"none"},[n("div",{class:h(`${u}-progress-graph`),"aria-hidden":!0},[n("div",{class:h(`${u}-progress-graph-circle`)},[(o(),c("svg",{viewBox:`0 0 ${r} ${r}`},[n("defs",null,[x(()=>m.map((S,l)=>C(S,l)))]),x(()=>m.map((S,l)=>(o(),c("g",{key:l},[n("path",{class:h(`${u}-progress-graph-circle-rail`),d:X(r/2-i/2*(1+2*l)-s*l,i,r),"stroke-width":i,"stroke-linecap":"round",fill:"none",style:P([{strokeDashoffset:0,stroke:v[l]},b[l]])},null,14,qe),n("path",{class:h([`${u}-progress-graph-circle-fill`,S===0&&`${u}-progress-graph-circle-fill--empty`]),d:X(r/2-i/2*(1+2*l)-s*l,i,r),"stroke-width":i,"stroke-linecap":"round",fill:"none",style:P({strokeDasharray:w.value[l],strokeDashoffset:0,stroke:typeof p[l]=="object"?`url(#gradient-${l})`:p[l]})},null,14,Le)]))))],8,Me))],2)],2),a&&t.default?(o(),c("div",{key:0},[n("div",{class:h(`${u}-progress-text`)},[x(()=>t.default())],2)])):x(()=>null)],2)}}}),je=H([y("progress",{display:"inline-block"},[y("progress-icon",`
 color: var(--n-icon-color);
 transition: color .3s var(--n-bezier);
 `),R("line",`
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
 `,[R("as-text",`
 color: var(--n-text-color-line-outer);
 text-align: center;
 width: 40px;
 font-size: var(--n-font-size);
 padding-left: 4px;
 transition: color .3s var(--n-bezier);
 `)])]),R("circle, dashboard",{width:"120px"},[y("progress-custom-content",`
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
 `)]),R("multiple-circle",`
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
 `)]),y("progress-content",{position:"relative"}),y("progress-graph",{position:"relative"},[y("progress-graph-circle",[H("svg",{verticalAlign:"bottom"}),y("progress-graph-circle-fill",`
 stroke: var(--n-fill-color);
 transition:
 opacity .3s var(--n-bezier),
 stroke .3s var(--n-bezier),
 stroke-dasharray .3s var(--n-bezier);
 `,[R("empty",{opacity:0})]),y("progress-graph-circle-rail",`
 transition: stroke .3s var(--n-bezier);
 overflow: hidden;
 stroke: var(--n-rail-color);
 `)]),y("progress-graph-line",[R("indicator-inside",[y("progress-graph-line-rail",`
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
 `)])]),R("indicator-inside-label",`
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
 `,[R("processing",[H("&::after",`
 content: "";
 background-image: var(--n-line-bg-processing);
 animation: progress-processing-animation 2s var(--n-bezier) infinite;
 `)])])])])])]),H("@keyframes progress-processing-animation",`
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
 `)]);const Ge=["aria-valuenow","role"],Oe={...ee.props,processing:Boolean,type:{type:String,default:"line"},gapDegree:Number,gapOffsetDegree:Number,status:{type:String,default:"default"},railColor:[String,Array],railStyle:[String,Array],color:[String,Array,Object],viewBoxWidth:{type:Number,default:100},strokeWidth:{type:Number,default:7},percentage:[Number,Array],unit:{type:String,default:"%"},showIndicator:{type:Boolean,default:!0},indicatorPosition:{type:String,default:"outside"},indicatorPlacement:{type:String,default:"outside"},indicatorTextColor:String,circleGap:{type:Number,default:1},height:Number,borderRadius:[String,Number],fillBorderRadius:[String,Number],offsetDegree:Number};var Ue=j({name:"Progress",props:Oe,setup(e){const t=z(()=>e.indicatorPlacement||e.indicatorPosition),w=z(()=>{if(e.gapDegree||e.gapDegree===0)return e.gapDegree;if(e.type==="dashboard")return 75}),{mergedClsPrefixRef:C,inlineThemeDisabled:r}=se(e),i=ee("Progress","-progress",je,le,e,C),s=z(()=>{const{status:p}=e,{common:{cubicBezierEaseInOut:v},self:{fontSize:b,fontSizeCircle:m,railColor:u,railHeight:S,iconSizeCircle:l,iconSizeLine:d,textColorCircle:T,textColorLineInner:N,textColorLineOuter:$,lineBgProcessing:D,fontWeightCircle:W,[E("iconColor",p)]:G,[E("fillColor",p)]:L}}=i.value;return{"--n-bezier":v,"--n-fill-color":L,"--n-font-size":b,"--n-font-size-circle":m,"--n-font-weight-circle":W,"--n-icon-color":G,"--n-icon-size-circle":l,"--n-icon-size-line":d,"--n-line-bg-processing":D,"--n-rail-color":u,"--n-rail-height":S,"--n-text-color-circle":T,"--n-text-color-line-inner":N,"--n-text-color-line-outer":$}}),a=r?ne("progress",z(()=>e.status[0]),s,e):void 0;return{mergedClsPrefix:C,mergedIndicatorPlacement:t,gapDeg:w,cssVars:r?void 0:s,themeClass:a?.themeClass,onRender:a?.onRender}},render(){const{type:e,cssVars:t,indicatorTextColor:w,showIndicator:C,status:r,railColor:i,railStyle:s,color:a,percentage:p,viewBoxWidth:v,strokeWidth:b,mergedIndicatorPlacement:m,unit:u,borderRadius:S,fillBorderRadius:l,height:d,processing:T,circleGap:N,mergedClsPrefix:$,gapDeg:D,gapOffsetDegree:W,themeClass:G,$slots:L,onRender:oe}=this;return oe?.(),o(),c("div",{class:h([G,`${$}-progress`,`${$}-progress--${e}`,`${$}-progress--${r}`]),style:P(t),"aria-valuemax":100,"aria-valuemin":0,"aria-valuenow":p,role:e==="circle"||e==="line"||e==="dashboard"?"progressbar":"none"},[e==="circle"||e==="dashboard"?(o(),k(De,{key:0,clsPrefix:$,status:r,showIndicator:C,indicatorTextColor:w,railColor:i,fillColor:a,railStyle:s,offsetDegree:this.offsetDegree,percentage:p,viewBoxWidth:v,strokeWidth:b,gapDegree:D===void 0?e==="dashboard"?75:0:D,gapOffsetDegree:W,unit:u},O(L),1032,["clsPrefix","status","showIndicator","indicatorTextColor","railColor","fillColor","railStyle","offsetDegree","percentage","viewBoxWidth","strokeWidth","gapDegree","gapOffsetDegree","unit"])):(o(),c(q,{key:1},[e==="line"?(o(),k(Ie,{key:0,clsPrefix:$,status:r,showIndicator:C,indicatorTextColor:w,railColor:i,fillColor:a,railStyle:s,percentage:p,processing:T,indicatorPlacement:m,unit:u,fillBorderRadius:l,railBorderRadius:S,height:d},O(L),1032,["clsPrefix","status","showIndicator","indicatorTextColor","railColor","fillColor","railStyle","percentage","processing","indicatorPlacement","unit","fillBorderRadius","railBorderRadius","height"])):(o(),c(q,{key:1},[e==="multiple-circle"?(o(),k(He,{key:0,clsPrefix:$,strokeWidth:b,railColor:i,fillColor:a,railStyle:s,viewBoxWidth:v,percentage:p,showIndicator:C,circleGap:N},O(L),1032,["clsPrefix","strokeWidth","railColor","fillColor","railStyle","viewBoxWidth","percentage","showIndicator","circleGap"])):x(()=>null)],64))],64))],14,Ge)}});const Fe={components:{NButton:M,NForm:we,NFormItem:be,NInput:ue,NInputNumber:_e,NModal:te,NSelect:ve,NSpin:F},props:{show:Boolean},emits:["update:show"],data(){return{form:null,loading:!1,saving:!1,formats:[{label:"MP4",value:"mp4"},{label:"MKV",value:"mkv"}]}},computed:{visible:{get(){return this.show},set(e){this.$emit("update:show",e)}}},watch:{show(e){e&&this.load()}},methods:{async load(){this.loading=!0,this.form=null;try{this.form=await pe()}catch{}finally{this.loading=!1}},async save(){this.saving=!0;try{await ge(this.form),window.$message?.success("离线下载设置已保存"),this.visible=!1}catch{}finally{this.saving=!1}}}},Ve={class:"tip"},Ee={class:"footer"};function Xe(e,t,w,C,r,i){const s=_("n-input"),a=_("n-form-item"),p=_("n-input-number"),v=_("n-select"),b=_("n-form"),m=_("n-spin"),u=_("n-button"),S=_("n-modal");return o(),k(S,{show:i.visible,"onUpdate:show":t[6]||(t[6]=l=>i.visible=l),preset:"card",title:"离线下载设置",style:{width:"500px","max-width":"calc(100vw - 32px)"}},{footer:f(()=>[n("div",Ee,[g(u,{onClick:t[5]||(t[5]=l=>i.visible=!1)},{default:f(()=>[...t[8]||(t[8]=[B("关闭",-1)])]),_:1}),g(u,{type:"primary",disabled:r.loading||!r.form,loading:r.saving,onClick:i.save},{default:f(()=>[...t[9]||(t[9]=[B("保存",-1)])]),_:1},8,["disabled","loading","onClick"])])]),default:f(()=>[g(m,{show:r.loading},{default:f(()=>[r.form?(o(),k(b,{key:0,"label-placement":"top"},{default:f(()=>[g(a,{label:"下载根目录"},{default:f(()=>[g(s,{value:r.form.mediaRoot,readonly:""},null,8,["value"])]),_:1}),n("p",Ve,U(r.form.toolsReady?"下载工具已就绪":"缺少下载工具，需要更新 Docker 镜像")+"；"+U(r.form.mediaWritable?"媒体目录可写":"媒体目录不可写，请检查挂载与权限")+"。",1),g(a,{label:"分片线程数"},{default:f(()=>[g(p,{value:r.form.threadCount,"onUpdate:value":t[0]||(t[0]=l=>r.form.threadCount=l),min:1,max:32},null,8,["value"])]),_:1}),g(a,{label:"最大尝试次数"},{default:f(()=>[g(p,{value:r.form.maxAttempts,"onUpdate:value":t[1]||(t[1]=l=>r.form.maxAttempts=l),min:1,max:10},null,8,["value"])]),_:1}),g(a,{label:"每次任务超时（分钟）"},{default:f(()=>[g(p,{value:r.form.timeoutMinutes,"onUpdate:value":t[2]||(t[2]=l=>r.form.timeoutMinutes=l),min:5,max:1440},null,8,["value"])]),_:1}),g(a,{label:"封装格式"},{default:f(()=>[g(v,{value:r.form.format,"onUpdate:value":t[3]||(t[3]=l=>r.form.format=l),options:r.formats},null,8,["value","options"])]),_:1}),g(a,{label:"限速"},{default:f(()=>[g(s,{value:r.form.maxSpeed,"onUpdate:value":t[4]||(t[4]=l=>r.form.maxSpeed=l),placeholder:"如 15M 或 500K，留空不限速"},null,8,["value"])]),_:1}),t[7]||(t[7]=n("p",{class:"tip"},"同时下载一集，保留原始编码。失败后自动退避重试；已运行的任务沿用启动时的设置。",-1))]),_:1})):ae("",!0)]),_:1},8,["show"])]),_:1},8,["show"])}const Ye=V(Fe,[["render",Xe],["__scopeId","data-v-b5a4a2f0"]]),Ke={pending:"等待下载",downloading:"下载中",verifying:"检查中",completed:"已完成",failed:"失败",skipped:"首次跳过"},Ze={parsing:"正在解析资源",downloading:"下载中",merging:"正在合并",verifying:"检查并归档中"},Je={components:{NButton:M,NDataTable:re,NModal:te,NSpin:F,HlsSettingsDialog:Ye},data(){return{rows:[],loading:!1,fetching:!1,settings:!1,retrying:0,clearing:!1,timer:null,logDialog:!1,logLoading:!1,logContent:""}},computed:{clearable(){return this.rows.some(e=>["completed","failed","skipped"].includes(e.status))},columns(){return[{title:"剧名",key:"seriesTitle",width:180,ellipsis:{tooltip:!0}},{title:"季集",key:"episode",width:90,render:e=>`S${String(e.season).padStart(2,"0")}E${String(e.episode).padStart(2,"0")}`},{title:"状态",key:"status",width:110,render:e=>e.paused&&e.status==="pending"?"订阅已暂停":Ke[e.status]||e.status},{title:"下载进度",key:"progressPercent",width:210,render:e=>{const t=typeof e.progressPercent=="number"&&Number.isFinite(e.progressPercent);return["downloading","verifying","completed"].includes(e.status)?I("div",{class:"progress-cell"},[I(Ue,{type:"line",percentage:t?e.progressPercent:0,processing:e.status!=="completed",showIndicator:t,height:6,status:e.status==="completed"?"success":"default"}),I("div",{class:"progress-detail"},[I("span",e.status==="completed"?"已归档":Ze[e.phase]||"等待进度"),e.downloadSpeed&&e.phase==="downloading"?I("span",e.downloadSpeed):null])]):e.status==="pending"?"等待开始":"—"}},{title:"尝试次数",key:"attempts",width:90},{title:"下次尝试",key:"nextAttemptAt",width:160},{title:"归档文件",key:"savePath",width:240,ellipsis:{tooltip:!0}},{title:"提示",key:"message",width:200,ellipsis:{tooltip:!0}},{title:"操作",key:"action",width:140,render:e=>I("div",{style:"display:flex;gap:6px"},[I(M,{size:"tiny",onClick:()=>this.showLog(e)},{default:()=>"日志"}),["failed","skipped"].includes(e.status)?I(M,{size:"tiny",disabled:!!e.paused,loading:this.retrying===e.id,onClick:()=>this.retry(e)},{default:()=>e.status==="skipped"?"补下载":"重试"}):null])}]}},mounted(){this.load(),this.start()},activated(){this.timer||(this.load(),this.start())},deactivated(){this.stop()},beforeUnmount(){this.stop()},methods:{async showLog(e){this.logDialog=!0,this.logLoading=!0,this.logContent="";try{this.logContent=(await ye(e.id)).content}catch{}finally{this.logLoading=!1}},start(){this.timer||(this.timer=setInterval(()=>this.load(!0),3e3))},stop(){clearInterval(this.timer),this.timer=null},async load(e=!1){if(!this.fetching){this.fetching=!0,e||(this.loading=!0);try{this.rows=await me()}catch{}finally{this.loading=!1,this.fetching=!1}}},clearRecords(){window.$dialog?.warning({title:"清空下载记录",content:"将清空已完成 / 失败 / 跳过的历史记录，等待与下载中的任务保留。已归档的视频文件不受影响，确认清空？",positiveText:"清空",negativeText:"取消",onPositiveClick:async()=>{this.clearing=!0;try{const e=await he();window.$message?.success(e?.message||"已清空下载记录"),await this.load()}catch{}finally{this.clearing=!1}}})},async retry(e){this.retrying=e.id;try{await fe(e.id),window.$message?.success("已重新加入下载队列"),await this.load()}catch{}finally{this.retrying=0}}}},Qe={class:"head"},et={key:0,class:"empty"},tt={class:"log"};function rt(e,t,w,C,r,i){const s=_("n-button"),a=_("n-data-table"),p=_("n-spin"),v=_("HlsSettingsDialog"),b=_("n-modal");return o(),c("div",null,[n("div",Qe,[t[6]||(t[6]=n("span",null,"离线剧集任务（最近 500 条）",-1)),g(s,{size:"small",type:"error",ghost:"",disabled:!i.clearable,loading:r.clearing,onClick:i.clearRecords},{default:f(()=>[...t[3]||(t[3]=[B("清空下载记录",-1)])]),_:1},8,["disabled","loading","onClick"]),g(s,{size:"small",onClick:t[0]||(t[0]=m=>r.settings=!0)},{default:f(()=>[...t[4]||(t[4]=[B("下载设置",-1)])]),_:1}),g(s,{size:"small",loading:r.loading,onClick:i.load},{default:f(()=>[...t[5]||(t[5]=[B("刷新",-1)])]),_:1},8,["loading","onClick"])]),g(p,{show:r.loading},{default:f(()=>[r.rows.length?(o(),k(a,{key:1,columns:i.columns,data:r.rows,"row-key":m=>m.id,bordered:!1,"max-height":560,"scroll-x":1350,size:"small"},null,8,["columns","data","row-key"])):(o(),c("p",et,"还没有离线下载任务。可在电影天堂订阅追更或下载单集。"))]),_:1},8,["show"]),t[7]||(t[7]=n("p",{class:"tip"},"页面显示期间每 3 秒更新进度；下载后还需合并、检查和归档，完成后显示 100%。",-1)),g(v,{show:r.settings,"onUpdate:show":t[1]||(t[1]=m=>r.settings=m)},null,8,["show"]),g(b,{show:r.logDialog,"onUpdate:show":t[2]||(t[2]=m=>r.logDialog=m),preset:"card",title:"下载日志",style:{width:"760px","max-width":"calc(100vw - 32px)"}},{default:f(()=>[g(p,{show:r.logLoading},{default:f(()=>[n("pre",tt,U(r.logContent),1)]),_:1},8,["show"])]),_:1},8,["show"])])}const ot=V(Je,[["render",rt],["__scopeId","data-v-bc4ed118"]]),it={pending:"待提交",added:"已提交",downloading:"下载中",completed:"已完成",failed:"失败",removed:"已移除"},st={name:"MovieDownloads",components:{HlsDownloadsPanel:ot,NTabs:ce,NTabPane:de,NButton:M,NDataTable:re,NSpin:F},data(){return{kind:"hls",loading:!1,clearing:!1,firstActivate:!0,rows:[],columns:[{title:"剧名",key:"seriesTitle",ellipsis:{tooltip:!0}},{title:"集号",key:"episode",width:70},{title:"清晰度",key:"quality",width:90},{title:"工具",key:"downloader",width:100,render:e=>e.downloader==="xunlei"?"迅雷":"qBittorrent"},{title:"状态",key:"status",width:90,render:e=>it[e.status]||e.status},{title:"保存目录",key:"savePath",ellipsis:{tooltip:!0}},{title:"时间",key:"createdAt",width:160}]}},computed:{clearable(){return this.rows.some(e=>["completed","failed","removed"].includes(e.status))}},async mounted(){await this.load()},async activated(){if(this.firstActivate){this.firstActivate=!1;return}await this.load()},methods:{clearRecords(){window.$dialog?.warning({title:"清空下载记录",content:"将清空已完成 / 失败的历史记录，待提交与下载中的任务保留。下载器里已提交的任务不受影响，确认清空？",positiveText:"清空",negativeText:"取消",onPositiveClick:async()=>{this.clearing=!0;try{const e=await xe();window.$message?.success(e?.message||"已清空下载记录"),await this.load()}catch{}finally{this.clearing=!1}}})},async load(){this.loading=!0;try{const e=await ke(0);this.rows=Array.isArray(e)?e:[]}finally{this.loading=!1}}}},lt={class:"downloads-page"},nt={key:1,class:"downloads"},at={class:"downloads-head"},dt={key:0,class:"empty"};function ct(e,t,w,C,r,i){const s=_("n-tab-pane"),a=_("n-tabs"),p=_("HlsDownloadsPanel"),v=_("n-button"),b=_("n-data-table"),m=_("n-spin");return o(),c("div",lt,[g(a,{value:r.kind,"onUpdate:value":t[0]||(t[0]=u=>r.kind=u),type:"line"},{default:f(()=>[g(s,{name:"hls"},{tab:f(()=>[...t[1]||(t[1]=[B("离线剧集下载",-1)])]),_:1}),g(s,{name:"magnet"},{tab:f(()=>[...t[2]||(t[2]=[B("磁力下载",-1)])]),_:1})]),_:1},8,["value"]),r.kind==="hls"?(o(),k(p,{key:0})):(o(),c("div",nt,[n("div",at,[t[7]||(t[7]=n("h3",null,"下载任务",-1)),g(v,{size:"tiny",type:"error",ghost:"",disabled:!i.clearable,loading:r.clearing,onClick:i.clearRecords},{icon:f(()=>[...t[3]||(t[3]=[n("i",{class:"fa-solid fa-trash-can"},null,-1)])]),default:f(()=>[t[4]||(t[4]=B(" 清空下载记录 ",-1))]),_:1},8,["disabled","loading","onClick"]),g(v,{size:"tiny",loading:r.loading,onClick:i.load},{icon:f(()=>[...t[5]||(t[5]=[n("i",{class:"fa-solid fa-rotate"},null,-1)])]),default:f(()=>[t[6]||(t[6]=B(" 刷新 ",-1))]),_:1},8,["loading","onClick"])]),g(m,{show:r.loading},{default:f(()=>[r.rows.length?(o(),k(b,{key:1,size:"small",columns:r.columns,data:r.rows,bordered:!1,"max-height":560},null,8,["columns","data"])):(o(),c("div",dt,"还没有订阅触发的下载任务"))]),_:1},8,["show"])]))])}const Bt=V(st,[["render",ct],["__scopeId","data-v-b5331e44"]]);export{Bt as default};
