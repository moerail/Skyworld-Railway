const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const web=path.resolve(__dirname,'../src/main/resources/web');
const out=path.resolve(__dirname,'../../Common/target');fs.mkdirSync(out,{recursive:true});
const profile=fs.mkdtempSync(path.join(out,'operating-browser-'));
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const p=(x,z=0)=>({world:'world',x,y:64,z});
let unavailable=false,stale=false,validationFailure=false;
const secret='test-only-editor-token-0000000000000000',calls=[],receipts=new Map();
const base={name:'L1',dx:1,dz:0,depots:['main'],dispatchPolicy:{supplyMode:'GLOBAL',preferenceSeconds:30,maxPickupSeconds:120},visits:[{deviceId:'station:s0',reverse:false,dwellSeconds:10,visitId:'00000000-0000-4000-8000-000000000001'},{deviceId:'station:s1',reverse:false,dwellSeconds:10,visitId:'00000000-0000-4000-8000-000000000002'},{deviceId:'station:s0',reverse:false,dwellSeconds:10}]};
let line={name:'L1',revision:1,publishedVersion:1,draft:structuredClone(base),published:structuredClone(base),validated:false,retired:false,conflict:false,participants:[]};
let currentActor='',session=null,workspaceSequence=0,oldCatalog=null;
const devices=[{id:'a',type:'balise',name:'Engineering 777',position:p(0),route:'',number:0,valid:true},{id:'b',type:'switch',name:'Switch 301',position:p(100),route:'',number:0,valid:true},
{id:'station:s0',type:'station',name:'SharedStart',position:p(0,5),route:'',number:10,valid:true},{id:'station:s1',type:'station',name:'SharedSecond',position:p(100,5),route:'',number:10,valid:true},
...[1,2,3].map(i=>({id:'sal:d'+i,type:'depot',name:'main/L'+i,position:p(20+i*10,15),route:'',number:0,valid:true}))];
const points=[p(0,5),p(100,5),p(100,45),p(0,45),p(0,5)];
function operating(){return {schemaVersion:1,status:unavailable?'UNAVAILABLE':'AVAILABLE',producerSession:'test',graphRevision:1,emittedAtMillis:Date.now()-(stale?10000:0),routes:['DRAFT','VALIDATED','PUBLISHED'].map((status,i)=>({name:'L'+(i+1),status,reason:'',segments:[{points:points.map(p=>({...p,z:p.z+i*50})),estimated:i===0}],stops:[]})),depots:[{name:'main',berths:[1,2,3].map(i=>({id:'main/L'+i,name:'L'+i,position:p(20+i*10,15),valid:true,parkedTrains:[],reservedBy:null,geometry:{Q_TP_Alignment:1,D_Location:20+i*10,Q_Stop_Location_Tolerance:.5,usableLengthBlocks:30,maxTrainLengthBlocks:29,access:i===1?"SINGLE_ENDED":"DOUBLE_ENDED",reason:"",boundary:[p(20+i*10-15,15),p(20+i*10,17),p(20+i*10+15,15)]}})),parkedTrains:[],runningTrains:[],otherTrains:[]}],dispatch:[{id:"request",line:"L1",station:"SharedStart",status:"ASSIGNED",train:"planned",depot:"main",pickupSeconds:38,cost:68,reason:"shared preference test"}],trains:[{id:"planned",home:"main",stoppingCenter:p(42,15),stoppingOffsetBlocks:2}]};}
const graph={revision:1,nodes:[{id:'o',name:'Origin',type:'origin',rail:p(-10)},{id:'z',name:'End',type:'end',rail:p(110)},{id:'a',name:'Engineering 777',type:'balise',rail:p(0)},{id:'b',name:'Switch 301',type:'switch',rail:p(100),ports:{common:'west',straight:'east',diverging:'south'},state:'straight'}],edges:[{id:'e',from:'a',to:'b',distanceMeters:100,path:[{...p(0),distanceMeters:0},{...p(100),distanceMeters:100}]}]};
const server=http.createServer(async(req,res)=>{
 const json=(v,status=200)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(v));};
 if(req.url==='/api/v5/config')return json({updateMode:'poll',pollIntervalMillis:250,controlEnabled:true,requireHttpsForControl:false});
 if(req.url==='/api/v5/graph')return json(graph);
 if(req.url==='/api/v5/trains')return json({schemaVersion:5,serverTimeMillis:Date.now(),serviceStatus:'AVAILABLE',graphRevision:1,switchStates:{b:'straight'},trains:[],operatingNetwork:operating(),operationalEvents:{status:'AVAILABLE',session:'diagnostics',latestSequence:1,evictedCount:0,events:[{session:'diagnostics',sequence:1,emittedAtMillis:Date.now(),source:'SkyATO-Lite',type:'MA_UNAVAILABLE',trainId:'planned',trainName:'planned',reason:'ROUTE_DEVIATION',details:{detail:'STA定位不确定：rail=693,70,-707 <img src=x onerror=window.eventXss=1>',position:'world 693,70,-707',phase:'PICKUP'}}]}});
 if(req.url==='/test/conflict'){workspaceSequence++;line.conflict=true;line.participants.push({actor:'GAME:other',source:'GAME',working:structuredClone(line.draft)});return json({});}
 if(req.url==='/api/v10/routes'){
  if(req.method==='GET'&&oldCatalog){const stale=oldCatalog;oldCatalog=null;return json(stale);}
  if(req.method==='GET')return json({workspaceRevision:workspaceSequence,schemaVersion:1,status:unavailable?'UNAVAILABLE':'AVAILABLE',producerSession:'test',graphRevision:1,emittedAtMillis:Date.now(),lines:[line],devices,depots:['main']});
  if(req.headers.authorization!=='Bearer '+secret)return json({status:'REJECTED',reason:'CONTROL_AUTH_REQUIRED'},403);
  let body='';for await(const chunk of req)body+=chunk;const r=JSON.parse(body);calls.push(r.action);
  if(receipts.has(r.requestId))return json(receipts.get(r.requestId));
  let status='APPLIED',reason=r.action;
  if(r.action==='AUTH')reason='AUTHENTICATED';
  else if(r.action==='BEGIN'){currentActor='PCC:'+r.actor;session='test-session';line.participants=[{actor:currentActor,source:'PCC',working:structuredClone(line.draft)}];}
  else if(r.action==='RESOLVE'){line.conflict=false;line.participants=[];session=null;reason='RESOLVED';}
  else if(line.conflict){status='CONFLICT';reason='EDIT_FROZEN';}
  else if(r.action==='UPDATE'){line.participants[0].working=r.definition;}
  else if(r.action==='SAVE'){oldCatalog={schemaVersion:1,status:'AVAILABLE',producerSession:'test',graphRevision:1,emittedAtMillis:Date.now(),lines:[structuredClone(line)],devices,depots:['main'],workspaceRevision:workspaceSequence};line.draft=structuredClone(line.participants[0].working);line.revision++;line.validated=false;}
  else if(r.action==='VALIDATE'){if(validationFailure){status='REJECTED';reason='存在多条路径，请追加道岔/应答器绑定';}else if(r.expectedRevision!==line.revision){status='CONFLICT';reason='STALE_REVISION_FROZEN';line.conflict=true;}else line.validated=true;}
  else if(r.action==='PUBLISH'){line.published=structuredClone(line.draft);line.publishedVersion++;}
  else if(r.action==='RETIRE')line.retired=true;
  else if(r.action==='CANCEL'){session=null;line.participants=[];}
  const working=line.participants[0]?.working||line.draft,closed=working.visits.at(-1)?.deviceId.startsWith('station:');
  if(r.action!=='AUTH')workspaceSequence++;
  const issues=validationFailure&&r.action==='VALIDATE'?[{fromIndex:2,toIndex:3,fromDeviceId:'station:s1',toDeviceId:'station:s0',fromLabel:'SharedSecond',toLabel:'SharedStart',detail:reason}]:[];
  const reply={workspaceRevision:workspaceSequence,requestId:r.requestId,status,reason,session,line,graphRevision:1,preview:[{points,estimated:false}],closed,issues};
  const fixed=structuredClone(reply);receipts.set(r.requestId,fixed);return json(fixed);
 }
 const name=req.url==='/'?'index.html':req.url.replace('/assets/','');
 if(!['index.html','app.js','operating-map.js','styles.css','i18n.js','day_logo.png','night_logo.png'].includes(name)){res.writeHead(404);res.end();return;}
 res.writeHead(200,{'Content-Type':name.endsWith('.js')?'text/javascript':name.endsWith('.css')?'text/css':name.endsWith('.png')?'image/png':'text/html'});res.end(fs.readFileSync(path.join(web,name)));
});

async function connect(url){
 const socket=new WebSocket(url),pending=new Map(),errors=[];let sequence=0;
 await new Promise((resolve,reject)=>{socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',reject,{once:true});});
 socket.addEventListener('message',event=>{const m=JSON.parse(event.data);if(m.id){const waiter=pending.get(m.id);if(waiter){pending.delete(m.id);clearTimeout(waiter.timer);m.error?waiter.reject(Error(JSON.stringify(m.error))):waiter.resolve(m.result);}}if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.exception?.description||m.params.exceptionDetails.text);});
 function call(method,params={}){return new Promise((resolve,reject)=>{const id=++sequence;const timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout '+method+' '+(params.type||'')));},15000);pending.set(id,{resolve,reject,timer});socket.send(JSON.stringify({id,method,params}));});}
 async function evaluate(expression){const r=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;}
 return {call,evaluate,errors,close:()=>socket.close()};
}

(async()=>{
 let browser,cdp;
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try {
  const executable=process.env.PCC_BROWSER||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';assert(fs.existsSync(executable),'Set PCC_BROWSER to a Chromium executable');
  browser=spawn(executable,['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=0','--user-data-dir='+profile,'about:blank'],{windowsHide:true,stdio:'ignore'});
  const portFile=path.join(profile,'DevToolsActivePort');for(let i=0;i<150&&!fs.existsSync(portFile);i++)await delay(100);assert(fs.existsSync(portFile),'Headless browser did not start');
  const port=fs.readFileSync(portFile,'utf8').split('\n')[0];
  const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT'})).json();cdp=await connect(target.webSocketDebuggerUrl);
  await cdp.call('Page.enable');await cdp.call('Runtime.enable');await cdp.call('Emulation.setDeviceMetricsOverride',{width:1440,height:900,deviceScaleFactor:1,mobile:false});
  await cdp.call('Page.navigate',{url:`http://127.0.0.1:${server.address().port}`});
  async function wait(expression){for(let i=0;i<100;i++){if(await cdp.evaluate(expression))return;await delay(100);}throw Error('Condition timed out: '+expression);}
  const select=(id,value)=>cdp.evaluate(`document.getElementById(${JSON.stringify(id)}).value=${JSON.stringify(value)};document.getElementById(${JSON.stringify(id)}).dispatchEvent(new Event('change',{bubbles:true}));true`);
  async function pointerClick(selector,holdMillis=0){
    const point=await cdp.evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)}),r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()`);
    await cdp.call('Input.dispatchMouseEvent',{type:'mouseMoved',...point});
    await cdp.call('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',clickCount:1,...point});
    if(holdMillis)await delay(holdMillis);
    await cdp.call('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',clickCount:1,...point});
  }

  await wait(`document.querySelector('#graphRevision')?.textContent==='1'&&document.querySelectorAll('#salDeviceLayer .op-device').length===5`);
  assert.equal(await cdp.evaluate(`document.querySelectorAll('svg#railMap').length`),1);
  await wait(`document.querySelector('#dispatchList').textContent.includes('SharedStart')`);
  await wait(`document.querySelector('.event-message')?.textContent.includes('693,70,-707')`);
  assert.equal(await cdp.evaluate(`!!window.eventXss||!!document.querySelector('.event-message img')`),false,'Original failure is rendered as text, never HTML');
  assert.match(await cdp.evaluate(`document.querySelector('.event-message').title`),/world 693,70,-707/);
  assert.match(await cdp.evaluate(`document.querySelector('#dispatchList').textContent`),/38/);
  assert.equal(await cdp.evaluate(`!!document.querySelector('#operatingMap')`),false);
  const view=await cdp.evaluate(`document.querySelector('#railMap').getAttribute('viewBox')`);
  const styles=await cdp.evaluate(`[...document.querySelectorAll('#operatingRouteLayer .op-route')].map(p=>getComputedStyle(p).strokeDasharray)`);assert.equal(new Set(styles).size,3);
  await cdp.evaluate(`document.querySelector('#layerInfrastructure').click();true`);
  assert.equal(await cdp.evaluate(`getComputedStyle(document.querySelector('#nodeLayer')).display`),'none');
  assert.equal(await cdp.evaluate(`document.querySelector('#railMap').getAttribute('viewBox')`),view);
  assert.equal(await cdp.evaluate(`getComputedStyle(document.querySelector('#salDeviceLayer')).display==='none'`),false);
  await cdp.evaluate(`document.querySelector('#layerRoutes').click();true`);assert.equal(await cdp.evaluate(`getComputedStyle(document.querySelector('#operatingRouteLayer')).display`),'none');
  await cdp.evaluate(`document.querySelector('#layerInfrastructure').click();document.querySelector('#layerRoutes').click();true`);

  for(const [key,type] of [['Balise','balise'],['End','end'],['Origin','origin']]){
    await cdp.evaluate(`document.querySelector('#layer${key}').click();true`);
    assert.equal(await cdp.evaluate(`[...document.querySelectorAll('[data-infrastructure-type="${type}"]')].every(e=>getComputedStyle(e).display==='none')`),true);
    await cdp.evaluate(`document.querySelector('#layer${key}').click();true`);
  }
  await cdp.evaluate(`document.querySelector('#layerSwitch').click();true`);
  assert.equal(await cdp.evaluate(`document.querySelector('#layerInfrastructure').indeterminate`),true);
  assert.equal(await cdp.evaluate(`getComputedStyle(document.querySelector('#nodeLayer .switch')).display`),'none');
  assert.equal(await cdp.evaluate(`getComputedStyle(document.querySelector('#switchLayer .switch-direction')).display`),'none');
  assert.equal(await cdp.evaluate(`getComputedStyle(document.querySelector('#nodeLayer .balise')).display==='none'`),false);
  await cdp.evaluate(`document.querySelector('#layerInfrastructure').click();document.querySelector('#layerInfrastructure').click();true`);
  assert.equal(await cdp.evaluate(`['Balise','End','Origin','Switch'].every(k=>document.getElementById('layer'+k).checked)`),true);
  await cdp.evaluate(`document.querySelector('#edgeLayer .edge-hit').focus();document.querySelector('#edgeLayer .edge-hit').dispatchEvent(new MouseEvent('click',{bubbles:true}));true`);
  assert.equal(await cdp.evaluate(`getComputedStyle(document.activeElement).outlineStyle`),'none');
  assert.equal(await cdp.evaluate(`document.querySelector('#infrastructureSelectionLayer polyline').getAttribute('vector-effect')`),'non-scaling-stroke');
  assert.equal(await cdp.evaluate(`document.querySelector('#infrastructureSelectionLayer polyline').getAttribute('points')===document.querySelector('#edgeLayer .rail-edge').getAttribute('points')`),true);
  await cdp.evaluate(`document.querySelector('#highlightIncrease').click();document.querySelector('#highlightColor').value='#ff00ff';document.querySelector('#highlightColor').dispatchEvent(new Event('input'));true`);
  assert.equal(await cdp.evaluate(`document.querySelector('#infrastructureSelectionLayer polyline').getAttribute('stroke-width')`),'7.5');
  assert.equal(await cdp.evaluate(`document.querySelector('#infrastructureSelectionLayer polyline').getAttribute('stroke')`),'#ff00ff');
  await cdp.evaluate(`document.querySelector('#zoomIn').click();true`);
  assert.equal(await cdp.evaluate(`document.querySelector('#infrastructureSelectionLayer polyline').getAttribute('stroke-width')`),'7.5');
  assert.equal(await cdp.evaluate(`localStorage.getItem('skypcc.highlightScale')`),'1.25');
  await cdp.evaluate(`document.querySelector('#highlightSizeValue').click();document.querySelector('#highlightColor').value='#56c7d9';document.querySelector('#highlightColor').dispatchEvent(new Event('input'));document.activeElement.blur();true`);
  await select('operatingDepot','main');
  await wait(`document.querySelectorAll('.berth-capacity').length===3`);
  assert.equal(await cdp.evaluate(`document.querySelectorAll('.op-storage-boundary').length`),3);
  assert.equal(await cdp.evaluate(`document.querySelector('.op-storage-boundary').getAttribute('points').split(' ').length`),3,'Curved boundaries use all graph samples');
  assert.equal(await cdp.evaluate(`document.querySelectorAll('.op-stopping-centre').length`),1);
  assert.match(await cdp.evaluate(`document.querySelector('.berth-capacity').textContent`),/29/);
  await select('operatingDepot','');
  await cdp.evaluate(`document.querySelector('#enterRouteEditor').click();document.querySelector('#routeToken').value='wrong';document.querySelector('#routeAuthConfirm').click();true`);
  await wait(`document.querySelector('#routeAuthResult').textContent.length>0`);
  assert.equal(await cdp.evaluate(`document.querySelector('#routeEditorPanel').hidden`),true);
  await cdp.evaluate(`document.querySelector('#routeToken').value=${JSON.stringify(secret)};document.querySelector('#routeAuthConfirm').click();true`);
  await wait(`!document.querySelector('#routeEditorPanel').hidden`);
  await cdp.evaluate(`document.querySelector('#routeBegin').click();true`);
  await wait(`document.querySelectorAll('#routeVisits li').length===3&&!document.querySelector('#routeSave').disabled`);
  // Real pointer input crosses multiple telemetry/catalog polls, unlike synthetic .click().
  await cdp.evaluate(`window.originalStation=document.querySelector('[data-device-id="station:s0"]');window.originalRouteButton=document.querySelector('#operatingRoutesList button');true`);
  await delay(1300);
  assert.equal(await cdp.evaluate(`window.originalStation===document.querySelector('[data-device-id="station:s0"]')&&window.originalRouteButton===document.querySelector('#operatingRoutesList button')`),true,'Unchanged polling preserves live map/list click targets');
  await cdp.call('Emulation.setDeviceMetricsOverride',{width:850,height:900,deviceScaleFactor:1,mobile:false});
  await delay(1200);
  assert.equal(await cdp.evaluate(`getComputedStyle(document.querySelector('.right-panel')).display!=='none'&&document.querySelector('.workspace').classList.contains('has-selection')`),true,'Polling keeps the editor visible without a selected train');
  await cdp.call('Emulation.setDeviceMetricsOverride',{width:1440,height:900,deviceScaleFactor:1,mobile:false});
  await cdp.evaluate(`window.originalDwell=document.querySelector('#routeVisits input[type=number]');window.originalDwell.focus();window.originalDwell.value='73';true`);
  unavailable=true;
  await wait(`document.querySelector('#routeSave').disabled`);
  assert.equal(await cdp.evaluate(`window.originalDwell===document.querySelector('#routeVisits input[type=number]')&&window.originalDwell.value==='73'&&!document.querySelector('#routeEditorPanel').hidden`),true,'A temporarily unavailable catalog must preserve staged work and typed inputs');
  unavailable=false;
  await wait(`!document.querySelector('#routeSave').disabled`);
  assert.equal(await cdp.evaluate(`document.querySelector('#routeVisits input[type=number]').value`),'73','Recovery must not reset unsent text');
  await cdp.evaluate(`document.querySelector('#routeVisits').scrollIntoView({block:'start'});true`);
  const orderBefore=structuredClone(line.participants[0].working.visits),updatesBefore=calls.filter(a=>a==='UPDATE').length;
  const dragPoints=await cdp.evaluate(`(()=>{const rows=[...document.querySelectorAll('#routeVisits li')],a=rows[0].querySelector('.route-drag-handle').getBoundingClientRect(),b=rows[2].getBoundingClientRect();return {x:a.x+a.width/2,y:a.y+a.height/2,to:b.bottom-3};})()`);
  await cdp.call('Input.dispatchMouseEvent',{type:'mouseMoved',x:dragPoints.x,y:dragPoints.y});
  await cdp.call('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',clickCount:1,x:dragPoints.x,y:dragPoints.y});
  await cdp.call('Input.dispatchMouseEvent',{type:'mouseMoved',button:'left',buttons:1,x:dragPoints.x,y:dragPoints.to});
  await delay(300);
  assert.equal(calls.filter(a=>a==='UPDATE').length,updatesBefore,'Dragging must not submit intermediate orders');
  await cdp.call('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',clickCount:1,x:dragPoints.x,y:dragPoints.to});
  await wait(`!document.querySelector('#routeSave').disabled`);
  assert.deepEqual(line.participants[0].working.visits,[orderBefore[1],orderBefore[2],orderBefore[0]],'Dragging retains full visit identity, dwell and reversal');
  for(const index of [2,1]){
    await cdp.evaluate(`document.querySelectorAll('.route-drag-handle')[${index}].focus();true`);
    await cdp.call('Input.dispatchKeyEvent',{type:'keyDown',key:'ArrowUp',code:'ArrowUp',windowsVirtualKeyCode:38});
    await cdp.call('Input.dispatchKeyEvent',{type:'keyUp',key:'ArrowUp',code:'ArrowUp',windowsVirtualKeyCode:38});
    await wait(`!document.querySelector('#routeSave').disabled`);
  }
  assert.deepEqual(line.participants[0].working.visits,orderBefore,'Keyboard sorting restores original order');
  await cdp.evaluate(`document.querySelector('#routeVisits').scrollIntoView({block:'start'});true`);
  const touch=await cdp.evaluate(`(()=>{const rows=[...document.querySelectorAll('#routeVisits li')],a=rows[0].querySelector('.route-drag-handle').getBoundingClientRect(),b=rows[2].getBoundingClientRect();return {x:a.x+a.width/2,y:a.y+a.height/2,to:b.bottom-3};})()`);
  const beforeCancel=calls.filter(a=>a==='UPDATE').length;
  assert(touch.y>=0&&touch.y<900&&touch.to<900,JSON.stringify(touch));
  await cdp.call('Input.dispatchMouseEvent',{type:'mouseMoved',x:touch.x,y:touch.y});
  await cdp.call('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',clickCount:1,x:touch.x,y:touch.y});
  await cdp.call('Input.dispatchMouseEvent',{type:'mouseMoved',button:'left',buttons:1,x:touch.x,y:touch.to});
  await delay(150);
  assert.equal(await cdp.evaluate(`!!document.querySelector('#routeVisits .route-dragging')`),true);
  await cdp.call('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});
  await cdp.call('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});
  await cdp.call('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',clickCount:1,x:touch.x,y:touch.to});
  assert.equal(calls.filter(a=>a==='UPDATE').length,beforeCancel,'Cancelled sort never submits a proposal');
  assert.deepEqual(line.participants[0].working.visits,orderBefore);
  assert.equal(await cdp.evaluate(`getComputedStyle(document.querySelector('.route-drag-handle')).touchAction`),'none','Handles reserve pointer gestures for sorting; real touch-device acceptance remains separate');
  assert.equal(await cdp.evaluate(`document.querySelector('#routeSupply').value`),'GLOBAL');
  await select('routePreference','45');
  await wait(`!document.querySelector('#routeSave').disabled`);
  assert.equal(line.participants[0].working.dispatchPolicy.preferenceSeconds,45);
  await cdp.evaluate(`window.PccOperatingMap.pick('node','a');true`);
  await wait(`document.querySelectorAll('#routeVisits li').length===4&&!document.querySelector('#routeSave').disabled`);
  assert.equal(line.draft.visits.length,3,'Staged changes must not become saved drafts');
  assert.equal(await cdp.evaluate(`document.querySelector('#routePreviewLayer .op-route').classList.contains('draft')`),true,'Unclosed draft retains its draft style');
  assert.equal(await cdp.evaluate(`document.querySelector('#routePreviewLayer .op-route').classList.contains('estimated')`),false,'A confirmed track prefix is not an estimated straight connection just because the loop is unfinished');
  assert.equal(await cdp.evaluate(`document.querySelector('#routePreviewLayer .op-route').getAttribute('points').split(' ').length`),points.length,'Preview follows all track bends');
  await pointerClick('[data-device-id="station:s0"] circle',1200);
  await wait(`document.querySelectorAll('#routeVisits li').length===5&&!document.querySelector('#routeSave').disabled`);
  assert.equal(await cdp.evaluate(`document.querySelectorAll('.entry-reference').length>0`),true,'Public station accepted and entry reference displayed');
  assert.equal(await cdp.evaluate(`document.querySelector('#routePreviewLayer .op-route').classList.contains('estimated')`),false);
  await cdp.evaluate(`const input=document.querySelector('#routeVisits input[type=number]');input.value=17;input.dispatchEvent(new Event('change'));true`);
  await wait(`!document.querySelector('#routeSave').disabled`);
  await cdp.evaluate(`document.querySelector('#routeSave').click();true`);
  await wait(`document.querySelector('#routeSave').dataset.dirty==='false'&&!document.querySelector('#routeSave').disabled`);
  assert.equal(line.draft.visits.length,5);assert.equal(line.published.visits.length,3);assert.equal(line.draft.visits[0].dwellSeconds,17);
  validationFailure=true;
  await cdp.evaluate(`document.querySelector('#routeValidate').click();true`);
  await wait(`!document.querySelector('#routeValidationIssues').hidden&&!document.querySelector('#routeValidate').disabled`);
  assert.match(await cdp.evaluate(`document.querySelector('#routeValidationIssues').textContent`),/#2 SharedSecond.*#3 SharedStart/);
  assert.equal(await cdp.evaluate(`document.querySelectorAll('#routeVisits .validation-problem').length`),2);
  await cdp.evaluate(`document.querySelector('#routeValidationIssues button').click();true`);
  assert.equal(await cdp.evaluate(`document.activeElement===document.querySelector('#routeVisits').children[1]`),true);
  validationFailure=false;
  await cdp.evaluate(`document.querySelector('#routeValidate').click();true`);await wait(`!document.querySelector('#routeValidate').disabled`);
  assert.equal(await cdp.evaluate(`document.querySelector('#routeValidationIssues').hidden`),true);
  await cdp.evaluate(`document.querySelector('#routePublish').click();true`);await wait(`!document.querySelector('#routePublish').disabled`);assert.equal(line.publishedVersion,2);assert(!line.conflict,'A stale cached GET must not roll the editor back after SAVE');
  await cdp.evaluate(`fetch('/test/conflict');true`);await wait(`!document.querySelector('#routeConflict').hidden&&document.querySelector('#routeSave').disabled`);
  const saved=calls.filter(a=>a==='SAVE').length;await cdp.evaluate(`document.querySelector('#routeSave').click();true`);assert.equal(calls.filter(a=>a==='SAVE').length,saved);
  assert(await cdp.evaluate(`document.querySelectorAll('#operatingRouteLayer .op-route').length>0`),'Conflict must not remove published display');
  await cdp.evaluate(`document.querySelector('#routeResolve').click();true`);await wait(`document.querySelector('#routeConflict').hidden`);assert.equal(calls.filter(a=>a==='SAVE').length,saved);
  await select('operatingDepot','main');assert.equal(await cdp.evaluate(`document.querySelector('#berthCount').textContent`),'3');
  await select('languageSelect','zh');assert(await cdp.evaluate(`document.querySelector('#routeSave').textContent.includes('保存')`));
  const shot=await cdp.call('Page.captureScreenshot',{format:'png'});fs.writeFileSync(path.join(out,'pcc-route-editor.png'),Buffer.from(shot.data,'base64'));
  stale=true;await wait(`document.querySelector('#berthCount').textContent==='--'`);stale=false;
  assert.deepEqual(cdp.errors,[]);
  assert(!await cdp.evaluate(`JSON.stringify(localStorage).includes(${JSON.stringify(secret)})`));
  console.log('PASS real mouse reorder and keyboard identity preservation, Escape cancellation, touch-action policy, failing-segment labels/IDs/highlight/jump/clear, stable polling and narrow editor, station selection, catalog recovery, token/publication/conflict fencing, four-language UI');
 }finally{if(cdp){await cdp.call('Browser.close').catch(()=>{});cdp.close();}if(browser&&browser.exitCode==null)browser.kill();server.close();}
})().catch(error=>{console.error(error.stack);process.exitCode=1;});
