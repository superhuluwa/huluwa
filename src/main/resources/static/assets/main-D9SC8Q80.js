import{c as f,a as g,b as k,d as v,e as T,u as ae,o as u,f as I,g as w,n as P,h as he,i as E,j as Le,k as no,l as Oe,m as y,p as W,q as Q,t as ve,r as V,s as io,v as ao,w as lo,x as B,y as _,z as so,A as J,B as co,C as be,D as q,F as O,E as M,I as uo,G as xe,H as j,J as pe,K as mo,L as de,V as ho,M as vo,N as Me,O as po,P as $,Q as fo,R as H,S as ne,T as S,U as A,W as ie,X as go,Y as bo,Z as xo,_ as wo,$ as Re,a0 as Ne,a1 as Ae,a2 as He,a3 as Co,a4 as yo,a5 as zo,a6 as Po,a7 as Io,a8 as _o}from"./index-B1K0S1eV.js";import{T as So}from"./Tooltip-HxFNO2I3.js";import{D as Be}from"./Dropdown-C9zBdXXJ.js";import{u as Ee,I as ko}from"./Input-DdEKGNcw.js";import{V as Mo,c as ue}from"./create-KzBHyMG8.js";import{u as Ro}from"./use-compitable-CdgHCgkv.js";import{S as No}from"./Spin-26ZZTb92.js";import{u as Ao}from"./chat-DLU7o1ve.js";import{_ as we}from"./_plugin-vue_export-helper-DlAUqK2U.js";import{F as Ho,a as Eo}from"./FormItem-DKWCo7lc.js";import{_ as To}from"./huluwa-logo-BxQdg_kY.js";import"./chat-oLvdPLtv.js";var Ko=f("breadcrumb",`
 white-space: nowrap;
 cursor: default;
 line-height: var(--n-item-line-height);
`,[g("ul",`
 list-style: none;
 padding: 0;
 margin: 0;
 `),g("a",`
 color: inherit;
 text-decoration: inherit;
 `),f("breadcrumb-item",`
 font-size: var(--n-font-size);
 transition: color .3s var(--n-bezier);
 display: inline-flex;
 align-items: center;
 `,[f("icon",`
 font-size: 18px;
 vertical-align: -.2em;
 transition: color .3s var(--n-bezier);
 color: var(--n-item-text-color);
 `),g("&:not(:last-child)",[k("clickable",[v("link",`
 cursor: pointer;
 `,[g("&:hover",`
 background-color: var(--n-item-color-hover);
 `),g("&:active",`
 background-color: var(--n-item-color-pressed);
 `)])])]),v("link",`
 padding: 4px;
 border-radius: var(--n-item-border-radius);
 transition:
 background-color .3s var(--n-bezier),
 color .3s var(--n-bezier);
 color: var(--n-item-text-color);
 position: relative;
 `,[g("&:hover",`
 color: var(--n-item-text-color-hover);
 `,[f("icon",`
 color: var(--n-item-text-color-hover);
 `)]),g("&:active",`
 color: var(--n-item-text-color-pressed);
 `,[f("icon",`
 color: var(--n-item-text-color-pressed);
 `)])]),v("separator",`
 margin: 0 8px;
 color: var(--n-separator-color);
 transition: color .3s var(--n-bezier);
 user-select: none;
 -webkit-user-select: none;
 `),g("&:last-child",[v("link",`
 font-weight: var(--n-font-weight-active);
 cursor: unset;
 color: var(--n-item-text-color-active);
 `,[f("icon",`
 color: var(--n-item-text-color-active);
 `)]),v("separator",`
 display: none;
 `)])])]);const De=W("n-breadcrumb"),Fo={...ae.props,separator:{type:String,default:"/"}};var Lo=T({name:"Breadcrumb",props:Fo,setup(e){const{mergedClsPrefixRef:o,inlineThemeDisabled:t}=Le(e),l=ae("Breadcrumb","-breadcrumb",Ko,no,e,o);Q(De,{separatorRef:ve(e,"separator"),mergedClsPrefixRef:o});const n=y(()=>{const{common:{cubicBezierEaseInOut:s},self:{separatorColor:c,itemTextColor:m,itemTextColorHover:b,itemTextColorPressed:z,itemTextColorActive:x,fontSize:p,fontWeightActive:R,itemBorderRadius:K,itemColorHover:F,itemColorPressed:X,itemLineHeight:D}}=l.value;return{"--n-font-size":p,"--n-bezier":s,"--n-item-text-color":m,"--n-item-text-color-hover":b,"--n-item-text-color-pressed":z,"--n-item-text-color-active":x,"--n-separator-color":c,"--n-item-color-hover":F,"--n-item-color-pressed":X,"--n-item-border-radius":K,"--n-font-weight-active":R,"--n-item-line-height":D}}),a=t?Oe("breadcrumb",void 0,n,e):void 0;return{mergedClsPrefix:o,cssVars:t?void 0:n,themeClass:a?.themeClass,onRender:a?.onRender}},render(){return this.onRender?.(),u(),I("nav",{class:E([`${this.mergedClsPrefix}-breadcrumb`,this.themeClass]),style:he(this.cssVars),"aria-label":"Breadcrumb"},[w("ul",null,[P(()=>this.$slots.default?.())])],6)}});function Oo(e=lo?window:null){const o=()=>{const{hash:n,host:a,hostname:s,href:c,origin:m,pathname:b,port:z,protocol:x,search:p}=e?.location||{};return{hash:n,host:a,hostname:s,href:c,origin:m,pathname:b,port:z,protocol:x,search:p}},t=V(o()),l=()=>{t.value=o()};return io(()=>{e&&(e.addEventListener("popstate",l),e.addEventListener("hashchange",l))}),ao(()=>{e&&(e.removeEventListener("popstate",l),e.removeEventListener("hashchange",l))}),t}const Bo={separator:String,href:String,clickable:{type:Boolean,default:!0},showSeparator:{type:Boolean,default:!0},onClick:Function};var Do=T({name:"BreadcrumbItem",props:Bo,slots:Object,setup(e,{slots:o}){const t=B(De,null);if(!t)return()=>null;const{separatorRef:l,mergedClsPrefixRef:n}=t,a=Oo(),s=y(()=>e.href?"a":"span"),c=y(()=>a.value.href===e.href?"location":null);return()=>{const{value:m}=n;return u(),I("li",{class:E([`${m}-breadcrumb-item`,e.clickable&&`${m}-breadcrumb-item--clickable`])},[P(()=>_(s.value,{class:`${m}-breadcrumb-item__link`,"aria-current":c.value,href:e.href,onClick:e.onClick},o)),P(()=>e.showSeparator&&(u(),I("span",{class:E(`${m}-breadcrumb-item__separator`),"aria-hidden":"true"},[P(()=>so(o.separator,()=>[e.separator??l.value]))],2)))],2)}}});const $o=W("n-layout-sider"),ee=W("n-menu"),$e=W("n-submenu"),Ce=W("n-menu-item-group"),Te=[g("&::before","background-color: var(--n-item-color-hover);"),v("arrow",`
 color: var(--n-arrow-color-hover);
 `),v("icon",`
 color: var(--n-item-icon-color-hover);
 `),f("menu-item-content-header",`
 color: var(--n-item-text-color-hover);
 `,[g("a",`
 color: var(--n-item-text-color-hover);
 `),v("extra",`
 color: var(--n-item-text-color-hover);
 `)])],Ke=[v("icon",`
 color: var(--n-item-icon-color-hover-horizontal);
 `),f("menu-item-content-header",`
 color: var(--n-item-text-color-hover-horizontal);
 `,[g("a",`
 color: var(--n-item-text-color-hover-horizontal);
 `),v("extra",`
 color: var(--n-item-text-color-hover-horizontal);
 `)])];var Uo=g([f("menu",`
 background-color: var(--n-color);
 color: var(--n-item-text-color);
 overflow: hidden;
 transition: background-color .3s var(--n-bezier);
 box-sizing: border-box;
 font-size: var(--n-font-size);
 padding-bottom: 6px;
 `,[k("horizontal",`
 max-width: 100%;
 width: 100%;
 display: flex;
 overflow: hidden;
 padding-bottom: 0;
 `,[f("submenu","margin: 0;"),f("menu-item","margin: 0;"),f("menu-item-content",`
 padding: 0 20px;
 border-bottom: 2px solid #0000;
 `,[g("&::before","display: none;"),k("selected","border-bottom: 2px solid var(--n-border-color-horizontal)")]),f("menu-item-content",[k("selected",[v("icon","color: var(--n-item-icon-color-active-horizontal);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active-horizontal);
 `,[g("a","color: var(--n-item-text-color-active-horizontal);"),v("extra","color: var(--n-item-text-color-active-horizontal);")])]),k("child-active",`
 border-bottom: 2px solid var(--n-border-color-horizontal);
 `,[f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-horizontal);
 `,[g("a",`
 color: var(--n-item-text-color-child-active-horizontal);
 `),v("extra",`
 color: var(--n-item-text-color-child-active-horizontal);
 `)]),v("icon",`
 color: var(--n-item-icon-color-child-active-horizontal);
 `)]),J("disabled",[J("selected, child-active",[g("&:focus-within",Ke)]),k("selected",[U(null,[v("icon","color: var(--n-item-icon-color-active-hover-horizontal);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active-hover-horizontal);
 `,[g("a","color: var(--n-item-text-color-active-hover-horizontal);"),v("extra","color: var(--n-item-text-color-active-hover-horizontal);")])])]),k("child-active",[U(null,[v("icon","color: var(--n-item-icon-color-child-active-hover-horizontal);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-hover-horizontal);
 `,[g("a","color: var(--n-item-text-color-child-active-hover-horizontal);"),v("extra","color: var(--n-item-text-color-child-active-hover-horizontal);")])])]),U("border-bottom: 2px solid var(--n-border-color-horizontal);",Ke)]),f("menu-item-content-header",[g("a","color: var(--n-item-text-color-horizontal);")])])]),J("responsive",[f("menu-item-content-header",`
 overflow: hidden;
 text-overflow: ellipsis;
 `)]),k("collapsed",[f("menu-item-content",[k("selected",[g("&::before",`
 background-color: var(--n-item-color-active-collapsed) !important;
 `)]),f("menu-item-content-header","opacity: 0;"),v("arrow","opacity: 0;"),v("icon","color: var(--n-item-icon-color-collapsed);")])]),f("menu-item",`
 height: var(--n-item-height);
 margin-top: 6px;
 position: relative;
 `),f("menu-item-content",`
 box-sizing: border-box;
 line-height: 1.75;
 height: 100%;
 display: grid;
 grid-template-areas: "icon content arrow";
 grid-template-columns: auto 1fr auto;
 align-items: center;
 cursor: pointer;
 position: relative;
 padding-right: 18px;
 transition:
 background-color .3s var(--n-bezier),
 padding-left .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 `,[g("> *","z-index: 1;"),g("&::before",`
 z-index: auto;
 content: "";
 background-color: #0000;
 position: absolute;
 left: 8px;
 right: 8px;
 top: 0;
 bottom: 0;
 pointer-events: none;
 border-radius: var(--n-border-radius);
 transition: background-color .3s var(--n-bezier);
 `),k("disabled",`
 opacity: .45;
 cursor: not-allowed;
 `),k("collapsed",[v("arrow","transform: rotate(0);")]),k("selected",[g("&::before","background-color: var(--n-item-color-active);"),v("arrow","color: var(--n-arrow-color-active);"),v("icon","color: var(--n-item-icon-color-active);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active);
 `,[g("a","color: var(--n-item-text-color-active);"),v("extra","color: var(--n-item-text-color-active);")])]),k("child-active",[f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active);
 `,[g("a",`
 color: var(--n-item-text-color-child-active);
 `),v("extra",`
 color: var(--n-item-text-color-child-active);
 `)]),v("arrow",`
 color: var(--n-arrow-color-child-active);
 `),v("icon",`
 color: var(--n-item-icon-color-child-active);
 `)]),J("disabled",[J("selected, child-active",[g("&:focus-within",Te)]),k("selected",[U(null,[v("arrow","color: var(--n-arrow-color-active-hover);"),v("icon","color: var(--n-item-icon-color-active-hover);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active-hover);
 `,[g("a","color: var(--n-item-text-color-active-hover);"),v("extra","color: var(--n-item-text-color-active-hover);")])])]),k("child-active",[U(null,[v("arrow","color: var(--n-arrow-color-child-active-hover);"),v("icon","color: var(--n-item-icon-color-child-active-hover);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-hover);
 `,[g("a","color: var(--n-item-text-color-child-active-hover);"),v("extra","color: var(--n-item-text-color-child-active-hover);")])])]),k("selected",[U(null,[g("&::before","background-color: var(--n-item-color-active-hover);")])]),U(null,Te)]),v("icon",`
 grid-area: icon;
 color: var(--n-item-icon-color);
 transition:
 color .3s var(--n-bezier),
 font-size .3s var(--n-bezier),
 margin-right .3s var(--n-bezier);
 box-sizing: content-box;
 display: inline-flex;
 align-items: center;
 justify-content: center;
 `),v("arrow",`
 grid-area: arrow;
 font-size: 16px;
 color: var(--n-arrow-color);
 transform: rotate(180deg);
 opacity: 1;
 transition:
 color .3s var(--n-bezier),
 transform 0.2s var(--n-bezier),
 opacity 0.2s var(--n-bezier);
 `),f("menu-item-content-header",`
 grid-area: content;
 transition:
 color .3s var(--n-bezier),
 opacity .3s var(--n-bezier);
 opacity: 1;
 white-space: nowrap;
 color: var(--n-item-text-color);
 `,[g("a",`
 outline: none;
 text-decoration: none;
 transition: color .3s var(--n-bezier);
 color: var(--n-item-text-color);
 `,[g("&::before",`
 content: "";
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 `)]),v("extra",`
 font-size: .93em;
 color: var(--n-group-text-color);
 transition: color .3s var(--n-bezier);
 `)])]),f("submenu",`
 cursor: pointer;
 position: relative;
 margin-top: 6px;
 `,[f("menu-item-content",`
 height: var(--n-item-height);
 `),f("submenu-children",`
 overflow: hidden;
 padding: 0;
 `,[co({duration:".2s"})])]),f("menu-item-group",[f("menu-item-group-title",`
 margin-top: 6px;
 color: var(--n-group-text-color);
 cursor: default;
 font-size: .93em;
 height: 36px;
 display: flex;
 align-items: center;
 transition:
 padding-left .3s var(--n-bezier),
 color .3s var(--n-bezier);
 `)])]),f("menu-tooltip",[g("a",`
 color: inherit;
 text-decoration: none;
 `)]),f("menu-divider",`
 transition: background-color .3s var(--n-bezier);
 background-color: var(--n-divider-color);
 height: 1px;
 margin: 6px 18px;
 `)]);function U(e,o){return[k("hover",e,o),g("&:hover",e,o)]}var Vo=T({name:"MenuDivider",setup(){const{mergedClsPrefixRef:e,isHorizontalRef:o}=B(ee);return()=>o.value?null:(u(),I("div",{key:1,class:E(`${e.value}-menu-divider`)},null,2))}}),Go=T({name:"ChevronDownFilled",render(){return(()=>{const e=be("f3af82a2aab086a5");return e[0]||(e[0]=w("svg",{viewBox:"0 0 16 16",fill:"none",xmlns:"http://www.w3.org/2000/svg"},[w("path",{d:"M3.20041 5.73966C3.48226 5.43613 3.95681 5.41856 4.26034 5.70041L8 9.22652L11.7397 5.70041C12.0432 5.41856 12.5177 5.43613 12.7996 5.73966C13.0815 6.0432 13.0639 6.51775 12.7603 6.7996L8.51034 10.7996C8.22258 11.0668 7.77743 11.0668 7.48967 10.7996L3.23966 6.7996C2.93613 6.51775 2.91856 6.0432 3.20041 5.73966Z",fill:"currentColor"})],-1))})()}});const qo=["onClick"];var Ue=T({name:"MenuOptionContent",props:{collapsed:Boolean,disabled:Boolean,title:[String,Function],icon:Function,extra:[String,Function],showArrow:Boolean,childActive:Boolean,hover:Boolean,paddingLeft:Number,selected:Boolean,maxIconSize:{type:Number,required:!0},activeIconSize:{type:Number,required:!0},iconMarginRight:{type:Number,required:!0},clsPrefix:{type:String,required:!0},onClick:Function,tmNode:{type:Object,required:!0},isEllipsisPlaceholder:Boolean},setup(e){const{props:o}=B(ee);return{menuProps:o,style:y(()=>{const{paddingLeft:t}=e;return{paddingLeft:t&&`${t}px`}}),iconStyle:y(()=>{const{maxIconSize:t,activeIconSize:l,iconMarginRight:n}=e;return{width:`${t}px`,height:`${t}px`,fontSize:`${l}px`,marginRight:`${n}px`}})}},render(){const{clsPrefix:e,tmNode:o,menuProps:{renderIcon:t,renderLabel:l,renderExtra:n,expandIcon:a}}=this,s=t?t(o.rawNode):q(this.icon);return(()=>{const c=be("7bb10afc6caf8fa4");return u(),I("div",{onClick:m=>{this.onClick?.(m)},role:"none",class:E([`${e}-menu-item-content`,{[`${e}-menu-item-content--selected`]:this.selected,[`${e}-menu-item-content--collapsed`]:this.collapsed,[`${e}-menu-item-content--child-active`]:this.childActive,[`${e}-menu-item-content--disabled`]:this.disabled,[`${e}-menu-item-content--hover`]:this.hover}]),style:he(this.style)},[P(()=>s&&(u(),I("div",{class:E(`${e}-menu-item-content__icon`),style:he(this.iconStyle),role:"none"},[P(()=>[s])],6))),w("div",{class:E(`${e}-menu-item-content-header`),role:"none"},[this.isEllipsisPlaceholder?(u(),I(O,{key:0},[P(()=>this.title)],64)):(u(),I(O,{key:1},[l?(u(),I(O,{key:0},[P(()=>l(o.rawNode))],64)):(u(),I(O,{key:1},[P(()=>q(this.title))],64))],64)),this.extra||n?(u(),I("span",{key:2,class:E(`${e}-menu-item-content-header__extra`)},[c[0]||(c[0]=P(" ",-1)),n?(u(),I(O,{key:0},[P(()=>n(o.rawNode))],64)):(u(),I(O,{key:1},[P(()=>q(this.extra))],64))],2)):P(()=>null)],2),this.showArrow?(u(),M(uo,{key:0,ariaHidden:!0,class:E(`${e}-menu-item-content__arrow`),clsPrefix:e},{default:()=>a?a(o.rawNode):(u(),M(Go,{key:1}))},1032,["class","clsPrefix"])):P(()=>null)],14,qo)})()}});const re=8;function ye(e){const o=B(ee),{props:t,mergedCollapsedRef:l}=o,n=B($e,null),a=B(Ce,null),s=y(()=>t.mode==="horizontal"),c=y(()=>s.value?t.dropdownPlacement:"tmNodes"in e?"right-start":"right"),m=y(()=>Math.max(t.collapsedIconSize??t.iconSize,t.iconSize));return{dropdownPlacement:c,activeIconSize:y(()=>!s.value&&e.root&&l.value?t.collapsedIconSize??t.iconSize:t.iconSize),maxIconSize:m,paddingLeft:y(()=>{if(s.value)return;const{collapsedWidth:b,indent:z,rootIndent:x}=t,{root:p,isGroup:R}=e,K=x===void 0?z:x;return p?l.value?b/2-m.value/2:K:a&&typeof a.paddingLeftRef.value=="number"?l.value?b/2-m.value/2:z/2+a.paddingLeftRef.value:n&&typeof n.paddingLeftRef.value=="number"?(R?z/2:z)+n.paddingLeftRef.value:0}),iconMarginRight:y(()=>{const{collapsedWidth:b,indent:z,rootIndent:x}=t,{value:p}=m,{root:R}=e;return s.value||!R||!l.value?re:(x===void 0?z:x)+p+re-(b+p)/2}),NMenu:o,NSubmenu:n,NMenuOptionGroup:a}}const ze={internalKey:{type:[String,Number],required:!0},root:Boolean,isGroup:Boolean,level:{type:Number,required:!0},title:[String,Function],extra:[String,Function]},Ve={...ze,tmNode:{type:Object,required:!0},disabled:Boolean,icon:Function,onClick:Function},jo=xe(Ve),Xo=T({name:"MenuOption",props:Ve,setup(e){const o=ye(e),{NSubmenu:t,NMenu:l,NMenuOptionGroup:n}=o,{props:a,mergedClsPrefixRef:s,mergedCollapsedRef:c}=l,m=t?t.mergedDisabledRef:n?n.mergedDisabledRef:{value:!1},b=y(()=>m.value||e.disabled);function z(p){const{onClick:R}=e;R&&R(p)}function x(p){b.value||(l.doSelect(e.internalKey,e.tmNode.rawNode),z(p))}return{mergedClsPrefix:s,dropdownPlacement:o.dropdownPlacement,paddingLeft:o.paddingLeft,iconMarginRight:o.iconMarginRight,maxIconSize:o.maxIconSize,activeIconSize:o.activeIconSize,mergedTheme:l.mergedThemeRef,menuProps:a,dropdownEnabled:pe(()=>e.root&&c.value&&a.mode!=="horizontal"&&!b.value),selected:pe(()=>l.mergedValueRef.value===e.internalKey),mergedDisabled:b,handleClick:x}},render(){const{mergedClsPrefix:e,mergedTheme:o,tmNode:t,menuProps:{renderLabel:l,nodeProps:n}}=this,a=n?.(t.rawNode);return u(),I("div",j(a,{role:"menuitem",class:[`${e}-menu-item`,a?.class]}),[(u(),M(So,{theme:o.peers.Tooltip,themeOverrides:o.peerOverrides.Tooltip,trigger:"hover",placement:this.dropdownPlacement,disabled:!this.dropdownEnabled||this.title===void 0,internalExtraClass:["menu-tooltip"]},{default:()=>l?l(t.rawNode):q(this.title),trigger:()=>(u(),M(Ue,{tmNode:t,clsPrefix:e,paddingLeft:this.paddingLeft,iconMarginRight:this.iconMarginRight,maxIconSize:this.maxIconSize,activeIconSize:this.activeIconSize,selected:this.selected,title:this.title,extra:this.extra,disabled:this.mergedDisabled,icon:this.icon,onClick:this.handleClick},null,8,["tmNode","clsPrefix","paddingLeft","iconMarginRight","maxIconSize","activeIconSize","selected","title","extra","disabled","icon","onClick"]))},1032,["theme","themeOverrides","placement","disabled"]))],16)}}),Ge={...ze,tmNode:{type:Object,required:!0},tmNodes:{type:Array,required:!0}},Yo=xe(Ge),Zo=T({name:"MenuOptionGroup",props:Ge,setup(e){const o=ye(e),{NSubmenu:t}=o,l=y(()=>t?.mergedDisabledRef.value?!0:e.tmNode.disabled);Q(Ce,{paddingLeftRef:o.paddingLeft,mergedDisabledRef:l});const{mergedClsPrefixRef:n,props:a}=B(ee);return function(){const{value:s}=n,c=o.paddingLeft.value,{nodeProps:m}=a,b=m?.(e.tmNode.rawNode);return(()=>{const z=be("45eca6a63be5028b");return u(),I("div",{class:E(`${s}-menu-item-group`),role:"group"},[w("div",j(b,{class:[`${s}-menu-item-group-title`,b?.class],style:[b?.style||"",c!==void 0?`padding-left: ${c}px;`:""]}),[P(()=>q(e.title)),e.extra?(u(),I(O,{key:0},[z[0]||(z[0]=P(" ",-1)),P(()=>q(e.extra))],64)):P(()=>null)],16),w("div",null,[P(()=>e.tmNodes.map(x=>Pe(x,a)))])],2)})()}}}),Jo=["aria-expanded","id"],Qo=["aria-expanded","id"],qe={...ze,rawNodes:{type:Array,default:()=>[]},tmNodes:{type:Array,default:()=>[]},tmNode:{type:Object,required:!0},disabled:Boolean,icon:Function,onClick:Function,domId:String,virtualChildActive:{type:Boolean,default:void 0},isEllipsisPlaceholder:Boolean},Wo=xe(qe),fe=T({name:"Submenu",props:qe,setup(e){const o=ye(e),{NMenu:t,NSubmenu:l}=o,{props:n,mergedCollapsedRef:a,mergedThemeRef:s}=t,c=y(()=>{const{disabled:p}=e;return l?.mergedDisabledRef.value||n.disabled?!0:p}),m=V(!1);Q($e,{paddingLeftRef:o.paddingLeft,mergedDisabledRef:c}),Q(Ce,null);function b(){const{onClick:p}=e;p&&p()}function z(){c.value||(a.value||t.toggleExpand(e.internalKey),b())}function x(p){m.value=p}return{menuProps:n,mergedTheme:s,doSelect:t.doSelect,inverted:t.invertedRef,isHorizontal:t.isHorizontalRef,mergedClsPrefix:t.mergedClsPrefixRef,maxIconSize:o.maxIconSize,activeIconSize:o.activeIconSize,iconMarginRight:o.iconMarginRight,dropdownPlacement:o.dropdownPlacement,dropdownShow:m,paddingLeft:o.paddingLeft,mergedDisabled:c,mergedValue:t.mergedValueRef,childActive:pe(()=>e.virtualChildActive??t.activePathRef.value.includes(e.internalKey)),collapsed:y(()=>n.mode==="horizontal"?!1:a.value?!0:!t.mergedExpandedKeysRef.value.includes(e.internalKey)),dropdownEnabled:y(()=>!c.value&&(n.mode==="horizontal"||a.value)),handlePopoverShowChange:x,handleClick:z}},render(){const{mergedClsPrefix:e,menuProps:{renderIcon:o,renderLabel:t}}=this,l=()=>{const{isHorizontal:a,paddingLeft:s,collapsed:c,mergedDisabled:m,maxIconSize:b,activeIconSize:z,title:x,childActive:p,icon:R,handleClick:K,menuProps:{nodeProps:F},dropdownShow:X,iconMarginRight:D,tmNode:oe,mergedClsPrefix:Y,isEllipsisPlaceholder:le,extra:se}=this,Z=F?.(oe.rawNode);return u(),I("div",j(Z,{class:[`${Y}-menu-item`,Z?.class],role:"menuitem"}),[(u(),M(Ue,{tmNode:oe,paddingLeft:s,collapsed:c,disabled:m,iconMarginRight:D,maxIconSize:b,activeIconSize:z,title:x,extra:se,showArrow:!a,childActive:p,clsPrefix:Y,icon:R,hover:X,onClick:K,isEllipsisPlaceholder:le},null,8,["tmNode","paddingLeft","collapsed","disabled","iconMarginRight","maxIconSize","activeIconSize","title","extra","showArrow","childActive","clsPrefix","icon","hover","onClick","isEllipsisPlaceholder"]))],16)},n=()=>(u(),M(mo,null,{default:()=>{const{tmNodes:a,collapsed:s}=this;return s?null:(u(),I("div",{key:1,class:E(`${e}-submenu-children`),role:"menu"},[P(()=>a.map(c=>Pe(c,this.menuProps)))],2))}},1024));return this.root?(u(),M(Be,j({key:2,size:"large",trigger:"hover"},this.menuProps?.dropdownProps,{themeOverrides:this.mergedTheme.peerOverrides.Dropdown,theme:this.mergedTheme.peers.Dropdown,builtinThemeOverrides:{fontSizeLarge:"14px",optionIconSizeLarge:"18px"},value:this.mergedValue,disabled:!this.dropdownEnabled,placement:this.dropdownPlacement,keyField:this.menuProps.keyField,labelField:this.menuProps.labelField,childrenField:this.menuProps.childrenField,onUpdateShow:this.handlePopoverShowChange,options:this.rawNodes,onSelect:this.doSelect,inverted:this.inverted,renderIcon:o,renderLabel:t}),{default:()=>(u(),I("div",{class:E(`${e}-submenu`),role:"menu","aria-expanded":!this.collapsed,id:this.domId},[P(()=>l()),this.isHorizontal?P(()=>null):(u(),I(O,{key:1},[P(()=>n())],64))],10,Jo))},1040,["themeOverrides","theme","value","disabled","placement","keyField","labelField","childrenField","onUpdateShow","options","onSelect","inverted","renderIcon","renderLabel"])):(u(),I("div",{key:3,class:E(`${e}-submenu`),role:"menu","aria-expanded":!this.collapsed,id:this.domId},[P(()=>l()),P(()=>n())],10,Qo))}});function ge(e){return e.type==="divider"||e.type==="render"}function et(e){return e.type==="divider"}function Pe(e,o){const{rawNode:t}=e,{show:l}=t;if(l===!1)return null;if(ge(t))return et(t)?(u(),M(Vo,j({key:e.key},t.props),null,16)):null;const{labelField:n}=o,{key:a,level:s,isGroup:c}=e,m={...t,title:t.title||t[n],extra:t.titleExtra||t.extra,key:a,internalKey:a,level:s,root:s===0,isGroup:c};return e.children?e.isGroup?_(Zo,de(m,Yo,{tmNode:e,tmNodes:e.children,key:a})):_(fe,de(m,Wo,{key:a,rawNodes:t[o.childrenField],tmNodes:e.children,tmNode:e})):_(Xo,de(m,jo,{key:a,tmNode:e}))}const ot={...ae.props,options:{type:Array,default:()=>[]},collapsed:{type:Boolean,default:void 0},collapsedWidth:{type:Number,default:48},iconSize:{type:Number,default:20},collapsedIconSize:{type:Number,default:24},rootIndent:Number,indent:{type:Number,default:32},labelField:{type:String,default:"label"},keyField:{type:String,default:"key"},childrenField:{type:String,default:"children"},disabledField:{type:String,default:"disabled"},defaultExpandAll:Boolean,defaultExpandedKeys:Array,expandedKeys:Array,value:[String,Number],defaultValue:{type:[String,Number],default:null},mode:{type:String,default:"vertical"},watchProps:{type:Array,default:void 0},disabled:Boolean,show:{type:Boolean,default:!0},inverted:Boolean,"onUpdate:expandedKeys":[Function,Array],onUpdateExpandedKeys:[Function,Array],onUpdateValue:[Function,Array],"onUpdate:value":[Function,Array],expandIcon:Function,renderIcon:Function,renderLabel:Function,renderExtra:Function,dropdownProps:Object,accordion:Boolean,nodeProps:Function,dropdownPlacement:{type:String,default:"bottom"},responsive:Boolean,items:Array,onOpenNamesChange:[Function,Array],onSelect:[Function,Array],onExpandedNamesChange:[Function,Array],expandedNames:Array,defaultExpandedNames:Array};var tt=T({name:"Menu",inheritAttrs:!1,props:ot,setup(e){const{mergedClsPrefixRef:o,inlineThemeDisabled:t}=Le(e),l=ae("Menu","-menu",Uo,vo,e,o),n=B($o,null),a=y(()=>{const{collapsed:d}=e;if(d!==void 0)return d;if(n){const{collapseModeRef:C,collapsedRef:r}=n;if(C.value==="width")return r.value??!1}return!1}),s=y(()=>{const{keyField:d,childrenField:C,disabledField:r}=e;return ue(e.items||e.options,{getIgnored(h){return ge(h)},getChildren(h){return h[C]},getDisabled(h){return h[r]},getKey(h){return h[d]??h.name}})}),c=y(()=>new Set(s.value.treeNodes.map(d=>d.key))),{watchProps:m}=e,b=V(null);m?.includes("defaultValue")?Me(()=>{b.value=e.defaultValue}):b.value=e.defaultValue;const z=ve(e,"value"),x=Ee(z,b),p=V([]),R=()=>{p.value=e.defaultExpandAll?s.value.getNonLeafKeys():e.defaultExpandedNames||e.defaultExpandedKeys||s.value.getPath(x.value,{includeSelf:!1}).keyPath};m?.includes("defaultExpandedKeys")?Me(R):R();const K=Ro(e,["expandedNames","expandedKeys"]),F=Ee(K,p),X=y(()=>s.value.treeNodes),D=y(()=>s.value.getPath(x.value).keyPath);Q(ee,{props:e,mergedCollapsedRef:a,mergedThemeRef:l,mergedValueRef:x,mergedExpandedKeysRef:F,activePathRef:D,mergedClsPrefixRef:o,isHorizontalRef:y(()=>e.mode==="horizontal"),invertedRef:ve(e,"inverted"),doSelect:oe,toggleExpand:le});function oe(d,C){const{"onUpdate:value":r,onUpdateValue:h,onSelect:N}=e;h&&$(h,d,C),r&&$(r,d,C),N&&$(N,d,C),b.value=d}function Y(d){const{"onUpdate:expandedKeys":C,onUpdateExpandedKeys:r,onExpandedNamesChange:h,onOpenNamesChange:N}=e;C&&$(C,d),r&&$(r,d),h&&$(h,d),N&&$(N,d),p.value=d}function le(d){const C=Array.from(F.value),r=C.findIndex(h=>h===d);if(~r)C.splice(r,1);else{if(e.accordion&&c.value.has(d)){const h=C.findIndex(N=>c.value.has(N));h>-1&&C.splice(h,1)}C.push(d)}Y(C)}const se=d=>{const C=s.value.getPath(d??x.value,{includeSelf:!1}).keyPath;if(!C.length)return;const r=Array.from(F.value),h=new Set([...r,...C]);e.accordion&&c.value.forEach(N=>{h.has(N)&&!C.includes(N)&&h.delete(N)}),Y(Array.from(h))},Z=y(()=>{const{inverted:d}=e,{common:{cubicBezierEaseInOut:C},self:r}=l.value,{borderRadius:h,borderColorHorizontal:N,fontSize:oo,itemHeight:to,dividerColor:ro}=r,i={"--n-divider-color":ro,"--n-bezier":C,"--n-font-size":oo,"--n-border-color-horizontal":N,"--n-border-radius":h,"--n-item-height":to};return d?(i["--n-group-text-color"]=r.groupTextColorInverted,i["--n-color"]=r.colorInverted,i["--n-item-text-color"]=r.itemTextColorInverted,i["--n-item-text-color-hover"]=r.itemTextColorHoverInverted,i["--n-item-text-color-active"]=r.itemTextColorActiveInverted,i["--n-item-text-color-child-active"]=r.itemTextColorChildActiveInverted,i["--n-item-text-color-child-active-hover"]=r.itemTextColorChildActiveInverted,i["--n-item-text-color-active-hover"]=r.itemTextColorActiveHoverInverted,i["--n-item-icon-color"]=r.itemIconColorInverted,i["--n-item-icon-color-hover"]=r.itemIconColorHoverInverted,i["--n-item-icon-color-active"]=r.itemIconColorActiveInverted,i["--n-item-icon-color-active-hover"]=r.itemIconColorActiveHoverInverted,i["--n-item-icon-color-child-active"]=r.itemIconColorChildActiveInverted,i["--n-item-icon-color-child-active-hover"]=r.itemIconColorChildActiveHoverInverted,i["--n-item-icon-color-collapsed"]=r.itemIconColorCollapsedInverted,i["--n-item-text-color-horizontal"]=r.itemTextColorHorizontalInverted,i["--n-item-text-color-hover-horizontal"]=r.itemTextColorHoverHorizontalInverted,i["--n-item-text-color-active-horizontal"]=r.itemTextColorActiveHorizontalInverted,i["--n-item-text-color-child-active-horizontal"]=r.itemTextColorChildActiveHorizontalInverted,i["--n-item-text-color-child-active-hover-horizontal"]=r.itemTextColorChildActiveHoverHorizontalInverted,i["--n-item-text-color-active-hover-horizontal"]=r.itemTextColorActiveHoverHorizontalInverted,i["--n-item-icon-color-horizontal"]=r.itemIconColorHorizontalInverted,i["--n-item-icon-color-hover-horizontal"]=r.itemIconColorHoverHorizontalInverted,i["--n-item-icon-color-active-horizontal"]=r.itemIconColorActiveHorizontalInverted,i["--n-item-icon-color-active-hover-horizontal"]=r.itemIconColorActiveHoverHorizontalInverted,i["--n-item-icon-color-child-active-horizontal"]=r.itemIconColorChildActiveHorizontalInverted,i["--n-item-icon-color-child-active-hover-horizontal"]=r.itemIconColorChildActiveHoverHorizontalInverted,i["--n-arrow-color"]=r.arrowColorInverted,i["--n-arrow-color-hover"]=r.arrowColorHoverInverted,i["--n-arrow-color-active"]=r.arrowColorActiveInverted,i["--n-arrow-color-active-hover"]=r.arrowColorActiveHoverInverted,i["--n-arrow-color-child-active"]=r.arrowColorChildActiveInverted,i["--n-arrow-color-child-active-hover"]=r.arrowColorChildActiveHoverInverted,i["--n-item-color-hover"]=r.itemColorHoverInverted,i["--n-item-color-active"]=r.itemColorActiveInverted,i["--n-item-color-active-hover"]=r.itemColorActiveHoverInverted,i["--n-item-color-active-collapsed"]=r.itemColorActiveCollapsedInverted):(i["--n-group-text-color"]=r.groupTextColor,i["--n-color"]=r.color,i["--n-item-text-color"]=r.itemTextColor,i["--n-item-text-color-hover"]=r.itemTextColorHover,i["--n-item-text-color-active"]=r.itemTextColorActive,i["--n-item-text-color-child-active"]=r.itemTextColorChildActive,i["--n-item-text-color-child-active-hover"]=r.itemTextColorChildActiveHover,i["--n-item-text-color-active-hover"]=r.itemTextColorActiveHover,i["--n-item-icon-color"]=r.itemIconColor,i["--n-item-icon-color-hover"]=r.itemIconColorHover,i["--n-item-icon-color-active"]=r.itemIconColorActive,i["--n-item-icon-color-active-hover"]=r.itemIconColorActiveHover,i["--n-item-icon-color-child-active"]=r.itemIconColorChildActive,i["--n-item-icon-color-child-active-hover"]=r.itemIconColorChildActiveHover,i["--n-item-icon-color-collapsed"]=r.itemIconColorCollapsed,i["--n-item-text-color-horizontal"]=r.itemTextColorHorizontal,i["--n-item-text-color-hover-horizontal"]=r.itemTextColorHoverHorizontal,i["--n-item-text-color-active-horizontal"]=r.itemTextColorActiveHorizontal,i["--n-item-text-color-child-active-horizontal"]=r.itemTextColorChildActiveHorizontal,i["--n-item-text-color-child-active-hover-horizontal"]=r.itemTextColorChildActiveHoverHorizontal,i["--n-item-text-color-active-hover-horizontal"]=r.itemTextColorActiveHoverHorizontal,i["--n-item-icon-color-horizontal"]=r.itemIconColorHorizontal,i["--n-item-icon-color-hover-horizontal"]=r.itemIconColorHoverHorizontal,i["--n-item-icon-color-active-horizontal"]=r.itemIconColorActiveHorizontal,i["--n-item-icon-color-active-hover-horizontal"]=r.itemIconColorActiveHoverHorizontal,i["--n-item-icon-color-child-active-horizontal"]=r.itemIconColorChildActiveHorizontal,i["--n-item-icon-color-child-active-hover-horizontal"]=r.itemIconColorChildActiveHoverHorizontal,i["--n-arrow-color"]=r.arrowColor,i["--n-arrow-color-hover"]=r.arrowColorHover,i["--n-arrow-color-active"]=r.arrowColorActive,i["--n-arrow-color-active-hover"]=r.arrowColorActiveHover,i["--n-arrow-color-child-active"]=r.arrowColorChildActive,i["--n-arrow-color-child-active-hover"]=r.arrowColorChildActiveHover,i["--n-item-color-hover"]=r.itemColorHover,i["--n-item-color-active"]=r.itemColorActive,i["--n-item-color-active-hover"]=r.itemColorActiveHover,i["--n-item-color-active-collapsed"]=r.itemColorActiveCollapsed),i}),Ie=t?Oe("menu",y(()=>e.inverted?"a":"b"),Z,e):void 0,ce=po(),_e=V(null),je=V(null);let Se=!0;const ke=()=>{Se?Se=!1:_e.value?.sync({showAllItemsBeforeCalculate:!0})};function Xe(){return document.getElementById(ce)}const te=V(-1);function Ye(d){te.value=e.options.length-d}function Ze(d){d||(te.value=-1)}const Je=y(()=>{const d=te.value;return{children:d===-1?[]:e.options.slice(d)}}),Qe=y(()=>{const{childrenField:d,disabledField:C,keyField:r}=e;return ue([Je.value],{getIgnored(h){return ge(h)},getChildren(h){return h[d]},getDisabled(h){return h[C]},getKey(h){return h[r]??h.name}})}),We=y(()=>ue([{}]).treeNodes[0]);function eo(){if(te.value===-1)return u(),M(fe,{root:!0,level:0,key:"__ellpisisGroupPlaceholder__",internalKey:"__ellpisisGroupPlaceholder__",title:"···",tmNode:We.value,domId:ce,isEllipsisPlaceholder:!0},null,8,["tmNode","domId"]);const d=Qe.value.treeNodes[0],C=D.value,r=!!d.children?.some(h=>C.includes(h.key));return u(),M(fe,{level:0,root:!0,key:"__ellpisisGroup__",internalKey:"__ellpisisGroup__",title:"···",virtualChildActive:r,tmNode:d,domId:ce,rawNodes:d.rawNode.children||[],tmNodes:d.children||[],isEllipsisPlaceholder:!0},null,8,["virtualChildActive","tmNode","domId","rawNodes","tmNodes"])}return{mergedClsPrefix:o,controlledExpandedKeys:K,uncontrolledExpanededKeys:p,mergedExpandedKeys:F,uncontrolledValue:b,mergedValue:x,activePath:D,tmNodes:X,mergedTheme:l,mergedCollapsed:a,cssVars:t?void 0:Z,themeClass:Ie?.themeClass,overflowRef:_e,counterRef:je,updateCounter:()=>{},onResize:ke,onUpdateOverflow:Ze,onUpdateCount:Ye,renderCounter:eo,getCounter:Xe,onRender:Ie?.onRender,showOption:se,deriveResponsiveState:ke}},render(){const{mergedClsPrefix:e,mode:o,themeClass:t,onRender:l}=this;l?.();const n=()=>this.tmNodes.map(c=>Pe(c,this.$props)),a=o==="horizontal"&&this.responsive,s=()=>_("div",j(this.$attrs,{role:o==="horizontal"?"menubar":"menu",class:[`${e}-menu`,t,`${e}-menu--${o}`,a&&`${e}-menu--responsive`,this.mergedCollapsed&&`${e}-menu--collapsed`],style:this.cssVars}),a?(u(),M(Mo,{key:2,ref:"overflowRef",onUpdateOverflow:this.onUpdateOverflow,getCounter:this.getCounter,onUpdateCount:this.onUpdateCount,updateCounter:this.updateCounter,style:{width:"100%",display:"flex",overflow:"hidden"}},{default:n,counter:this.renderCounter},1032,["onUpdateOverflow","getCounter","onUpdateCount","updateCounter"])):n());return a?(u(),M(ho,{key:3,onResize:this.onResize},{default:s},1032,["onResize"])):s()}});const me="huluwa.collapsed";function rt(e,o){try{const t=localStorage.getItem(e);return t===null?o:t==="1"}catch{return o}}function Fe(e,o){try{localStorage.setItem(e,o?"1":"0")}catch{}}const nt=fo("app",{state:()=>({collapsed:rt(me,!1),tagNavList:[],breadCrumb:[]}),actions:{toggleCollapsed(){this.collapsed=!this.collapsed,Fe(me,this.collapsed)},setCollapsed(e){this.collapsed=!!e,Fe(me,this.collapsed)},setBreadCrumb(e){this.breadCrumb=e}}}),it={components:{NBreadcrumb:Lo,NBreadcrumbItem:Do},name:"HeaderBar",props:{collapsed:Boolean,mobile:Boolean,pageTitle:String},emits:["on-coll-change"],computed:{currentTitle(){return this.pageTitle?this.pageTitle:this.$route.name==="messages"?"会话":this.$route.meta&&this.$route.meta.title||"首页"}}},at={class:"header-bar"},lt=["title"],st={class:"custom-content-con"};function ct(e,o,t,l,n,a){const s=H("n-breadcrumb-item"),c=H("n-breadcrumb");return u(),I("div",at,[w("button",{class:"icon-btn",type:"button",onClick:o[0]||(o[0]=m=>e.$emit("on-coll-change",!t.collapsed)),title:t.mobile?"打开菜单":t.collapsed?"展开侧栏":"收起侧栏"},[w("i",{class:ne(t.mobile?"fa-solid fa-bars":t.collapsed?"fa-solid fa-indent":"fa-solid fa-outdent")},null,2)],8,lt),S(c,{class:"crumb"},{default:A(()=>[S(s,null,{default:A(()=>[o[1]||(o[1]=w("i",{class:"fa-solid fa-house crumb-home"},null,-1)),w("span",null,ie(a.currentTitle),1)]),_:1})]),_:1}),w("div",st,[go(e.$slots,"default",{},void 0,!0)])])}const dt=we(it,[["render",ct],["__scopeId","data-v-5321f362"]]),ut={components:{NButton:xo,NDropdown:Be,NForm:Eo,NFormItem:Ho,NInput:ko,NModal:bo},name:"UserChip",setup(){return{auth:wo()}},data(){return{passwordModal:{visible:!1,loading:!1,data:{oldPassword:"",newPassword:"",confirmPassword:""}},passwordRules:{oldPassword:{required:!0,message:"请输入原密码",trigger:["input","blur"]},newPassword:[{required:!0,message:"请输入新密码",trigger:["input","blur"]},{min:6,message:"新密码至少 6 位",trigger:["input","blur"]}],confirmPassword:{required:!0,validator:(e,o)=>o?o!==this.passwordModal.data.newPassword?new Error("两次输入的密码不一致"):!0:new Error("请再次输入新密码"),trigger:["input","blur"]}}}},computed:{avatarText(){return(this.auth.username||"A").charAt(0).toUpperCase()},options(){return[{label:"修改密码",key:"password",icon:()=>_("i",{class:"fa-solid fa-key"})},{label:"退出登录",key:"logout",icon:()=>_("i",{class:"fa-solid fa-right-from-bracket"})}]}},methods:{handleSelect(e){e==="password"?(this.passwordModal.data={oldPassword:"",newPassword:"",confirmPassword:""},this.passwordModal.visible=!0):e==="logout"&&this.$dialog.warning({title:"退出登录",content:"确认退出当前账号？",positiveText:"退出",negativeText:"取消",onPositiveClick:()=>{this.auth.clear(),this.$router.push({name:"login"})}})},submitPassword(){this.$refs.passwordForm.validate(async e=>{if(!e){this.passwordModal.loading=!0;try{await this.auth.changePassword({oldPassword:this.passwordModal.data.oldPassword,newPassword:this.passwordModal.data.newPassword}),this.passwordModal.visible=!1,this.$message.success("密码已修改，请重新登录"),this.$router.push({name:"login"})}catch{}finally{this.passwordModal.loading=!1}}})}}},mt={class:"user-avatar-dropdown"},ht={class:"user-chip",type:"button"},vt={class:"ava"},pt={class:"name"},ft={class:"modal-footer"};function gt(e,o,t,l,n,a){const s=H("n-dropdown"),c=H("n-input"),m=H("n-form-item"),b=H("n-form"),z=H("n-button"),x=H("n-modal");return u(),I("div",mt,[S(s,{options:a.options,onSelect:a.handleSelect},{default:A(()=>[w("button",ht,[w("span",vt,ie(a.avatarText),1),w("span",pt,ie(l.auth.username||"admin"),1),o[5]||(o[5]=w("i",{class:"fa-solid fa-chevron-down"},null,-1))])]),_:1},8,["options","onSelect"]),S(x,{show:n.passwordModal.visible,"onUpdate:show":o[4]||(o[4]=p=>n.passwordModal.visible=p),preset:"card",title:"修改密码",style:{width:"460px"},"mask-closable":!1},{footer:A(()=>[w("div",ft,[S(z,{onClick:o[3]||(o[3]=p=>n.passwordModal.visible=!1)},{default:A(()=>[...o[6]||(o[6]=[Re("取消",-1)])]),_:1}),S(z,{type:"primary",loading:n.passwordModal.loading,onClick:a.submitPassword},{default:A(()=>[...o[7]||(o[7]=[Re("确认",-1)])]),_:1},8,["loading","onClick"])])]),default:A(()=>[S(b,{ref:"passwordForm",model:n.passwordModal.data,rules:n.passwordRules,"label-placement":"left","label-width":"90"},{default:A(()=>[S(m,{label:"原密码",path:"oldPassword"},{default:A(()=>[S(c,{value:n.passwordModal.data.oldPassword,"onUpdate:value":o[0]||(o[0]=p=>n.passwordModal.data.oldPassword=p),type:"password","show-password-on":"click",placeholder:"请输入原密码"},null,8,["value"])]),_:1}),S(m,{label:"新密码",path:"newPassword"},{default:A(()=>[S(c,{value:n.passwordModal.data.newPassword,"onUpdate:value":o[1]||(o[1]=p=>n.passwordModal.data.newPassword=p),type:"password","show-password-on":"click",placeholder:"至少 6 位"},null,8,["value"])]),_:1}),S(m,{label:"确认密码",path:"confirmPassword"},{default:A(()=>[S(c,{value:n.passwordModal.data.confirmPassword,"onUpdate:value":o[2]||(o[2]=p=>n.passwordModal.data.confirmPassword=p),type:"password","show-password-on":"click",placeholder:"请再次输入新密码"},null,8,["value"])]),_:1})]),_:1},8,["model","rules"])]),_:1},8,["show"])])}const bt=we(ut,[["render",gt],["__scopeId","data-v-572be87c"]]),G="messages-index",xt="会话",L=e=>{if(e)return()=>_("i",{class:e})},wt={name:"MainLayout",components:{HeaderBar:dt,UserChip:bt,NMenu:tt,NSpin:No},setup(){const e=nt(),o=Ao(),t=_o();return{appStore:e,chatStore:o,shellHidden:t}},data(){return{expandedKeys:[],isMobile:!1,mobileDrawerOpen:!1,pendingPage:null,navigationSequence:0}},computed:{collapsed(){return this.appStore.collapsed},siderCollapsed(){return this.isMobile?!this.mobileDrawerOpen:this.collapsed},headerCollapsed(){return this.isMobile?!this.mobileDrawerOpen:this.collapsed},activeMenu(){const e=this.pendingPage?this.pendingPage.name:this.$route.name;return e==="messages"?G:e},menuOptions(){const e=this.$router.options.routes.find(o=>o.path==="/");return!e||!Array.isArray(e.children)?[]:e.children.filter(o=>!(o.meta&&o.meta.hideInMenu)).map(o=>o.name==="messages"?this.messagesMenuOption(o):this.toMenuOption(o))}},watch:{"$route.fullPath"(){this.navigationSequence++,this.pendingPage=null},shellHidden:{immediate:!0,handler(e,o){if(e){this.stopMenuPolling();return}o!==void 0&&(this.chatStore.refreshSessions().catch(()=>{}),this.startMenuPolling())}},"$route.name"(e,o){(this.isMessagesGroup(e)||this.isMessagesGroup(o))&&(this.ensureMessagesExpanded(),e==="messages")||this.syncExpandedKeys()}},async mounted(){this.handleResize(),window.addEventListener("resize",this.handleResize),this.syncExpandedKeys();try{await this.chatStore.refreshSessions()}catch{}this.startMenuPolling()},beforeUnmount(){window.removeEventListener("resize",this.handleResize),this.stopMenuPolling()},methods:{handleResize(){const e=window.innerWidth<=900;e!==this.isMobile&&(this.isMobile=e,e&&(this.mobileDrawerOpen=!1)),this.$route.name==="messages"&&Po(Io())},handleCollapsedChange(e){if(this.isMobile){this.mobileDrawerOpen=!this.mobileDrawerOpen;return}this.appStore.setCollapsed(e)},closeDrawer(){this.mobileDrawerOpen=!1},ensureSessionsLoaded(){(this.chatStore.sessions||[]).length||this.chatStore.refreshSessions().catch(()=>{})},isMessagesGroup(e){return e==="messages"||e==="movie-subscriptions"||e==="movie-downloads"||e==="movie-library"||e==="movie-check-logs"},ensureMessagesExpanded(){this.ensureSessionsLoaded(),this.expandedKeys.includes("messages")||(this.expandedKeys=[...this.expandedKeys,"messages"])},startMenuPolling(){this._menuTimer||(this._menuTimer=setInterval(()=>{this.chatStore.refreshSessions().catch(()=>{})},15e3))},stopMenuPolling(){this._menuTimer&&(clearInterval(this._menuTimer),this._menuTimer=null)},toMenuOption(e){const o=Array.isArray(e.children)?e.children.filter(t=>!(t.meta&&t.meta.hideInMenu)):[];return o.length?{key:e.name,label:e.meta&&e.meta.title,icon:L(e.meta&&e.meta.icon),children:o.map(t=>this.toMenuOption(t))}:{key:e.name,label:()=>_("a",this.menuLinkProps(e.name),e.meta.title),icon:L(e.meta&&e.meta.icon)}},messagesMenuOption(e){const o=(this.chatStore.sessions||[]).reduce((t,l)=>t+(l.unread||0),0);return{key:e.name,label:()=>_("span",{class:"menu-label"},[_("span",e.meta&&e.meta.title),o>0?_("span",{class:"menu-dot"}):null]),icon:L(e.meta&&e.meta.icon),children:[{key:G,label:()=>_("a",{...this.menuLinkProps(G),class:"menu-label"},[_("span",xt),o>0?_("span",{class:"menu-badge"},o>99?"99+":String(o)):null]),icon:L("fa-solid fa-comments")},{key:"movie-subscriptions",label:()=>_("a",this.menuLinkProps("movie-subscriptions"),"我的订阅"),icon:L("fa-solid fa-bell")},{key:"movie-downloads",label:()=>_("a",this.menuLinkProps("movie-downloads"),"我的下载"),icon:L("fa-solid fa-download")},{key:"movie-library",label:()=>_("a",this.menuLinkProps("movie-library"),"剧集管理"),icon:L("fa-solid fa-hard-drive")},{key:"movie-check-logs",label:()=>_("a",this.menuLinkProps("movie-check-logs"),"巡检记录"),icon:L("fa-solid fa-list-check")}]}},syncExpandedKeys(){const e=this.$route.name;if(this.isMessagesGroup(e)){this.ensureMessagesExpanded();return}const o=this.$router.options.routes.find(n=>n.path==="/"),l=(o&&Array.isArray(o.children)?o.children:[]).filter(n=>Array.isArray(n.children)&&n.children.length).find(n=>n.children.some(a=>a.name===e));this.expandedKeys=l?[l.name]:[]},menuLinkProps(e){const o=e===G?"messages":e;return{href:this.$router.resolve({name:o}).href,onClick:t=>{t.stopPropagation(),t.button===0&&!t.ctrlKey&&!t.metaKey&&!t.shiftKey&&!t.altKey&&(t.preventDefault(),this.turnToPage(e))}}},menuNodeProps(e){if(e.children)return{};const o=()=>this.preloadMenuPage(e.key);return{onMouseenter:o,onFocusin:o,onTouchstart:o}},preloadMenuPage(e){return zo(this.$router,e===G?"messages":e)},async turnToPage(e){this.closeDrawer();const o=e===G?"messages":e,t=this.$router.resolve({name:o});if(this.pendingPage&&this.pendingPage.fullPath===t.fullPath||!this.pendingPage&&t.fullPath===this.$route.fullPath)return;const l=++this.navigationSequence;this.pendingPage={name:t.name,title:t.meta.title,fullPath:t.fullPath};try{await this.$router.push({name:o})}catch{l===this.navigationSequence&&this.$message.error("页面加载失败，请重试")}finally{l===this.navigationSequence&&(this.pendingPage=null)}},handleExpandedChange(e){for(const t of e){if(this.expandedKeys.includes(t))continue;const l=this.menuOptions.find(n=>n.key===t);for(const n of l?.children||[])this.preloadMenuPage(n.key)}if(this.isMessagesGroup(this.$route.name)&&!e.includes("messages")){this.expandedKeys=[...e,"messages"];return}this.expandedKeys=e}}},Ct={class:"logo"},yt={class:"logo-text"},zt={class:"sider-foot"},Pt={class:"main-body"},It=["aria-busy"],_t={key:0,class:"page-loading",role:"status","aria-live":"polite"};function St(e,o,t,l,n,a){const s=H("n-menu"),c=H("user-chip"),m=H("header-bar"),b=H("n-spin"),z=H("router-view");return u(),I("div",{class:ne(["main-console",{"is-mobile":n.isMobile,"drawer-open":n.isMobile&&n.mobileDrawerOpen,"is-immersive":l.shellHidden}])},[w("aside",{class:ne(["sider",{collapsed:a.siderCollapsed}])},[w("div",Ct,[o[2]||(o[2]=w("img",{class:"logo-mark",src:To,alt:"葫芦娃 Logo",width:"36",height:"36"},null,-1)),Ne(w("div",yt,[...o[1]||(o[1]=[w("div",{class:"logo-name"},"葫芦娃助手",-1),w("div",{class:"logo-sub"},"OPS CONSOLE",-1)])],512),[[Ae,!a.siderCollapsed]])]),S(s,{value:a.activeMenu,options:a.menuOptions,collapsed:a.siderCollapsed,"collapsed-width":64,"collapsed-icon-size":17,indent:18,"expanded-keys":n.expandedKeys,"node-props":a.menuNodeProps,accordion:"","onUpdate:value":a.turnToPage,"onUpdate:expandedKeys":a.handleExpandedChange},null,8,["value","options","collapsed","expanded-keys","node-props","onUpdate:value","onUpdate:expandedKeys"]),Ne(w("div",zt,[...o[3]||(o[3]=[w("span",null,"版本",-1),w("span",{class:"mono"},"v1.0.0",-1)])],512),[[Ae,!a.siderCollapsed]])],2),w("div",{class:ne(["sider-mask",{show:n.isMobile&&n.mobileDrawerOpen}]),"aria-hidden":"true",onClick:o[0]||(o[0]=(...x)=>a.closeDrawer&&a.closeDrawer(...x))},null,2),w("div",Pt,[S(m,{collapsed:a.headerCollapsed,mobile:n.isMobile,"page-title":n.pendingPage&&(n.pendingPage.name==="messages"?"会话":n.pendingPage.title),onOnCollChange:a.handleCollapsedChange},{default:A(()=>[S(c)]),_:1},8,["collapsed","mobile","page-title","onOnCollChange"]),w("div",{class:"content-wrapper","aria-busy":!!n.pendingPage},[n.pendingPage?(u(),I("div",_t,[S(b,{size:"medium"}),w("span",null,"正在打开"+ie(n.pendingPage.title||"页面")+"…",1)])):He("",!0),S(z,null,{default:A(({Component:x})=>[(u(),M(Co,null,[n.pendingPage?He("",!0):(u(),M(yo(x),{key:e.$route.path}))],1024))]),_:1})],8,It)])],2)}const Bt=we(wt,[["render",St],["__scopeId","data-v-01811efb"]]);export{Bt as default};
