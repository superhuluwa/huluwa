const fs=require('fs');const p='app-core.js';fs.writeFileSync(p,String.raw`
function bind(root){
 root.addEventListener('click',e=>{
  const el=e.target.closest('[data-screen-jump],[data-open],[data-course],[data-back],[data-action],[data-lesson],[data-toast],[data-task-tab],[data-more],[data-menu-delete],[data-menu-detail],[data-select-all],.select-all,.task-select-all');
  if(!el)return;
  if(el.dataset.screenJump){root.dataset.screen=el.dataset.screenJump;$$(root,'[data-screen-jump]').forEach(b=>b.classList.toggle('active',b===el));return}
  if(el.dataset.open){openCourse(root);return}
  if(el.dataset.course){if(root.classList.contains('edit-mode')){el.classList.toggle('selected');updateCount(root);return}openCourse(root);return}
  if(el.dataset.back){if(root.classList.contains('edit-mode')){setEdit(root,false);return}root.dataset.screen='main';return}
  if(el.dataset.action){setEdit(root,!root.classList.contains('edit-mode'));return}
  if(el.dataset.lesson){if(root.classList.contains('edit-mode')){el.classList.toggle('checked');updateCount(root,true);return}toast(root,'正在离线播放：'+(el.querySelector('b,.lesson-title,.title')?.textContent||'该课节'));return}
  if(el.dataset.taskTab){filterTask(root,el.dataset.taskTab);return}
  if(el.dataset.more){const pop=root.querySelector('.menu-pop');pop.classList.toggle('show');e.stopPropagation();return}
  if(el.dataset.menuDetail){root.querySelector('.menu-pop').classList.remove('show');openCourse(root);return}
  if(el.dataset.menuDelete){root.querySelector('.menu-pop').classList.remove('show');toast(root,'已从下载列表移除以释放空间');return}
  if(el.dataset.toast){toast(root,el.dataset.toast);return}
  if(el.classList.contains('select-all')||el.classList.contains('task-select-all')){selectAll(root);return}
 });
 root.addEventListener('click',e=>{
  const chip=e.target.closest('.filter-chip');if(chip){const row=chip.parentElement;$$(row,'.filter-chip').forEach(x=>x.classList.toggle('active',x===chip));toast(root,'已筛选：'+chip.textContent);}
 });
}
function openCourse(root){root.dataset.screen='detail';toast(root,'已打开离线课程目录')}
function setEdit(root,on){root.classList.toggle('edit-mode',on);if(!on){$$(root,'.selected').forEach(x=>x.classList.remove('selected'));$$(root,'.checked').forEach(x=>{if(x.matches('.lesson-row'))x.classList.remove('checked')})}updateCount(root)}
function updateCount(root,isLesson){const n=$$(root,isLesson?'.lesson-row.checked':'.course-item.selected,.course-row.selected,.ed-course.selected').length;const a=root.querySelector('.selected-count');if(a)a.textContent='已选 '+n+(isLesson?' 节':root.dataset.version==='editorial'?' 门课程':' 项');const b=root.querySelector('.task-count');if(b)b.textContent=n}
function selectAll(root){if(root.dataset.screen==='detail'){$$(root,'.lesson-row').forEach(x=>x.classList.add('checked'));updateCount(root,true)}else{$$(root,'.course-item,.course-row,.ed-course').forEach(x=>x.classList.add('selected'));updateCount(root)}toast(root,'已全选当前列表')}
function filterTask(root,tab){$$(root,'.segmented button').forEach(x=>x.classList.toggle('active',x.dataset.taskTab===tab));const q=root.querySelector('.task-doing-panel');const lib=root.querySelector('#task-library');if(q)q.hidden=tab==='done';if(lib)lib.hidden=tab==='doing';}
function toast(root,text){const t=root.querySelector('.toast');if(!t)return;t.textContent=text;t.classList.add('show');clearTimeout(root._t);root._t=setTimeout(()=>t.classList.remove('show'),1700)}
const $$=(root,sel)=>Array.from(root.querySelectorAll(sel));
`);
