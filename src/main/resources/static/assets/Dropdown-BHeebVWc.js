import{by as Oe,bz as Fe,bd as Te,bs as ee,bA as Ee,bB as Le,aH as oe,aE as he,e as I,C as Me,g as q,c as R,b as N,a as $,u as re,bg as ge,y as J,H as G,j as xe,bC as je,l as Se,m,p as ve,o as l,f as w,i as C,r as F,E as x,n as k,D as te,F as D,bi as ke,x as T,bD as Pe,J as ne,ba as He,q as W,bE as Ve,bF as Ue,bG as qe,bv as We,A as be,d as _,L as Ge,t as K,bH as Je,P as pe,as as O}from"./index-CCRB5hRG.js";import{B as Xe,h as we,e as Ze,f as Qe,r as Ye,p as Re,P as eo,c as oo}from"./create-Bk5wmZb5.js";import{f as no,u as to}from"./Input-gKwdcPYd.js";function ro(e={},o){const r=Le({ctrl:!1,command:!1,win:!1,shift:!1,tab:!1}),{keydown:n,keyup:i}=e,a=s=>{switch(s.key){case"Control":r.ctrl=!0;break;case"Meta":r.command=!0,r.win=!0;break;case"Shift":r.shift=!0;break;case"Tab":r.tab=!0;break}n!==void 0&&Object.keys(n).forEach(g=>{if(g!==s.key)return;const b=n[g];if(typeof b=="function")b(s);else{const{stop:P=!1,prevent:S=!1}=b;P&&s.stopPropagation(),S&&s.preventDefault(),b.handler(s)}})},d=s=>{switch(s.key){case"Control":r.ctrl=!1;break;case"Meta":r.command=!1,r.win=!1;break;case"Shift":r.shift=!1;break;case"Tab":r.tab=!1;break}i!==void 0&&Object.keys(i).forEach(g=>{if(g!==s.key)return;const b=i[g];if(typeof b=="function")b(s);else{const{stop:P=!1,prevent:S=!1}=b;P&&s.stopPropagation(),S&&s.preventDefault(),b.handler(s)}})},u=()=>{(o===void 0||o.value)&&(oe("keydown",document,a),oe("keyup",document,d)),o!==void 0&&he(o,s=>{s?(oe("keydown",document,a),oe("keyup",document,d)):(ee("keydown",document,a),ee("keyup",document,d))})};return Oe()?(Fe(u),Te(()=>{(o===void 0||o.value)&&(ee("keydown",document,a),ee("keyup",document,d))})):u(),Ee(r)}var io=I({name:"ChevronRight",render(){return(()=>{const e=Me("6ab04425f4fcb756");return e[0]||(e[0]=q("svg",{viewBox:"0 0 16 16",fill:"none",xmlns:"http://www.w3.org/2000/svg"},[q("path",{d:"M5.64645 3.14645C5.45118 3.34171 5.45118 3.65829 5.64645 3.85355L9.79289 8L5.64645 12.1464C5.45118 12.3417 5.45118 12.6583 5.64645 12.8536C5.84171 13.0488 6.15829 13.0488 6.35355 12.8536L10.8536 8.35355C11.0488 8.15829 11.0488 7.84171 10.8536 7.64645L6.35355 3.14645C6.15829 2.95118 5.84171 2.95118 5.64645 3.14645Z",fill:"currentColor"})],-1))})()}});function ao(e){return o=>{o?e.value=o.$el:e.value=null}}var so=R("icon",`
 height: 1em;
 width: 1em;
 line-height: 1em;
 text-align: center;
 display: inline-block;
 position: relative;
 fill: currentColor;
`,[N("color-transition",{transition:"color .3s var(--n-bezier)"}),N("depth",{color:"var(--n-color)"},[$("svg",{opacity:"var(--n-opacity)",transition:"opacity .3s var(--n-bezier)"})]),$("svg",{height:"1em",width:"1em"})]);const lo={...re.props,depth:[String,Number],size:[Number,String],color:String,component:[Object,Function]},co=I({_n_icon__:!0,name:"Icon",inheritAttrs:!1,props:lo,setup(e){const{mergedClsPrefixRef:o,inlineThemeDisabled:r}=xe(e),n=re("Icon","-icon",so,je,e,o),i=m(()=>{const{depth:d}=e,{common:{cubicBezierEaseInOut:u},self:s}=n.value;if(d!==void 0){const{color:g,[`opacity${d}Depth`]:b}=s;return{"--n-bezier":u,"--n-color":g,"--n-opacity":b}}return{"--n-bezier":u,"--n-color":"","--n-opacity":""}}),a=r?Se("icon",m(()=>`${e.depth||"d"}`),i,e):void 0;return{mergedClsPrefix:o,mergedStyle:m(()=>{const{size:d,color:u}=e;return{fontSize:no(d),color:u}}),cssVars:r?void 0:i,themeClass:a?.themeClass,onRender:a?.onRender}},render(){const{$parent:e,depth:o,mergedClsPrefix:r,component:n,onRender:i,themeClass:a}=this;return e?.$options?._n_icon__&&ge("icon","don't wrap `n-icon` inside `n-icon`"),i?.(),J("i",G(this.$attrs,{role:"img",class:[`${r}-icon`,a,{[`${r}-icon--depth`]:o,[`${r}-icon--color-transition`]:o!==void 0}],style:[this.cssVars,this.mergedStyle]}),n?J(n):this.$slots.default?.())}}),me=ve("n-dropdown-menu"),ie=ve("n-dropdown"),ye=ve("n-dropdown-option");var Ne=I({name:"DropdownDivider",props:{clsPrefix:{type:String,required:!0}},render(){return l(),w("div",{class:C(`${this.clsPrefix}-dropdown-divider`)},null,2)}});function fe(e,o){return e.type==="submenu"||e.type===void 0&&e[o]!==void 0}function uo(e){return e.type==="group"}function Ce(e){return e.type==="divider"}function po(e){return e.type==="render"}function fo(e,o,r){const n=F(e.value);let i=null;return he(e,a=>{i!==null&&window.clearTimeout(i),a===!0?r&&!r.value?n.value=!0:i=window.setTimeout(()=>{n.value=!0},o):n.value=!1}),n}var Ke=I({name:"DropdownOption",props:{clsPrefix:{type:String,required:!0},tmNode:{type:Object,required:!0},parentKey:{type:[String,Number],default:null},placement:{type:String,default:"right-start"},props:Object,scrollable:Boolean},setup(e){const o=T(ie),{hoverKeyRef:r,keyboardKeyRef:n,lastToggledSubmenuKeyRef:i,pendingKeyPathRef:a,activeKeyPathRef:d,animatedRef:u,mergedShowRef:s,renderLabelRef:g,renderIconRef:b,labelFieldRef:P,childrenFieldRef:S,renderOptionRef:E,nodePropsRef:L,menuPropsRef:z}=o,M=T(ye,null),j=T(me),H=T(Pe),Q=m(()=>e.tmNode.rawNode),X=m(()=>{const{value:t}=S;return fe(e.tmNode.rawNode,t)}),ae=m(()=>{const{disabled:t}=e.tmNode;return t}),de=m(()=>{if(!X.value)return!1;const{key:t,disabled:c}=e.tmNode;if(c)return!1;const{value:y}=r,{value:B}=n,{value:ue}=i,{value:A}=a;return y!==null?A.includes(t):B!==null?A.includes(t)&&A[A.length-1]!==t:ue!==null?A.includes(t):!1}),se=m(()=>n.value===null&&!u.value),le=fo(de,300,se),ce=m(()=>!!M?.enteringSubmenuRef.value),V=F(!1);W(ye,{enteringSubmenuRef:V});function U(){V.value=!0}function Y(){V.value=!1}function Z(){const{parentKey:t,tmNode:c}=e;c.disabled||s.value&&(i.value=t,n.value=null,r.value=c.key)}function p(){const{tmNode:t}=e;t.disabled||s.value&&r.value!==t.key&&Z()}function f(t){if(e.tmNode.disabled||!s.value)return;const{relatedTarget:c}=t;c&&!we({target:c},"dropdownOption")&&!we({target:c},"scrollbarRail")&&(r.value=null)}function v(){const{value:t}=X,{tmNode:c}=e;s.value&&!t&&!c.disabled&&(o.doSelect(c.key,c.rawNode),o.doUpdateShow(!1))}return{labelField:P,renderLabel:g,renderIcon:b,siblingHasIcon:j.showIconRef,siblingHasSubmenu:j.hasSubmenuRef,menuProps:z,popoverBody:H,animated:u,mergedShowSubmenu:m(()=>le.value&&!ce.value),rawNode:Q,hasSubmenu:X,pending:ne(()=>{const{value:t}=a,{key:c}=e.tmNode;return t.includes(c)}),childActive:ne(()=>{const{value:t}=d,{key:c}=e.tmNode,y=t.findIndex(B=>c===B);return y===-1?!1:y<t.length-1}),active:ne(()=>{const{value:t}=d,{key:c}=e.tmNode,y=t.findIndex(B=>c===B);return y===-1?!1:y===t.length-1}),mergedDisabled:ae,renderOption:E,nodeProps:L,handleClick:v,handleMouseMove:p,handleMouseEnter:Z,handleMouseLeave:f,handleSubmenuBeforeEnter:U,handleSubmenuAfterEnter:Y}},render(){const{animated:e,rawNode:o,mergedShowSubmenu:r,clsPrefix:n,siblingHasIcon:i,siblingHasSubmenu:a,renderLabel:d,renderIcon:u,renderOption:s,nodeProps:g,props:b,scrollable:P}=this;let S=null;if(r){const M=this.menuProps?.(o,o.children);S=(j=>(l(),x(Ie,G({key:1},M,{clsPrefix:n,scrollable:this.scrollable,tmNodes:this.tmNode.children,parentKey:this.tmNode.key}),null,16,["clsPrefix","scrollable","tmNodes","parentKey"])))()}const E={class:[`${n}-dropdown-option-body`,this.pending&&`${n}-dropdown-option-body--pending`,this.active&&`${n}-dropdown-option-body--active`,this.childActive&&`${n}-dropdown-option-body--child-active`,this.mergedDisabled&&`${n}-dropdown-option-body--disabled`],onMousemove:this.handleMouseMove,onMouseenter:this.handleMouseEnter,onMouseleave:this.handleMouseLeave,onClick:this.handleClick},L=g?.(o),z=(l(),w("div",G({class:[`${n}-dropdown-option`,L?.class],"data-dropdown-option":!0},L),[k(()=>J("div",G(E,b),[(l(),w("div",{class:C([`${n}-dropdown-option-body__prefix`,i&&`${n}-dropdown-option-body__prefix--show-icon`])},[k(()=>[u?u(o):te(o.icon)])],2)),(l(),w("div",{"data-dropdown-option":!0,class:C(`${n}-dropdown-option-body__label`)},[d?(l(),w(D,{key:0},[k(()=>d(o))],64)):(l(),w(D,{key:1},[k(()=>te(o[this.labelField]??o.title))],64))],2)),(l(),w("div",{"data-dropdown-option":!0,class:C([`${n}-dropdown-option-body__suffix`,a&&`${n}-dropdown-option-body__suffix--has-submenu`])},[this.hasSubmenu?(l(),x(co,{key:0},{_:1,default:ke(()=>(l(),x(io)))})):k(()=>null)],2))])),this.hasSubmenu?(l(),x(Xe,{key:0},{default:()=>[(l(),x(Ze,null,{default:()=>(l(),w("div",{class:C(`${n}-dropdown-offset-container`)},[(l(),x(Qe,{show:this.mergedShowSubmenu,placement:this.placement,to:P&&this.popoverBody||void 0,teleportDisabled:!P},{default:()=>(l(),w("div",{class:C(`${n}-dropdown-menu-wrapper`)},[e?(l(),x(He,{key:0,onBeforeEnter:this.handleSubmenuBeforeEnter,onAfterEnter:this.handleSubmenuAfterEnter,name:"fade-in-scale-up-transition",appear:!0},{default:()=>S},1032,["onBeforeEnter","onAfterEnter"])):(l(),w(D,{key:1},[k(()=>S)],64))],2))},1032,["show","placement","to","teleportDisabled"]))],2))},1024))]},1024)):k(()=>null)],16));return s?s({node:z,option:o}):z}}),ho=I({name:"DropdownGroupHeader",props:{clsPrefix:{type:String,required:!0},tmNode:{type:Object,required:!0}},setup(){const{showIconRef:e,hasSubmenuRef:o}=T(me),{renderLabelRef:r,labelFieldRef:n,nodePropsRef:i,renderOptionRef:a}=T(ie);return{labelField:n,showIcon:e,hasSubmenu:o,renderLabel:r,nodeProps:i,renderOption:a}},render(){const{clsPrefix:e,hasSubmenu:o,showIcon:r,nodeProps:n,renderLabel:i,renderOption:a}=this,{rawNode:d}=this.tmNode,u=(l(),w("div",G({class:`${e}-dropdown-option`},n?.(d)),[q("div",{class:C(`${e}-dropdown-option-body ${e}-dropdown-option-body--group`)},[q("div",{"data-dropdown-option":!0,class:C([`${e}-dropdown-option-body__prefix`,r&&`${e}-dropdown-option-body__prefix--show-icon`])},[k(()=>te(d.icon))],2),q("div",{class:C(`${e}-dropdown-option-body__label`),"data-dropdown-option":!0},[i?(l(),w(D,{key:0},[k(()=>i(d))],64)):(l(),w(D,{key:1},[k(()=>te(d.title??d[this.labelField]))],64))],2),q("div",{class:C([`${e}-dropdown-option-body__suffix`,o&&`${e}-dropdown-option-body__suffix--has-submenu`]),"data-dropdown-option":!0},null,2)],2)],16));return a?a({node:u,option:d}):u}}),vo=I({name:"NDropdownGroup",props:{clsPrefix:{type:String,required:!0},tmNode:{type:Object,required:!0},parentKey:{type:[String,Number],default:null}},render(){const{tmNode:e,parentKey:o,clsPrefix:r}=this,{children:n}=e;return l(),w(D,null,[(l(),x(ho,{clsPrefix:r,tmNode:e,key:e.key},null,8,["clsPrefix","tmNode"])),k(()=>n?.map(i=>{const{rawNode:a}=i;return a.show===!1?null:Ce(a)?J(Ne,{clsPrefix:r,key:i.key}):i.isGroup?(ge("dropdown","`group` node is not allowed to be put in `group` node."),null):(l(),x(Ke,{clsPrefix:r,tmNode:i,parentKey:o,key:i.key},null,8,["clsPrefix","tmNode","parentKey"]))}))],64)}}),mo=I({name:"DropdownRenderOption",props:{tmNode:{type:Object,required:!0}},render(){const{rawNode:{render:e,props:o}}=this.tmNode;return J("div",o,[e?.()])}}),Ie=I({name:"DropdownMenu",props:{scrollable:Boolean,showArrow:Boolean,arrowStyle:[String,Object],clsPrefix:{type:String,required:!0},tmNodes:{type:Array,default:()=>[]},parentKey:{type:[String,Number],default:null}},setup(e){const{renderIconRef:o,childrenFieldRef:r}=T(ie);W(me,{showIconRef:m(()=>{const i=o.value;return e.tmNodes.some(a=>{if(a.isGroup)return a.children?.some(({rawNode:u})=>i?i(u):u.icon);const{rawNode:d}=a;return i?i(d):d.icon})}),hasSubmenuRef:m(()=>{const{value:i}=r;return e.tmNodes.some(a=>{if(a.isGroup)return a.children?.some(({rawNode:u})=>fe(u,i));const{rawNode:d}=a;return fe(d,i)})})});const n=F(null);return W(Ue,null),W(qe,null),W(Pe,n),{bodyRef:n}},render(){const{parentKey:e,clsPrefix:o,scrollable:r}=this,n=this.tmNodes.map(i=>{const{rawNode:a}=i;return a.show===!1?null:po(a)?(l(),x(mo,{tmNode:i,key:i.key},null,8,["tmNode"])):Ce(a)?(l(),x(Ne,{clsPrefix:o,key:i.key},null,8,["clsPrefix"])):uo(a)?(l(),x(vo,{clsPrefix:o,tmNode:i,parentKey:e,key:i.key},null,8,["clsPrefix","tmNode","parentKey"])):(l(),x(Ke,{clsPrefix:o,tmNode:i,parentKey:e,key:i.key,props:a.props,scrollable:r},null,8,["clsPrefix","tmNode","parentKey","props","scrollable"]))});return l(),w("div",{class:C([`${o}-dropdown-menu`,r&&`${o}-dropdown-menu--scrollable`]),ref:"bodyRef"},[r?(l(),x(Ve,{key:0,contentClass:`${o}-dropdown-menu__content`},{default:()=>n},1032,["contentClass"])):(l(),w(D,{key:1},[k(()=>n)],64)),this.showArrow?(l(),w(D,{key:2},[k(()=>Ye({clsPrefix:o,arrowStyle:this.arrowStyle,arrowClass:void 0,arrowWrapperClass:void 0,arrowWrapperStyle:void 0}))],64)):k(()=>null)],2)}}),bo=R("dropdown-menu",`
 transform-origin: var(--v-transform-origin);
 background-color: var(--n-color);
 border-radius: var(--n-border-radius);
 box-shadow: var(--n-box-shadow);
 position: relative;
 transition:
 background-color .3s var(--n-bezier),
 box-shadow .3s var(--n-bezier);
`,[We(),R("dropdown-option",`
 position: relative;
 `,[$("a",`
 text-decoration: none;
 color: inherit;
 outline: none;
 `,[$("&::before",`
 content: "";
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 `)]),R("dropdown-option-body",`
 display: flex;
 cursor: pointer;
 position: relative;
 height: var(--n-option-height);
 line-height: var(--n-option-height);
 font-size: var(--n-font-size);
 color: var(--n-option-text-color);
 transition: color .3s var(--n-bezier);
 `,[$("&::before",`
 content: "";
 position: absolute;
 top: 0;
 bottom: 0;
 left: 4px;
 right: 4px;
 transition: background-color .3s var(--n-bezier);
 border-radius: var(--n-border-radius);
 `),be("disabled",[N("pending",`
 color: var(--n-option-text-color-hover);
 `,[_("prefix, suffix",`
 color: var(--n-option-text-color-hover);
 `),$("&::before","background-color: var(--n-option-color-hover);")]),N("active",`
 color: var(--n-option-text-color-active);
 `,[_("prefix, suffix",`
 color: var(--n-option-text-color-active);
 `),$("&::before","background-color: var(--n-option-color-active);")]),N("child-active",`
 color: var(--n-option-text-color-child-active);
 `,[_("prefix, suffix",`
 color: var(--n-option-text-color-child-active);
 `)])]),N("disabled",`
 cursor: not-allowed;
 opacity: var(--n-option-opacity-disabled);
 `),N("group",`
 font-size: calc(var(--n-font-size) - 1px);
 color: var(--n-group-header-text-color);
 `,[_("prefix",`
 width: calc(var(--n-option-prefix-width) / 2);
 `,[N("show-icon",`
 width: calc(var(--n-option-icon-prefix-width) / 2);
 `)])]),_("prefix",`
 width: var(--n-option-prefix-width);
 display: flex;
 justify-content: center;
 align-items: center;
 color: var(--n-prefix-color);
 transition: color .3s var(--n-bezier);
 z-index: 1;
 `,[N("show-icon",`
 width: var(--n-option-icon-prefix-width);
 `),R("icon",`
 font-size: var(--n-option-icon-size);
 `)]),_("label",`
 white-space: nowrap;
 flex: 1;
 z-index: 1;
 `),_("suffix",`
 box-sizing: border-box;
 flex-grow: 0;
 flex-shrink: 0;
 display: flex;
 justify-content: flex-end;
 align-items: center;
 min-width: var(--n-option-suffix-width);
 padding: 0 8px;
 transition: color .3s var(--n-bezier);
 color: var(--n-suffix-color);
 z-index: 1;
 `,[N("has-submenu",`
 width: var(--n-option-icon-suffix-width);
 `),R("icon",`
 font-size: var(--n-option-icon-size);
 `)]),R("dropdown-menu","pointer-events: all;")]),R("dropdown-offset-container",`
 pointer-events: none;
 position: absolute;
 left: 0;
 right: 0;
 top: -4px;
 bottom: -4px;
 `)]),R("dropdown-divider",`
 transition: background-color .3s var(--n-bezier);
 background-color: var(--n-divider-color);
 height: 1px;
 margin: 4px 0;
 `),R("dropdown-menu-wrapper",`
 transform-origin: var(--v-transform-origin);
 width: fit-content;
 `),$(">",[R("scrollbar",`
 height: inherit;
 max-height: inherit;
 `)]),be("scrollable",`
 padding: var(--n-padding);
 `),N("scrollable",[_("content",`
 padding: var(--n-padding);
 `)])]);const wo={animated:{type:Boolean,default:!0},keyboard:{type:Boolean,default:!0},size:String,inverted:Boolean,placement:{type:String,default:"bottom"},onSelect:[Function,Array],options:{type:Array,default:()=>[]},menuProps:Function,showArrow:Boolean,renderLabel:Function,renderIcon:Function,renderOption:Function,nodeProps:Function,labelField:{type:String,default:"label"},keyField:{type:String,default:"key"},childrenField:{type:String,default:"children"},value:[String,Number]},yo=Object.keys(Re),go={...Re,...wo,...re.props};var Po=I({name:"Dropdown",inheritAttrs:!1,props:go,setup(e){const o=F(!1),r=to(K(e,"show"),o),n=m(()=>{const{keyField:p,childrenField:f}=e;return oo(e.options,{getKey(v){return v[p]},getDisabled(v){return v.disabled===!0},getIgnored(v){return v.type==="divider"||v.type==="render"},getChildren(v){return v[f]}})}),i=m(()=>n.value.treeNodes),a=F(null),d=F(null),u=F(null),s=m(()=>a.value??d.value??u.value??null),g=m(()=>n.value.getPath(s.value).keyPath),b=m(()=>n.value.getPath(e.value).keyPath),P=ne(()=>e.keyboard&&r.value);ro({keydown:{ArrowUp:{prevent:!0,handler:se},ArrowRight:{prevent:!0,handler:de},ArrowDown:{prevent:!0,handler:le},ArrowLeft:{prevent:!0,handler:ae},Enter:{prevent:!0,handler:ce},Escape:X}},P);const{mergedClsPrefixRef:S,inlineThemeDisabled:E,mergedComponentPropsRef:L}=xe(e),z=m(()=>e.size||L?.value?.Dropdown?.size||"medium"),M=re("Dropdown","-dropdown",bo,Je,e,S);W(ie,{labelFieldRef:K(e,"labelField"),childrenFieldRef:K(e,"childrenField"),renderLabelRef:K(e,"renderLabel"),renderIconRef:K(e,"renderIcon"),hoverKeyRef:a,keyboardKeyRef:d,lastToggledSubmenuKeyRef:u,pendingKeyPathRef:g,activeKeyPathRef:b,animatedRef:K(e,"animated"),mergedShowRef:r,nodePropsRef:K(e,"nodeProps"),renderOptionRef:K(e,"renderOption"),menuPropsRef:K(e,"menuProps"),doSelect:j,doUpdateShow:H}),he(r,p=>{!e.animated&&!p&&Q()});function j(p,f){const{onSelect:v}=e;v&&pe(v,p,f)}function H(p){const{"onUpdate:show":f,onUpdateShow:v}=e;f&&pe(f,p),v&&pe(v,p),o.value=p}function Q(){a.value=null,d.value=null,u.value=null}function X(){H(!1)}function ae(){U("left")}function de(){U("right")}function se(){U("up")}function le(){U("down")}function ce(){const p=V();p?.isLeaf&&r.value&&(j(p.key,p.rawNode),H(!1))}function V(){const{value:p}=n,{value:f}=s;return!p||f===null?null:p.getNode(f)??null}function U(p){const{value:f}=s,{value:{getFirstAvailableNode:v}}=n;let t=null;if(f===null){const c=v();c!==null&&(t=c.key)}else{const c=V();if(c){let y;switch(p){case"down":y=c.getNext();break;case"up":y=c.getPrev();break;case"right":y=c.getChild();break;case"left":y=c.getParent()}y&&(t=y.key)}}t!==null&&(a.value=null,d.value=t)}const Y=m(()=>{const{inverted:p}=e,f=z.value,{common:{cubicBezierEaseInOut:v},self:t}=M.value,{padding:c,dividerColor:y,borderRadius:B,optionOpacityDisabled:ue,[O("optionIconSuffixWidth",f)]:A,[O("optionSuffixWidth",f)]:ze,[O("optionIconPrefixWidth",f)]:_e,[O("optionPrefixWidth",f)]:$e,[O("fontSize",f)]:De,[O("optionHeight",f)]:Be,[O("optionIconSize",f)]:Ae}=t,h={"--n-bezier":v,"--n-font-size":De,"--n-padding":c,"--n-border-radius":B,"--n-option-height":Be,"--n-option-prefix-width":$e,"--n-option-icon-prefix-width":_e,"--n-option-suffix-width":ze,"--n-option-icon-suffix-width":A,"--n-option-icon-size":Ae,"--n-divider-color":y,"--n-option-opacity-disabled":ue};return p?(h["--n-color"]=t.colorInverted,h["--n-option-color-hover"]=t.optionColorHoverInverted,h["--n-option-color-active"]=t.optionColorActiveInverted,h["--n-option-text-color"]=t.optionTextColorInverted,h["--n-option-text-color-hover"]=t.optionTextColorHoverInverted,h["--n-option-text-color-active"]=t.optionTextColorActiveInverted,h["--n-option-text-color-child-active"]=t.optionTextColorChildActiveInverted,h["--n-prefix-color"]=t.prefixColorInverted,h["--n-suffix-color"]=t.suffixColorInverted,h["--n-group-header-text-color"]=t.groupHeaderTextColorInverted):(h["--n-color"]=t.color,h["--n-option-color-hover"]=t.optionColorHover,h["--n-option-color-active"]=t.optionColorActive,h["--n-option-text-color"]=t.optionTextColor,h["--n-option-text-color-hover"]=t.optionTextColorHover,h["--n-option-text-color-active"]=t.optionTextColorActive,h["--n-option-text-color-child-active"]=t.optionTextColorChildActive,h["--n-prefix-color"]=t.prefixColor,h["--n-suffix-color"]=t.suffixColor,h["--n-group-header-text-color"]=t.groupHeaderTextColor),h}),Z=E?Se("dropdown",m(()=>`${z.value[0]}${e.inverted?"i":""}`),Y,e):void 0;return{mergedClsPrefix:S,mergedTheme:M,mergedSize:z,tmNodes:i,mergedShow:r,handleAfterLeave:()=>{e.animated&&Q()},doUpdateShow:H,cssVars:E?void 0:Y,themeClass:Z?.themeClass,onRender:Z?.onRender}},render(){const e=(n,i,a,d,u)=>{const{mergedClsPrefix:s,menuProps:g}=this;this.onRender?.();const b=g?.(void 0,this.tmNodes.map(S=>S.rawNode))||{},P={ref:ao(i),class:[n,`${s}-dropdown`,`${s}-dropdown--${this.mergedSize}-size`,this.themeClass],clsPrefix:s,tmNodes:this.tmNodes,style:[...a,this.cssVars],showArrow:this.showArrow,arrowStyle:this.arrowStyle,scrollable:this.scrollable,onMouseenter:d,onMouseleave:u};return J(Ie,G(this.$attrs,P,b))},{mergedTheme:o}=this,r={show:this.mergedShow,theme:o.peers.Popover,themeOverrides:o.peerOverrides.Popover,internalOnAfterLeave:this.handleAfterLeave,internalRenderBody:e,onUpdateShow:this.doUpdateShow,"onUpdate:show":void 0};return l(),x(eo,Ge(this.$props,yo,r),{_:1,trigger:ke(()=>this.$slots.default?.())},16)}});export{io as C,Po as D,ao as c};
