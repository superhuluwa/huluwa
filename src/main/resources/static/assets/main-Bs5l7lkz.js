import{c as f,a as g,b as S,d as v,e as T,u as re,o as d,f as z,g as y,n as _,h as me,i as H,j as Le,k as Be,l as C,m as Q,p as io,q as J,t as he,r as U,s as ao,v as lo,w as so,x as L,y as R,z as co,A as Z,B as uo,C as xe,D as G,F as O,E as k,I as mo,G as we,H as j,J as ve,K as ho,L as le,V as vo,M as Ae,N as po,O as fo,P as $,Q as go,R as E,S as pe,T as P,U as N,W as fe,X as bo,Y as xo,Z as wo,_ as Co,$ as Me,a0 as Ne,a1 as He,a2 as Ee,a3 as yo,a4 as zo,a5 as se,a6 as Io,a7 as _o,a8 as So}from"./index-D-Ieq7DB.js";import{T as Po}from"./Tooltip-4cvTcWg9.js";import{D as $e}from"./Dropdown-f_X_CApl.js";import{u as Te,I as Ro}from"./Input-DGDWrra5.js";import{V as ko,c as ce}from"./create-DrGbyF8A.js";import{u as Ao}from"./use-compitable-DrrpCM3q.js";import{u as Mo}from"./chat-O6qVP1ms.js";import{_ as Ce}from"./_plugin-vue_export-helper-DlAUqK2U.js";import{F as No,a as Ho}from"./FormItem-a6lP6KgW.js";import{_ as Eo}from"./huluwa-logo-BxQdg_kY.js";import"./chat-CsoqRoT7.js";var To=f("breadcrumb",`
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
 `),g("&:not(:last-child)",[S("clickable",[v("link",`
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
 `)])])]);const De=Q("n-breadcrumb"),Fo={...re.props,separator:{type:String,default:"/"}};var Ko=T({name:"Breadcrumb",props:Fo,setup(e){const{mergedClsPrefixRef:o,inlineThemeDisabled:r}=Le(e),l=re("Breadcrumb","-breadcrumb",To,io,e,o);J(De,{separatorRef:he(e,"separator"),mergedClsPrefixRef:o});const i=C(()=>{const{common:{cubicBezierEaseInOut:s},self:{separatorColor:c,itemTextColor:m,itemTextColorHover:b,itemTextColorPressed:x,itemTextColorActive:I,fontSize:p,fontWeightActive:A,itemBorderRadius:F,itemColorHover:K,itemColorPressed:q,itemLineHeight:B}}=l.value;return{"--n-font-size":p,"--n-bezier":s,"--n-item-text-color":m,"--n-item-text-color-hover":b,"--n-item-text-color-pressed":x,"--n-item-text-color-active":I,"--n-separator-color":c,"--n-item-color-hover":K,"--n-item-color-pressed":q,"--n-item-border-radius":F,"--n-font-weight-active":A,"--n-item-line-height":B}}),a=r?Be("breadcrumb",void 0,i,e):void 0;return{mergedClsPrefix:o,cssVars:r?void 0:i,themeClass:a?.themeClass,onRender:a?.onRender}},render(){return this.onRender?.(),d(),z("nav",{class:H([`${this.mergedClsPrefix}-breadcrumb`,this.themeClass]),style:me(this.cssVars),"aria-label":"Breadcrumb"},[y("ul",null,[_(()=>this.$slots.default?.())])],6)}});function Oo(e=so?window:null){const o=()=>{const{hash:i,host:a,hostname:s,href:c,origin:m,pathname:b,port:x,protocol:I,search:p}=e?.location||{};return{hash:i,host:a,hostname:s,href:c,origin:m,pathname:b,port:x,protocol:I,search:p}},r=U(o()),l=()=>{r.value=o()};return ao(()=>{e&&(e.addEventListener("popstate",l),e.addEventListener("hashchange",l))}),lo(()=>{e&&(e.removeEventListener("popstate",l),e.removeEventListener("hashchange",l))}),r}const Lo={separator:String,href:String,clickable:{type:Boolean,default:!0},showSeparator:{type:Boolean,default:!0},onClick:Function};var Bo=T({name:"BreadcrumbItem",props:Lo,slots:Object,setup(e,{slots:o}){const r=L(De,null);if(!r)return()=>null;const{separatorRef:l,mergedClsPrefixRef:i}=r,a=Oo(),s=C(()=>e.href?"a":"span"),c=C(()=>a.value.href===e.href?"location":null);return()=>{const{value:m}=i;return d(),z("li",{class:H([`${m}-breadcrumb-item`,e.clickable&&`${m}-breadcrumb-item--clickable`])},[_(()=>R(s.value,{class:`${m}-breadcrumb-item__link`,"aria-current":c.value,href:e.href,onClick:e.onClick},o)),_(()=>e.showSeparator&&(d(),z("span",{class:H(`${m}-breadcrumb-item__separator`),"aria-hidden":"true"},[_(()=>co(o.separator,()=>[e.separator??l.value]))],2)))],2)}}});const $o=Q("n-layout-sider"),W=Q("n-menu"),Ue=Q("n-submenu"),ye=Q("n-menu-item-group"),Fe=[g("&::before","background-color: var(--n-item-color-hover);"),v("arrow",`
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
 `)])];var Do=g([f("menu",`
 background-color: var(--n-color);
 color: var(--n-item-text-color);
 overflow: hidden;
 transition: background-color .3s var(--n-bezier);
 box-sizing: border-box;
 font-size: var(--n-font-size);
 padding-bottom: 6px;
 `,[S("horizontal",`
 max-width: 100%;
 width: 100%;
 display: flex;
 overflow: hidden;
 padding-bottom: 0;
 `,[f("submenu","margin: 0;"),f("menu-item","margin: 0;"),f("menu-item-content",`
 padding: 0 20px;
 border-bottom: 2px solid #0000;
 `,[g("&::before","display: none;"),S("selected","border-bottom: 2px solid var(--n-border-color-horizontal)")]),f("menu-item-content",[S("selected",[v("icon","color: var(--n-item-icon-color-active-horizontal);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active-horizontal);
 `,[g("a","color: var(--n-item-text-color-active-horizontal);"),v("extra","color: var(--n-item-text-color-active-horizontal);")])]),S("child-active",`
 border-bottom: 2px solid var(--n-border-color-horizontal);
 `,[f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-horizontal);
 `,[g("a",`
 color: var(--n-item-text-color-child-active-horizontal);
 `),v("extra",`
 color: var(--n-item-text-color-child-active-horizontal);
 `)]),v("icon",`
 color: var(--n-item-icon-color-child-active-horizontal);
 `)]),Z("disabled",[Z("selected, child-active",[g("&:focus-within",Ke)]),S("selected",[D(null,[v("icon","color: var(--n-item-icon-color-active-hover-horizontal);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active-hover-horizontal);
 `,[g("a","color: var(--n-item-text-color-active-hover-horizontal);"),v("extra","color: var(--n-item-text-color-active-hover-horizontal);")])])]),S("child-active",[D(null,[v("icon","color: var(--n-item-icon-color-child-active-hover-horizontal);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-hover-horizontal);
 `,[g("a","color: var(--n-item-text-color-child-active-hover-horizontal);"),v("extra","color: var(--n-item-text-color-child-active-hover-horizontal);")])])]),D("border-bottom: 2px solid var(--n-border-color-horizontal);",Ke)]),f("menu-item-content-header",[g("a","color: var(--n-item-text-color-horizontal);")])])]),Z("responsive",[f("menu-item-content-header",`
 overflow: hidden;
 text-overflow: ellipsis;
 `)]),S("collapsed",[f("menu-item-content",[S("selected",[g("&::before",`
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
 `),S("disabled",`
 opacity: .45;
 cursor: not-allowed;
 `),S("collapsed",[v("arrow","transform: rotate(0);")]),S("selected",[g("&::before","background-color: var(--n-item-color-active);"),v("arrow","color: var(--n-arrow-color-active);"),v("icon","color: var(--n-item-icon-color-active);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active);
 `,[g("a","color: var(--n-item-text-color-active);"),v("extra","color: var(--n-item-text-color-active);")])]),S("child-active",[f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active);
 `,[g("a",`
 color: var(--n-item-text-color-child-active);
 `),v("extra",`
 color: var(--n-item-text-color-child-active);
 `)]),v("arrow",`
 color: var(--n-arrow-color-child-active);
 `),v("icon",`
 color: var(--n-item-icon-color-child-active);
 `)]),Z("disabled",[Z("selected, child-active",[g("&:focus-within",Fe)]),S("selected",[D(null,[v("arrow","color: var(--n-arrow-color-active-hover);"),v("icon","color: var(--n-item-icon-color-active-hover);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active-hover);
 `,[g("a","color: var(--n-item-text-color-active-hover);"),v("extra","color: var(--n-item-text-color-active-hover);")])])]),S("child-active",[D(null,[v("arrow","color: var(--n-arrow-color-child-active-hover);"),v("icon","color: var(--n-item-icon-color-child-active-hover);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-hover);
 `,[g("a","color: var(--n-item-text-color-child-active-hover);"),v("extra","color: var(--n-item-text-color-child-active-hover);")])])]),S("selected",[D(null,[g("&::before","background-color: var(--n-item-color-active-hover);")])]),D(null,Fe)]),v("icon",`
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
 `,[uo({duration:".2s"})])]),f("menu-item-group",[f("menu-item-group-title",`
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
 `)]);function D(e,o){return[S("hover",e,o),g("&:hover",e,o)]}var Uo=T({name:"MenuDivider",setup(){const{mergedClsPrefixRef:e,isHorizontalRef:o}=L(W);return()=>o.value?null:(d(),z("div",{key:1,class:H(`${e.value}-menu-divider`)},null,2))}}),Vo=T({name:"ChevronDownFilled",render(){return(()=>{const e=xe("f3af82a2aab086a5");return e[0]||(e[0]=y("svg",{viewBox:"0 0 16 16",fill:"none",xmlns:"http://www.w3.org/2000/svg"},[y("path",{d:"M3.20041 5.73966C3.48226 5.43613 3.95681 5.41856 4.26034 5.70041L8 9.22652L11.7397 5.70041C12.0432 5.41856 12.5177 5.43613 12.7996 5.73966C13.0815 6.0432 13.0639 6.51775 12.7603 6.7996L8.51034 10.7996C8.22258 11.0668 7.77743 11.0668 7.48967 10.7996L3.23966 6.7996C2.93613 6.51775 2.91856 6.0432 3.20041 5.73966Z",fill:"currentColor"})],-1))})()}});const Go=["onClick"];var Ve=T({name:"MenuOptionContent",props:{collapsed:Boolean,disabled:Boolean,title:[String,Function],icon:Function,extra:[String,Function],showArrow:Boolean,childActive:Boolean,hover:Boolean,paddingLeft:Number,selected:Boolean,maxIconSize:{type:Number,required:!0},activeIconSize:{type:Number,required:!0},iconMarginRight:{type:Number,required:!0},clsPrefix:{type:String,required:!0},onClick:Function,tmNode:{type:Object,required:!0},isEllipsisPlaceholder:Boolean},setup(e){const{props:o}=L(W);return{menuProps:o,style:C(()=>{const{paddingLeft:r}=e;return{paddingLeft:r&&`${r}px`}}),iconStyle:C(()=>{const{maxIconSize:r,activeIconSize:l,iconMarginRight:i}=e;return{width:`${r}px`,height:`${r}px`,fontSize:`${l}px`,marginRight:`${i}px`}})}},render(){const{clsPrefix:e,tmNode:o,menuProps:{renderIcon:r,renderLabel:l,renderExtra:i,expandIcon:a}}=this,s=r?r(o.rawNode):G(this.icon);return(()=>{const c=xe("7bb10afc6caf8fa4");return d(),z("div",{onClick:m=>{this.onClick?.(m)},role:"none",class:H([`${e}-menu-item-content`,{[`${e}-menu-item-content--selected`]:this.selected,[`${e}-menu-item-content--collapsed`]:this.collapsed,[`${e}-menu-item-content--child-active`]:this.childActive,[`${e}-menu-item-content--disabled`]:this.disabled,[`${e}-menu-item-content--hover`]:this.hover}]),style:me(this.style)},[_(()=>s&&(d(),z("div",{class:H(`${e}-menu-item-content__icon`),style:me(this.iconStyle),role:"none"},[_(()=>[s])],6))),y("div",{class:H(`${e}-menu-item-content-header`),role:"none"},[this.isEllipsisPlaceholder?(d(),z(O,{key:0},[_(()=>this.title)],64)):(d(),z(O,{key:1},[l?(d(),z(O,{key:0},[_(()=>l(o.rawNode))],64)):(d(),z(O,{key:1},[_(()=>G(this.title))],64))],64)),this.extra||i?(d(),z("span",{key:2,class:H(`${e}-menu-item-content-header__extra`)},[c[0]||(c[0]=_(" ",-1)),i?(d(),z(O,{key:0},[_(()=>i(o.rawNode))],64)):(d(),z(O,{key:1},[_(()=>G(this.extra))],64))],2)):_(()=>null)],2),this.showArrow?(d(),k(mo,{key:0,ariaHidden:!0,class:H(`${e}-menu-item-content__arrow`),clsPrefix:e},{default:()=>a?a(o.rawNode):(d(),k(Vo,{key:1}))},1032,["class","clsPrefix"])):_(()=>null)],14,Go)})()}});const te=8;function ze(e){const o=L(W),{props:r,mergedCollapsedRef:l}=o,i=L(Ue,null),a=L(ye,null),s=C(()=>r.mode==="horizontal"),c=C(()=>s.value?r.dropdownPlacement:"tmNodes"in e?"right-start":"right"),m=C(()=>Math.max(r.collapsedIconSize??r.iconSize,r.iconSize));return{dropdownPlacement:c,activeIconSize:C(()=>!s.value&&e.root&&l.value?r.collapsedIconSize??r.iconSize:r.iconSize),maxIconSize:m,paddingLeft:C(()=>{if(s.value)return;const{collapsedWidth:b,indent:x,rootIndent:I}=r,{root:p,isGroup:A}=e,F=I===void 0?x:I;return p?l.value?b/2-m.value/2:F:a&&typeof a.paddingLeftRef.value=="number"?l.value?b/2-m.value/2:x/2+a.paddingLeftRef.value:i&&typeof i.paddingLeftRef.value=="number"?(A?x/2:x)+i.paddingLeftRef.value:0}),iconMarginRight:C(()=>{const{collapsedWidth:b,indent:x,rootIndent:I}=r,{value:p}=m,{root:A}=e;return s.value||!A||!l.value?te:(I===void 0?x:I)+p+te-(b+p)/2}),NMenu:o,NSubmenu:i,NMenuOptionGroup:a}}const Ie={internalKey:{type:[String,Number],required:!0},root:Boolean,isGroup:Boolean,level:{type:Number,required:!0},title:[String,Function],extra:[String,Function]},Ge={...Ie,tmNode:{type:Object,required:!0},disabled:Boolean,icon:Function,onClick:Function},jo=we(Ge),qo=T({name:"MenuOption",props:Ge,setup(e){const o=ze(e),{NSubmenu:r,NMenu:l,NMenuOptionGroup:i}=o,{props:a,mergedClsPrefixRef:s,mergedCollapsedRef:c}=l,m=r?r.mergedDisabledRef:i?i.mergedDisabledRef:{value:!1},b=C(()=>m.value||e.disabled);function x(p){const{onClick:A}=e;A&&A(p)}function I(p){b.value||(l.doSelect(e.internalKey,e.tmNode.rawNode),x(p))}return{mergedClsPrefix:s,dropdownPlacement:o.dropdownPlacement,paddingLeft:o.paddingLeft,iconMarginRight:o.iconMarginRight,maxIconSize:o.maxIconSize,activeIconSize:o.activeIconSize,mergedTheme:l.mergedThemeRef,menuProps:a,dropdownEnabled:ve(()=>e.root&&c.value&&a.mode!=="horizontal"&&!b.value),selected:ve(()=>l.mergedValueRef.value===e.internalKey),mergedDisabled:b,handleClick:I}},render(){const{mergedClsPrefix:e,mergedTheme:o,tmNode:r,menuProps:{renderLabel:l,nodeProps:i}}=this,a=i?.(r.rawNode);return d(),z("div",j(a,{role:"menuitem",class:[`${e}-menu-item`,a?.class]}),[(d(),k(Po,{theme:o.peers.Tooltip,themeOverrides:o.peerOverrides.Tooltip,trigger:"hover",placement:this.dropdownPlacement,disabled:!this.dropdownEnabled||this.title===void 0,internalExtraClass:["menu-tooltip"]},{default:()=>l?l(r.rawNode):G(this.title),trigger:()=>(d(),k(Ve,{tmNode:r,clsPrefix:e,paddingLeft:this.paddingLeft,iconMarginRight:this.iconMarginRight,maxIconSize:this.maxIconSize,activeIconSize:this.activeIconSize,selected:this.selected,title:this.title,extra:this.extra,disabled:this.mergedDisabled,icon:this.icon,onClick:this.handleClick},null,8,["tmNode","clsPrefix","paddingLeft","iconMarginRight","maxIconSize","activeIconSize","selected","title","extra","disabled","icon","onClick"]))},1032,["theme","themeOverrides","placement","disabled"]))],16)}}),je={...Ie,tmNode:{type:Object,required:!0},tmNodes:{type:Array,required:!0}},Xo=we(je),Yo=T({name:"MenuOptionGroup",props:je,setup(e){const o=ze(e),{NSubmenu:r}=o,l=C(()=>r?.mergedDisabledRef.value?!0:e.tmNode.disabled);J(ye,{paddingLeftRef:o.paddingLeft,mergedDisabledRef:l});const{mergedClsPrefixRef:i,props:a}=L(W);return function(){const{value:s}=i,c=o.paddingLeft.value,{nodeProps:m}=a,b=m?.(e.tmNode.rawNode);return(()=>{const x=xe("45eca6a63be5028b");return d(),z("div",{class:H(`${s}-menu-item-group`),role:"group"},[y("div",j(b,{class:[`${s}-menu-item-group-title`,b?.class],style:[b?.style||"",c!==void 0?`padding-left: ${c}px;`:""]}),[_(()=>G(e.title)),e.extra?(d(),z(O,{key:0},[x[0]||(x[0]=_(" ",-1)),_(()=>G(e.extra))],64)):_(()=>null)],16),y("div",null,[_(()=>e.tmNodes.map(I=>_e(I,a)))])],2)})()}}}),Zo=["aria-expanded","id"],Jo=["aria-expanded","id"],qe={...Ie,rawNodes:{type:Array,default:()=>[]},tmNodes:{type:Array,default:()=>[]},tmNode:{type:Object,required:!0},disabled:Boolean,icon:Function,onClick:Function,domId:String,virtualChildActive:{type:Boolean,default:void 0},isEllipsisPlaceholder:Boolean},Qo=we(qe),ge=T({name:"Submenu",props:qe,setup(e){const o=ze(e),{NMenu:r,NSubmenu:l}=o,{props:i,mergedCollapsedRef:a,mergedThemeRef:s}=r,c=C(()=>{const{disabled:p}=e;return l?.mergedDisabledRef.value||i.disabled?!0:p}),m=U(!1);J(Ue,{paddingLeftRef:o.paddingLeft,mergedDisabledRef:c}),J(ye,null);function b(){const{onClick:p}=e;p&&p()}function x(){c.value||(a.value||r.toggleExpand(e.internalKey),b())}function I(p){m.value=p}return{menuProps:i,mergedTheme:s,doSelect:r.doSelect,inverted:r.invertedRef,isHorizontal:r.isHorizontalRef,mergedClsPrefix:r.mergedClsPrefixRef,maxIconSize:o.maxIconSize,activeIconSize:o.activeIconSize,iconMarginRight:o.iconMarginRight,dropdownPlacement:o.dropdownPlacement,dropdownShow:m,paddingLeft:o.paddingLeft,mergedDisabled:c,mergedValue:r.mergedValueRef,childActive:ve(()=>e.virtualChildActive??r.activePathRef.value.includes(e.internalKey)),collapsed:C(()=>i.mode==="horizontal"?!1:a.value?!0:!r.mergedExpandedKeysRef.value.includes(e.internalKey)),dropdownEnabled:C(()=>!c.value&&(i.mode==="horizontal"||a.value)),handlePopoverShowChange:I,handleClick:x}},render(){const{mergedClsPrefix:e,menuProps:{renderIcon:o,renderLabel:r}}=this,l=()=>{const{isHorizontal:a,paddingLeft:s,collapsed:c,mergedDisabled:m,maxIconSize:b,activeIconSize:x,title:I,childActive:p,icon:A,handleClick:F,menuProps:{nodeProps:K},dropdownShow:q,iconMarginRight:B,tmNode:ee,mergedClsPrefix:X,isEllipsisPlaceholder:ne,extra:ie}=this,Y=K?.(ee.rawNode);return d(),z("div",j(Y,{class:[`${X}-menu-item`,Y?.class],role:"menuitem"}),[(d(),k(Ve,{tmNode:ee,paddingLeft:s,collapsed:c,disabled:m,iconMarginRight:B,maxIconSize:b,activeIconSize:x,title:I,extra:ie,showArrow:!a,childActive:p,clsPrefix:X,icon:A,hover:q,onClick:F,isEllipsisPlaceholder:ne},null,8,["tmNode","paddingLeft","collapsed","disabled","iconMarginRight","maxIconSize","activeIconSize","title","extra","showArrow","childActive","clsPrefix","icon","hover","onClick","isEllipsisPlaceholder"]))],16)},i=()=>(d(),k(ho,null,{default:()=>{const{tmNodes:a,collapsed:s}=this;return s?null:(d(),z("div",{key:1,class:H(`${e}-submenu-children`),role:"menu"},[_(()=>a.map(c=>_e(c,this.menuProps)))],2))}},1024));return this.root?(d(),k($e,j({key:2,size:"large",trigger:"hover"},this.menuProps?.dropdownProps,{themeOverrides:this.mergedTheme.peerOverrides.Dropdown,theme:this.mergedTheme.peers.Dropdown,builtinThemeOverrides:{fontSizeLarge:"14px",optionIconSizeLarge:"18px"},value:this.mergedValue,disabled:!this.dropdownEnabled,placement:this.dropdownPlacement,keyField:this.menuProps.keyField,labelField:this.menuProps.labelField,childrenField:this.menuProps.childrenField,onUpdateShow:this.handlePopoverShowChange,options:this.rawNodes,onSelect:this.doSelect,inverted:this.inverted,renderIcon:o,renderLabel:r}),{default:()=>(d(),z("div",{class:H(`${e}-submenu`),role:"menu","aria-expanded":!this.collapsed,id:this.domId},[_(()=>l()),this.isHorizontal?_(()=>null):(d(),z(O,{key:1},[_(()=>i())],64))],10,Zo))},1040,["themeOverrides","theme","value","disabled","placement","keyField","labelField","childrenField","onUpdateShow","options","onSelect","inverted","renderIcon","renderLabel"])):(d(),z("div",{key:3,class:H(`${e}-submenu`),role:"menu","aria-expanded":!this.collapsed,id:this.domId},[_(()=>l()),_(()=>i())],10,Jo))}});function be(e){return e.type==="divider"||e.type==="render"}function Wo(e){return e.type==="divider"}function _e(e,o){const{rawNode:r}=e,{show:l}=r;if(l===!1)return null;if(be(r))return Wo(r)?(d(),k(Uo,j({key:e.key},r.props),null,16)):null;const{labelField:i}=o,{key:a,level:s,isGroup:c}=e,m={...r,title:r.title||r[i],extra:r.titleExtra||r.extra,key:a,internalKey:a,level:s,root:s===0,isGroup:c};return e.children?e.isGroup?R(Yo,le(m,Xo,{tmNode:e,tmNodes:e.children,key:a})):R(ge,le(m,Qo,{key:a,rawNodes:r[o.childrenField],tmNodes:e.children,tmNode:e})):R(qo,le(m,jo,{key:a,tmNode:e}))}const et={...re.props,options:{type:Array,default:()=>[]},collapsed:{type:Boolean,default:void 0},collapsedWidth:{type:Number,default:48},iconSize:{type:Number,default:20},collapsedIconSize:{type:Number,default:24},rootIndent:Number,indent:{type:Number,default:32},labelField:{type:String,default:"label"},keyField:{type:String,default:"key"},childrenField:{type:String,default:"children"},disabledField:{type:String,default:"disabled"},defaultExpandAll:Boolean,defaultExpandedKeys:Array,expandedKeys:Array,value:[String,Number],defaultValue:{type:[String,Number],default:null},mode:{type:String,default:"vertical"},watchProps:{type:Array,default:void 0},disabled:Boolean,show:{type:Boolean,default:!0},inverted:Boolean,"onUpdate:expandedKeys":[Function,Array],onUpdateExpandedKeys:[Function,Array],onUpdateValue:[Function,Array],"onUpdate:value":[Function,Array],expandIcon:Function,renderIcon:Function,renderLabel:Function,renderExtra:Function,dropdownProps:Object,accordion:Boolean,nodeProps:Function,dropdownPlacement:{type:String,default:"bottom"},responsive:Boolean,items:Array,onOpenNamesChange:[Function,Array],onSelect:[Function,Array],onExpandedNamesChange:[Function,Array],expandedNames:Array,defaultExpandedNames:Array};var ot=T({name:"Menu",inheritAttrs:!1,props:et,setup(e){const{mergedClsPrefixRef:o,inlineThemeDisabled:r}=Le(e),l=re("Menu","-menu",Do,fo,e,o),i=L($o,null),a=C(()=>{const{collapsed:u}=e;if(u!==void 0)return u;if(i){const{collapseModeRef:w,collapsedRef:t}=i;if(w.value==="width")return t.value??!1}return!1}),s=C(()=>{const{keyField:u,childrenField:w,disabledField:t}=e;return ce(e.items||e.options,{getIgnored(h){return be(h)},getChildren(h){return h[w]},getDisabled(h){return h[t]},getKey(h){return h[u]??h.name}})}),c=C(()=>new Set(s.value.treeNodes.map(u=>u.key))),{watchProps:m}=e,b=U(null);m?.includes("defaultValue")?Ae(()=>{b.value=e.defaultValue}):b.value=e.defaultValue;const x=he(e,"value"),I=Te(x,b),p=U([]),A=()=>{p.value=e.defaultExpandAll?s.value.getNonLeafKeys():e.defaultExpandedNames||e.defaultExpandedKeys||s.value.getPath(I.value,{includeSelf:!1}).keyPath};m?.includes("defaultExpandedKeys")?Ae(A):A();const F=Ao(e,["expandedNames","expandedKeys"]),K=Te(F,p),q=C(()=>s.value.treeNodes),B=C(()=>s.value.getPath(I.value).keyPath);J(W,{props:e,mergedCollapsedRef:a,mergedThemeRef:l,mergedValueRef:I,mergedExpandedKeysRef:K,activePathRef:B,mergedClsPrefixRef:o,isHorizontalRef:C(()=>e.mode==="horizontal"),invertedRef:he(e,"inverted"),doSelect:ee,toggleExpand:ne});function ee(u,w){const{"onUpdate:value":t,onUpdateValue:h,onSelect:M}=e;h&&$(h,u,w),t&&$(t,u,w),M&&$(M,u,w),b.value=u}function X(u){const{"onUpdate:expandedKeys":w,onUpdateExpandedKeys:t,onExpandedNamesChange:h,onOpenNamesChange:M}=e;w&&$(w,u),t&&$(t,u),h&&$(h,u),M&&$(M,u),p.value=u}function ne(u){const w=Array.from(K.value),t=w.findIndex(h=>h===u);if(~t)w.splice(t,1);else{if(e.accordion&&c.value.has(u)){const h=w.findIndex(M=>c.value.has(M));h>-1&&w.splice(h,1)}w.push(u)}X(w)}const ie=u=>{const w=s.value.getPath(u??I.value,{includeSelf:!1}).keyPath;if(!w.length)return;const t=Array.from(K.value),h=new Set([...t,...w]);e.accordion&&c.value.forEach(M=>{h.has(M)&&!w.includes(M)&&h.delete(M)}),X(Array.from(h))},Y=C(()=>{const{inverted:u}=e,{common:{cubicBezierEaseInOut:w},self:t}=l.value,{borderRadius:h,borderColorHorizontal:M,fontSize:to,itemHeight:ro,dividerColor:no}=t,n={"--n-divider-color":no,"--n-bezier":w,"--n-font-size":to,"--n-border-color-horizontal":M,"--n-border-radius":h,"--n-item-height":ro};return u?(n["--n-group-text-color"]=t.groupTextColorInverted,n["--n-color"]=t.colorInverted,n["--n-item-text-color"]=t.itemTextColorInverted,n["--n-item-text-color-hover"]=t.itemTextColorHoverInverted,n["--n-item-text-color-active"]=t.itemTextColorActiveInverted,n["--n-item-text-color-child-active"]=t.itemTextColorChildActiveInverted,n["--n-item-text-color-child-active-hover"]=t.itemTextColorChildActiveInverted,n["--n-item-text-color-active-hover"]=t.itemTextColorActiveHoverInverted,n["--n-item-icon-color"]=t.itemIconColorInverted,n["--n-item-icon-color-hover"]=t.itemIconColorHoverInverted,n["--n-item-icon-color-active"]=t.itemIconColorActiveInverted,n["--n-item-icon-color-active-hover"]=t.itemIconColorActiveHoverInverted,n["--n-item-icon-color-child-active"]=t.itemIconColorChildActiveInverted,n["--n-item-icon-color-child-active-hover"]=t.itemIconColorChildActiveHoverInverted,n["--n-item-icon-color-collapsed"]=t.itemIconColorCollapsedInverted,n["--n-item-text-color-horizontal"]=t.itemTextColorHorizontalInverted,n["--n-item-text-color-hover-horizontal"]=t.itemTextColorHoverHorizontalInverted,n["--n-item-text-color-active-horizontal"]=t.itemTextColorActiveHorizontalInverted,n["--n-item-text-color-child-active-horizontal"]=t.itemTextColorChildActiveHorizontalInverted,n["--n-item-text-color-child-active-hover-horizontal"]=t.itemTextColorChildActiveHoverHorizontalInverted,n["--n-item-text-color-active-hover-horizontal"]=t.itemTextColorActiveHoverHorizontalInverted,n["--n-item-icon-color-horizontal"]=t.itemIconColorHorizontalInverted,n["--n-item-icon-color-hover-horizontal"]=t.itemIconColorHoverHorizontalInverted,n["--n-item-icon-color-active-horizontal"]=t.itemIconColorActiveHorizontalInverted,n["--n-item-icon-color-active-hover-horizontal"]=t.itemIconColorActiveHoverHorizontalInverted,n["--n-item-icon-color-child-active-horizontal"]=t.itemIconColorChildActiveHorizontalInverted,n["--n-item-icon-color-child-active-hover-horizontal"]=t.itemIconColorChildActiveHoverHorizontalInverted,n["--n-arrow-color"]=t.arrowColorInverted,n["--n-arrow-color-hover"]=t.arrowColorHoverInverted,n["--n-arrow-color-active"]=t.arrowColorActiveInverted,n["--n-arrow-color-active-hover"]=t.arrowColorActiveHoverInverted,n["--n-arrow-color-child-active"]=t.arrowColorChildActiveInverted,n["--n-arrow-color-child-active-hover"]=t.arrowColorChildActiveHoverInverted,n["--n-item-color-hover"]=t.itemColorHoverInverted,n["--n-item-color-active"]=t.itemColorActiveInverted,n["--n-item-color-active-hover"]=t.itemColorActiveHoverInverted,n["--n-item-color-active-collapsed"]=t.itemColorActiveCollapsedInverted):(n["--n-group-text-color"]=t.groupTextColor,n["--n-color"]=t.color,n["--n-item-text-color"]=t.itemTextColor,n["--n-item-text-color-hover"]=t.itemTextColorHover,n["--n-item-text-color-active"]=t.itemTextColorActive,n["--n-item-text-color-child-active"]=t.itemTextColorChildActive,n["--n-item-text-color-child-active-hover"]=t.itemTextColorChildActiveHover,n["--n-item-text-color-active-hover"]=t.itemTextColorActiveHover,n["--n-item-icon-color"]=t.itemIconColor,n["--n-item-icon-color-hover"]=t.itemIconColorHover,n["--n-item-icon-color-active"]=t.itemIconColorActive,n["--n-item-icon-color-active-hover"]=t.itemIconColorActiveHover,n["--n-item-icon-color-child-active"]=t.itemIconColorChildActive,n["--n-item-icon-color-child-active-hover"]=t.itemIconColorChildActiveHover,n["--n-item-icon-color-collapsed"]=t.itemIconColorCollapsed,n["--n-item-text-color-horizontal"]=t.itemTextColorHorizontal,n["--n-item-text-color-hover-horizontal"]=t.itemTextColorHoverHorizontal,n["--n-item-text-color-active-horizontal"]=t.itemTextColorActiveHorizontal,n["--n-item-text-color-child-active-horizontal"]=t.itemTextColorChildActiveHorizontal,n["--n-item-text-color-child-active-hover-horizontal"]=t.itemTextColorChildActiveHoverHorizontal,n["--n-item-text-color-active-hover-horizontal"]=t.itemTextColorActiveHoverHorizontal,n["--n-item-icon-color-horizontal"]=t.itemIconColorHorizontal,n["--n-item-icon-color-hover-horizontal"]=t.itemIconColorHoverHorizontal,n["--n-item-icon-color-active-horizontal"]=t.itemIconColorActiveHorizontal,n["--n-item-icon-color-active-hover-horizontal"]=t.itemIconColorActiveHoverHorizontal,n["--n-item-icon-color-child-active-horizontal"]=t.itemIconColorChildActiveHorizontal,n["--n-item-icon-color-child-active-hover-horizontal"]=t.itemIconColorChildActiveHoverHorizontal,n["--n-arrow-color"]=t.arrowColor,n["--n-arrow-color-hover"]=t.arrowColorHover,n["--n-arrow-color-active"]=t.arrowColorActive,n["--n-arrow-color-active-hover"]=t.arrowColorActiveHover,n["--n-arrow-color-child-active"]=t.arrowColorChildActive,n["--n-arrow-color-child-active-hover"]=t.arrowColorChildActiveHover,n["--n-item-color-hover"]=t.itemColorHover,n["--n-item-color-active"]=t.itemColorActive,n["--n-item-color-active-hover"]=t.itemColorActiveHover,n["--n-item-color-active-collapsed"]=t.itemColorActiveCollapsed),n}),Se=r?Be("menu",C(()=>e.inverted?"a":"b"),Y,e):void 0,ae=po(),Pe=U(null),Xe=U(null);let Re=!0;const ke=()=>{Re?Re=!1:Pe.value?.sync({showAllItemsBeforeCalculate:!0})};function Ye(){return document.getElementById(ae)}const oe=U(-1);function Ze(u){oe.value=e.options.length-u}function Je(u){u||(oe.value=-1)}const Qe=C(()=>{const u=oe.value;return{children:u===-1?[]:e.options.slice(u)}}),We=C(()=>{const{childrenField:u,disabledField:w,keyField:t}=e;return ce([Qe.value],{getIgnored(h){return be(h)},getChildren(h){return h[u]},getDisabled(h){return h[w]},getKey(h){return h[t]??h.name}})}),eo=C(()=>ce([{}]).treeNodes[0]);function oo(){if(oe.value===-1)return d(),k(ge,{root:!0,level:0,key:"__ellpisisGroupPlaceholder__",internalKey:"__ellpisisGroupPlaceholder__",title:"···",tmNode:eo.value,domId:ae,isEllipsisPlaceholder:!0},null,8,["tmNode","domId"]);const u=We.value.treeNodes[0],w=B.value,t=!!u.children?.some(h=>w.includes(h.key));return d(),k(ge,{level:0,root:!0,key:"__ellpisisGroup__",internalKey:"__ellpisisGroup__",title:"···",virtualChildActive:t,tmNode:u,domId:ae,rawNodes:u.rawNode.children||[],tmNodes:u.children||[],isEllipsisPlaceholder:!0},null,8,["virtualChildActive","tmNode","domId","rawNodes","tmNodes"])}return{mergedClsPrefix:o,controlledExpandedKeys:F,uncontrolledExpanededKeys:p,mergedExpandedKeys:K,uncontrolledValue:b,mergedValue:I,activePath:B,tmNodes:q,mergedTheme:l,mergedCollapsed:a,cssVars:r?void 0:Y,themeClass:Se?.themeClass,overflowRef:Pe,counterRef:Xe,updateCounter:()=>{},onResize:ke,onUpdateOverflow:Je,onUpdateCount:Ze,renderCounter:oo,getCounter:Ye,onRender:Se?.onRender,showOption:ie,deriveResponsiveState:ke}},render(){const{mergedClsPrefix:e,mode:o,themeClass:r,onRender:l}=this;l?.();const i=()=>this.tmNodes.map(c=>_e(c,this.$props)),a=o==="horizontal"&&this.responsive,s=()=>R("div",j(this.$attrs,{role:o==="horizontal"?"menubar":"menu",class:[`${e}-menu`,r,`${e}-menu--${o}`,a&&`${e}-menu--responsive`,this.mergedCollapsed&&`${e}-menu--collapsed`],style:this.cssVars}),a?(d(),k(ko,{key:2,ref:"overflowRef",onUpdateOverflow:this.onUpdateOverflow,getCounter:this.getCounter,onUpdateCount:this.onUpdateCount,updateCounter:this.updateCounter,style:{width:"100%",display:"flex",overflow:"hidden"}},{default:i,counter:this.renderCounter},1032,["onUpdateOverflow","getCounter","onUpdateCount","updateCounter"])):i());return a?(d(),k(vo,{key:3,onResize:this.onResize},{default:s},1032,["onResize"])):s()}});const de="huluwa.collapsed";function tt(e,o){try{const r=localStorage.getItem(e);return r===null?o:r==="1"}catch{return o}}function Oe(e,o){try{localStorage.setItem(e,o?"1":"0")}catch{}}const rt=go("app",{state:()=>({collapsed:tt(de,!1),tagNavList:[],breadCrumb:[]}),actions:{toggleCollapsed(){this.collapsed=!this.collapsed,Oe(de,this.collapsed)},setCollapsed(e){this.collapsed=!!e,Oe(de,this.collapsed)},setBreadCrumb(e){this.breadCrumb=e}}}),nt={components:{NBreadcrumb:Ko,NBreadcrumbItem:Bo},name:"HeaderBar",props:{collapsed:Boolean,mobile:Boolean},emits:["on-coll-change"],computed:{currentTitle(){return this.$route.name==="messages"?"会话":this.$route.meta&&this.$route.meta.title||"首页"}}},it={class:"header-bar"},at=["title"],lt={class:"custom-content-con"};function st(e,o,r,l,i,a){const s=E("n-breadcrumb-item"),c=E("n-breadcrumb");return d(),z("div",it,[y("button",{class:"icon-btn",type:"button",onClick:o[0]||(o[0]=m=>e.$emit("on-coll-change",!r.collapsed)),title:r.mobile?"打开菜单":r.collapsed?"展开侧栏":"收起侧栏"},[y("i",{class:pe(r.mobile?"fa-solid fa-bars":r.collapsed?"fa-solid fa-indent":"fa-solid fa-outdent")},null,2)],8,at),P(c,{class:"crumb"},{default:N(()=>[P(s,null,{default:N(()=>[o[1]||(o[1]=y("i",{class:"fa-solid fa-house crumb-home"},null,-1)),y("span",null,fe(a.currentTitle),1)]),_:1})]),_:1}),y("div",lt,[bo(e.$slots,"default",{},void 0,!0)])])}const ct=Ce(nt,[["render",st],["__scopeId","data-v-02afa996"]]),dt={components:{NButton:wo,NDropdown:$e,NForm:Ho,NFormItem:No,NInput:Ro,NModal:xo},name:"UserChip",setup(){return{auth:Co()}},data(){return{passwordModal:{visible:!1,loading:!1,data:{oldPassword:"",newPassword:"",confirmPassword:""}},passwordRules:{oldPassword:{required:!0,message:"请输入原密码",trigger:["input","blur"]},newPassword:[{required:!0,message:"请输入新密码",trigger:["input","blur"]},{min:6,message:"新密码至少 6 位",trigger:["input","blur"]}],confirmPassword:{required:!0,validator:(e,o)=>o?o!==this.passwordModal.data.newPassword?new Error("两次输入的密码不一致"):!0:new Error("请再次输入新密码"),trigger:["input","blur"]}}}},computed:{avatarText(){return(this.auth.username||"A").charAt(0).toUpperCase()},options(){return[{label:"修改密码",key:"password",icon:()=>R("i",{class:"fa-solid fa-key"})},{label:"退出登录",key:"logout",icon:()=>R("i",{class:"fa-solid fa-right-from-bracket"})}]}},methods:{handleSelect(e){e==="password"?(this.passwordModal.data={oldPassword:"",newPassword:"",confirmPassword:""},this.passwordModal.visible=!0):e==="logout"&&this.$dialog.warning({title:"退出登录",content:"确认退出当前账号？",positiveText:"退出",negativeText:"取消",onPositiveClick:()=>{this.auth.clear(),this.$router.push({name:"login"})}})},submitPassword(){this.$refs.passwordForm.validate(async e=>{if(!e){this.passwordModal.loading=!0;try{await this.auth.changePassword({oldPassword:this.passwordModal.data.oldPassword,newPassword:this.passwordModal.data.newPassword}),this.passwordModal.visible=!1,this.$message.success("密码已修改，请重新登录"),this.$router.push({name:"login"})}catch{}finally{this.passwordModal.loading=!1}}})}}},ut={class:"user-avatar-dropdown"},mt={class:"user-chip",type:"button"},ht={class:"ava"},vt={class:"name"},pt={class:"modal-footer"};function ft(e,o,r,l,i,a){const s=E("n-dropdown"),c=E("n-input"),m=E("n-form-item"),b=E("n-form"),x=E("n-button"),I=E("n-modal");return d(),z("div",ut,[P(s,{options:a.options,onSelect:a.handleSelect},{default:N(()=>[y("button",mt,[y("span",ht,fe(a.avatarText),1),y("span",vt,fe(l.auth.username||"admin"),1),o[5]||(o[5]=y("i",{class:"fa-solid fa-chevron-down"},null,-1))])]),_:1},8,["options","onSelect"]),P(I,{show:i.passwordModal.visible,"onUpdate:show":o[4]||(o[4]=p=>i.passwordModal.visible=p),preset:"card",title:"修改密码",style:{width:"460px"},"mask-closable":!1},{footer:N(()=>[y("div",pt,[P(x,{onClick:o[3]||(o[3]=p=>i.passwordModal.visible=!1)},{default:N(()=>[...o[6]||(o[6]=[Me("取消",-1)])]),_:1}),P(x,{type:"primary",loading:i.passwordModal.loading,onClick:a.submitPassword},{default:N(()=>[...o[7]||(o[7]=[Me("确认",-1)])]),_:1},8,["loading","onClick"])])]),default:N(()=>[P(b,{ref:"passwordForm",model:i.passwordModal.data,rules:i.passwordRules,"label-placement":"left","label-width":"90"},{default:N(()=>[P(m,{label:"原密码",path:"oldPassword"},{default:N(()=>[P(c,{value:i.passwordModal.data.oldPassword,"onUpdate:value":o[0]||(o[0]=p=>i.passwordModal.data.oldPassword=p),type:"password","show-password-on":"click",placeholder:"请输入原密码"},null,8,["value"])]),_:1}),P(m,{label:"新密码",path:"newPassword"},{default:N(()=>[P(c,{value:i.passwordModal.data.newPassword,"onUpdate:value":o[1]||(o[1]=p=>i.passwordModal.data.newPassword=p),type:"password","show-password-on":"click",placeholder:"至少 6 位"},null,8,["value"])]),_:1}),P(m,{label:"确认密码",path:"confirmPassword"},{default:N(()=>[P(c,{value:i.passwordModal.data.confirmPassword,"onUpdate:value":o[2]||(o[2]=p=>i.passwordModal.data.confirmPassword=p),type:"password","show-password-on":"click",placeholder:"请再次输入新密码"},null,8,["value"])]),_:1})]),_:1},8,["model","rules"])]),_:1},8,["show"])])}const gt=Ce(dt,[["render",ft],["__scopeId","data-v-cc77110f"]]),ue="messages-index",bt="会话",V=e=>{if(e)return()=>R("i",{class:e})},xt={name:"MainLayout",components:{HeaderBar:ct,UserChip:gt,NMenu:ot},setup(){const e=rt(),o=Mo(),r=So();return{appStore:e,chatStore:o,shellHidden:r}},data(){return{expandedKeys:[],isMobile:!1,mobileDrawerOpen:!1}},computed:{shellVisible(){return!this.shellHidden||this.mobileDrawerOpen},collapsed(){return this.appStore.collapsed},siderCollapsed(){return this.isMobile?!this.mobileDrawerOpen:this.collapsed},headerCollapsed(){return this.isMobile?!this.mobileDrawerOpen:this.collapsed},activeMenu(){return this.$route.name==="messages"?ue:this.$route.name},menuOptions(){const e=this.$router.options.routes.find(o=>o.path==="/");return!e||!Array.isArray(e.children)?[]:e.children.filter(o=>!(o.meta&&o.meta.hideInMenu)).map(o=>o.name==="messages"?this.messagesMenuOption(o):this.toMenuOption(o))}},watch:{shellHidden:{immediate:!0,handler(e,o){if(e){this.stopMenuPolling();return}o!==void 0&&(this.chatStore.refreshSessions().catch(()=>{}),this.startMenuPolling())}},"$route.name"(e,o){(e==="messages"||o==="messages")&&(this.ensureMessagesExpanded(),e==="messages")||this.syncExpandedKeys()}},async mounted(){this.handleResize(),window.addEventListener("resize",this.handleResize),this.syncExpandedKeys();try{await this.chatStore.refreshSessions()}catch{}this.startMenuPolling()},beforeUnmount(){window.removeEventListener("resize",this.handleResize),this.stopMenuPolling()},methods:{handleResize(){const e=window.innerWidth<=900;e!==this.isMobile&&(this.isMobile=e,e&&(this.mobileDrawerOpen=!1)),this.$route.name==="messages"&&Io(_o())},handleCollapsedChange(e){if(this.isMobile){this.mobileDrawerOpen=!this.mobileDrawerOpen;return}this.appStore.setCollapsed(e)},closeDrawer(){this.mobileDrawerOpen=!1},ensureSessionsLoaded(){(this.chatStore.sessions||[]).length||this.chatStore.refreshSessions().catch(()=>{})},ensureMessagesExpanded(){this.ensureSessionsLoaded(),this.expandedKeys.includes("messages")||(this.expandedKeys=[...this.expandedKeys,"messages"])},startMenuPolling(){this._menuTimer||(this._menuTimer=setInterval(()=>{this.chatStore.refreshSessions().catch(()=>{})},15e3))},stopMenuPolling(){this._menuTimer&&(clearInterval(this._menuTimer),this._menuTimer=null)},toMenuOption(e){const o=Array.isArray(e.children)?e.children.filter(r=>!(r.meta&&r.meta.hideInMenu)):[];return o.length?{key:e.name,label:e.meta&&e.meta.title,icon:V(e.meta&&e.meta.icon),children:o.map(r=>this.toMenuOption(r))}:{key:e.name,label:()=>R(se,{to:{name:e.name}},{default:()=>e.meta.title}),icon:V(e.meta&&e.meta.icon)}},messagesMenuOption(e){const o=(this.chatStore.sessions||[]).reduce((r,l)=>r+(l.unread||0),0);return{key:e.name,label:()=>R("span",{class:"menu-label"},[R("span",e.meta&&e.meta.title),o>0?R("span",{class:"menu-dot"}):null]),icon:V(e.meta&&e.meta.icon),children:[{key:ue,label:()=>R("span",{class:"menu-label"},[R("span",bt),o>0?R("span",{class:"menu-badge"},o>99?"99+":String(o)):null]),icon:V("fa-solid fa-comments")},{key:"movie-subscriptions",label:()=>R(se,{to:{name:"movie-subscriptions"}},{default:()=>"我的订阅"}),icon:V("fa-solid fa-bell")},{key:"movie-downloads",label:()=>R(se,{to:{name:"movie-downloads"}},{default:()=>"我的下载"}),icon:V("fa-solid fa-download")}]}},syncExpandedKeys(){const e=this.$route.name;if(e==="messages"||e==="movie-subscriptions"||e==="movie-downloads"){this.ensureMessagesExpanded();return}const o=this.$router.options.routes.find(i=>i.path==="/"),l=(o&&Array.isArray(o.children)?o.children:[]).filter(i=>Array.isArray(i.children)&&i.children.length).find(i=>i.children.some(a=>a.name===e));this.expandedKeys=l?[l.name]:[]},turnToPage(e){if(this.closeDrawer(),e===ue){this.$router.push({name:"messages"}).catch(()=>{});return}e!==this.$route.name&&this.$router.push({name:e}).catch(()=>{})},handleExpandedChange(e){if((this.$route.name==="messages"||this.$route.name==="movie-subscriptions"||this.$route.name==="movie-downloads")&&!e.includes("messages")){this.expandedKeys=[...e,"messages"];return}this.expandedKeys=e}}},wt={class:"logo"},Ct={class:"logo-text"},yt={class:"sider-foot"},zt={class:"main-body"},It={class:"content-wrapper"};function _t(e,o,r,l,i,a){const s=E("n-menu"),c=E("user-chip"),m=E("header-bar"),b=E("router-view");return d(),z("div",{class:pe(["main-console",{"is-mobile":i.isMobile,"drawer-open":i.isMobile&&i.mobileDrawerOpen,"is-immersive":l.shellHidden}])},[a.shellVisible?(d(),z("aside",{key:0,class:pe(["sider",{collapsed:a.siderCollapsed}])},[y("div",wt,[o[2]||(o[2]=y("img",{class:"logo-mark",src:Eo,alt:"葫芦娃 Logo",width:"36",height:"36"},null,-1)),Ne(y("div",Ct,[...o[1]||(o[1]=[y("div",{class:"logo-name"},"葫芦娃助手",-1),y("div",{class:"logo-sub"},"OPS CONSOLE",-1)])],512),[[He,!a.siderCollapsed]])]),P(s,{value:a.activeMenu,options:a.menuOptions,collapsed:a.siderCollapsed,"collapsed-width":64,"collapsed-icon-size":17,indent:18,"expanded-keys":i.expandedKeys,accordion:"","onUpdate:value":a.turnToPage,"onUpdate:expandedKeys":a.handleExpandedChange},null,8,["value","options","collapsed","expanded-keys","onUpdate:value","onUpdate:expandedKeys"]),Ne(y("div",yt,[...o[3]||(o[3]=[y("span",null,"版本",-1),y("span",{class:"mono"},"v1.0.0",-1)])],512),[[He,!a.siderCollapsed]])],2)):Ee("",!0),a.shellVisible&&i.isMobile&&i.mobileDrawerOpen?(d(),z("div",{key:1,class:"sider-mask",onClick:o[0]||(o[0]=(...x)=>a.closeDrawer&&a.closeDrawer(...x))})):Ee("",!0),y("div",zt,[P(m,{collapsed:a.headerCollapsed,mobile:i.isMobile,onOnCollChange:a.handleCollapsedChange},{default:N(()=>[P(c)]),_:1},8,["collapsed","mobile","onOnCollChange"]),y("div",It,[P(b,null,{default:N(({Component:x})=>[(d(),k(yo,null,[(d(),k(zo(x),{key:e.$route.path}))],1024))]),_:1})])])],2)}const Kt=Ce(xt,[["render",_t],["__scopeId","data-v-f8125f1c"]]);export{Kt as default};
