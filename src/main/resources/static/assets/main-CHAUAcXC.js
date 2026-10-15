import{c as f,a as g,b as P,d as v,e as T,u as ie,o as m,f as _,g as w,n as I,h as he,i as H,j as Oe,k as Le,l as y,m as Q,p as no,q as J,t as ve,r as V,s as io,v as ao,w as lo,x as L,y as S,z as so,A as Z,B as co,C as xe,D as G,F as O,E as k,I as uo,G as we,H as j,J as pe,K as mo,L as ce,V as ho,M as Me,N as vo,O as po,P as $,Q as fo,R as E,S as ne,T as R,U as N,W as fe,X as go,Y as bo,Z as xo,_ as wo,$ as Ae,a0 as Ne,a1 as He,a2 as Co,a3 as yo,a4 as te,a5 as zo,a6 as Io,a7 as _o}from"./index-B36oTUEG.js";import{T as So}from"./Tooltip-BTJZV8WK.js";import{D as Be}from"./Dropdown-gi_CQLNH.js";import{u as Ee,I as Po}from"./Input-fjlDVKu-.js";import{V as Ro,c as de}from"./create-DEgfV4JL.js";import{u as ko}from"./use-compitable-DqezV-VP.js";import{u as Mo}from"./chat-rNVFCaUN.js";import{_ as Ce}from"./_plugin-vue_export-helper-DlAUqK2U.js";import{F as Ao,a as No}from"./FormItem-0n4AqZM7.js";import{_ as Ho}from"./huluwa-logo-BxQdg_kY.js";import"./chat-CI7aOBtX.js";var Eo=f("breadcrumb",`
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
 `),g("&:not(:last-child)",[P("clickable",[v("link",`
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
 `)])])]);const $e=Q("n-breadcrumb"),To={...ie.props,separator:{type:String,default:"/"}};var Fo=T({name:"Breadcrumb",props:To,setup(e){const{mergedClsPrefixRef:o,inlineThemeDisabled:r}=Oe(e),l=ie("Breadcrumb","-breadcrumb",Eo,no,e,o);J($e,{separatorRef:ve(e,"separator"),mergedClsPrefixRef:o});const i=y(()=>{const{common:{cubicBezierEaseInOut:s},self:{separatorColor:c,itemTextColor:u,itemTextColorHover:b,itemTextColorPressed:x,itemTextColorActive:z,fontSize:p,fontWeightActive:M,itemBorderRadius:F,itemColorHover:K,itemColorPressed:q,itemLineHeight:B}}=l.value;return{"--n-font-size":p,"--n-bezier":s,"--n-item-text-color":u,"--n-item-text-color-hover":b,"--n-item-text-color-pressed":x,"--n-item-text-color-active":z,"--n-separator-color":c,"--n-item-color-hover":K,"--n-item-color-pressed":q,"--n-item-border-radius":F,"--n-font-weight-active":M,"--n-item-line-height":B}}),a=r?Le("breadcrumb",void 0,i,e):void 0;return{mergedClsPrefix:o,cssVars:r?void 0:i,themeClass:a?.themeClass,onRender:a?.onRender}},render(){return this.onRender?.(),m(),_("nav",{class:H([`${this.mergedClsPrefix}-breadcrumb`,this.themeClass]),style:he(this.cssVars),"aria-label":"Breadcrumb"},[w("ul",null,[I(()=>this.$slots.default?.())])],6)}});function Ko(e=lo?window:null){const o=()=>{const{hash:i,host:a,hostname:s,href:c,origin:u,pathname:b,port:x,protocol:z,search:p}=e?.location||{};return{hash:i,host:a,hostname:s,href:c,origin:u,pathname:b,port:x,protocol:z,search:p}},r=V(o()),l=()=>{r.value=o()};return io(()=>{e&&(e.addEventListener("popstate",l),e.addEventListener("hashchange",l))}),ao(()=>{e&&(e.removeEventListener("popstate",l),e.removeEventListener("hashchange",l))}),r}const Oo={separator:String,href:String,clickable:{type:Boolean,default:!0},showSeparator:{type:Boolean,default:!0},onClick:Function};var Lo=T({name:"BreadcrumbItem",props:Oo,slots:Object,setup(e,{slots:o}){const r=L($e,null);if(!r)return()=>null;const{separatorRef:l,mergedClsPrefixRef:i}=r,a=Ko(),s=y(()=>e.href?"a":"span"),c=y(()=>a.value.href===e.href?"location":null);return()=>{const{value:u}=i;return m(),_("li",{class:H([`${u}-breadcrumb-item`,e.clickable&&`${u}-breadcrumb-item--clickable`])},[I(()=>S(s.value,{class:`${u}-breadcrumb-item__link`,"aria-current":c.value,href:e.href,onClick:e.onClick},o)),I(()=>e.showSeparator&&(m(),_("span",{class:H(`${u}-breadcrumb-item__separator`),"aria-hidden":"true"},[I(()=>so(o.separator,()=>[e.separator??l.value]))],2)))],2)}}});const Bo=Q("n-layout-sider"),W=Q("n-menu"),De=Q("n-submenu"),ye=Q("n-menu-item-group"),Te=[g("&::before","background-color: var(--n-item-color-hover);"),v("arrow",`
 color: var(--n-arrow-color-hover);
 `),v("icon",`
 color: var(--n-item-icon-color-hover);
 `),f("menu-item-content-header",`
 color: var(--n-item-text-color-hover);
 `,[g("a",`
 color: var(--n-item-text-color-hover);
 `),v("extra",`
 color: var(--n-item-text-color-hover);
 `)])],Fe=[v("icon",`
 color: var(--n-item-icon-color-hover-horizontal);
 `),f("menu-item-content-header",`
 color: var(--n-item-text-color-hover-horizontal);
 `,[g("a",`
 color: var(--n-item-text-color-hover-horizontal);
 `),v("extra",`
 color: var(--n-item-text-color-hover-horizontal);
 `)])];var $o=g([f("menu",`
 background-color: var(--n-color);
 color: var(--n-item-text-color);
 overflow: hidden;
 transition: background-color .3s var(--n-bezier);
 box-sizing: border-box;
 font-size: var(--n-font-size);
 padding-bottom: 6px;
 `,[P("horizontal",`
 max-width: 100%;
 width: 100%;
 display: flex;
 overflow: hidden;
 padding-bottom: 0;
 `,[f("submenu","margin: 0;"),f("menu-item","margin: 0;"),f("menu-item-content",`
 padding: 0 20px;
 border-bottom: 2px solid #0000;
 `,[g("&::before","display: none;"),P("selected","border-bottom: 2px solid var(--n-border-color-horizontal)")]),f("menu-item-content",[P("selected",[v("icon","color: var(--n-item-icon-color-active-horizontal);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active-horizontal);
 `,[g("a","color: var(--n-item-text-color-active-horizontal);"),v("extra","color: var(--n-item-text-color-active-horizontal);")])]),P("child-active",`
 border-bottom: 2px solid var(--n-border-color-horizontal);
 `,[f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-horizontal);
 `,[g("a",`
 color: var(--n-item-text-color-child-active-horizontal);
 `),v("extra",`
 color: var(--n-item-text-color-child-active-horizontal);
 `)]),v("icon",`
 color: var(--n-item-icon-color-child-active-horizontal);
 `)]),Z("disabled",[Z("selected, child-active",[g("&:focus-within",Fe)]),P("selected",[D(null,[v("icon","color: var(--n-item-icon-color-active-hover-horizontal);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active-hover-horizontal);
 `,[g("a","color: var(--n-item-text-color-active-hover-horizontal);"),v("extra","color: var(--n-item-text-color-active-hover-horizontal);")])])]),P("child-active",[D(null,[v("icon","color: var(--n-item-icon-color-child-active-hover-horizontal);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-hover-horizontal);
 `,[g("a","color: var(--n-item-text-color-child-active-hover-horizontal);"),v("extra","color: var(--n-item-text-color-child-active-hover-horizontal);")])])]),D("border-bottom: 2px solid var(--n-border-color-horizontal);",Fe)]),f("menu-item-content-header",[g("a","color: var(--n-item-text-color-horizontal);")])])]),Z("responsive",[f("menu-item-content-header",`
 overflow: hidden;
 text-overflow: ellipsis;
 `)]),P("collapsed",[f("menu-item-content",[P("selected",[g("&::before",`
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
 `),P("disabled",`
 opacity: .45;
 cursor: not-allowed;
 `),P("collapsed",[v("arrow","transform: rotate(0);")]),P("selected",[g("&::before","background-color: var(--n-item-color-active);"),v("arrow","color: var(--n-arrow-color-active);"),v("icon","color: var(--n-item-icon-color-active);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active);
 `,[g("a","color: var(--n-item-text-color-active);"),v("extra","color: var(--n-item-text-color-active);")])]),P("child-active",[f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active);
 `,[g("a",`
 color: var(--n-item-text-color-child-active);
 `),v("extra",`
 color: var(--n-item-text-color-child-active);
 `)]),v("arrow",`
 color: var(--n-arrow-color-child-active);
 `),v("icon",`
 color: var(--n-item-icon-color-child-active);
 `)]),Z("disabled",[Z("selected, child-active",[g("&:focus-within",Te)]),P("selected",[D(null,[v("arrow","color: var(--n-arrow-color-active-hover);"),v("icon","color: var(--n-item-icon-color-active-hover);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-active-hover);
 `,[g("a","color: var(--n-item-text-color-active-hover);"),v("extra","color: var(--n-item-text-color-active-hover);")])])]),P("child-active",[D(null,[v("arrow","color: var(--n-arrow-color-child-active-hover);"),v("icon","color: var(--n-item-icon-color-child-active-hover);"),f("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-hover);
 `,[g("a","color: var(--n-item-text-color-child-active-hover);"),v("extra","color: var(--n-item-text-color-child-active-hover);")])])]),P("selected",[D(null,[g("&::before","background-color: var(--n-item-color-active-hover);")])]),D(null,Te)]),v("icon",`
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
 `)]);function D(e,o){return[P("hover",e,o),g("&:hover",e,o)]}var Do=T({name:"MenuDivider",setup(){const{mergedClsPrefixRef:e,isHorizontalRef:o}=L(W);return()=>o.value?null:(m(),_("div",{key:1,class:H(`${e.value}-menu-divider`)},null,2))}}),Uo=T({name:"ChevronDownFilled",render(){return(()=>{const e=xe("f3af82a2aab086a5");return e[0]||(e[0]=w("svg",{viewBox:"0 0 16 16",fill:"none",xmlns:"http://www.w3.org/2000/svg"},[w("path",{d:"M3.20041 5.73966C3.48226 5.43613 3.95681 5.41856 4.26034 5.70041L8 9.22652L11.7397 5.70041C12.0432 5.41856 12.5177 5.43613 12.7996 5.73966C13.0815 6.0432 13.0639 6.51775 12.7603 6.7996L8.51034 10.7996C8.22258 11.0668 7.77743 11.0668 7.48967 10.7996L3.23966 6.7996C2.93613 6.51775 2.91856 6.0432 3.20041 5.73966Z",fill:"currentColor"})],-1))})()}});const Vo=["onClick"];var Ue=T({name:"MenuOptionContent",props:{collapsed:Boolean,disabled:Boolean,title:[String,Function],icon:Function,extra:[String,Function],showArrow:Boolean,childActive:Boolean,hover:Boolean,paddingLeft:Number,selected:Boolean,maxIconSize:{type:Number,required:!0},activeIconSize:{type:Number,required:!0},iconMarginRight:{type:Number,required:!0},clsPrefix:{type:String,required:!0},onClick:Function,tmNode:{type:Object,required:!0},isEllipsisPlaceholder:Boolean},setup(e){const{props:o}=L(W);return{menuProps:o,style:y(()=>{const{paddingLeft:r}=e;return{paddingLeft:r&&`${r}px`}}),iconStyle:y(()=>{const{maxIconSize:r,activeIconSize:l,iconMarginRight:i}=e;return{width:`${r}px`,height:`${r}px`,fontSize:`${l}px`,marginRight:`${i}px`}})}},render(){const{clsPrefix:e,tmNode:o,menuProps:{renderIcon:r,renderLabel:l,renderExtra:i,expandIcon:a}}=this,s=r?r(o.rawNode):G(this.icon);return(()=>{const c=xe("7bb10afc6caf8fa4");return m(),_("div",{onClick:u=>{this.onClick?.(u)},role:"none",class:H([`${e}-menu-item-content`,{[`${e}-menu-item-content--selected`]:this.selected,[`${e}-menu-item-content--collapsed`]:this.collapsed,[`${e}-menu-item-content--child-active`]:this.childActive,[`${e}-menu-item-content--disabled`]:this.disabled,[`${e}-menu-item-content--hover`]:this.hover}]),style:he(this.style)},[I(()=>s&&(m(),_("div",{class:H(`${e}-menu-item-content__icon`),style:he(this.iconStyle),role:"none"},[I(()=>[s])],6))),w("div",{class:H(`${e}-menu-item-content-header`),role:"none"},[this.isEllipsisPlaceholder?(m(),_(O,{key:0},[I(()=>this.title)],64)):(m(),_(O,{key:1},[l?(m(),_(O,{key:0},[I(()=>l(o.rawNode))],64)):(m(),_(O,{key:1},[I(()=>G(this.title))],64))],64)),this.extra||i?(m(),_("span",{key:2,class:H(`${e}-menu-item-content-header__extra`)},[c[0]||(c[0]=I(" ",-1)),i?(m(),_(O,{key:0},[I(()=>i(o.rawNode))],64)):(m(),_(O,{key:1},[I(()=>G(this.extra))],64))],2)):I(()=>null)],2),this.showArrow?(m(),k(uo,{key:0,ariaHidden:!0,class:H(`${e}-menu-item-content__arrow`),clsPrefix:e},{default:()=>a?a(o.rawNode):(m(),k(Uo,{key:1}))},1032,["class","clsPrefix"])):I(()=>null)],14,Vo)})()}});const re=8;function ze(e){const o=L(W),{props:r,mergedCollapsedRef:l}=o,i=L(De,null),a=L(ye,null),s=y(()=>r.mode==="horizontal"),c=y(()=>s.value?r.dropdownPlacement:"tmNodes"in e?"right-start":"right"),u=y(()=>Math.max(r.collapsedIconSize??r.iconSize,r.iconSize));return{dropdownPlacement:c,activeIconSize:y(()=>!s.value&&e.root&&l.value?r.collapsedIconSize??r.iconSize:r.iconSize),maxIconSize:u,paddingLeft:y(()=>{if(s.value)return;const{collapsedWidth:b,indent:x,rootIndent:z}=r,{root:p,isGroup:M}=e,F=z===void 0?x:z;return p?l.value?b/2-u.value/2:F:a&&typeof a.paddingLeftRef.value=="number"?l.value?b/2-u.value/2:x/2+a.paddingLeftRef.value:i&&typeof i.paddingLeftRef.value=="number"?(M?x/2:x)+i.paddingLeftRef.value:0}),iconMarginRight:y(()=>{const{collapsedWidth:b,indent:x,rootIndent:z}=r,{value:p}=u,{root:M}=e;return s.value||!M||!l.value?re:(z===void 0?x:z)+p+re-(b+p)/2}),NMenu:o,NSubmenu:i,NMenuOptionGroup:a}}const Ie={internalKey:{type:[String,Number],required:!0},root:Boolean,isGroup:Boolean,level:{type:Number,required:!0},title:[String,Function],extra:[String,Function]},Ve={...Ie,tmNode:{type:Object,required:!0},disabled:Boolean,icon:Function,onClick:Function},Go=we(Ve),jo=T({name:"MenuOption",props:Ve,setup(e){const o=ze(e),{NSubmenu:r,NMenu:l,NMenuOptionGroup:i}=o,{props:a,mergedClsPrefixRef:s,mergedCollapsedRef:c}=l,u=r?r.mergedDisabledRef:i?i.mergedDisabledRef:{value:!1},b=y(()=>u.value||e.disabled);function x(p){const{onClick:M}=e;M&&M(p)}function z(p){b.value||(l.doSelect(e.internalKey,e.tmNode.rawNode),x(p))}return{mergedClsPrefix:s,dropdownPlacement:o.dropdownPlacement,paddingLeft:o.paddingLeft,iconMarginRight:o.iconMarginRight,maxIconSize:o.maxIconSize,activeIconSize:o.activeIconSize,mergedTheme:l.mergedThemeRef,menuProps:a,dropdownEnabled:pe(()=>e.root&&c.value&&a.mode!=="horizontal"&&!b.value),selected:pe(()=>l.mergedValueRef.value===e.internalKey),mergedDisabled:b,handleClick:z}},render(){const{mergedClsPrefix:e,mergedTheme:o,tmNode:r,menuProps:{renderLabel:l,nodeProps:i}}=this,a=i?.(r.rawNode);return m(),_("div",j(a,{role:"menuitem",class:[`${e}-menu-item`,a?.class]}),[(m(),k(So,{theme:o.peers.Tooltip,themeOverrides:o.peerOverrides.Tooltip,trigger:"hover",placement:this.dropdownPlacement,disabled:!this.dropdownEnabled||this.title===void 0,internalExtraClass:["menu-tooltip"]},{default:()=>l?l(r.rawNode):G(this.title),trigger:()=>(m(),k(Ue,{tmNode:r,clsPrefix:e,paddingLeft:this.paddingLeft,iconMarginRight:this.iconMarginRight,maxIconSize:this.maxIconSize,activeIconSize:this.activeIconSize,selected:this.selected,title:this.title,extra:this.extra,disabled:this.mergedDisabled,icon:this.icon,onClick:this.handleClick},null,8,["tmNode","clsPrefix","paddingLeft","iconMarginRight","maxIconSize","activeIconSize","selected","title","extra","disabled","icon","onClick"]))},1032,["theme","themeOverrides","placement","disabled"]))],16)}}),Ge={...Ie,tmNode:{type:Object,required:!0},tmNodes:{type:Array,required:!0}},qo=we(Ge),Xo=T({name:"MenuOptionGroup",props:Ge,setup(e){const o=ze(e),{NSubmenu:r}=o,l=y(()=>r?.mergedDisabledRef.value?!0:e.tmNode.disabled);J(ye,{paddingLeftRef:o.paddingLeft,mergedDisabledRef:l});const{mergedClsPrefixRef:i,props:a}=L(W);return function(){const{value:s}=i,c=o.paddingLeft.value,{nodeProps:u}=a,b=u?.(e.tmNode.rawNode);return(()=>{const x=xe("45eca6a63be5028b");return m(),_("div",{class:H(`${s}-menu-item-group`),role:"group"},[w("div",j(b,{class:[`${s}-menu-item-group-title`,b?.class],style:[b?.style||"",c!==void 0?`padding-left: ${c}px;`:""]}),[I(()=>G(e.title)),e.extra?(m(),_(O,{key:0},[x[0]||(x[0]=I(" ",-1)),I(()=>G(e.extra))],64)):I(()=>null)],16),w("div",null,[I(()=>e.tmNodes.map(z=>_e(z,a)))])],2)})()}}}),Yo=["aria-expanded","id"],Zo=["aria-expanded","id"],je={...Ie,rawNodes:{type:Array,default:()=>[]},tmNodes:{type:Array,default:()=>[]},tmNode:{type:Object,required:!0},disabled:Boolean,icon:Function,onClick:Function,domId:String,virtualChildActive:{type:Boolean,default:void 0},isEllipsisPlaceholder:Boolean},Jo=we(je),ge=T({name:"Submenu",props:je,setup(e){const o=ze(e),{NMenu:r,NSubmenu:l}=o,{props:i,mergedCollapsedRef:a,mergedThemeRef:s}=r,c=y(()=>{const{disabled:p}=e;return l?.mergedDisabledRef.value||i.disabled?!0:p}),u=V(!1);J(De,{paddingLeftRef:o.paddingLeft,mergedDisabledRef:c}),J(ye,null);function b(){const{onClick:p}=e;p&&p()}function x(){c.value||(a.value||r.toggleExpand(e.internalKey),b())}function z(p){u.value=p}return{menuProps:i,mergedTheme:s,doSelect:r.doSelect,inverted:r.invertedRef,isHorizontal:r.isHorizontalRef,mergedClsPrefix:r.mergedClsPrefixRef,maxIconSize:o.maxIconSize,activeIconSize:o.activeIconSize,iconMarginRight:o.iconMarginRight,dropdownPlacement:o.dropdownPlacement,dropdownShow:u,paddingLeft:o.paddingLeft,mergedDisabled:c,mergedValue:r.mergedValueRef,childActive:pe(()=>e.virtualChildActive??r.activePathRef.value.includes(e.internalKey)),collapsed:y(()=>i.mode==="horizontal"?!1:a.value?!0:!r.mergedExpandedKeysRef.value.includes(e.internalKey)),dropdownEnabled:y(()=>!c.value&&(i.mode==="horizontal"||a.value)),handlePopoverShowChange:z,handleClick:x}},render(){const{mergedClsPrefix:e,menuProps:{renderIcon:o,renderLabel:r}}=this,l=()=>{const{isHorizontal:a,paddingLeft:s,collapsed:c,mergedDisabled:u,maxIconSize:b,activeIconSize:x,title:z,childActive:p,icon:M,handleClick:F,menuProps:{nodeProps:K},dropdownShow:q,iconMarginRight:B,tmNode:ee,mergedClsPrefix:X,isEllipsisPlaceholder:ae,extra:le}=this,Y=K?.(ee.rawNode);return m(),_("div",j(Y,{class:[`${X}-menu-item`,Y?.class],role:"menuitem"}),[(m(),k(Ue,{tmNode:ee,paddingLeft:s,collapsed:c,disabled:u,iconMarginRight:B,maxIconSize:b,activeIconSize:x,title:z,extra:le,showArrow:!a,childActive:p,clsPrefix:X,icon:M,hover:q,onClick:F,isEllipsisPlaceholder:ae},null,8,["tmNode","paddingLeft","collapsed","disabled","iconMarginRight","maxIconSize","activeIconSize","title","extra","showArrow","childActive","clsPrefix","icon","hover","onClick","isEllipsisPlaceholder"]))],16)},i=()=>(m(),k(mo,null,{default:()=>{const{tmNodes:a,collapsed:s}=this;return s?null:(m(),_("div",{key:1,class:H(`${e}-submenu-children`),role:"menu"},[I(()=>a.map(c=>_e(c,this.menuProps)))],2))}},1024));return this.root?(m(),k(Be,j({key:2,size:"large",trigger:"hover"},this.menuProps?.dropdownProps,{themeOverrides:this.mergedTheme.peerOverrides.Dropdown,theme:this.mergedTheme.peers.Dropdown,builtinThemeOverrides:{fontSizeLarge:"14px",optionIconSizeLarge:"18px"},value:this.mergedValue,disabled:!this.dropdownEnabled,placement:this.dropdownPlacement,keyField:this.menuProps.keyField,labelField:this.menuProps.labelField,childrenField:this.menuProps.childrenField,onUpdateShow:this.handlePopoverShowChange,options:this.rawNodes,onSelect:this.doSelect,inverted:this.inverted,renderIcon:o,renderLabel:r}),{default:()=>(m(),_("div",{class:H(`${e}-submenu`),role:"menu","aria-expanded":!this.collapsed,id:this.domId},[I(()=>l()),this.isHorizontal?I(()=>null):(m(),_(O,{key:1},[I(()=>i())],64))],10,Yo))},1040,["themeOverrides","theme","value","disabled","placement","keyField","labelField","childrenField","onUpdateShow","options","onSelect","inverted","renderIcon","renderLabel"])):(m(),_("div",{key:3,class:H(`${e}-submenu`),role:"menu","aria-expanded":!this.collapsed,id:this.domId},[I(()=>l()),I(()=>i())],10,Zo))}});function be(e){return e.type==="divider"||e.type==="render"}function Qo(e){return e.type==="divider"}function _e(e,o){const{rawNode:r}=e,{show:l}=r;if(l===!1)return null;if(be(r))return Qo(r)?(m(),k(Do,j({key:e.key},r.props),null,16)):null;const{labelField:i}=o,{key:a,level:s,isGroup:c}=e,u={...r,title:r.title||r[i],extra:r.titleExtra||r.extra,key:a,internalKey:a,level:s,root:s===0,isGroup:c};return e.children?e.isGroup?S(Xo,ce(u,qo,{tmNode:e,tmNodes:e.children,key:a})):S(ge,ce(u,Jo,{key:a,rawNodes:r[o.childrenField],tmNodes:e.children,tmNode:e})):S(jo,ce(u,Go,{key:a,tmNode:e}))}const Wo={...ie.props,options:{type:Array,default:()=>[]},collapsed:{type:Boolean,default:void 0},collapsedWidth:{type:Number,default:48},iconSize:{type:Number,default:20},collapsedIconSize:{type:Number,default:24},rootIndent:Number,indent:{type:Number,default:32},labelField:{type:String,default:"label"},keyField:{type:String,default:"key"},childrenField:{type:String,default:"children"},disabledField:{type:String,default:"disabled"},defaultExpandAll:Boolean,defaultExpandedKeys:Array,expandedKeys:Array,value:[String,Number],defaultValue:{type:[String,Number],default:null},mode:{type:String,default:"vertical"},watchProps:{type:Array,default:void 0},disabled:Boolean,show:{type:Boolean,default:!0},inverted:Boolean,"onUpdate:expandedKeys":[Function,Array],onUpdateExpandedKeys:[Function,Array],onUpdateValue:[Function,Array],"onUpdate:value":[Function,Array],expandIcon:Function,renderIcon:Function,renderLabel:Function,renderExtra:Function,dropdownProps:Object,accordion:Boolean,nodeProps:Function,dropdownPlacement:{type:String,default:"bottom"},responsive:Boolean,items:Array,onOpenNamesChange:[Function,Array],onSelect:[Function,Array],onExpandedNamesChange:[Function,Array],expandedNames:Array,defaultExpandedNames:Array};var et=T({name:"Menu",inheritAttrs:!1,props:Wo,setup(e){const{mergedClsPrefixRef:o,inlineThemeDisabled:r}=Oe(e),l=ie("Menu","-menu",$o,po,e,o),i=L(Bo,null),a=y(()=>{const{collapsed:d}=e;if(d!==void 0)return d;if(i){const{collapseModeRef:C,collapsedRef:t}=i;if(C.value==="width")return t.value??!1}return!1}),s=y(()=>{const{keyField:d,childrenField:C,disabledField:t}=e;return de(e.items||e.options,{getIgnored(h){return be(h)},getChildren(h){return h[C]},getDisabled(h){return h[t]},getKey(h){return h[d]??h.name}})}),c=y(()=>new Set(s.value.treeNodes.map(d=>d.key))),{watchProps:u}=e,b=V(null);u?.includes("defaultValue")?Me(()=>{b.value=e.defaultValue}):b.value=e.defaultValue;const x=ve(e,"value"),z=Ee(x,b),p=V([]),M=()=>{p.value=e.defaultExpandAll?s.value.getNonLeafKeys():e.defaultExpandedNames||e.defaultExpandedKeys||s.value.getPath(z.value,{includeSelf:!1}).keyPath};u?.includes("defaultExpandedKeys")?Me(M):M();const F=ko(e,["expandedNames","expandedKeys"]),K=Ee(F,p),q=y(()=>s.value.treeNodes),B=y(()=>s.value.getPath(z.value).keyPath);J(W,{props:e,mergedCollapsedRef:a,mergedThemeRef:l,mergedValueRef:z,mergedExpandedKeysRef:K,activePathRef:B,mergedClsPrefixRef:o,isHorizontalRef:y(()=>e.mode==="horizontal"),invertedRef:ve(e,"inverted"),doSelect:ee,toggleExpand:ae});function ee(d,C){const{"onUpdate:value":t,onUpdateValue:h,onSelect:A}=e;h&&$(h,d,C),t&&$(t,d,C),A&&$(A,d,C),b.value=d}function X(d){const{"onUpdate:expandedKeys":C,onUpdateExpandedKeys:t,onExpandedNamesChange:h,onOpenNamesChange:A}=e;C&&$(C,d),t&&$(t,d),h&&$(h,d),A&&$(A,d),p.value=d}function ae(d){const C=Array.from(K.value),t=C.findIndex(h=>h===d);if(~t)C.splice(t,1);else{if(e.accordion&&c.value.has(d)){const h=C.findIndex(A=>c.value.has(A));h>-1&&C.splice(h,1)}C.push(d)}X(C)}const le=d=>{const C=s.value.getPath(d??z.value,{includeSelf:!1}).keyPath;if(!C.length)return;const t=Array.from(K.value),h=new Set([...t,...C]);e.accordion&&c.value.forEach(A=>{h.has(A)&&!C.includes(A)&&h.delete(A)}),X(Array.from(h))},Y=y(()=>{const{inverted:d}=e,{common:{cubicBezierEaseInOut:C},self:t}=l.value,{borderRadius:h,borderColorHorizontal:A,fontSize:oo,itemHeight:to,dividerColor:ro}=t,n={"--n-divider-color":ro,"--n-bezier":C,"--n-font-size":oo,"--n-border-color-horizontal":A,"--n-border-radius":h,"--n-item-height":to};return d?(n["--n-group-text-color"]=t.groupTextColorInverted,n["--n-color"]=t.colorInverted,n["--n-item-text-color"]=t.itemTextColorInverted,n["--n-item-text-color-hover"]=t.itemTextColorHoverInverted,n["--n-item-text-color-active"]=t.itemTextColorActiveInverted,n["--n-item-text-color-child-active"]=t.itemTextColorChildActiveInverted,n["--n-item-text-color-child-active-hover"]=t.itemTextColorChildActiveInverted,n["--n-item-text-color-active-hover"]=t.itemTextColorActiveHoverInverted,n["--n-item-icon-color"]=t.itemIconColorInverted,n["--n-item-icon-color-hover"]=t.itemIconColorHoverInverted,n["--n-item-icon-color-active"]=t.itemIconColorActiveInverted,n["--n-item-icon-color-active-hover"]=t.itemIconColorActiveHoverInverted,n["--n-item-icon-color-child-active"]=t.itemIconColorChildActiveInverted,n["--n-item-icon-color-child-active-hover"]=t.itemIconColorChildActiveHoverInverted,n["--n-item-icon-color-collapsed"]=t.itemIconColorCollapsedInverted,n["--n-item-text-color-horizontal"]=t.itemTextColorHorizontalInverted,n["--n-item-text-color-hover-horizontal"]=t.itemTextColorHoverHorizontalInverted,n["--n-item-text-color-active-horizontal"]=t.itemTextColorActiveHorizontalInverted,n["--n-item-text-color-child-active-horizontal"]=t.itemTextColorChildActiveHorizontalInverted,n["--n-item-text-color-child-active-hover-horizontal"]=t.itemTextColorChildActiveHoverHorizontalInverted,n["--n-item-text-color-active-hover-horizontal"]=t.itemTextColorActiveHoverHorizontalInverted,n["--n-item-icon-color-horizontal"]=t.itemIconColorHorizontalInverted,n["--n-item-icon-color-hover-horizontal"]=t.itemIconColorHoverHorizontalInverted,n["--n-item-icon-color-active-horizontal"]=t.itemIconColorActiveHorizontalInverted,n["--n-item-icon-color-active-hover-horizontal"]=t.itemIconColorActiveHoverHorizontalInverted,n["--n-item-icon-color-child-active-horizontal"]=t.itemIconColorChildActiveHorizontalInverted,n["--n-item-icon-color-child-active-hover-horizontal"]=t.itemIconColorChildActiveHoverHorizontalInverted,n["--n-arrow-color"]=t.arrowColorInverted,n["--n-arrow-color-hover"]=t.arrowColorHoverInverted,n["--n-arrow-color-active"]=t.arrowColorActiveInverted,n["--n-arrow-color-active-hover"]=t.arrowColorActiveHoverInverted,n["--n-arrow-color-child-active"]=t.arrowColorChildActiveInverted,n["--n-arrow-color-child-active-hover"]=t.arrowColorChildActiveHoverInverted,n["--n-item-color-hover"]=t.itemColorHoverInverted,n["--n-item-color-active"]=t.itemColorActiveInverted,n["--n-item-color-active-hover"]=t.itemColorActiveHoverInverted,n["--n-item-color-active-collapsed"]=t.itemColorActiveCollapsedInverted):(n["--n-group-text-color"]=t.groupTextColor,n["--n-color"]=t.color,n["--n-item-text-color"]=t.itemTextColor,n["--n-item-text-color-hover"]=t.itemTextColorHover,n["--n-item-text-color-active"]=t.itemTextColorActive,n["--n-item-text-color-child-active"]=t.itemTextColorChildActive,n["--n-item-text-color-child-active-hover"]=t.itemTextColorChildActiveHover,n["--n-item-text-color-active-hover"]=t.itemTextColorActiveHover,n["--n-item-icon-color"]=t.itemIconColor,n["--n-item-icon-color-hover"]=t.itemIconColorHover,n["--n-item-icon-color-active"]=t.itemIconColorActive,n["--n-item-icon-color-active-hover"]=t.itemIconColorActiveHover,n["--n-item-icon-color-child-active"]=t.itemIconColorChildActive,n["--n-item-icon-color-child-active-hover"]=t.itemIconColorChildActiveHover,n["--n-item-icon-color-collapsed"]=t.itemIconColorCollapsed,n["--n-item-text-color-horizontal"]=t.itemTextColorHorizontal,n["--n-item-text-color-hover-horizontal"]=t.itemTextColorHoverHorizontal,n["--n-item-text-color-active-horizontal"]=t.itemTextColorActiveHorizontal,n["--n-item-text-color-child-active-horizontal"]=t.itemTextColorChildActiveHorizontal,n["--n-item-text-color-child-active-hover-horizontal"]=t.itemTextColorChildActiveHoverHorizontal,n["--n-item-text-color-active-hover-horizontal"]=t.itemTextColorActiveHoverHorizontal,n["--n-item-icon-color-horizontal"]=t.itemIconColorHorizontal,n["--n-item-icon-color-hover-horizontal"]=t.itemIconColorHoverHorizontal,n["--n-item-icon-color-active-horizontal"]=t.itemIconColorActiveHorizontal,n["--n-item-icon-color-active-hover-horizontal"]=t.itemIconColorActiveHoverHorizontal,n["--n-item-icon-color-child-active-horizontal"]=t.itemIconColorChildActiveHorizontal,n["--n-item-icon-color-child-active-hover-horizontal"]=t.itemIconColorChildActiveHoverHorizontal,n["--n-arrow-color"]=t.arrowColor,n["--n-arrow-color-hover"]=t.arrowColorHover,n["--n-arrow-color-active"]=t.arrowColorActive,n["--n-arrow-color-active-hover"]=t.arrowColorActiveHover,n["--n-arrow-color-child-active"]=t.arrowColorChildActive,n["--n-arrow-color-child-active-hover"]=t.arrowColorChildActiveHover,n["--n-item-color-hover"]=t.itemColorHover,n["--n-item-color-active"]=t.itemColorActive,n["--n-item-color-active-hover"]=t.itemColorActiveHover,n["--n-item-color-active-collapsed"]=t.itemColorActiveCollapsed),n}),Se=r?Le("menu",y(()=>e.inverted?"a":"b"),Y,e):void 0,se=vo(),Pe=V(null),qe=V(null);let Re=!0;const ke=()=>{Re?Re=!1:Pe.value?.sync({showAllItemsBeforeCalculate:!0})};function Xe(){return document.getElementById(se)}const oe=V(-1);function Ye(d){oe.value=e.options.length-d}function Ze(d){d||(oe.value=-1)}const Je=y(()=>{const d=oe.value;return{children:d===-1?[]:e.options.slice(d)}}),Qe=y(()=>{const{childrenField:d,disabledField:C,keyField:t}=e;return de([Je.value],{getIgnored(h){return be(h)},getChildren(h){return h[d]},getDisabled(h){return h[C]},getKey(h){return h[t]??h.name}})}),We=y(()=>de([{}]).treeNodes[0]);function eo(){if(oe.value===-1)return m(),k(ge,{root:!0,level:0,key:"__ellpisisGroupPlaceholder__",internalKey:"__ellpisisGroupPlaceholder__",title:"···",tmNode:We.value,domId:se,isEllipsisPlaceholder:!0},null,8,["tmNode","domId"]);const d=Qe.value.treeNodes[0],C=B.value,t=!!d.children?.some(h=>C.includes(h.key));return m(),k(ge,{level:0,root:!0,key:"__ellpisisGroup__",internalKey:"__ellpisisGroup__",title:"···",virtualChildActive:t,tmNode:d,domId:se,rawNodes:d.rawNode.children||[],tmNodes:d.children||[],isEllipsisPlaceholder:!0},null,8,["virtualChildActive","tmNode","domId","rawNodes","tmNodes"])}return{mergedClsPrefix:o,controlledExpandedKeys:F,uncontrolledExpanededKeys:p,mergedExpandedKeys:K,uncontrolledValue:b,mergedValue:z,activePath:B,tmNodes:q,mergedTheme:l,mergedCollapsed:a,cssVars:r?void 0:Y,themeClass:Se?.themeClass,overflowRef:Pe,counterRef:qe,updateCounter:()=>{},onResize:ke,onUpdateOverflow:Ze,onUpdateCount:Ye,renderCounter:eo,getCounter:Xe,onRender:Se?.onRender,showOption:le,deriveResponsiveState:ke}},render(){const{mergedClsPrefix:e,mode:o,themeClass:r,onRender:l}=this;l?.();const i=()=>this.tmNodes.map(c=>_e(c,this.$props)),a=o==="horizontal"&&this.responsive,s=()=>S("div",j(this.$attrs,{role:o==="horizontal"?"menubar":"menu",class:[`${e}-menu`,r,`${e}-menu--${o}`,a&&`${e}-menu--responsive`,this.mergedCollapsed&&`${e}-menu--collapsed`],style:this.cssVars}),a?(m(),k(Ro,{key:2,ref:"overflowRef",onUpdateOverflow:this.onUpdateOverflow,getCounter:this.getCounter,onUpdateCount:this.onUpdateCount,updateCounter:this.updateCounter,style:{width:"100%",display:"flex",overflow:"hidden"}},{default:i,counter:this.renderCounter},1032,["onUpdateOverflow","getCounter","onUpdateCount","updateCounter"])):i());return a?(m(),k(ho,{key:3,onResize:this.onResize},{default:s},1032,["onResize"])):s()}});const ue="huluwa.collapsed";function ot(e,o){try{const r=localStorage.getItem(e);return r===null?o:r==="1"}catch{return o}}function Ke(e,o){try{localStorage.setItem(e,o?"1":"0")}catch{}}const tt=fo("app",{state:()=>({collapsed:ot(ue,!1),tagNavList:[],breadCrumb:[]}),actions:{toggleCollapsed(){this.collapsed=!this.collapsed,Ke(ue,this.collapsed)},setCollapsed(e){this.collapsed=!!e,Ke(ue,this.collapsed)},setBreadCrumb(e){this.breadCrumb=e}}}),rt={components:{NBreadcrumb:Fo,NBreadcrumbItem:Lo},name:"HeaderBar",props:{collapsed:Boolean,mobile:Boolean},emits:["on-coll-change"],computed:{currentTitle(){return this.$route.name==="messages"?"会话":this.$route.meta&&this.$route.meta.title||"首页"}}},nt={class:"header-bar"},it=["title"],at={class:"custom-content-con"};function lt(e,o,r,l,i,a){const s=E("n-breadcrumb-item"),c=E("n-breadcrumb");return m(),_("div",nt,[w("button",{class:"icon-btn",type:"button",onClick:o[0]||(o[0]=u=>e.$emit("on-coll-change",!r.collapsed)),title:r.mobile?"打开菜单":r.collapsed?"展开侧栏":"收起侧栏"},[w("i",{class:ne(r.mobile?"fa-solid fa-bars":r.collapsed?"fa-solid fa-indent":"fa-solid fa-outdent")},null,2)],8,it),R(c,{class:"crumb"},{default:N(()=>[R(s,null,{default:N(()=>[o[1]||(o[1]=w("i",{class:"fa-solid fa-house crumb-home"},null,-1)),w("span",null,fe(a.currentTitle),1)]),_:1})]),_:1}),w("div",at,[go(e.$slots,"default",{},void 0,!0)])])}const st=Ce(rt,[["render",lt],["__scopeId","data-v-1a57d3e9"]]),ct={components:{NButton:xo,NDropdown:Be,NForm:No,NFormItem:Ao,NInput:Po,NModal:bo},name:"UserChip",setup(){return{auth:wo()}},data(){return{passwordModal:{visible:!1,loading:!1,data:{oldPassword:"",newPassword:"",confirmPassword:""}},passwordRules:{oldPassword:{required:!0,message:"请输入原密码",trigger:["input","blur"]},newPassword:[{required:!0,message:"请输入新密码",trigger:["input","blur"]},{min:6,message:"新密码至少 6 位",trigger:["input","blur"]}],confirmPassword:{required:!0,validator:(e,o)=>o?o!==this.passwordModal.data.newPassword?new Error("两次输入的密码不一致"):!0:new Error("请再次输入新密码"),trigger:["input","blur"]}}}},computed:{avatarText(){return(this.auth.username||"A").charAt(0).toUpperCase()},options(){return[{label:"修改密码",key:"password",icon:()=>S("i",{class:"fa-solid fa-key"})},{label:"退出登录",key:"logout",icon:()=>S("i",{class:"fa-solid fa-right-from-bracket"})}]}},methods:{handleSelect(e){e==="password"?(this.passwordModal.data={oldPassword:"",newPassword:"",confirmPassword:""},this.passwordModal.visible=!0):e==="logout"&&this.$dialog.warning({title:"退出登录",content:"确认退出当前账号？",positiveText:"退出",negativeText:"取消",onPositiveClick:()=>{this.auth.clear(),this.$router.push({name:"login"})}})},submitPassword(){this.$refs.passwordForm.validate(async e=>{if(!e){this.passwordModal.loading=!0;try{await this.auth.changePassword({oldPassword:this.passwordModal.data.oldPassword,newPassword:this.passwordModal.data.newPassword}),this.passwordModal.visible=!1,this.$message.success("密码已修改，请重新登录"),this.$router.push({name:"login"})}catch{}finally{this.passwordModal.loading=!1}}})}}},dt={class:"user-avatar-dropdown"},ut={class:"user-chip",type:"button"},mt={class:"ava"},ht={class:"name"},vt={class:"modal-footer"};function pt(e,o,r,l,i,a){const s=E("n-dropdown"),c=E("n-input"),u=E("n-form-item"),b=E("n-form"),x=E("n-button"),z=E("n-modal");return m(),_("div",dt,[R(s,{options:a.options,onSelect:a.handleSelect},{default:N(()=>[w("button",ut,[w("span",mt,fe(a.avatarText),1),w("span",ht,fe(l.auth.username||"admin"),1),o[5]||(o[5]=w("i",{class:"fa-solid fa-chevron-down"},null,-1))])]),_:1},8,["options","onSelect"]),R(z,{show:i.passwordModal.visible,"onUpdate:show":o[4]||(o[4]=p=>i.passwordModal.visible=p),preset:"card",title:"修改密码",style:{width:"460px"},"mask-closable":!1},{footer:N(()=>[w("div",vt,[R(x,{onClick:o[3]||(o[3]=p=>i.passwordModal.visible=!1)},{default:N(()=>[...o[6]||(o[6]=[Ae("取消",-1)])]),_:1}),R(x,{type:"primary",loading:i.passwordModal.loading,onClick:a.submitPassword},{default:N(()=>[...o[7]||(o[7]=[Ae("确认",-1)])]),_:1},8,["loading","onClick"])])]),default:N(()=>[R(b,{ref:"passwordForm",model:i.passwordModal.data,rules:i.passwordRules,"label-placement":"left","label-width":"90"},{default:N(()=>[R(u,{label:"原密码",path:"oldPassword"},{default:N(()=>[R(c,{value:i.passwordModal.data.oldPassword,"onUpdate:value":o[0]||(o[0]=p=>i.passwordModal.data.oldPassword=p),type:"password","show-password-on":"click",placeholder:"请输入原密码"},null,8,["value"])]),_:1}),R(u,{label:"新密码",path:"newPassword"},{default:N(()=>[R(c,{value:i.passwordModal.data.newPassword,"onUpdate:value":o[1]||(o[1]=p=>i.passwordModal.data.newPassword=p),type:"password","show-password-on":"click",placeholder:"至少 6 位"},null,8,["value"])]),_:1}),R(u,{label:"确认密码",path:"confirmPassword"},{default:N(()=>[R(c,{value:i.passwordModal.data.confirmPassword,"onUpdate:value":o[2]||(o[2]=p=>i.passwordModal.data.confirmPassword=p),type:"password","show-password-on":"click",placeholder:"请再次输入新密码"},null,8,["value"])]),_:1})]),_:1},8,["model","rules"])]),_:1},8,["show"])])}const ft=Ce(ct,[["render",pt],["__scopeId","data-v-572be87c"]]),me="messages-index",gt="会话",U=e=>{if(e)return()=>S("i",{class:e})},bt={name:"MainLayout",components:{HeaderBar:st,UserChip:ft,NMenu:et},setup(){const e=tt(),o=Mo(),r=_o();return{appStore:e,chatStore:o,shellHidden:r}},data(){return{expandedKeys:[],isMobile:!1,mobileDrawerOpen:!1}},computed:{collapsed(){return this.appStore.collapsed},siderCollapsed(){return this.isMobile?!this.mobileDrawerOpen:this.collapsed},headerCollapsed(){return this.isMobile?!this.mobileDrawerOpen:this.collapsed},activeMenu(){return this.$route.name==="messages"?me:this.$route.name},menuOptions(){const e=this.$router.options.routes.find(o=>o.path==="/");return!e||!Array.isArray(e.children)?[]:e.children.filter(o=>!(o.meta&&o.meta.hideInMenu)).map(o=>o.name==="messages"?this.messagesMenuOption(o):this.toMenuOption(o))}},watch:{shellHidden:{immediate:!0,handler(e,o){if(e){this.stopMenuPolling();return}o!==void 0&&(this.chatStore.refreshSessions().catch(()=>{}),this.startMenuPolling())}},"$route.name"(e,o){(this.isMessagesGroup(e)||this.isMessagesGroup(o))&&(this.ensureMessagesExpanded(),e==="messages")||this.syncExpandedKeys()}},async mounted(){this.handleResize(),window.addEventListener("resize",this.handleResize),this.syncExpandedKeys();try{await this.chatStore.refreshSessions()}catch{}this.startMenuPolling()},beforeUnmount(){window.removeEventListener("resize",this.handleResize),this.stopMenuPolling()},methods:{handleResize(){const e=window.innerWidth<=900;e!==this.isMobile&&(this.isMobile=e,e&&(this.mobileDrawerOpen=!1)),this.$route.name==="messages"&&zo(Io())},handleCollapsedChange(e){if(this.isMobile){this.mobileDrawerOpen=!this.mobileDrawerOpen;return}this.appStore.setCollapsed(e)},closeDrawer(){this.mobileDrawerOpen=!1},ensureSessionsLoaded(){(this.chatStore.sessions||[]).length||this.chatStore.refreshSessions().catch(()=>{})},isMessagesGroup(e){return e==="messages"||e==="movie-subscriptions"||e==="movie-downloads"||e==="movie-check-logs"},ensureMessagesExpanded(){this.ensureSessionsLoaded(),this.expandedKeys.includes("messages")||(this.expandedKeys=[...this.expandedKeys,"messages"])},startMenuPolling(){this._menuTimer||(this._menuTimer=setInterval(()=>{this.chatStore.refreshSessions().catch(()=>{})},15e3))},stopMenuPolling(){this._menuTimer&&(clearInterval(this._menuTimer),this._menuTimer=null)},toMenuOption(e){const o=Array.isArray(e.children)?e.children.filter(r=>!(r.meta&&r.meta.hideInMenu)):[];return o.length?{key:e.name,label:e.meta&&e.meta.title,icon:U(e.meta&&e.meta.icon),children:o.map(r=>this.toMenuOption(r))}:{key:e.name,label:()=>S(te,{to:{name:e.name}},{default:()=>e.meta.title}),icon:U(e.meta&&e.meta.icon)}},messagesMenuOption(e){const o=(this.chatStore.sessions||[]).reduce((r,l)=>r+(l.unread||0),0);return{key:e.name,label:()=>S("span",{class:"menu-label"},[S("span",e.meta&&e.meta.title),o>0?S("span",{class:"menu-dot"}):null]),icon:U(e.meta&&e.meta.icon),children:[{key:me,label:()=>S("span",{class:"menu-label"},[S("span",gt),o>0?S("span",{class:"menu-badge"},o>99?"99+":String(o)):null]),icon:U("fa-solid fa-comments")},{key:"movie-subscriptions",label:()=>S(te,{to:{name:"movie-subscriptions"}},{default:()=>"我的订阅"}),icon:U("fa-solid fa-bell")},{key:"movie-downloads",label:()=>S(te,{to:{name:"movie-downloads"}},{default:()=>"我的下载"}),icon:U("fa-solid fa-download")},{key:"movie-check-logs",label:()=>S(te,{to:{name:"movie-check-logs"}},{default:()=>"巡检记录"}),icon:U("fa-solid fa-list-check")}]}},syncExpandedKeys(){const e=this.$route.name;if(this.isMessagesGroup(e)){this.ensureMessagesExpanded();return}const o=this.$router.options.routes.find(i=>i.path==="/"),l=(o&&Array.isArray(o.children)?o.children:[]).filter(i=>Array.isArray(i.children)&&i.children.length).find(i=>i.children.some(a=>a.name===e));this.expandedKeys=l?[l.name]:[]},turnToPage(e){if(this.closeDrawer(),e===me){this.$router.push({name:"messages"}).catch(()=>{});return}e!==this.$route.name&&this.$router.push({name:e}).catch(()=>{})},handleExpandedChange(e){if(this.isMessagesGroup(this.$route.name)&&!e.includes("messages")){this.expandedKeys=[...e,"messages"];return}this.expandedKeys=e}}},xt={class:"logo"},wt={class:"logo-text"},Ct={class:"sider-foot"},yt={class:"main-body"},zt={class:"content-wrapper"};function It(e,o,r,l,i,a){const s=E("n-menu"),c=E("user-chip"),u=E("header-bar"),b=E("router-view");return m(),_("div",{class:ne(["main-console",{"is-mobile":i.isMobile,"drawer-open":i.isMobile&&i.mobileDrawerOpen,"is-immersive":l.shellHidden}])},[w("aside",{class:ne(["sider",{collapsed:a.siderCollapsed}])},[w("div",xt,[o[2]||(o[2]=w("img",{class:"logo-mark",src:Ho,alt:"葫芦娃 Logo",width:"36",height:"36"},null,-1)),Ne(w("div",wt,[...o[1]||(o[1]=[w("div",{class:"logo-name"},"葫芦娃助手",-1),w("div",{class:"logo-sub"},"OPS CONSOLE",-1)])],512),[[He,!a.siderCollapsed]])]),R(s,{value:a.activeMenu,options:a.menuOptions,collapsed:a.siderCollapsed,"collapsed-width":64,"collapsed-icon-size":17,indent:18,"expanded-keys":i.expandedKeys,accordion:"","onUpdate:value":a.turnToPage,"onUpdate:expandedKeys":a.handleExpandedChange},null,8,["value","options","collapsed","expanded-keys","onUpdate:value","onUpdate:expandedKeys"]),Ne(w("div",Ct,[...o[3]||(o[3]=[w("span",null,"版本",-1),w("span",{class:"mono"},"v1.0.0",-1)])],512),[[He,!a.siderCollapsed]])],2),w("div",{class:ne(["sider-mask",{show:i.isMobile&&i.mobileDrawerOpen}]),"aria-hidden":"true",onClick:o[0]||(o[0]=(...x)=>a.closeDrawer&&a.closeDrawer(...x))},null,2),w("div",yt,[R(u,{collapsed:a.headerCollapsed,mobile:i.isMobile,onOnCollChange:a.handleCollapsedChange},{default:N(()=>[R(c)]),_:1},8,["collapsed","mobile","onOnCollChange"]),w("div",zt,[R(b,null,{default:N(({Component:x})=>[(m(),k(Co,null,[(m(),k(yo(x),{key:e.$route.path}))],1024))]),_:1})])])],2)}const Ft=Ce(bt,[["render",It],["__scopeId","data-v-7989f9f3"]]);export{Ft as default};
