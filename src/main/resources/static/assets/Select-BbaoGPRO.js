import{a as mo,d as Co,b as ft,i as wt,g as yo,h as Ue,j as wo,V as Rt,P as xo,u as Ct,e as ko,f as So,B as Ro,c as zo}from"./create-D_ZLikh2.js";import{e as be,C as xt,g as se,c as A,d as $,a as ue,u as we,o as a,f as m,i as x,F as re,n as C,E as j,I as Et,h as De,j as qe,bJ as To,l as Ge,m as F,as as ee,J as Be,r as O,q as nt,x as kt,y as ze,H as Te,V as zt,b2 as Po,s as it,bK as Fo,bp as Io,ay as yt,aW as je,t as ne,aC as Mo,bL as Oo,aQ as Z,b as oe,A as Oe,aI as rt,aS as Bo,aw as St,bM as Tt,P as me,aR as Ae,p as _o,bN as vt,aE as _e,bd as Lt,D as Le,bk as gt,ba as At,bv as Dt,aX as $o,br as Eo,z as Lo,bO as Ao,aG as Ht,bP as Do,bQ as Ho,N as Vo,T as No,bi as Re,a0 as Wo,a1 as Ko,bR as Pt,bS as jo,aD as Uo,bT as qo,bU as Go,bV as Xo}from"./index-CA2o6hUt.js";import{a as Vt,S as Yo,u as Ft}from"./Input-C8jkNxbW.js";import{u as Zo}from"./use-compitable-DYgNP55y.js";var Jo=be({name:"Empty",render(){return(()=>{const e=xt("15c1a247ae156450");return e[0]||(e[0]=se("svg",{viewBox:"0 0 28 28",fill:"none",xmlns:"http://www.w3.org/2000/svg"},[se("path",{d:"M26 7.5C26 11.0899 23.0899 14 19.5 14C15.9101 14 13 11.0899 13 7.5C13 3.91015 15.9101 1 19.5 1C23.0899 1 26 3.91015 26 7.5ZM16.8536 4.14645C16.6583 3.95118 16.3417 3.95118 16.1464 4.14645C15.9512 4.34171 15.9512 4.65829 16.1464 4.85355L18.7929 7.5L16.1464 10.1464C15.9512 10.3417 15.9512 10.6583 16.1464 10.8536C16.3417 11.0488 16.6583 11.0488 16.8536 10.8536L19.5 8.20711L22.1464 10.8536C22.3417 11.0488 22.6583 11.0488 22.8536 10.8536C23.0488 10.6583 23.0488 10.3417 22.8536 10.1464L20.2071 7.5L22.8536 4.85355C23.0488 4.65829 23.0488 4.34171 22.8536 4.14645C22.6583 3.95118 22.3417 3.95118 22.1464 4.14645L19.5 6.79289L16.8536 4.14645Z",fill:"currentColor"}),se("path",{d:"M25 22.75V12.5991C24.5572 13.0765 24.053 13.4961 23.5 13.8454V16H17.5L17.3982 16.0068C17.0322 16.0565 16.75 16.3703 16.75 16.75C16.75 18.2688 15.5188 19.5 14 19.5C12.4812 19.5 11.25 18.2688 11.25 16.75L11.2432 16.6482C11.1935 16.2822 10.8797 16 10.5 16H4.5V7.25C4.5 6.2835 5.2835 5.5 6.25 5.5H12.2696C12.4146 4.97463 12.6153 4.47237 12.865 4H6.25C4.45507 4 3 5.45507 3 7.25V22.75C3 24.5449 4.45507 26 6.25 26H21.75C23.5449 26 25 24.5449 25 22.75ZM4.5 22.75V17.5H9.81597L9.85751 17.7041C10.2905 19.5919 11.9808 21 14 21L14.215 20.9947C16.2095 20.8953 17.842 19.4209 18.184 17.5H23.5V22.75C23.5 23.7165 22.7165 24.5 21.75 24.5H6.25C5.2835 24.5 4.5 23.7165 4.5 22.75Z",fill:"currentColor"})],-1))})()}}),Qo=A("empty",`
 display: flex;
 flex-direction: column;
 align-items: center;
 font-size: var(--n-font-size);
`,[$("icon",`
 width: var(--n-icon-size);
 height: var(--n-icon-size);
 font-size: var(--n-icon-size);
 line-height: var(--n-icon-size);
 color: var(--n-icon-color);
 transition:
 color .3s var(--n-bezier);
 `,[ue("+",[$("description",`
 margin-top: 8px;
 `)])]),$("description",`
 transition: color .3s var(--n-bezier);
 color: var(--n-text-color);
 `),$("extra",`
 text-align: center;
 transition: color .3s var(--n-bezier);
 margin-top: 12px;
 color: var(--n-extra-text-color);
 `)]);const en={...we.props,description:String,showDescription:{type:Boolean,default:!0},showIcon:{type:Boolean,default:!0},size:{type:String,default:"medium"},renderIcon:Function};var tn=be({name:"Empty",props:en,slots:Object,setup(e){const{mergedClsPrefixRef:t,inlineThemeDisabled:o,mergedComponentPropsRef:d}=qe(e),l=we("Empty","-empty",Qo,To,e,t),{localeRef:s}=Vt("Empty"),u=F(()=>e.description??d?.value?.Empty?.description),r=F(()=>d?.value?.Empty?.renderIcon||(()=>(a(),j(Jo)))),v=F(()=>{const{size:w}=e,{common:{cubicBezierEaseInOut:y},self:{[ee("iconSize",w)]:E,[ee("fontSize",w)]:k,textColor:S,iconColor:b,extraTextColor:M}}=l.value;return{"--n-icon-size":E,"--n-font-size":k,"--n-bezier":y,"--n-text-color":S,"--n-icon-color":b,"--n-extra-text-color":M}}),g=o?Ge("empty",F(()=>{let w="";const{size:y}=e;return w+=y[0],w}),v,e):void 0;return{mergedClsPrefix:t,mergedRenderIcon:r,localizedDescription:F(()=>u.value||s.value.description),cssVars:o?void 0:v,themeClass:g?.themeClass,onRender:g?.onRender}},render(){const{$slots:e,mergedClsPrefix:t,onRender:o}=this;return o?.(),a(),m("div",{class:x([`${t}-empty`,this.themeClass]),style:De(this.cssVars)},[this.showIcon?(a(),m("div",{key:0,class:x(`${t}-empty__icon`)},[e.icon?(a(),m(re,{key:0},[C(()=>e.icon())],64)):(a(),j(Et,{key:1,clsPrefix:t},{default:this.mergedRenderIcon},1032,["clsPrefix"]))],2)):C(()=>null),this.showDescription?(a(),m("div",{key:2,class:x(`${t}-empty__description`)},[e.default?(a(),m(re,{key:0},[C(()=>e.default())],64)):(a(),m(re,{key:1},[C(()=>this.localizedDescription)],64))],2)):C(()=>null),e.extra?(a(),m("div",{key:4,class:x(`${t}-empty__extra`)},[C(()=>e.extra())],2)):C(()=>null)],6)}});function It(e){return e&-e}class Nt{constructor(t,o){this.l=t,this.min=o;const d=new Array(t+1);for(let l=0;l<t+1;++l)d[l]=0;this.ft=d}add(t,o){if(o===0)return;const{l:d,ft:l}=this;for(t+=1;t<=d;)l[t]+=o,t+=It(t)}get(t){return this.sum(t+1)-this.sum(t)}sum(t){if(t===void 0&&(t=this.l),t<=0)return 0;const{ft:o,min:d,l}=this;if(t>l)throw new Error("[FinweckTree.sum]: `i` is larger than length.");let s=t*d;for(;t>0;)s+=o[t],t-=It(t);return s}getBound(t){let o=0,d=this.l;for(;d>o;){const l=Math.floor((o+d)/2),s=this.sum(l);if(s>t){d=l;continue}else if(s<t){if(o===l)return this.sum(o+1)<=t?o+1:l;o=l}else return l}return o}}let ot;function on(){return typeof document>"u"?!1:(ot===void 0&&("matchMedia"in window?ot=window.matchMedia("(pointer:coarse)").matches:ot=!1),ot)}let bt;function Mt(){return typeof document>"u"?1:(bt===void 0&&(bt="chrome"in window?window.devicePixelRatio:1),bt)}const Wt="VVirtualListXScroll";function nn({columnsRef:e,renderColRef:t,renderItemWithColsRef:o}){const d=O(0),l=O(0),s=F(()=>{const g=e.value;if(g.length===0)return null;const w=new Nt(g.length,0);return g.forEach((y,E)=>{w.add(E,y.width)}),w}),u=Be(()=>{const g=s.value;return g!==null?Math.max(g.getBound(l.value)-1,0):0}),r=g=>{const w=s.value;return w!==null?w.sum(g):0},v=Be(()=>{const g=s.value;return g!==null?Math.min(g.getBound(l.value+d.value)+1,e.value.length-1):0});return nt(Wt,{startIndexRef:u,endIndexRef:v,columnsRef:e,renderColRef:t,renderItemWithColsRef:o,getLeft:r}),{listWidthRef:d,scrollLeftRef:l}}const Ot=be({name:"VirtualListRow",props:{index:{type:Number,required:!0},item:{type:Object,required:!0}},setup(){const{startIndexRef:e,endIndexRef:t,columnsRef:o,getLeft:d,renderColRef:l,renderItemWithColsRef:s}=kt(Wt);return{startIndex:e,endIndex:t,columns:o,renderCol:l,renderItemWithCols:s,getLeft:d}},render(){const{startIndex:e,endIndex:t,columns:o,renderCol:d,renderItemWithCols:l,getLeft:s,item:u}=this;if(l!=null)return l({itemIndex:this.index,startColIndex:e,endColIndex:t,allColumns:o,item:u,getLeft:s});if(d!=null){const r=[];for(let v=e;v<=t;++v){const g=o[v];r.push(d({column:g,left:s(v),item:u}))}return r}return null}}),rn=ft(".v-vl",{maxHeight:"inherit",height:"100%",overflow:"auto",minWidth:"1px"},[ft("&:not(.v-vl--show-scrollbar)",{scrollbarWidth:"none"},[ft("&::-webkit-scrollbar, &::-webkit-scrollbar-track-piece, &::-webkit-scrollbar-thumb",{width:0,height:0,display:"none"})])]),ln=be({name:"VirtualList",inheritAttrs:!1,props:{showScrollbar:{type:Boolean,default:!0},columns:{type:Array,default:()=>[]},renderCol:Function,renderItemWithCols:Function,items:{type:Array,default:()=>[]},itemSize:{type:Number,required:!0},itemResizable:Boolean,itemsStyle:[String,Object],visibleItemsTag:{type:[String,Object],default:"div"},visibleItemsProps:Object,ignoreItemResize:Boolean,onScroll:Function,onWheel:Function,onResize:Function,defaultScrollKey:[Number,String],defaultScrollIndex:Number,keyField:{type:String,default:"key"},paddingTop:{type:[Number,String],default:0},paddingBottom:{type:[Number,String],default:0}},setup(e){const t=Po();rn.mount({id:"vueuc/virtual-list",head:!0,anchorMetaName:mo,ssr:t}),it(()=>{const{defaultScrollIndex:h,defaultScrollKey:p}=e;h!=null?M({index:h}):p!=null&&M({key:p})});let o=!1,d=!1;Fo(()=>{if(o=!1,!d){d=!0;return}M({top:k.value,left:u.value})}),Io(()=>{o=!0,d||(d=!0)});const l=Be(()=>{if(e.renderCol==null&&e.renderItemWithCols==null||e.columns.length===0)return;let h=0;return e.columns.forEach(p=>{h+=p.width}),h}),s=F(()=>{const h=new Map,{keyField:p}=e;return e.items.forEach((L,B)=>{h.set(L[p],B)}),h}),{scrollLeftRef:u,listWidthRef:r}=nn({columnsRef:ne(e,"columns"),renderColRef:ne(e,"renderCol"),renderItemWithColsRef:ne(e,"renderItemWithCols")}),v=O(null),g=O(void 0),w=new Map,y=F(()=>{const{items:h,itemSize:p,keyField:L}=e,B=new Nt(h.length,p);return h.forEach((Y,G)=>{const D=Y[L],J=w.get(D);J!==void 0&&B.add(G,J)}),B}),E=O(0),k=O(0),S=Be(()=>Math.max(y.value.getBound(k.value-yt(e.paddingTop))-1,0)),b=F(()=>{const{value:h}=g;if(h===void 0)return[];const{items:p,itemSize:L}=e,B=S.value,Y=Math.min(B+Math.ceil(h/L+1),p.length-1),G=[];for(let D=B;D<=Y;++D)G.push(p[D]);return G}),M=(h,p)=>{if(typeof h=="number"){Q(h,p,"auto");return}const{left:L,top:B,index:Y,key:G,position:D,behavior:J,debounce:K=!0}=h;if(L!==void 0||B!==void 0)Q(L,B,J);else if(Y!==void 0)U(Y,J,K);else if(G!==void 0){const ve=s.value.get(G);ve!==void 0&&U(ve,J,K)}else D==="bottom"?Q(0,Number.MAX_SAFE_INTEGER,J):D==="top"&&Q(0,0,J)};let z,R=null;function U(h,p,L){const B=v.value;if(B==null)return;const{value:Y}=y,G=Y.sum(h)+yt(e.paddingTop);if(!L)B.scrollTo({left:0,top:G,behavior:p});else{z=h,R!==null&&window.clearTimeout(R),R=window.setTimeout(()=>{z=void 0,R=null},16);const{scrollTop:D,offsetHeight:J}=B;if(G>D){const K=Y.get(h);G+K<=D+J||B.scrollTo({left:0,top:G+K-J,behavior:p})}else B.scrollTo({left:0,top:G,behavior:p})}}function Q(h,p,L){const B=v.value;B?.scrollTo({left:h,top:p,behavior:L})}function q(h,p){var L,B,Y;if(o||e.ignoreItemResize||N(p.target))return;const{value:G}=y,D=s.value.get(h),J=G.get(D),K=(Y=(B=(L=p.borderBoxSize)===null||L===void 0?void 0:L[0])===null||B===void 0?void 0:B.blockSize)!==null&&Y!==void 0?Y:p.contentRect.height;if(K===J)return;K-e.itemSize===0?w.delete(h):w.set(h,K-e.itemSize);const ce=K-J;if(ce===0)return;G.add(D,ce);const c=v.value;if(c!=null){if(z===void 0){const I=G.sum(D);c.scrollTop>I&&c.scrollBy(0,ce)}else if(D<z)c.scrollBy(0,ce);else if(D===z){const I=G.sum(D);K+I>c.scrollTop+c.offsetHeight&&c.scrollBy(0,ce)}te()}E.value++}const V=!on();let le=!1;function ie(h){var p;(p=e.onScroll)===null||p===void 0||p.call(e,h),(!V||!le)&&te()}function he(h){var p;if((p=e.onWheel)===null||p===void 0||p.call(e,h),V){const L=v.value;if(L!=null){if(h.deltaX===0&&(L.scrollTop===0&&h.deltaY<=0||L.scrollTop+L.offsetHeight>=L.scrollHeight&&h.deltaY>=0))return;h.preventDefault(),L.scrollTop+=h.deltaY/Mt(),L.scrollLeft+=h.deltaX/Mt(),te(),le=!0,Co(()=>{le=!1})}}}function fe(h){if(o||N(h.target))return;if(e.renderCol==null&&e.renderItemWithCols==null){if(h.contentRect.height===g.value)return}else if(h.contentRect.height===g.value&&h.contentRect.width===r.value)return;g.value=h.contentRect.height,r.value=h.contentRect.width;const{onResize:p}=e;p!==void 0&&p(h)}function te(){const{value:h}=v;h!=null&&(k.value=h.scrollTop,u.value=h.scrollLeft)}function N(h){let p=h;for(;p!==null;){if(p.style.display==="none")return!0;p=p.parentElement}return!1}return{listHeight:g,listStyle:{overflow:"auto"},keyToIndex:s,itemsStyle:F(()=>{const{itemResizable:h}=e,p=je(y.value.sum());return E.value,[e.itemsStyle,{boxSizing:"content-box",width:je(l.value),height:h?"":p,minHeight:h?p:"",paddingTop:je(e.paddingTop),paddingBottom:je(e.paddingBottom)}]}),visibleItemsStyle:F(()=>(E.value,{transform:`translateY(${je(y.value.sum(S.value))})`})),viewportItems:b,listElRef:v,itemsElRef:O(null),scrollTo:M,handleListResize:fe,handleListScroll:ie,handleListWheel:he,handleItemResize:q}},render(){const{itemResizable:e,keyField:t,keyToIndex:o,visibleItemsTag:d}=this;return ze(zt,{onResize:this.handleListResize},{default:()=>{var l,s;return ze("div",Te(this.$attrs,{class:["v-vl",this.showScrollbar&&"v-vl--show-scrollbar"],onScroll:this.handleListScroll,onWheel:this.handleListWheel,ref:"listElRef"}),[this.items.length!==0?ze("div",{ref:"itemsElRef",class:"v-vl-items",style:this.itemsStyle},[ze(d,Object.assign({class:"v-vl-visible-items",style:this.visibleItemsStyle},this.visibleItemsProps),{default:()=>{const{renderCol:u,renderItemWithCols:r}=this;return this.viewportItems.map(v=>{const g=v[t],w=o.get(g),y=u!=null?ze(Ot,{index:w,item:v}):void 0,E=r!=null?ze(Ot,{index:w,item:v}):void 0,k=this.$slots.default({item:v,renderedCols:y,renderedItemWithCols:E,index:w})[0];return e?ze(zt,{key:g,onResize:S=>this.handleItemResize(g,S)},{default:()=>k}):(k.key=g,k)})}})]):(s=(l=this.$slots).empty)===null||s===void 0?void 0:s.call(l)])}})}});function an(e){const{textColor2:t,primaryColorHover:o,primaryColorPressed:d,primaryColor:l,infoColor:s,successColor:u,warningColor:r,errorColor:v,baseColor:g,borderColor:w,opacityDisabled:y,tagColor:E,closeIconColor:k,closeIconColorHover:S,closeIconColorPressed:b,borderRadiusSmall:M,fontSizeMini:z,fontSizeTiny:R,fontSizeSmall:U,fontSizeMedium:Q,heightMini:q,heightTiny:V,heightSmall:le,heightMedium:ie,closeColorHover:he,closeColorPressed:fe,buttonColor2Hover:te,buttonColor2Pressed:N,fontWeightStrong:h}=e;return{...Oo,closeBorderRadius:M,heightTiny:q,heightSmall:V,heightMedium:le,heightLarge:ie,borderRadius:M,opacityDisabled:y,fontSizeTiny:z,fontSizeSmall:R,fontSizeMedium:U,fontSizeLarge:Q,fontWeightStrong:h,textColorCheckable:t,textColorHoverCheckable:t,textColorPressedCheckable:t,textColorChecked:g,colorCheckable:"#0000",colorHoverCheckable:te,colorPressedCheckable:N,colorChecked:l,colorCheckedHover:o,colorCheckedPressed:d,border:`1px solid ${w}`,textColor:t,color:E,colorBordered:"rgb(250, 250, 252)",closeIconColor:k,closeIconColorHover:S,closeIconColorPressed:b,closeColorHover:he,closeColorPressed:fe,borderPrimary:`1px solid ${Z(l,{alpha:.3})}`,textColorPrimary:l,colorPrimary:Z(l,{alpha:.12}),colorBorderedPrimary:Z(l,{alpha:.1}),closeIconColorPrimary:l,closeIconColorHoverPrimary:l,closeIconColorPressedPrimary:l,closeColorHoverPrimary:Z(l,{alpha:.12}),closeColorPressedPrimary:Z(l,{alpha:.18}),borderInfo:`1px solid ${Z(s,{alpha:.3})}`,textColorInfo:s,colorInfo:Z(s,{alpha:.12}),colorBorderedInfo:Z(s,{alpha:.1}),closeIconColorInfo:s,closeIconColorHoverInfo:s,closeIconColorPressedInfo:s,closeColorHoverInfo:Z(s,{alpha:.12}),closeColorPressedInfo:Z(s,{alpha:.18}),borderSuccess:`1px solid ${Z(u,{alpha:.3})}`,textColorSuccess:u,colorSuccess:Z(u,{alpha:.12}),colorBorderedSuccess:Z(u,{alpha:.1}),closeIconColorSuccess:u,closeIconColorHoverSuccess:u,closeIconColorPressedSuccess:u,closeColorHoverSuccess:Z(u,{alpha:.12}),closeColorPressedSuccess:Z(u,{alpha:.18}),borderWarning:`1px solid ${Z(r,{alpha:.35})}`,textColorWarning:r,colorWarning:Z(r,{alpha:.15}),colorBorderedWarning:Z(r,{alpha:.12}),closeIconColorWarning:r,closeIconColorHoverWarning:r,closeIconColorPressedWarning:r,closeColorHoverWarning:Z(r,{alpha:.12}),closeColorPressedWarning:Z(r,{alpha:.18}),borderError:`1px solid ${Z(v,{alpha:.23})}`,textColorError:v,colorError:Z(v,{alpha:.1}),colorBorderedError:Z(v,{alpha:.08}),closeIconColorError:v,closeIconColorHoverError:v,closeIconColorPressedError:v,closeColorHoverError:Z(v,{alpha:.12}),closeColorPressedError:Z(v,{alpha:.18})}}const sn={common:Mo,self:an};var cn={color:Object,type:{type:String,default:"default"},round:Boolean,size:String,closable:Boolean,disabled:{type:Boolean,default:void 0}},dn=A("tag",`
 --n-close-margin: var(--n-close-margin-top) var(--n-close-margin-right) var(--n-close-margin-bottom) var(--n-close-margin-left);
 white-space: nowrap;
 position: relative;
 box-sizing: border-box;
 cursor: default;
 display: inline-flex;
 align-items: center;
 flex-wrap: nowrap;
 padding: var(--n-padding);
 border-radius: var(--n-border-radius);
 color: var(--n-text-color);
 background-color: var(--n-color);
 transition: 
 border-color .3s var(--n-bezier),
 background-color .3s var(--n-bezier),
 color .3s var(--n-bezier),
 box-shadow .3s var(--n-bezier),
 opacity .3s var(--n-bezier);
 line-height: 1;
 height: var(--n-height);
 font-size: var(--n-font-size);
`,[oe("strong",`
 font-weight: var(--n-font-weight-strong);
 `),$("border",`
 pointer-events: none;
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 border-radius: inherit;
 border: var(--n-border);
 transition: border-color .3s var(--n-bezier);
 `),$("icon",`
 display: flex;
 margin: 0 4px 0 0;
 color: var(--n-text-color);
 transition: color .3s var(--n-bezier);
 font-size: var(--n-avatar-size-override);
 `),$("avatar",`
 display: flex;
 margin: 0 6px 0 0;
 `),$("close",`
 margin: var(--n-close-margin);
 transition:
 background-color .3s var(--n-bezier),
 color .3s var(--n-bezier);
 `),oe("round",`
 padding: 0 calc(var(--n-height) / 3);
 border-radius: calc(var(--n-height) / 2);
 `,[$("icon",`
 margin: 0 4px 0 calc((var(--n-height) - 8px) / -2);
 `),$("avatar",`
 margin: 0 6px 0 calc((var(--n-height) - 8px) / -2);
 `),oe("closable",`
 padding: 0 calc(var(--n-height) / 4) 0 calc(var(--n-height) / 3);
 `)]),oe("icon, avatar",[oe("round",`
 padding: 0 calc(var(--n-height) / 3) 0 calc(var(--n-height) / 2);
 `)]),oe("disabled",`
 cursor: not-allowed !important;
 opacity: var(--n-opacity-disabled);
 `),oe("checkable",`
 cursor: pointer;
 box-shadow: none;
 color: var(--n-text-color-checkable);
 background-color: var(--n-color-checkable);
 `,[Oe("disabled",[ue("&:hover","background-color: var(--n-color-hover-checkable);",[Oe("checked","color: var(--n-text-color-hover-checkable);")]),ue("&:active","background-color: var(--n-color-pressed-checkable);",[Oe("checked","color: var(--n-text-color-pressed-checkable);")])]),oe("checked",`
 color: var(--n-text-color-checked);
 background-color: var(--n-color-checked);
 `,[Oe("disabled",[ue("&:hover","background-color: var(--n-color-checked-hover);"),ue("&:active","background-color: var(--n-color-checked-pressed);")])])])]);const un=["onClick","onMouseenter","onMouseleave"],hn={...we.props,...cn,bordered:{type:Boolean,default:void 0},checked:Boolean,checkable:Boolean,strong:Boolean,triggerClickOnClose:Boolean,onClose:[Array,Function],onMouseenter:Function,onMouseleave:Function,"onUpdate:checked":Function,onUpdateChecked:Function,internalCloseFocusable:{type:Boolean,default:!0},internalCloseIsButtonTag:{type:Boolean,default:!0},onCheckedChange:Function},fn=_o("n-tag");var pt=be({name:"Tag",props:hn,slots:Object,setup(e){const t=O(null),{mergedBorderedRef:o,mergedClsPrefixRef:d,inlineThemeDisabled:l,mergedRtlRef:s,mergedComponentPropsRef:u}=qe(e),r=F(()=>e.size||u?.value?.Tag?.size||"medium"),v=we("Tag","-tag",dn,sn,e,d);nt(fn,{roundRef:ne(e,"round")});function g(){if(!e.disabled&&e.checkable){const{checked:b,onCheckedChange:M,onUpdateChecked:z,"onUpdate:checked":R}=e;z&&z(!b),R&&R(!b),M&&M(!b)}}function w(b){if(e.triggerClickOnClose||b.stopPropagation(),!e.disabled){const{onClose:M}=e;M&&me(M,b)}}const y={setTextContent(b){const{value:M}=t;M&&(M.textContent=b)}},E=St("Tag",s,d),k=F(()=>{const{type:b,color:{color:M,textColor:z}={}}=e,R=r.value,{common:{cubicBezierEaseInOut:U},self:{padding:Q,closeMargin:q,borderRadius:V,opacityDisabled:le,textColorCheckable:ie,textColorHoverCheckable:he,textColorPressedCheckable:fe,textColorChecked:te,colorCheckable:N,colorHoverCheckable:h,colorPressedCheckable:p,colorChecked:L,colorCheckedHover:B,colorCheckedPressed:Y,closeBorderRadius:G,fontWeightStrong:D,[ee("colorBordered",b)]:J,[ee("closeSize",R)]:K,[ee("closeIconSize",R)]:ve,[ee("fontSize",R)]:ce,[ee("height",R)]:c,[ee("color",b)]:I,[ee("textColor",b)]:ge,[ee("border",b)]:ke,[ee("closeIconColor",b)]:xe,[ee("closeIconColorHover",b)]:pe,[ee("closeIconColorPressed",b)]:de,[ee("closeColorHover",b)]:Se,[ee("closeColorPressed",b)]:Ce}}=v.value,ye=Ae(q);return{"--n-font-weight-strong":D,"--n-avatar-size-override":`calc(${c} - 8px)`,"--n-bezier":U,"--n-border-radius":V,"--n-border":ke,"--n-close-icon-size":ve,"--n-close-color-pressed":Ce,"--n-close-color-hover":Se,"--n-close-border-radius":G,"--n-close-icon-color":xe,"--n-close-icon-color-hover":pe,"--n-close-icon-color-pressed":de,"--n-close-icon-color-disabled":xe,"--n-close-margin-top":ye.top,"--n-close-margin-right":ye.right,"--n-close-margin-bottom":ye.bottom,"--n-close-margin-left":ye.left,"--n-close-size":K,"--n-color":M||(o.value?J:I),"--n-color-checkable":N,"--n-color-checked":L,"--n-color-checked-hover":B,"--n-color-checked-pressed":Y,"--n-color-hover-checkable":h,"--n-color-pressed-checkable":p,"--n-font-size":ce,"--n-height":c,"--n-opacity-disabled":le,"--n-padding":Q,"--n-text-color":z||ge,"--n-text-color-checkable":ie,"--n-text-color-checked":te,"--n-text-color-hover-checkable":he,"--n-text-color-pressed-checkable":fe}}),S=l?Ge("tag",F(()=>{let b="";const{type:M,color:{color:z,textColor:R}={}}=e;return b+=M[0],b+=r.value[0],z&&(b+=`a${Tt(z)}`),R&&(b+=`b${Tt(R)}`),o.value&&(b+="c"),b}),k,e):void 0;return{...y,rtlEnabled:E,mergedClsPrefix:d,contentRef:t,mergedBordered:o,handleClick:g,handleCloseClick:w,cssVars:l?void 0:k,themeClass:S?.themeClass,onRender:S?.onRender}},render(){const{mergedClsPrefix:e,rtlEnabled:t,closable:o,color:{borderColor:d}={},round:l,onRender:s,$slots:u}=this;s?.();const r=rt(u.avatar,g=>g&&(a(),m("div",{class:x(`${e}-tag__avatar`)},[C(()=>g)],2))),v=rt(u.icon,g=>g&&(a(),m("div",{class:x(`${e}-tag__icon`)},[C(()=>g)],2)));return a(),m("div",{class:x([`${e}-tag`,this.themeClass,{[`${e}-tag--rtl`]:t,[`${e}-tag--strong`]:this.strong,[`${e}-tag--disabled`]:this.disabled,[`${e}-tag--checkable`]:this.checkable,[`${e}-tag--checked`]:this.checkable&&this.checked,[`${e}-tag--round`]:l,[`${e}-tag--avatar`]:r,[`${e}-tag--icon`]:v,[`${e}-tag--closable`]:o}]),style:De(this.cssVars),onClick:this.handleClick,onMouseenter:this.onMouseenter,onMouseleave:this.onMouseleave},[C(()=>v||r),se("span",{class:x(`${e}-tag__content`),ref:"contentRef"},[C(()=>this.$slots.default?.())],2),!this.checkable&&o?(a(),j(Bo,{key:0,clsPrefix:e,class:x(`${e}-tag__close`),disabled:this.disabled,onClick:this.handleCloseClick,focusable:this.internalCloseFocusable,round:l,isButtonTag:this.internalCloseIsButtonTag,absolute:!0},null,8,["clsPrefix","class","disabled","onClick","focusable","round","isButtonTag"])):C(()=>null),!this.checkable&&this.mergedBordered?(a(),m("div",{key:2,class:x(`${e}-tag__border`),style:De({borderColor:d})},null,6)):C(()=>null)],46,un)}});function Bt(e){switch(typeof e){case"string":return e||void 0;case"number":return String(e);default:return}}function Kt(e,t){t&&(it(()=>{const{value:o}=e;o&&vt.registerHandler(o,t)}),_e(e,(o,d)=>{d&&vt.unregisterHandler(d)},{deep:!1}),Lt(()=>{const{value:o}=e;o&&vt.unregisterHandler(o)}))}var vn=be({props:{onFocus:Function,onBlur:Function},setup(e){return()=>(()=>{const t=xt("d16ead82505dc285");return a(),m("div",{style:"width: 0; height: 0",tabindex:0,onFocus:t[0]||(t[0]=o=>e.onFocus?.(o)),onBlur:t[1]||(t[1]=o=>e.onBlur?.(o))},null,32)})()}}),gn=vn,_t=be({name:"NBaseSelectGroupHeader",props:{clsPrefix:{type:String,required:!0},tmNode:{type:Object,required:!0}},setup(){const{renderLabelRef:e,renderOptionRef:t,labelFieldRef:o,nodePropsRef:d}=kt(wt);return{labelField:o,nodeProps:d,renderLabel:e,renderOption:t}},render(){const{clsPrefix:e,renderLabel:t,renderOption:o,nodeProps:d,tmNode:{rawNode:l}}=this,s=d?.(l),u=t?t(l,!1):Le(l[this.labelField],l,!1),r=(a(),m("div",Te(s,{class:[`${e}-base-select-group-header`,s?.class]}),[C(()=>u)],16));return l.render?l.render({node:r,option:l}):o?o({node:r,option:l,selected:!1}):r}}),bn=be({name:"Checkmark",render(){return(()=>{const e=xt("3c84eac8ae4e1f96");return e[0]||(e[0]=se("svg",{xmlns:"http://www.w3.org/2000/svg",viewBox:"0 0 16 16"},[se("g",{fill:"none"},[se("path",{d:"M14.046 3.486a.75.75 0 0 1-.032 1.06l-7.93 7.474a.85.85 0 0 1-1.188-.022l-2.68-2.72a.75.75 0 1 1 1.068-1.053l2.234 2.267l7.468-7.038a.75.75 0 0 1 1.06.032z",fill:"currentColor"})])],-1))})()}});const pn=["onClick","onMouseenter","onMousemove"];function mn(e,t){return a(),j(At,{name:"fade-in-scale-up-transition"},{default:()=>e?(a(),j(Et,{key:1,clsPrefix:t,class:x(`${t}-base-select-option__check`)},{default:()=>ze(bn)},1032,["clsPrefix","class"])):null},1024)}var $t=be({name:"NBaseSelectOption",props:{clsPrefix:{type:String,required:!0},tmNode:{type:Object,required:!0}},setup(e){const{valueRef:t,pendingTmNodeRef:o,multipleRef:d,valueSetRef:l,renderLabelRef:s,renderOptionRef:u,labelFieldRef:r,valueFieldRef:v,showCheckmarkRef:g,nodePropsRef:w,handleOptionClick:y,handleOptionMouseEnter:E}=kt(wt),k=Be(()=>{const{value:z}=o;return z?e.tmNode.key===z.key:!1});function S(z){const{tmNode:R}=e;R.disabled||y(z,R)}function b(z){const{tmNode:R}=e;R.disabled||E(z,R)}function M(z){const{tmNode:R}=e,{value:U}=k;R.disabled||U||E(z,R)}return{multiple:d,isGrouped:Be(()=>{const{tmNode:z}=e,{parent:R}=z;return R&&R.rawNode.type==="group"}),showCheckmark:g,nodeProps:w,isPending:k,isSelected:Be(()=>{const{value:z}=t,{value:R}=d;if(z===null)return!1;const U=e.tmNode.rawNode[v.value];if(R){const{value:Q}=l;return Q.has(U)}else return z===U}),labelField:r,renderLabel:s,renderOption:u,handleMouseMove:M,handleMouseEnter:b,handleClick:S}},render(){const{clsPrefix:e,tmNode:{rawNode:t},isSelected:o,isPending:d,isGrouped:l,showCheckmark:s,nodeProps:u,renderOption:r,renderLabel:v,handleClick:g,handleMouseEnter:w,handleMouseMove:y}=this,E=mn(o,e),k=v?[v(t,o),s&&E]:[Le(t[this.labelField],t,o),s&&E],S=u?.(t),b=(a(),m("div",Te(S,{class:[`${e}-base-select-option`,t.class,S?.class,{[`${e}-base-select-option--disabled`]:t.disabled,[`${e}-base-select-option--selected`]:o,[`${e}-base-select-option--grouped`]:l,[`${e}-base-select-option--pending`]:d,[`${e}-base-select-option--show-checkmark`]:s}],style:[S?.style||"",t.style||""],onClick:gt([g,S?.onClick]),onMouseenter:gt([w,S?.onMouseenter]),onMousemove:gt([y,S?.onMousemove])}),[se("div",{class:x(`${e}-base-select-option__content`)},[C(()=>k)],2)],16,pn));return t.render?t.render({node:b,option:t,selected:o}):r?r({node:b,option:t,selected:o}):b}}),Cn=A("base-select-menu",`
 line-height: 1.5;
 outline: none;
 z-index: 0;
 position: relative;
 border-radius: var(--n-border-radius);
 transition:
 background-color .3s var(--n-bezier),
 box-shadow .3s var(--n-bezier);
 background-color: var(--n-color);
`,[A("scrollbar",`
 max-height: var(--n-height);
 `),A("virtual-list",`
 max-height: var(--n-height);
 `),A("base-select-option",`
 min-height: var(--n-option-height);
 font-size: var(--n-option-font-size);
 display: flex;
 align-items: center;
 `,[$("content",`
 z-index: 1;
 white-space: nowrap;
 text-overflow: ellipsis;
 overflow: hidden;
 `)]),A("base-select-group-header",`
 min-height: var(--n-option-height);
 font-size: .93em;
 display: flex;
 align-items: center;
 `),A("base-select-menu-option-wrapper",`
 position: relative;
 width: 100%;
 `),$("loading, empty",`
 display: flex;
 padding: 12px 32px;
 flex: 1;
 justify-content: center;
 `),$("loading",`
 color: var(--n-loading-color);
 font-size: var(--n-loading-size);
 `),$("header",`
 padding: 8px var(--n-option-padding-left);
 font-size: var(--n-option-font-size);
 transition: 
 color .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 border-bottom: 1px solid var(--n-action-divider-color);
 color: var(--n-action-text-color);
 `),$("action",`
 padding: 8px var(--n-option-padding-left);
 font-size: var(--n-option-font-size);
 transition: 
 color .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 border-top: 1px solid var(--n-action-divider-color);
 color: var(--n-action-text-color);
 `),A("base-select-group-header",`
 position: relative;
 cursor: default;
 padding: var(--n-option-padding);
 color: var(--n-group-header-text-color);
 `),A("base-select-option",`
 cursor: pointer;
 position: relative;
 padding: var(--n-option-padding);
 transition:
 color .3s var(--n-bezier),
 opacity .3s var(--n-bezier);
 box-sizing: border-box;
 color: var(--n-option-text-color);
 opacity: 1;
 `,[oe("show-checkmark",`
 padding-right: calc(var(--n-option-padding-right) + 20px);
 `),ue("&::before",`
 content: "";
 position: absolute;
 left: 4px;
 right: 4px;
 top: 0;
 bottom: 0;
 border-radius: var(--n-border-radius);
 transition: background-color .3s var(--n-bezier);
 `),ue("&:active",`
 color: var(--n-option-text-color-pressed);
 `),oe("grouped",`
 padding-left: calc(var(--n-option-padding-left) * 1.5);
 `),oe("pending",[ue("&::before",`
 background-color: var(--n-option-color-pending);
 `)]),oe("selected",`
 color: var(--n-option-text-color-active);
 `,[ue("&::before",`
 background-color: var(--n-option-color-active);
 `),oe("pending",[ue("&::before",`
 background-color: var(--n-option-color-active-pending);
 `)])]),oe("disabled",`
 cursor: not-allowed;
 `,[Oe("selected",`
 color: var(--n-option-text-color-disabled);
 `),oe("selected",`
 opacity: var(--n-option-opacity-disabled);
 `)]),$("check",`
 font-size: 16px;
 position: absolute;
 right: calc(var(--n-option-padding-right) - 4px);
 top: calc(50% - 7px);
 color: var(--n-option-check-color);
 transition: color .3s var(--n-bezier);
 `,[Dt({enterScale:"0.5"})])])]);const yn=["tabindex","onFocusin","onFocusout","onKeyup","onKeydown","onMousedown","onMouseenter","onMouseleave"];var wn=be({name:"InternalSelectMenu",props:{...we.props,clsPrefix:{type:String,required:!0},scrollable:{type:Boolean,default:!0},treeMate:{type:Object,required:!0},multiple:Boolean,size:{type:String,default:"medium"},value:{type:[String,Number,Array],default:null},autoPending:Boolean,virtualScroll:{type:Boolean,default:!0},show:{type:Boolean,default:!0},labelField:{type:String,default:"label"},valueField:{type:String,default:"value"},loading:Boolean,focusable:Boolean,renderLabel:Function,renderOption:Function,nodeProps:Function,showCheckmark:{type:Boolean,default:!0},onMousedown:Function,onScroll:Function,onFocus:Function,onBlur:Function,onKeyup:Function,onKeydown:Function,onTabOut:Function,onMouseenter:Function,onMouseleave:Function,onResize:Function,resetMenuOnOptionsChange:{type:Boolean,default:!0},inlineThemeDisabled:Boolean,scrollbarProps:Object,onToggle:Function},setup(e){const{mergedClsPrefixRef:t,mergedRtlRef:o,mergedComponentPropsRef:d}=qe(e),l=St("InternalSelectMenu",o,t),s=we("InternalSelectMenu","-internal-select-menu",Cn,Ao,e,ne(e,"clsPrefix")),u=O(null),r=O(null),v=O(null),g=F(()=>e.treeMate.getFlattenedNodes()),w=F(()=>yo(g.value)),y=O(null);function E(){const{treeMate:c}=e;let I=null;const{value:ge}=e;ge===null?I=c.getFirstAvailableNode():(e.multiple?I=c.getNode((ge||[])[(ge||[]).length-1]):I=c.getNode(ge),(!I||I.disabled)&&(I=c.getFirstAvailableNode())),B(I||null)}function k(){const{value:c}=y;c&&!e.treeMate.getNode(c.key)&&(y.value=null)}let S;_e(()=>e.show,c=>{c?S=_e(()=>e.treeMate,()=>{e.resetMenuOnOptionsChange?(e.autoPending?E():k(),Ht(Y)):k()},{immediate:!0}):S?.()},{immediate:!0}),Lt(()=>{S?.()});const b=F(()=>yt(s.value.self[ee("optionHeight",e.size)])),M=F(()=>Ae(s.value.self[ee("padding",e.size)])),z=F(()=>e.multiple&&Array.isArray(e.value)?new Set(e.value):new Set),R=F(()=>{const c=g.value;return c&&c.length===0}),U=F(()=>d?.value?.Select?.renderEmpty);function Q(c){const{onToggle:I}=e;I&&I(c)}function q(c){const{onScroll:I}=e;I&&I(c)}function V(c){v.value?.sync(),q(c)}function le(){v.value?.sync()}function ie(){const{value:c}=y;return c||null}function he(c,I){I.disabled||B(I,!1)}function fe(c,I){I.disabled||Q(I)}function te(c){Ue(c,"action")||e.onKeyup?.(c)}function N(c){Ue(c,"action")||e.onKeydown?.(c)}function h(c){e.onMousedown?.(c),!e.focusable&&c.preventDefault()}function p(){const{value:c}=y;c&&B(c.getNext({loop:!0}),!0)}function L(){const{value:c}=y;c&&B(c.getPrev({loop:!0}),!0)}function B(c,I=!1){y.value=c,I&&Y()}function Y(){const c=y.value;if(!c)return;const I=w.value(c.key);I!==null&&(e.virtualScroll?r.value?.scrollTo({index:I}):v.value?.scrollTo({index:I,elSize:b.value}))}function G(c){u.value?.contains(c.target)&&e.onFocus?.(c)}function D(c){u.value?.contains(c.relatedTarget)||e.onBlur?.(c)}nt(wt,{handleOptionMouseEnter:he,handleOptionClick:fe,valueSetRef:z,pendingTmNodeRef:y,nodePropsRef:ne(e,"nodeProps"),showCheckmarkRef:ne(e,"showCheckmark"),multipleRef:ne(e,"multiple"),valueRef:ne(e,"value"),renderLabelRef:ne(e,"renderLabel"),renderOptionRef:ne(e,"renderOption"),labelFieldRef:ne(e,"labelField"),valueFieldRef:ne(e,"valueField")}),nt(wo,u),it(()=>{const{value:c}=v;c&&c.sync()});const J=F(()=>{const{size:c}=e,{common:{cubicBezierEaseInOut:I},self:{height:ge,borderRadius:ke,color:xe,groupHeaderTextColor:pe,actionDividerColor:de,optionTextColorPressed:Se,optionTextColor:Ce,optionTextColorDisabled:ye,optionTextColorActive:He,optionOpacityDisabled:Ve,optionCheckColor:Pe,actionTextColor:Fe,optionColorPending:Ne,optionColorActive:We,loadingColor:Ke,loadingSize:$e,optionColorActivePending:Ee,[ee("optionFontSize",c)]:Ie,[ee("optionHeight",c)]:i,[ee("optionPadding",c)]:_}}=s.value;return{"--n-height":ge,"--n-action-divider-color":de,"--n-action-text-color":Fe,"--n-bezier":I,"--n-border-radius":ke,"--n-color":xe,"--n-option-font-size":Ie,"--n-group-header-text-color":pe,"--n-option-check-color":Pe,"--n-option-color-pending":Ne,"--n-option-color-active":We,"--n-option-color-active-pending":Ee,"--n-option-height":i,"--n-option-opacity-disabled":Ve,"--n-option-text-color":Ce,"--n-option-text-color-active":He,"--n-option-text-color-disabled":ye,"--n-option-text-color-pressed":Se,"--n-option-padding":_,"--n-option-padding-left":Ae(_,"left"),"--n-option-padding-right":Ae(_,"right"),"--n-loading-color":Ke,"--n-loading-size":$e}}),{inlineThemeDisabled:K}=e,ve=K?Ge("internal-select-menu",F(()=>e.size[0]),J,e):void 0,ce={selfRef:u,next:p,prev:L,getPendingTmNode:ie};return Kt(u,e.onResize),{mergedTheme:s,mergedClsPrefix:t,rtlEnabled:l,virtualListRef:r,scrollbarRef:v,itemSize:b,padding:M,flattenedNodes:g,empty:R,mergedRenderEmpty:U,virtualListContainer(){const{value:c}=r;return c?.listElRef},virtualListContent(){const{value:c}=r;return c?.itemsElRef},doScroll:q,handleFocusin:G,handleFocusout:D,handleKeyUp:te,handleKeyDown:N,handleMouseDown:h,handleVirtualListResize:le,handleVirtualListScroll:V,cssVars:K?void 0:J,themeClass:ve?.themeClass,onRender:ve?.onRender,...ce}},render(){const{$slots:e,virtualScroll:t,clsPrefix:o,mergedTheme:d,themeClass:l,onRender:s}=this;return s?.(),a(),m("div",{ref:"selfRef",tabindex:this.focusable?0:-1,class:x([`${o}-base-select-menu`,`${o}-base-select-menu--${this.size}-size`,this.rtlEnabled&&`${o}-base-select-menu--rtl`,l,this.multiple&&`${o}-base-select-menu--multiple`]),style:De(this.cssVars),onFocusin:this.handleFocusin,onFocusout:this.handleFocusout,onKeyup:this.handleKeyUp,onKeydown:this.handleKeyDown,onMousedown:this.handleMouseDown,onMouseenter:this.onMouseenter,onMouseleave:this.onMouseleave},[C(()=>rt(e.header,u=>u&&(a(),m("div",{class:x(`${o}-base-select-menu__header`),"data-header":!0,key:"header"},[C(()=>u)],2)))),this.loading?(a(),m("div",{key:0,class:x(`${o}-base-select-menu__loading`)},[(a(),j($o,{clsPrefix:o,strokeWidth:20},null,8,["clsPrefix"]))],2)):(a(),m(re,{key:1},[this.empty?(a(),m("div",{key:1,class:x(`${o}-base-select-menu__empty`),"data-empty":!0},[C(()=>Lo(e.empty,()=>[this.mergedRenderEmpty?.()||(a(),j(tn,{theme:d.peers.Empty,themeOverrides:d.peerOverrides.Empty,size:this.size},null,8,["theme","themeOverrides","size"]))]))],2)):(a(),j(Eo,Te({key:0,ref:"scrollbarRef",theme:d.peers.Scrollbar,themeOverrides:d.peerOverrides.Scrollbar,scrollable:this.scrollable,container:t?this.virtualListContainer:void 0,content:t?this.virtualListContent:void 0,onScroll:t?void 0:this.doScroll},this.scrollbarProps),{default:()=>t?(a(),j(ln,{key:1,ref:"virtualListRef",class:x(`${o}-virtual-list`),items:this.flattenedNodes,itemSize:this.itemSize,showScrollbar:!1,paddingTop:this.padding.top,paddingBottom:this.padding.bottom,onResize:this.handleVirtualListResize,onScroll:this.handleVirtualListScroll,itemResizable:!0},{default:({item:u})=>u.isGroup?(a(),j(_t,{key:u.key,clsPrefix:o,tmNode:u},null,8,["clsPrefix","tmNode"])):u.ignored?null:(a(),j($t,{clsPrefix:o,key:u.key,tmNode:u},null,8,["clsPrefix","tmNode"]))},1032,["class","items","itemSize","paddingTop","paddingBottom","onResize","onScroll"])):(a(),m("div",{key:4,class:x(`${o}-base-select-menu-option-wrapper`),style:De({paddingTop:this.padding.top,paddingBottom:this.padding.bottom})},[C(()=>this.flattenedNodes.map(u=>u.isGroup?(a(),j(_t,{key:u.key,clsPrefix:o,tmNode:u},null,8,["clsPrefix","tmNode"])):(a(),j($t,{clsPrefix:o,key:u.key,tmNode:u},null,8,["clsPrefix","tmNode"]))))],6))},1040,["theme","themeOverrides","scrollable","container","content","onScroll"]))],64)),C(()=>rt(e.action,u=>u&&[(a(),m("div",{class:x(`${o}-base-select-menu__action`),"data-action":!0,key:"action"},[C(()=>u)],2)),(a(),j(gn,{onFocus:this.onTabOut,key:"focus-detector"},null,8,["onFocus"]))]))],46,yn)}});function lt(e){return e.type==="group"}function jt(e){return e.type==="ignored"}function mt(e,t){try{return!!(1+t.toString().toLowerCase().indexOf(e.trim().toLowerCase()))}catch{return!1}}function xn(e,t){return{getIsGroup:lt,getIgnored:jt,getKey(o){return lt(o)?o.name||o.key||"key-required":o[e]},getChildren(o){return o[t]}}}function kn(e,t,o,d){if(!t)return e;function l(s){if(!Array.isArray(s))return[];const u=[];for(const r of s)if(lt(r)){const v=l(r[d]);v.length&&u.push(Object.assign({},r,{[d]:v}))}else{if(jt(r))continue;t(o,r)&&u.push(r)}return u}return l(e)}function Sn(e,t,o){const d=new Map;return e.forEach(l=>{lt(l)?l[o].forEach(s=>{d.set(s[t],s)}):d.set(l[t],l)}),d}var Rn=ue([A("base-selection",`
 --n-padding-single: var(--n-padding-single-top) var(--n-padding-single-right) var(--n-padding-single-bottom) var(--n-padding-single-left);
 --n-padding-multiple: var(--n-padding-multiple-top) var(--n-padding-multiple-right) var(--n-padding-multiple-bottom) var(--n-padding-multiple-left);
 position: relative;
 z-index: auto;
 box-shadow: none;
 width: 100%;
 max-width: 100%;
 display: inline-block;
 vertical-align: bottom;
 border-radius: var(--n-border-radius);
 min-height: var(--n-height);
 line-height: 1.5;
 font-size: var(--n-font-size);
 `,[A("base-loading",`
 color: var(--n-loading-color);
 `),A("base-selection-tags","min-height: var(--n-height);"),$("border, state-border",`
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 pointer-events: none;
 border: var(--n-border);
 border-radius: inherit;
 transition:
 box-shadow .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 `),$("state-border",`
 z-index: 1;
 border-color: #0000;
 `),A("base-suffix",`
 cursor: pointer;
 position: absolute;
 top: 50%;
 transform: translateY(-50%);
 right: 10px;
 `,[$("arrow",`
 font-size: var(--n-arrow-size);
 color: var(--n-arrow-color);
 transition: color .3s var(--n-bezier);
 `)]),A("base-selection-overlay",`
 display: flex;
 align-items: center;
 white-space: nowrap;
 pointer-events: none;
 position: absolute;
 top: 0;
 right: 0;
 bottom: 0;
 left: 0;
 padding: var(--n-padding-single);
 transition: color .3s var(--n-bezier);
 `,[$("wrapper",`
 flex-basis: 0;
 flex-grow: 1;
 overflow: hidden;
 text-overflow: ellipsis;
 `)]),A("base-selection-placeholder",`
 color: var(--n-placeholder-color);
 `,[$("inner",`
 max-width: 100%;
 overflow: hidden;
 `)]),A("base-selection-tags",`
 cursor: pointer;
 outline: none;
 box-sizing: border-box;
 position: relative;
 z-index: auto;
 display: flex;
 padding: var(--n-padding-multiple);
 flex-wrap: wrap;
 align-items: center;
 width: 100%;
 vertical-align: bottom;
 background-color: var(--n-color);
 border-radius: inherit;
 transition:
 color .3s var(--n-bezier),
 box-shadow .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 `),A("base-selection-label",`
 height: var(--n-height);
 display: inline-flex;
 width: 100%;
 vertical-align: bottom;
 cursor: pointer;
 outline: none;
 z-index: auto;
 box-sizing: border-box;
 position: relative;
 transition:
 color .3s var(--n-bezier),
 box-shadow .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 border-radius: inherit;
 background-color: var(--n-color);
 align-items: center;
 `,[A("base-selection-input",`
 font-size: inherit;
 line-height: inherit;
 outline: none;
 cursor: pointer;
 box-sizing: border-box;
 border:none;
 width: 100%;
 padding: var(--n-padding-single);
 background-color: #0000;
 color: var(--n-text-color);
 transition: color .3s var(--n-bezier);
 caret-color: var(--n-caret-color);
 `,[$("content",`
 text-overflow: ellipsis;
 overflow: hidden;
 white-space: nowrap; 
 `)]),$("render-label",`
 color: var(--n-text-color);
 `)]),Oe("disabled",[ue("&:hover",[$("state-border",`
 box-shadow: var(--n-box-shadow-hover);
 border: var(--n-border-hover);
 `)]),oe("focus",[$("state-border",`
 box-shadow: var(--n-box-shadow-focus);
 border: var(--n-border-focus);
 `)]),oe("active",[$("state-border",`
 box-shadow: var(--n-box-shadow-active);
 border: var(--n-border-active);
 `),A("base-selection-label","background-color: var(--n-color-active);"),A("base-selection-tags","background-color: var(--n-color-active);")])]),oe("disabled","cursor: not-allowed;",[$("arrow",`
 color: var(--n-arrow-color-disabled);
 `),A("base-selection-label",`
 cursor: not-allowed;
 background-color: var(--n-color-disabled);
 `,[A("base-selection-input",`
 cursor: not-allowed;
 color: var(--n-text-color-disabled);
 `),$("render-label",`
 color: var(--n-text-color-disabled);
 `)]),A("base-selection-tags",`
 cursor: not-allowed;
 background-color: var(--n-color-disabled);
 `),A("base-selection-placeholder",`
 cursor: not-allowed;
 color: var(--n-placeholder-color-disabled);
 `)]),A("base-selection-input-tag",`
 height: calc(var(--n-height) - 6px);
 line-height: calc(var(--n-height) - 6px);
 outline: none;
 display: none;
 position: relative;
 margin-bottom: 3px;
 max-width: 100%;
 vertical-align: bottom;
 `,[$("input",`
 font-size: inherit;
 font-family: inherit;
 min-width: 1px;
 padding: 0;
 background-color: #0000;
 outline: none;
 border: none;
 max-width: 100%;
 overflow: hidden;
 width: 1em;
 line-height: inherit;
 cursor: pointer;
 color: var(--n-text-color);
 caret-color: var(--n-caret-color);
 `),$("mirror",`
 position: absolute;
 left: 0;
 top: 0;
 white-space: pre;
 visibility: hidden;
 user-select: none;
 -webkit-user-select: none;
 opacity: 0;
 `)]),["warning","error"].map(e=>oe(`${e}-status`,[$("state-border",`border: var(--n-border-${e});`),Oe("disabled",[ue("&:hover",[$("state-border",`
 box-shadow: var(--n-box-shadow-hover-${e});
 border: var(--n-border-hover-${e});
 `)]),oe("active",[$("state-border",`
 box-shadow: var(--n-box-shadow-active-${e});
 border: var(--n-border-active-${e});
 `),A("base-selection-label",`background-color: var(--n-color-active-${e});`),A("base-selection-tags",`background-color: var(--n-color-active-${e});`)]),oe("focus",[$("state-border",`
 box-shadow: var(--n-box-shadow-focus-${e});
 border: var(--n-border-focus-${e});
 `)])])]))]),A("base-selection-popover",`
 margin-bottom: -3px;
 display: flex;
 flex-wrap: wrap;
 margin-right: -8px;
 `),A("base-selection-tag-wrapper",`
 max-width: 100%;
 display: inline-flex;
 padding: 0 7px 3px 0;
 `,[ue("&:last-child","padding-right: 0;"),A("tag",`
 font-size: 14px;
 max-width: 100%;
 `,[$("content",`
 line-height: 1.25;
 text-overflow: ellipsis;
 overflow: hidden;
 `)])])]);const zn=["disabled","value","autofocus","onBlur","onFocus","onKeydown","onInput","onCompositionstart","onCompositionend"],Tn=["tabindex"],Pn=["title"],Fn=["value","readonly","disabled","autofocus","onFocus","onBlur","onInput","onCompositionstart","onCompositionend"],In=["tabindex"],Mn=["onClick","onMouseenter","onMouseleave","onKeydown","onFocusin","onFocusout","onMousedown"];var On=be({name:"InternalSelection",props:{...we.props,clsPrefix:{type:String,required:!0},bordered:{type:Boolean,default:void 0},active:Boolean,pattern:{type:String,default:""},placeholder:String,selectedOption:{type:Object,default:null},selectedOptions:{type:Array,default:null},labelField:{type:String,default:"label"},valueField:{type:String,default:"value"},multiple:Boolean,filterable:Boolean,clearable:Boolean,disabled:Boolean,size:{type:String,default:"medium"},loading:Boolean,autofocus:Boolean,showArrow:{type:Boolean,default:!0},inputProps:Object,focused:Boolean,renderTag:Function,onKeydown:Function,onClick:Function,onBlur:Function,onFocus:Function,onDeleteOption:Function,maxTagCount:[String,Number],ellipsisTagPopoverProps:Object,onClear:Function,onPatternInput:Function,onPatternFocus:Function,onPatternBlur:Function,renderLabel:Function,status:String,inlineThemeDisabled:Boolean,ignoreComposition:{type:Boolean,default:!0},onResize:Function},setup(e){const{mergedClsPrefixRef:t,mergedRtlRef:o}=qe(e),d=St("InternalSelection",o,t),l=O(null),s=O(null),u=O(null),r=O(null),v=O(null),g=O(null),w=O(null),y=O(null),E=O(null),k=O(null),S=O(!1),b=O(!1),M=O(!1),z=we("InternalSelection","-internal-selection",Rn,Ho,e,ne(e,"clsPrefix")),R=F(()=>e.clearable&&!e.disabled&&(M.value||e.active)),U=F(()=>e.selectedOption?e.renderTag?e.renderTag({option:e.selectedOption,handleClose:()=>{}}):e.renderLabel?e.renderLabel(e.selectedOption,!0):Le(e.selectedOption[e.labelField],e.selectedOption,!0):e.placeholder),Q=F(()=>{const i=e.selectedOption;if(i)return i[e.labelField]}),q=F(()=>e.multiple?!!(Array.isArray(e.selectedOptions)&&e.selectedOptions.length):e.selectedOption!==null);function V(){const{value:i}=l;if(i){const{value:_}=s;_&&(_.style.width=`${i.offsetWidth}px`,e.maxTagCount!=="responsive"&&E.value?.sync({showAllItemsBeforeCalculate:!1}))}}function le(){const{value:i}=k;i&&(i.style.display="none")}function ie(){const{value:i}=k;i&&(i.style.display="inline-block")}_e(ne(e,"active"),i=>{i||le()}),_e(ne(e,"pattern"),()=>{e.multiple&&Ht(V)});function he(i){const{onFocus:_}=e;_&&_(i)}function fe(i){const{onBlur:_}=e;_&&_(i)}function te(i){const{onDeleteOption:_}=e;_&&_(i)}function N(i){const{onClear:_}=e;_&&_(i)}function h(i){const{onPatternInput:_}=e;_&&_(i)}function p(i){(!i.relatedTarget||!u.value?.contains(i.relatedTarget))&&he(i)}function L(i){u.value?.contains(i.relatedTarget)||fe(i)}function B(i){N(i)}function Y(){M.value=!0}function G(){M.value=!1}function D(i){!e.active||!e.filterable||i.target!==s.value&&i.preventDefault()}function J(i){te(i)}const K=O(!1);function ve(i){if(i.key==="Backspace"&&!K.value&&!e.pattern.length){const{selectedOptions:_}=e;_?.length&&J(_[_.length-1])}}let ce=null;function c(i){const{value:_}=l;_&&(_.textContent=i.target.value,V()),e.ignoreComposition&&K.value?ce=i:h(i)}function I(){K.value=!0}function ge(){K.value=!1,e.ignoreComposition&&h(ce),ce=null}function ke(i){b.value=!0,e.onPatternFocus?.(i)}function xe(i){b.value=!1,e.onPatternBlur?.(i)}function pe(){if(e.filterable)b.value=!1,g.value?.blur(),s.value?.blur();else if(e.multiple){const{value:i}=r;i?.blur()}else{const{value:i}=v;i?.blur()}}function de(){e.filterable?(b.value=!1,g.value?.focus()):e.multiple?r.value?.focus():v.value?.focus()}function Se(){const{value:i}=s;i&&(ie(),i.focus())}function Ce(){const{value:i}=s;i&&i.blur()}function ye(i){const{value:_}=w;_&&_.setTextContent(`+${i}`)}function He(){const{value:i}=y;return i}function Ve(){return s.value}let Pe=null;function Fe(){Pe!==null&&window.clearTimeout(Pe)}function Ne(){e.active||(Fe(),Pe=window.setTimeout(()=>{q.value&&(S.value=!0)},100))}function We(){Fe()}function Ke(i){i||(Fe(),S.value=!1)}_e(q,i=>{i||(S.value=!1)}),it(()=>{Vo(()=>{const i=g.value;i&&(e.disabled?i.removeAttribute("tabindex"):i.tabIndex=b.value?-1:0)})}),Kt(u,e.onResize);const{inlineThemeDisabled:$e}=e,Ee=F(()=>{const{size:i}=e,{common:{cubicBezierEaseInOut:_},self:{fontWeight:at,borderRadius:st,color:ct,placeholderColor:dt,textColor:Xe,paddingSingle:Ye,paddingMultiple:Ze,caretColor:ut,colorDisabled:ht,textColorDisabled:Je,placeholderColorDisabled:Qe,colorActive:n,boxShadowFocus:f,boxShadowActive:T,boxShadowHover:P,border:H,borderFocus:W,borderHover:X,borderActive:ae,arrowColor:Me,arrowColorDisabled:Ut,loadingColor:qt,colorActiveWarning:Gt,boxShadowFocusWarning:Xt,boxShadowActiveWarning:Yt,boxShadowHoverWarning:Zt,borderWarning:Jt,borderFocusWarning:Qt,borderHoverWarning:eo,borderActiveWarning:to,colorActiveError:oo,boxShadowFocusError:no,boxShadowActiveError:ro,boxShadowHoverError:lo,borderError:io,borderFocusError:ao,borderHoverError:so,borderActiveError:co,clearColor:uo,clearColorHover:ho,clearColorPressed:fo,clearSize:vo,arrowSize:go,[ee("height",i)]:bo,[ee("fontSize",i)]:po}}=z.value,et=Ae(Ye),tt=Ae(Ze);return{"--n-bezier":_,"--n-border":H,"--n-border-active":ae,"--n-border-focus":W,"--n-border-hover":X,"--n-border-radius":st,"--n-box-shadow-active":T,"--n-box-shadow-focus":f,"--n-box-shadow-hover":P,"--n-caret-color":ut,"--n-color":ct,"--n-color-active":n,"--n-color-disabled":ht,"--n-font-size":po,"--n-height":bo,"--n-padding-single-top":et.top,"--n-padding-multiple-top":tt.top,"--n-padding-single-right":et.right,"--n-padding-multiple-right":tt.right,"--n-padding-single-left":et.left,"--n-padding-multiple-left":tt.left,"--n-padding-single-bottom":et.bottom,"--n-padding-multiple-bottom":tt.bottom,"--n-placeholder-color":dt,"--n-placeholder-color-disabled":Qe,"--n-text-color":Xe,"--n-text-color-disabled":Je,"--n-arrow-color":Me,"--n-arrow-color-disabled":Ut,"--n-loading-color":qt,"--n-color-active-warning":Gt,"--n-box-shadow-focus-warning":Xt,"--n-box-shadow-active-warning":Yt,"--n-box-shadow-hover-warning":Zt,"--n-border-warning":Jt,"--n-border-focus-warning":Qt,"--n-border-hover-warning":eo,"--n-border-active-warning":to,"--n-color-active-error":oo,"--n-box-shadow-focus-error":no,"--n-box-shadow-active-error":ro,"--n-box-shadow-hover-error":lo,"--n-border-error":io,"--n-border-focus-error":ao,"--n-border-hover-error":so,"--n-border-active-error":co,"--n-clear-size":vo,"--n-clear-color":uo,"--n-clear-color-hover":ho,"--n-clear-color-pressed":fo,"--n-arrow-size":go,"--n-font-weight":at}}),Ie=$e?Ge("internal-selection",F(()=>e.size[0]),Ee,e):void 0;return{mergedTheme:z,mergedClearable:R,mergedClsPrefix:t,rtlEnabled:d,patternInputFocused:b,filterablePlaceholder:U,label:Q,selected:q,showTagsPanel:S,isComposing:K,counterRef:w,counterWrapperRef:y,patternInputMirrorRef:l,patternInputRef:s,selfRef:u,multipleElRef:r,singleElRef:v,patternInputWrapperRef:g,overflowRef:E,inputTagElRef:k,handleMouseDown:D,handleFocusin:p,handleClear:B,handleMouseEnter:Y,handleMouseLeave:G,handleDeleteOption:J,handlePatternKeyDown:ve,handlePatternInputInput:c,handlePatternInputBlur:xe,handlePatternInputFocus:ke,handleMouseEnterCounter:Ne,handleMouseLeaveCounter:We,handleFocusout:L,handleCompositionEnd:ge,handleCompositionStart:I,onPopoverUpdateShow:Ke,focus:de,focusInput:Se,blur:pe,blurInput:Ce,updateCounter:ye,getCounter:He,getTail:Ve,renderLabel:e.renderLabel,cssVars:$e?void 0:Ee,themeClass:Ie?.themeClass,onRender:Ie?.onRender}},render(){const{status:e,multiple:t,size:o,disabled:d,filterable:l,maxTagCount:s,bordered:u,clsPrefix:r,ellipsisTagPopoverProps:v,onRender:g,renderTag:w,renderLabel:y}=this;g?.();const E=s==="responsive",k=typeof s=="number",S=E||k,b=(a(),j(Do,null,{default:()=>(a(),j(Yo,{clsPrefix:r,loading:this.loading,showArrow:this.showArrow,showClear:this.mergedClearable&&this.selected,onClear:this.handleClear},{default:()=>this.$slots.arrow?.()},1032,["clsPrefix","loading","showArrow","showClear","onClear"]))},1024));let M;if(t){const{labelField:z}=this,R=N=>(a(),m("div",{class:x(`${r}-base-selection-tag-wrapper`),key:N.value},[w?(a(),m(re,{key:0},[C(()=>w({option:N,handleClose:()=>{this.handleDeleteOption(N)}}))],64)):(a(),j(pt,{key:1,size:o,closable:!N.disabled,disabled:d,onClose:()=>{this.handleDeleteOption(N)},internalCloseIsButtonTag:!1,internalCloseFocusable:!1},{default:()=>y?y(N,!0):Le(N[z],N,!0)},1032,["size","closable","disabled","onClose"]))],2)),U=()=>(k?this.selectedOptions.slice(0,s):this.selectedOptions).map(R),Q=l?(a(),m("div",{class:x(`${r}-base-selection-input-tag`),ref:"inputTagElRef",key:"__input-tag__"},[se("input",Te(this.inputProps,{ref:"patternInputRef",tabindex:-1,disabled:d,value:this.pattern,autofocus:this.autofocus,class:`${r}-base-selection-input-tag__input`,onBlur:this.handlePatternInputBlur,onFocus:this.handlePatternInputFocus,onKeydown:this.handlePatternKeyDown,onInput:this.handlePatternInputInput,onCompositionstart:this.handleCompositionStart,onCompositionend:this.handleCompositionEnd}),null,16,zn),se("span",{ref:"patternInputMirrorRef",class:x(`${r}-base-selection-input-tag__mirror`)},[C(()=>this.pattern)],2)],2)):null,q=E?()=>(a(),m("div",{class:x(`${r}-base-selection-tag-wrapper`),ref:"counterWrapperRef"},[(a(),j(pt,{size:o,ref:"counterRef",onMouseenter:this.handleMouseEnterCounter,onMouseleave:this.handleMouseLeaveCounter,disabled:d},null,8,["size","onMouseenter","onMouseleave","disabled"]))],2)):void 0;let V;if(k){const N=this.selectedOptions.length-s;N>0&&(V=(h=>(a(),m("div",{class:x(`${r}-base-selection-tag-wrapper`),key:"__counter__"},[(a(),j(pt,{size:o,ref:"counterRef",onMouseenter:this.handleMouseEnterCounter,disabled:d},{default:()=>`+${N}`},1032,["size","onMouseenter","disabled"]))],2)))())}const le=E?l?(a(),j(Rt,{key:3,ref:"overflowRef",updateCounter:this.updateCounter,getCounter:this.getCounter,getTail:this.getTail,style:{width:"100%",display:"flex",overflow:"hidden"}},{default:U,counter:q,tail:()=>Q},1032,["updateCounter","getCounter","getTail"])):(a(),j(Rt,{key:4,ref:"overflowRef",updateCounter:this.updateCounter,getCounter:this.getCounter,style:{width:"100%",display:"flex",overflow:"hidden"}},{default:U,counter:q},1032,["updateCounter","getCounter"])):k&&V?U().concat(V):U(),ie=S?()=>(a(),m("div",{class:x(`${r}-base-selection-popover`)},[E?(a(),m(re,{key:0},[C(()=>U())],64)):(a(),m(re,{key:1},[C(()=>this.selectedOptions.map(R))],64))],2)):void 0,he=S?{show:this.showTagsPanel,trigger:"hover",overlap:!0,placement:"top",width:"trigger",onUpdateShow:this.onPopoverUpdateShow,theme:this.mergedTheme.peers.Popover,themeOverrides:this.mergedTheme.peerOverrides.Popover,...v}:null,fe=!this.selected&&(!this.active||!this.pattern&&!this.isComposing)?(a(),m("div",{key:5,class:x(`${r}-base-selection-placeholder ${r}-base-selection-overlay`)},[se("div",{class:x(`${r}-base-selection-placeholder__inner`)},[C(()=>this.placeholder)],2)],2)):null,te=l?(a(),m("div",{key:6,ref:"patternInputWrapperRef",class:x(`${r}-base-selection-tags`)},[C(()=>le),E?C(()=>null):(a(),m(re,{key:1},[C(()=>Q)],64)),C(()=>b)],2)):(a(),m("div",{key:7,ref:"multipleElRef",class:x(`${r}-base-selection-tags`),tabindex:d?void 0:0},[C(()=>le),C(()=>b)],10,Tn));M=(N=>(a(),m(re,{key:8},[S?(a(),j(xo,Te({key:0},he,{scrollable:!0,style:"max-height: calc(var(--v-target-height) * 6.6);"}),{trigger:()=>te,default:ie},1040)):(a(),m(re,{key:1},[C(()=>te)],64)),C(()=>fe)],64)))()}else if(l){const z=this.pattern||this.isComposing,R=this.active?!z:!this.selected,U=this.active?!1:this.selected;M=(Q=>(a(),m("div",{key:9,ref:"patternInputWrapperRef",class:x(`${r}-base-selection-label`),title:this.patternInputFocused?void 0:Bt(this.label)},[se("input",Te(this.inputProps,{ref:"patternInputRef",class:`${r}-base-selection-input`,value:this.active?this.pattern:"",placeholder:"",readonly:d,disabled:d,tabindex:-1,autofocus:this.autofocus,onFocus:this.handlePatternInputFocus,onBlur:this.handlePatternInputBlur,onInput:this.handlePatternInputInput,onCompositionstart:this.handleCompositionStart,onCompositionend:this.handleCompositionEnd}),null,16,Fn),U?(a(),m("div",{class:x(`${r}-base-selection-label__render-label ${r}-base-selection-overlay`),key:"input"},[se("div",{class:x(`${r}-base-selection-overlay__wrapper`)},[w?(a(),m(re,{key:0},[C(()=>w({option:this.selectedOption,handleClose:()=>{}}))],64)):(a(),m(re,{key:1},[y?(a(),m(re,{key:0},[C(()=>y(this.selectedOption,!0))],64)):(a(),m(re,{key:1},[C(()=>Le(this.label,this.selectedOption,!0))],64))],64))],2)],2)):C(()=>null),R?(a(),m("div",{class:x(`${r}-base-selection-placeholder ${r}-base-selection-overlay`),key:"placeholder"},[se("div",{class:x(`${r}-base-selection-overlay__wrapper`)},[C(()=>this.filterablePlaceholder)],2)],2)):C(()=>null),C(()=>b)],10,Pn)))()}else M=(z=>(a(),m("div",{key:10,ref:"singleElRef",class:x(`${r}-base-selection-label`),tabindex:this.disabled?void 0:0},[this.label!==void 0?(a(),m("div",{class:x(`${r}-base-selection-input`),title:Bt(this.label),key:"input"},[se("div",{class:x(`${r}-base-selection-input__content`)},[w?(a(),m(re,{key:0},[C(()=>w({option:this.selectedOption,handleClose:()=>{}}))],64)):(a(),m(re,{key:1},[y?(a(),m(re,{key:0},[C(()=>y(this.selectedOption,!0))],64)):(a(),m(re,{key:1},[C(()=>Le(this.label,this.selectedOption,!0))],64))],64))],2)],10,["title"])):(a(),m("div",{class:x(`${r}-base-selection-placeholder ${r}-base-selection-overlay`),key:"placeholder"},[se("div",{class:x(`${r}-base-selection-placeholder__inner`)},[C(()=>this.placeholder)],2)],2)),C(()=>b)],10,In)))();return a(),m("div",{ref:"selfRef",class:x([`${r}-base-selection`,this.rtlEnabled&&`${r}-base-selection--rtl`,this.themeClass,e&&`${r}-base-selection--${e}-status`,{[`${r}-base-selection--active`]:this.active,[`${r}-base-selection--selected`]:this.selected||this.active&&this.pattern,[`${r}-base-selection--disabled`]:this.disabled,[`${r}-base-selection--multiple`]:this.multiple,[`${r}-base-selection--focus`]:this.focused}]),style:De(this.cssVars),onClick:this.onClick,onMouseenter:this.handleMouseEnter,onMouseleave:this.handleMouseLeave,onKeydown:this.onKeydown,onFocusin:this.handleFocusin,onFocusout:this.handleFocusout,onMousedown:this.handleMouseDown},[C(()=>M),u?(a(),m("div",{key:0,class:x(`${r}-base-selection__border`)},null,2)):C(()=>null),u?(a(),m("div",{key:2,class:x(`${r}-base-selection__state-border`)},null,2)):C(()=>null)],46,Mn)}}),Bn=ue([A("select",`
 z-index: auto;
 outline: none;
 width: 100%;
 position: relative;
 font-weight: var(--n-font-weight);
 `),A("select-menu",`
 margin: 4px 0;
 box-shadow: var(--n-menu-box-shadow);
 `,[Dt({originalTransition:"background-color .3s var(--n-bezier), box-shadow .3s var(--n-bezier)"})])]);const _n={...we.props,to:Ct.propTo,bordered:{type:Boolean,default:void 0},clearable:Boolean,clearCreatedOptionsOnClear:{type:Boolean,default:!0},clearFilterAfterSelect:{type:Boolean,default:!0},options:{type:Array,default:()=>[]},defaultValue:{type:[String,Number,Array],default:null},keyboard:{type:Boolean,default:!0},value:[String,Number,Array],placeholder:String,menuProps:Object,multiple:Boolean,size:String,menuSize:{type:String},filterable:Boolean,disabled:{type:Boolean,default:void 0},remote:Boolean,loading:Boolean,filter:Function,placement:{type:String,default:"bottom-start"},widthMode:{type:String,default:"trigger"},tag:Boolean,onCreate:Function,fallbackOption:{type:[Function,Boolean],default:void 0},show:{type:Boolean,default:void 0},showArrow:{type:Boolean,default:!0},maxTagCount:[Number,String],ellipsisTagPopoverProps:Object,consistentMenuWidth:{type:Boolean,default:!0},virtualScroll:{type:Boolean,default:!0},labelField:{type:String,default:"label"},valueField:{type:String,default:"value"},childrenField:{type:String,default:"children"},renderLabel:Function,renderOption:Function,renderTag:Function,"onUpdate:value":[Function,Array],inputProps:Object,nodeProps:Function,ignoreComposition:{type:Boolean,default:!0},showOnFocus:Boolean,onUpdateValue:[Function,Array],onBlur:[Function,Array],onClear:[Function,Array],onFocus:[Function,Array],onScroll:[Function,Array],onSearch:[Function,Array],onUpdateShow:[Function,Array],"onUpdate:show":[Function,Array],displayDirective:{type:String,default:"show"},resetMenuOnOptionsChange:{type:Boolean,default:!0},status:String,showCheckmark:{type:Boolean,default:!0},scrollbarProps:Object,onChange:[Function,Array],items:Array};var Dn=be({name:"Select",props:_n,slots:Object,setup(e){const{mergedClsPrefixRef:t,mergedBorderedRef:o,namespaceRef:d,inlineThemeDisabled:l,mergedComponentPropsRef:s}=qe(e),u=we("Select","-select",Bn,jo,e,t),r=O(e.defaultValue),v=ne(e,"value"),g=Ft(v,r),w=O(!1),y=O(""),E=Zo(e,["items","options"]),k=O([]),S=O([]),b=F(()=>S.value.concat(k.value).concat(E.value)),M=F(()=>{const{filter:n}=e;if(n)return n;const{labelField:f,valueField:T}=e;return(P,H)=>{if(!H)return!1;const W=H[f];if(typeof W=="string")return mt(P,W);const X=H[T];return typeof X=="string"?mt(P,X):typeof X=="number"?mt(P,String(X)):!1}}),z=F(()=>{if(e.remote)return E.value;{const{value:n}=b,{value:f}=y;return!f.length||!e.filterable?n:kn(n,M.value,f,e.childrenField)}}),R=F(()=>{const{valueField:n,childrenField:f}=e,T=xn(n,f);return zo(z.value,T)}),U=F(()=>Sn(b.value,e.valueField,e.childrenField)),Q=O(!1),q=Ft(ne(e,"show"),Q),V=O(null),le=O(null),ie=O(null),{localeRef:he}=Vt("Select"),fe=F(()=>e.placeholder??he.value.placeholder),te=[],N=O(new Map),h=F(()=>{const{fallbackOption:n}=e;if(n===void 0){const{labelField:f,valueField:T}=e;return P=>({[f]:String(P),[T]:P})}return n===!1?!1:f=>Object.assign(n(f),{value:f})});function p(n){const f=e.remote,{value:T}=N,{value:P}=U,{value:H}=h,W=[];return n.forEach(X=>{if(P.has(X))W.push(P.get(X));else if(f&&T.has(X))W.push(T.get(X));else if(H){const ae=H(X);ae&&W.push(ae)}}),W}const L=F(()=>{if(e.multiple){const{value:n}=g;return Array.isArray(n)?p(n):[]}return null}),B=F(()=>{const{value:n}=g;return!e.multiple&&!Array.isArray(n)?n===null?null:p([n])[0]||null:null}),Y=Uo(e,{mergedSize:n=>{const{size:f}=e;if(f)return f;const{mergedSize:T}=n||{};if(T?.value)return T.value;const P=s?.value?.Select?.size;return P||"medium"}}),{mergedSizeRef:G,mergedDisabledRef:D,mergedStatusRef:J}=Y;function K(n,f){const{onChange:T,"onUpdate:value":P,onUpdateValue:H}=e,{nTriggerFormChange:W,nTriggerFormInput:X}=Y;T&&me(T,n,f),H&&me(H,n,f),P&&me(P,n,f),r.value=n,W(),X()}function ve(n){const{onBlur:f}=e,{nTriggerFormBlur:T}=Y;f&&me(f,n),T()}function ce(){const{onClear:n}=e;n&&me(n)}function c(n){const{onFocus:f,showOnFocus:T}=e,{nTriggerFormFocus:P}=Y;f&&me(f,n),P(),T&&pe()}function I(n){const{onSearch:f}=e;f&&me(f,n)}function ge(n){const{onScroll:f}=e;f&&me(f,n)}function ke(){const{remote:n,multiple:f}=e;if(n){const{value:T}=N;if(f){const{valueField:P}=e;L.value?.forEach(H=>{T.set(H[P],H)})}else{const P=B.value;P&&T.set(P[e.valueField],P)}}}function xe(n){const{onUpdateShow:f,"onUpdate:show":T}=e;f&&me(f,n),T&&me(T,n),Q.value=n}function pe(){D.value||(xe(!0),Q.value=!0,e.filterable&&Ze())}function de(){xe(!1)}function Se(){y.value="",S.value=te}const Ce=O(!1);function ye(){e.filterable&&(Ce.value=!0)}function He(){e.filterable&&(Ce.value=!1,q.value||Se())}function Ve(){D.value||(q.value?e.filterable?Ze():de():pe())}function Pe(n){ie.value?.selfRef?.contains(n.relatedTarget)||(w.value=!1,ve(n),de())}function Fe(n){c(n),w.value=!0}function Ne(){w.value=!0}function We(n){V.value?.$el.contains(n.relatedTarget)||(w.value=!1,ve(n),de())}function Ke(){V.value?.focus(),de()}function $e(n){q.value&&(V.value?.$el.contains(Go(n))||de())}function Ee(n){if(!Array.isArray(n))return[];if(h.value)return Array.from(n);{const{remote:f}=e,{value:T}=U;if(f){const{value:P}=N;return n.filter(H=>T.has(H)||P.has(H))}else return n.filter(P=>T.has(P))}}function Ie(n){i(n.rawNode)}function i(n){if(D.value)return;const{tag:f,remote:T,clearFilterAfterSelect:P,valueField:H}=e;if(f&&!T){const{value:W}=S,X=W[0]||null;if(X){const ae=k.value;ae.length?ae.push(X):k.value=[X],S.value=te}}if(T&&N.value.set(n[H],n),e.multiple){const W=Ee(g.value),X=W.findIndex(ae=>ae===n[H]);if(~X){if(W.splice(X,1),f&&!T){const ae=_(n[H]);~ae&&(k.value.splice(ae,1),P&&(y.value=""))}}else W.push(n[H]),P&&(y.value="");K(W,p(W))}else{if(f&&!T){const W=_(n[H]);~W?k.value=[k.value[W]]:k.value=te}Ye(),de(),K(n[H],n)}}function _(n){return k.value.findIndex(f=>f[e.valueField]===n)}function at(n){q.value||pe();const{value:f}=n.target;y.value=f;const{tag:T,remote:P}=e;if(I(f),T&&!P){if(!f){S.value=te;return}const{onCreate:H}=e,W=H?H(f):{[e.labelField]:f,[e.valueField]:f},{valueField:X,labelField:ae}=e;E.value.some(Me=>Me[X]===W[X]||Me[ae]===W[ae])||k.value.some(Me=>Me[X]===W[X]||Me[ae]===W[ae])?S.value=te:S.value=[W]}}function st(n){n.stopPropagation();const{multiple:f,tag:T,remote:P,clearCreatedOptionsOnClear:H}=e;!f&&e.filterable&&de(),T&&!P&&H&&(k.value=te),ce(),f?K([],[]):K(null,null)}function ct(n){!Ue(n,"action")&&!Ue(n,"empty")&&!Ue(n,"header")&&n.preventDefault()}function dt(n){ge(n)}function Xe(n){if(!e.keyboard){n.preventDefault();return}switch(n.key){case" ":if(e.filterable)break;n.preventDefault();case"Enter":if(!V.value?.isComposing){if(q.value){const f=ie.value?.getPendingTmNode();f?Ie(f):e.filterable||(de(),Ye())}else if(pe(),e.tag&&Ce.value){const f=S.value[0];if(f){const T=f[e.valueField],{value:P}=g;e.multiple&&Array.isArray(P)&&P.includes(T)||i(f)}}}n.preventDefault();break;case"ArrowUp":if(n.preventDefault(),e.loading)return;q.value&&ie.value?.prev();break;case"ArrowDown":if(n.preventDefault(),e.loading)return;q.value?ie.value?.next():pe();break;case"Escape":q.value&&(Xo(n),de()),V.value?.focus()}}function Ye(){V.value?.focus()}function Ze(){V.value?.focusInput()}function ut(){q.value&&le.value?.syncPosition()}ke(),_e(ne(e,"options"),ke);const ht={focus:()=>{V.value?.focus()},focusInput:()=>{V.value?.focusInput()},blur:()=>{V.value?.blur()},blurInput:()=>{V.value?.blurInput()}},Je=F(()=>{const{self:{menuBoxShadow:n}}=u.value;return{"--n-menu-box-shadow":n}}),Qe=l?Ge("select",void 0,Je,e):void 0;return{...ht,mergedStatus:J,mergedClsPrefix:t,mergedBordered:o,namespace:d,treeMate:R,isMounted:qo(),triggerRef:V,menuRef:ie,pattern:y,uncontrolledShow:Q,mergedShow:q,adjustedTo:Ct(e),uncontrolledValue:r,mergedValue:g,followerRef:le,localizedPlaceholder:fe,selectedOption:B,selectedOptions:L,mergedSize:G,mergedDisabled:D,focused:w,activeWithoutMenuOpen:Ce,inlineThemeDisabled:l,onTriggerInputFocus:ye,onTriggerInputBlur:He,handleTriggerOrMenuResize:ut,handleMenuFocus:Ne,handleMenuBlur:We,handleMenuTabOut:Ke,handleTriggerClick:Ve,handleToggle:Ie,handleDeleteOption:i,handlePatternInput:at,handleClear:st,handleTriggerBlur:Pe,handleTriggerFocus:Fe,handleKeydown:Xe,handleMenuAfterLeave:Se,handleMenuClickOutside:$e,handleMenuScroll:dt,handleMenuKeydown:Xe,handleMenuMousedown:ct,mergedTheme:u,cssVars:l?void 0:Je,themeClass:Qe?.themeClass,onRender:Qe?.onRender}},render(){return a(),m("div",{class:x(`${this.mergedClsPrefix}-select`)},[No(Ro,null,{_:1,default:Re(()=>[(a(),j(ko,null,{_:1,default:Re(()=>(a(),j(On,{ref:"triggerRef",inlineThemeDisabled:this.inlineThemeDisabled,status:this.mergedStatus,inputProps:this.inputProps,clsPrefix:this.mergedClsPrefix,showArrow:this.showArrow,maxTagCount:this.maxTagCount,ellipsisTagPopoverProps:this.ellipsisTagPopoverProps,bordered:this.mergedBordered,active:this.activeWithoutMenuOpen||this.mergedShow,pattern:this.pattern,placeholder:this.localizedPlaceholder,selectedOption:this.selectedOption,selectedOptions:this.selectedOptions,multiple:this.multiple,renderTag:this.renderTag,renderLabel:this.renderLabel,filterable:this.filterable,clearable:this.clearable,disabled:this.mergedDisabled,size:this.mergedSize,theme:this.mergedTheme.peers.InternalSelection,labelField:this.labelField,valueField:this.valueField,themeOverrides:this.mergedTheme.peerOverrides.InternalSelection,loading:this.loading,focused:this.focused,onClick:this.handleTriggerClick,onDeleteOption:this.handleDeleteOption,onPatternInput:this.handlePatternInput,onClear:this.handleClear,onBlur:this.handleTriggerBlur,onFocus:this.handleTriggerFocus,onKeydown:this.handleKeydown,onPatternBlur:this.onTriggerInputBlur,onPatternFocus:this.onTriggerInputFocus,onResize:this.handleTriggerOrMenuResize,ignoreComposition:this.ignoreComposition},{_:1,arrow:Re(()=>[this.$slots.arrow?.()])},8,["inlineThemeDisabled","status","inputProps","clsPrefix","showArrow","maxTagCount","ellipsisTagPopoverProps","bordered","active","pattern","placeholder","selectedOption","selectedOptions","multiple","renderTag","renderLabel","filterable","clearable","disabled","size","theme","labelField","valueField","themeOverrides","loading","focused","onClick","onDeleteOption","onPatternInput","onClear","onBlur","onFocus","onKeydown","onPatternBlur","onPatternFocus","onResize","ignoreComposition"])))})),(a(),j(So,{ref:"followerRef",show:this.mergedShow,to:this.adjustedTo,teleportDisabled:this.adjustedTo===Ct.tdkey,containerClass:this.namespace,width:this.consistentMenuWidth?"target":void 0,minWidth:"target",placement:this.placement},{_:1,default:Re(()=>(a(),j(At,{name:"fade-in-scale-up-transition",appear:this.isMounted,onAfterLeave:this.handleMenuAfterLeave},{_:1,default:Re(()=>this.mergedShow||this.displayDirective==="show"?(this.onRender?.(),Wo((a(),j(wn,Te(this.menuProps,{ref:"menuRef",onResize:this.handleTriggerOrMenuResize,inlineThemeDisabled:this.inlineThemeDisabled,virtualScroll:this.consistentMenuWidth&&this.virtualScroll,class:[`${this.mergedClsPrefix}-select-menu`,this.themeClass,this.menuProps?.class],clsPrefix:this.mergedClsPrefix,focusable:!0,labelField:this.labelField,valueField:this.valueField,autoPending:!0,nodeProps:this.nodeProps,theme:this.mergedTheme.peers.InternalSelectMenu,themeOverrides:this.mergedTheme.peerOverrides.InternalSelectMenu,treeMate:this.treeMate,multiple:this.multiple,size:this.menuSize,renderOption:this.renderOption,renderLabel:this.renderLabel,value:this.mergedValue,style:[this.menuProps?.style,this.cssVars],onToggle:this.handleToggle,onScroll:this.handleMenuScroll,onFocus:this.handleMenuFocus,onBlur:this.handleMenuBlur,onKeydown:this.handleMenuKeydown,onTabOut:this.handleMenuTabOut,onMousedown:this.handleMenuMousedown,show:this.mergedShow,showCheckmark:this.showCheckmark,resetMenuOnOptionsChange:this.resetMenuOnOptionsChange,scrollbarProps:this.scrollbarProps}),{_:1,empty:Re(()=>[this.$slots.empty?.()]),header:Re(()=>[this.$slots.header?.()]),action:Re(()=>[this.$slots.action?.()])},16,["onResize","inlineThemeDisabled","virtualScroll","class","clsPrefix","labelField","valueField","nodeProps","theme","themeOverrides","treeMate","multiple","size","renderOption","renderLabel","value","style","onToggle","onScroll","onFocus","onBlur","onKeydown","onTabOut","onMousedown","show","showCheckmark","resetMenuOnOptionsChange","scrollbarProps"])),this.displayDirective==="show"?[[Ko,this.mergedShow],[Pt,this.handleMenuClickOutside,void 0,{capture:!0}]]:[[Pt,this.handleMenuClickOutside,void 0,{capture:!0}]])):null)},8,["appear","onAfterLeave"])))},8,["show","to","teleportDisabled","containerClass","width","placement"]))])})],2)}});export{tn as E,Dn as S,ln as V,wn as a,xn as c};
