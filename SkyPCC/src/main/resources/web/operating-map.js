(() => {
 'use strict';
 const {t,code}=PccI18n,NS='http://www.w3.org/2000/svg';
 const names=['railMap','operatingRoute','operatingDepot','operatingStatus','operatingRoutesList','depotSummary','depotName','berthCount','berthList','parkedCount','parkedList','operatingCount','operatingList','otherCount','otherList','layerInfrastructure','layerBalise','layerEnd','layerOrigin','layerSwitch','layerSal','layerRoutes','layerLive','routeEditorPanel','routeEditorStatus','enterRouteEditor','routeAuthDialog','routeToken','routeAuthResult','routeAuthConfirm','routeAuthClose','editRoute','routeBegin','routeExit','routeName','routeCreate','routeDepots','routeDirection','routeVisits','routeSave','routeValidate','routePublish','routeRetire','routeDelete','routeConflict','routeResolution','routeResolve','routeConflictDiff'];
 const ui=Object.fromEntries(names.map(id=>[id,document.getElementById(id)]));
 const commitActions=ui.routeSave.parentElement;ui.routeVisits.before(commitActions);commitActions.classList.add('route-commit-actions');
 const right=document.querySelector('.right-panel');right.insertBefore(ui.depotSummary,document.getElementById('inspectorEmpty'));
 function svg(tag,attrs={}){const e=document.createElementNS(NS,tag);for(const[k,v]of Object.entries(attrs))e.setAttribute(k,String(v));return e;}
 function el(tag,text,cls){const e=document.createElement(tag);if(text!=null)e.textContent=text;if(cls)e.className=cls;return e;}
 const legend=document.getElementById('operatingLegend');legend.classList.remove('map-legend');legend.style.display='contents';legend.hidden=false;document.getElementById('infrastructureLegend').append(legend);
 const overlay=svg('g',{id:'operatingLayer'}),routeLayer=svg('g',{id:'operatingRouteLayer'}),deviceLayer=svg('g',{id:'salDeviceLayer'}),previewLayer=svg('g',{id:'routePreviewLayer'});
 overlay.append(routeLayer,deviceLayer,previewLayer);ui.railMap.insertBefore(overlay,document.getElementById('trainLayer'));
 let report=null,receivedAt=0,clockOffset=0,catalog=null,catalogAt=0,labelScale=1.5,inspecting=false,token='',actor=crypto.randomUUID?crypto.randomUUID():uuid(),session=null,lineState=null,working=null,preview=[],loop=false,pending=false,authed=false,dirty=false,lastMessage='',lastReason=null,candidates=null;
 let minSequence=0,producer=null;
 const renderKeys=new WeakMap();
 function uuid(){const bytes=crypto.getRandomValues(new Uint8Array(16));bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;const h=[...bytes].map(b=>b.toString(16).padStart(2,'0')).join('');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;}
 const clone=v=>JSON.parse(JSON.stringify(v));
 const fresh=()=>report?.schemaVersion===1&&report.status==='AVAILABLE'&&Date.now()-receivedAt+clockOffset+receivedAt-Number(report.emittedAtMillis||0)<=3500;
 const catalogFresh=()=>catalog?.schemaVersion===1&&catalog.status==='AVAILABLE'&&Date.now()+clockOffset-Number(catalog.emittedAtMillis||0)<=3500&&String(catalog.graphRevision)===document.getElementById('graphRevision').textContent;
 const devices=()=>catalog?.devices||[];
 const lines=()=>catalog?.lines||[];
 const device=id=>devices().find(d=>d.id===id);
 function color(name){let h=0;for(const c of name)h=(h*31+c.charCodeAt(0))>>>0;return ['#56c7d9','#e3ad58','#77c989','#c58be4','#e17c99','#759fee'][h%6];}
 function populate(select,entries,all){const signature=JSON.stringify([entries,all]);if(renderKeys.get(select)===signature)return;renderKeys.set(select,signature);let previous=select.value;select.replaceChildren();if(all)select.add(new Option(all,''));for(const[v,label]of entries)select.add(new Option(label,v));select.value=entries.some(([v])=>v===previous)?previous:all?'':entries[0]?.[0]||'';}
 function statusLabel(status){return ({DRAFT:t('Route draft'),VALIDATED:t('Route validated'),PUBLISHED:t('Route published')})[status]||status;}
 function choices(){
  const options=lines().length?lines().map(l=>[l.name,`${l.name} · ${l.retired?t('Retired'):l.published?`v${l.publishedVersion}`:t('Route draft')}${l.conflict?' · '+t('Editing frozen'):''}`]):(report?.routes||[]).map(r=>[r.name,r.name]);
  populate(ui.operatingRoute,options,t('All operating routes'));populate(ui.editRoute,options);
  populate(ui.operatingDepot,(report?.depots||[]).map(d=>[d.name,d.name]),t('All depots'));
  const ds=catalog?.depots||[];const key=JSON.stringify(ds);if(renderKeys.get(ui.routeDepots)!==key){renderKeys.set(ui.routeDepots,key);ui.routeDepots.replaceChildren();for(const name of ds)ui.routeDepots.add(new Option(name,name));syncFields();}
 }
 function unit(){const r=ui.railMap.getBoundingClientRect(),v=ui.railMap.viewBox.baseVal;return Math.max(v.width/Math.max(1,r.width),v.height/Math.max(1,r.height),.01);}
 const screen=p=>({...p,x:p.x-.5,z:p.z-.5});
 function title(e,text){const child=svg('title');child.textContent=text;e.append(child);}
 function label(parent,p,text,u){const node=svg('text',{x:p.x+7*u,y:p.z-7*u,class:'op-label','font-size':12*u*labelScale/1.5,'stroke-width':3*u});node.textContent=text;parent.append(node);}
 function drawPath(parent,segment,name,status,selected=false){
  const pts=(segment.points||[]).map(screen);if(pts.length<2)return;const path=svg('polyline',{points:pts.map(p=>`${p.x},${p.z}`).join(' '),class:`op-route ${status.toLowerCase()}${segment.estimated?' estimated':''}${selected?' selected':''}`,stroke:color(name),'data-route':name,'data-status':status});
  title(path,name+(segment.estimated?' · '+t('Estimated draft connection'):''));path.onclick=()=>{ui.operatingRoute.value=name;candidates=null;render();};parent.append(path);
  if(selected){const u=unit(),step=Math.max(1,Math.floor(pts.length/12));for(let i=step;i<pts.length;i+=step){const a=pts[i-1],b=pts[i],dx=b.x-a.x,dz=b.z-a.z,n=Math.hypot(dx,dz);if(n<.001)continue;const x=dx/n,z=dz/n;parent.append(svg('path',{d:`M ${b.x-6*u*x+3*u*z},${b.z-6*u*z-3*u*x} L ${b.x},${b.z} L ${b.x-6*u*x-3*u*z},${b.z-6*u*z+3*u*x}`,class:'op-direction',stroke:color(name)}));}}
 }
 function render(){
  const live=fresh(),u=unit(),selection=ui.operatingRoute.value;routeLayer.replaceChildren();deviceLayer.replaceChildren();previewLayer.replaceChildren();
  routeLayer.classList.toggle('op-stale',!live);ui.operatingRoutesList.replaceChildren();
  ui.operatingStatus.textContent=live?t('{n} operating routes',{n:(report.routes||[]).length}):t('Operating data unavailable');
  for(const route of report?.routes||[]){
   if(selection&&route.name!==selection)continue;if(candidates&&!candidates.has(route.name))continue;
   const row=el('button',`${route.name} · ${statusLabel(route.status)}`,'op-route-row');row.onclick=()=>{ui.operatingRoute.value=route.name;ui.editRoute.value=route.name;candidates=null;render();};ui.operatingRoutesList.append(row);
   for(const segment of route.segments||[])drawPath(routeLayer,segment,route.name,route.status,selection===route.name);
  }
  // The RailGraph canvas and its camera are shared by every layer.
  for(const d of devices()){
   if(d.type==='edge'||!['station','depot','awake','property','v_target','message'].includes(d.type))continue;
   const p=d.position?screen(d.position):null;if(!p)continue;const marker=svg('g',{class:`op-device op-${d.type}${d.valid?'':' invalid'}${d.type==='depot'&&ui.operatingDepot.value&&d.name.startsWith(ui.operatingDepot.value+'/')?' selected':''}`,'data-device-id':d.id});
   if(d.type==='depot')marker.append(svg('rect',{x:p.x-4*u,y:p.z-4*u,width:8*u,height:8*u,class:'op-berth-box'}));else marker.append(svg('circle',{cx:p.x,cy:p.z,r:3.5*u,class:'op-stop'}));
   title(marker,`${d.name} · ${d.type}\n${d.id}`);label(marker,p,d.name||d.type,u);marker.onclick=()=>pick('device',d.id);deviceLayer.append(marker);
  }
  // Backward-compatible display when an older provider has no device catalog.
  if(!catalog?.devices)for(const depot of report?.depots||[])for(const berth of depot.berths||[]){const p=screen(berth.position);const marker=svg('g',{class:`op-berth${ui.operatingDepot.value===depot.name?' selected':''}`,'data-berth-id':berth.id});marker.append(svg('rect',{x:p.x-4*u,y:p.z-4*u,width:8*u,height:8*u,class:'op-berth-box'}));label(marker,p,berth.name,u);deviceLayer.append(marker);}
  if(authed&&working){for(const s of preview)drawPath(previewLayer,{...s,estimated:!loop||s.estimated},working.name,loop?(lineState?.validated&&!dirty?'VALIDATED':'CLOSED_DRAFT'):'DRAFT',true);working.visits.forEach((v,i)=>{const d=device(v.deviceId);if(!d)return;const p=screen(d.position);previewLayer.append(svg('circle',{cx:p.x,cy:p.z,r:5*u,class:'op-visit'}));label(previewLayer,p,String(i+1)+(v.reverse?' ↶':''),u);});}
  applyLayers();summary(live);refreshEditor();
 }
 const infrastructureTypes={balise:'layerBalise',end:'layerEnd',origin:'layerOrigin',switch:'layerSwitch'};
 function infrastructureVisible(type){return ui.layerInfrastructure.checked&&(!infrastructureTypes[type]||ui[infrastructureTypes[type]].checked);}
 function applyLayers(){
  for(const e of document.querySelectorAll('[data-infrastructure-type]'))e.style.display=infrastructureVisible(e.dataset.infrastructureType)?'':'none';
  for(const id of ['nodeLayer','labelLayer','switchLayer'])document.getElementById(id).style.display=ui.layerInfrastructure.checked?'':'none';
  deviceLayer.style.display=ui.layerSal.checked?'':'none';routeLayer.style.display=ui.layerRoutes.checked?'':'none';previewLayer.style.display=ui.layerRoutes.checked?'':'none';legend.hidden=!ui.layerRoutes.checked;
  for(const id of ['trainLayer','authorityLayer'])document.getElementById(id).style.display=ui.layerLive.checked?'':'none';
  for(const edge of document.querySelectorAll('#edgeLayer .rail-edge')){if(!ui.layerLive.checked)edge.style.setProperty('stroke',edge.getAttribute('stroke'),'important');else edge.style.removeProperty('stroke');}
 }
 function summary(live){
  const depot=(report?.depots||[]).find(d=>d.name===ui.operatingDepot.value);ui.depotSummary.hidden=!depot||inspecting||authed;if(!depot)return;ui.depotName.textContent=depot.name;
  for(const[id,items]of [['berthCount',depot.berths],['parkedCount',depot.parkedTrains],['operatingCount',depot.runningTrains],['otherCount',depot.otherTrains]])ui[id].textContent=live?String(items.length):'--';
  function list(node,items,train){const sig=JSON.stringify([PccI18n.language,live,items.map(i=>[i.id,i.name,i.phase,i.fresh,i.routeVersion])]);if(renderKeys.get(node)===sig)return;renderKeys.set(node,sig);node.replaceChildren();if(!live){node.append(el('li',t('Operating data unavailable')));return;}if(!items.length)node.append(el('li',t('None')));for(const item of items){const li=el('li');const b=el('button',item.name);b.onclick=()=>train?window.dispatchEvent(new CustomEvent('pcc-select-train',{detail:{id:item.id}})):fit([item.position]);li.append(b);if(train)li.append(el('small',[item.line,item.routeVersion?'v'+item.routeVersion:'',item.phase,!item.fresh?t('Unknown'):''].filter(Boolean).join(' · ')));node.append(li);}}
  list(ui.berthList,depot.berths,false);list(ui.parkedList,depot.parkedTrains,true);list(ui.operatingList,depot.runningTrains,true);list(ui.otherList,depot.otherTrains,true);
 }
 function fit(points){window.dispatchEvent(new CustomEvent('pcc-fit-points',{detail:{points:points.map(screen)}}));}
 function onPath(p,route){for(const s of route.segments||[])for(let i=1;i<s.points.length;i++){const a=s.points[i-1],b=s.points[i];if(a.world!==p.world)continue;const dx=b.x-a.x,dz=b.z-a.z,n=dx*dx+dz*dz,v=n?Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.z-a.z)*dz)/n)):0;if(Math.hypot(p.x-a.x-v*dx,p.z-a.z-v*dz)<1.5)return true;}return false;}
 function pick(kind,id){
  const d=device(kind==='edge'?'edge:'+id:id);if(!d)return authed;
  if(authed&&session&&working){
   if(pending||lineState?.conflict||!catalogFresh())return true;
   if(!['station','balise','switch','origin','end','edge'].includes(d.type)){message(t('Choose a track device or matching station'));return true;}
   if(d.type==='station'&&d.route!==working.name){message(t('Station belongs to another route'));return true;}
   mutate(w=>w.visits.push({deviceId:d.id,reverse:false,dwellSeconds:d.type==='station'?d.number:null}));return true;
  }
  if(d.type==='depot'){ui.operatingDepot.value=d.name.split('/')[0];render();return false;}
  const matches=lines().filter(l=>[l.draft,l.published].some(def=>def?.visits.some(v=>v.deviceId===d.id))||(report?.routes||[]).some(r=>r.name===l.name&&onPath(d.position,r)));
  if(matches.length===1){ui.operatingRoute.value=matches[0].name;ui.editRoute.value=matches[0].name;candidates=null;}
  else if(matches.length>1){candidates=new Set(matches.map(l=>l.name));if(!candidates.has(ui.operatingRoute.value))ui.operatingRoute.value='';message(t('Several routes use this device; choose a route'));}
  render();return authed;
 }
 function message(text,reason=null){lastMessage=text;lastReason=reason;ui.routeEditorStatus.textContent=text;}
 function syncFields(){if(!working){ui.routeVisits.replaceChildren();return;}for(const opt of ui.routeDepots.options)opt.selected=working.depots.includes(opt.value);ui.routeDirection.value=Math.abs(working.dx)>=Math.abs(working.dz)?working.dx>=0?'east':'west':working.dz>=0?'south':'north';renderVisits();}
 function renderVisits(){
  ui.routeVisits.replaceChildren();if(!working)return;const disabled=pending||lineState?.conflict||!session||!catalogFresh();
  working.visits.forEach((visit,i)=>{const d=device(visit.deviceId),li=el('li');li.append(el('span',d?`${d.name} · ${d.type}`:visit.deviceId));li.title=visit.deviceId;
   if(d?.type==='station'){const dwell=el('input');dwell.type='number';dwell.min='0';dwell.max='86400';dwell.value=visit.dwellSeconds??d.number;dwell.title=t('Dwell seconds');dwell.disabled=disabled;dwell.onchange=()=>mutate(w=>w.visits[i].dwellSeconds=Number(dwell.value));li.append(dwell);const reverse=el('input');reverse.type='checkbox';reverse.checked=visit.reverse;reverse.disabled=disabled;reverse.onchange=()=>mutate(w=>w.visits[i].reverse=reverse.checked);const label=el('label');label.append(reverse,document.createTextNode(t('Reverse after stop')));li.append(label);}
   for(const[caption,action]of [['↑',w=>{if(i>0)[w.visits[i-1],w.visits[i]]=[w.visits[i],w.visits[i-1]];}],['↓',w=>{if(i+1<w.visits.length)[w.visits[i+1],w.visits[i]]=[w.visits[i],w.visits[i+1]];}],['×',w=>w.visits.splice(i,1)]]){const b=el('button',caption);b.disabled=disabled;b.onclick=()=>mutate(action);li.append(b);}ui.routeVisits.append(li);
  });
 }
 function refreshEditor(){
  ui.routeEditorPanel.hidden=!authed;ui.enterRouteEditor.disabled=authed;
  if(!authed)return;
  if(lineState){const current=lines().find(l=>l.name===lineState.name);if(current)lineState=current;}
  const frozen=!!lineState?.conflict,unavailable=!catalogFresh();
  ui.routeEditorStatus.textContent=frozen?t('Editing frozen'):unavailable?t('Operating data unavailable'):lastMessage||t('Select a route or create a draft');
  ui.routeConflict.hidden=!frozen;
  for(const id of ['routeSave','routeValidate','routePublish','routeRetire','routeDelete'])ui[id].disabled=pending||frozen||unavailable||!session;
  ui.routeDirection.disabled=pending||frozen||unavailable;ui.routeDepots.disabled=pending||frozen||unavailable;ui.routeBegin.disabled=pending||frozen||unavailable;ui.routeCreate.disabled=pending||unavailable;ui.routeResolve.disabled=pending||unavailable;
  ui.routeSave.dataset.dirty=String(dirty);
  if(frozen){const options=[['DISCARD',t('Discard both pending edits')],...lineState.participants.map(p=>[p.actor,`${p.source} · ${p.actor}`])];populate(ui.routeResolution,options);const key=JSON.stringify(lineState.participants);if(renderKeys.get(ui.routeConflictDiff)!==key){renderKeys.set(ui.routeConflictDiff,key);ui.routeConflictDiff.replaceChildren();for(const party of lineState.participants){const section=el('details');section.append(el('summary',party.actor));const pre=el('pre',JSON.stringify(party.working,null,2));section.append(pre);ui.routeConflictDiff.append(section);}}}
  // Refresh disabled controls without replacing inputs while a user is typing.
  for(const input of ui.routeVisits.querySelectorAll('input,button'))input.disabled=pending||frozen||unavailable||!session;
 }
 async function request(action,definition=null,decision=null,line=null){
  const name=line??lineState?.name??ui.editRoute.value??'';
  const payload={requestId:uuid(),action,line:name,actor,session,expectedRevision:lineState?.name===name?lineState.revision:(lines().find(l=>l.name===name)?.revision||0),definition,decision};
  const deadline=Date.now()+35000;
  while(Date.now()<deadline){const response=await fetch('/api/v10/routes',{method:'POST',cache:'no-store',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify(payload)});const r=await response.json();if(response.status===202||r.status==='PENDING'){await new Promise(resolve=>setTimeout(resolve,200));continue;}if(response.status===403){authed=false;token='';session=null;throw Error(t('Control authentication required'));}const currentReply=Number(r.workspaceRevision||0)>=minSequence;minSequence=Math.max(minSequence,Number(r.workspaceRevision||0));if(r.line&&currentReply){lineState=r.line;const index=catalog?.lines.findIndex(l=>l.name===r.line.name);if(index>=0)catalog.lines[index]=r.line;}
   if(r.status!=='APPLIED'){if(r.reason==='EDIT_SESSION_REQUIRED')session=null;message(code(r.reason),r.reason);refreshEditor();throw Error(code(r.reason));}
   if(currentReply){if(action!=='AUTH')session=r.session??null;if(r.preview)preview=r.preview;loop=!!r.closed;}if(action!=='HEARTBEAT')message(code(r.reason),r.reason);return r;
  }throw Error(t('Edit unconfirmed; inspect saved state before retrying'));
 }
 async function run(action){if(pending)return;pending=true;refreshEditor();try{return await action();}catch(error){message(error.message);}finally{pending=false;refreshEditor();}}
 async function mutate(change){if(!session||pending||lineState?.conflict||!catalogFresh())return;const before=clone(working);await run(async()=>{change(working);try{await request('UPDATE',working);dirty=true;}catch(error){working=before;throw error;}finally{syncFields();render();}});}
 async function loadCatalog(){try{const response=await fetch('/api/v10/routes',{cache:'no-store'});if(!response.ok)throw Error();const incoming=await response.json();if(incoming?.producerSession&&producer&&incoming.producerSession!==producer){minSequence=0;session=null;working=null;preview=[];}if(incoming?.producerSession)producer=incoming.producerSession;if(Number(incoming?.workspaceRevision||0)<minSequence)return;minSequence=Math.max(minSequence,Number(incoming?.workspaceRevision||0));catalog=incoming;catalogAt=Date.now();choices();render();}catch(_){catalogAt=0;catalog=null;refreshEditor();}}
 ui.enterRouteEditor.onclick=()=>{ui.routeToken.value='';ui.routeAuthResult.textContent='';ui.routeAuthDialog.showModal();};
 ui.routeAuthClose.onclick=()=>ui.routeAuthDialog.close();
 ui.routeAuthDialog.addEventListener('close',()=>{ui.routeToken.value='';if(!authed)token='';});
 ui.routeAuthConfirm.onclick=async()=>{if(pending)return;ui.routeAuthConfirm.disabled=true;token=ui.routeToken.value;ui.routeToken.value='';try{await request('AUTH',null,null,'');authed=true;ui.routeAuthDialog.close();working=null;lineState=null;session=null;window.dispatchEvent(new CustomEvent('pcc-select-train',{detail:{id:null}}));message(t('Select a route or create a draft'));render();}catch(error){token='';ui.routeAuthResult.textContent=error.message;}finally{ui.routeAuthConfirm.disabled=false;}};
 ui.routeBegin.onclick=()=>run(async()=>{if(session&&lineState?.name!==ui.editRoute.value)await request('CANCEL');session=null;const r=await request('BEGIN',null,null,ui.editRoute.value);working=clone(r.line.participants.find(p=>p.actor==='PCC:'+actor)?.working||r.line.draft);dirty=JSON.stringify(working)!==JSON.stringify(r.line.draft);ui.operatingRoute.value=r.line.name;syncFields();render();if(preview.length)fit(preview.flatMap(s=>s.points));});
 ui.editRoute.onchange=()=>{if(session&&lineState&&ui.editRoute.value!==lineState.name){ui.editRoute.value=lineState.name;message(t('Close this edit before selecting another route'));}else{lineState=lines().find(l=>l.name===ui.editRoute.value)||null;refreshEditor();}};
 ui.routeCreate.onclick=()=>run(async()=>{if(session)throw Error(t('Close this edit before selecting another route'));const [dx,dz]=({east:[1,0],west:[-1,0],north:[0,-1],south:[0,1]})[ui.routeDirection.value];const d={name:ui.routeName.value.trim(),dx,dz,visits:[],depots:[...ui.routeDepots.selectedOptions].map(o=>o.value)};const r=await request('CREATE',d,null,d.name);working=clone(r.line.draft);dirty=false;await loadCatalog();ui.editRoute.value=d.name;ui.operatingRoute.value=d.name;syncFields();render();});
 ui.routeDepots.onchange=()=>{if(session)mutate(w=>w.depots=[...ui.routeDepots.selectedOptions].map(o=>o.value));};
 ui.routeDirection.onchange=()=>mutate(w=>{[w.dx,w.dz]=({east:[1,0],west:[-1,0],north:[0,-1],south:[0,1]})[ui.routeDirection.value];});
 for(const[id,action]of [['routeSave','SAVE'],['routeValidate','VALIDATE'],['routePublish','PUBLISH'],['routeRetire','RETIRE'],['routeDelete','DELETE']])ui[id].onclick=()=>run(async()=>{if(['RETIRE','DELETE'].includes(action)&&!confirm(t('Confirm route retirement or deletion?')))return;const r=await request(action);if(action==='SAVE')dirty=false;if(action==='DELETE'){working=null;lineState=null;session=null;preview=[];}await loadCatalog();render();});
 ui.routeResolve.onclick=()=>run(async()=>{const r=await request('RESOLVE',null,ui.routeResolution.value);working=r.session?clone(r.line.participants.find(p=>p.actor==='PCC:'+actor)?.working||r.line.draft):null;dirty=!!working&&JSON.stringify(working)!==JSON.stringify(r.line.draft);await loadCatalog();syncFields();render();});
 ui.routeExit.onclick=()=>run(async()=>{try{if(session&&!lineState?.conflict)await request('CANCEL');}finally{authed=false;token='';session=null;lineState=null;working=null;preview=[];dirty=false;syncFields();render();}});
 function filterChanged(){applyLayers();window.dispatchEvent(new Event('pcc-infrastructure-filter'));}
 ui.layerInfrastructure.onchange=()=>{for(const id of Object.values(infrastructureTypes))ui[id].checked=ui.layerInfrastructure.checked;ui.layerInfrastructure.indeterminate=false;filterChanged();};
 for(const id of Object.values(infrastructureTypes))ui[id].onchange=()=>{const count=Object.values(infrastructureTypes).filter(id=>ui[id].checked).length;ui.layerInfrastructure.checked=count>0;ui.layerInfrastructure.indeterminate=count>0&&count<4;filterChanged();};
 for(const id of ['layerSal','layerRoutes','layerLive'])ui[id].onchange=applyLayers;
 ui.operatingRoute.onchange=()=>{candidates=null;render();};ui.operatingDepot.onchange=()=>{window.dispatchEvent(new CustomEvent('pcc-select-train',{detail:{id:null}}));const d=(report?.depots||[]).find(d=>d.name===ui.operatingDepot.value);if(d)fit(d.berths.map(b=>b.position));render();};
 window.addEventListener('pcc-view',render);
 window.addEventListener('beforeunload',()=>{token='';});
 setInterval(loadCatalog,1000);setInterval(()=>{if(authed&&session&&!pending&&!lineState?.conflict)run(()=>request('HEARTBEAT'));},20000);
 window.PccOperatingMap={infrastructureVisible,refreshLayers:applyLayers,active:()=>false,hasDepot:()=>!!ui.operatingDepot.value,hasPanel:()=>authed||!!ui.operatingDepot.value,pick,fit:()=>{},zoom:()=>{},inspect(value){inspecting=value;summary(fresh());},scale(value){labelScale=value;render();},refreshLanguage(){if(lastReason)message(code(lastReason),lastReason);choices();syncFields();render();},update(value,serverNow){report=value;receivedAt=Date.now();clockOffset=Number(serverNow||Date.now())-Date.now();choices();render();}};
 loadCatalog();
})();
