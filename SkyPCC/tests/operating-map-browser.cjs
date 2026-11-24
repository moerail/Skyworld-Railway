const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const web=path.resolve(__dirname,'../src/main/resources/web');
const out=path.resolve(__dirname,'../../Common/target');fs.mkdirSync(out,{recursive:true});
const profile=fs.mkdtempSync(path.join(out,'operating-browser-'));
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const p=(x,z=0)=>({world:'world',x,y:64,z});
let unavailable=false,stale=false;
const secret='test-only-editor-token-0000000000000000',calls=[],receipts=new Map();
const base={name:'L1',dx:1,dz:0,depots:['main'],visits:[{deviceId:'station:s0',reverse:false,dwellSeconds:10},{deviceId:'station:s1',reverse:false,dwellSeconds:10},{deviceId:'station:s0',reverse:false,dwellSeconds:10}]};
let line={name:'L1',revision:1,publishedVersion:1,draft:structuredClone(base),published:structuredClone(base),validated:false,retired:false,conflict:false,participants:[]};
let currentActor='',session=null,workspaceSequence=0,oldCatalog=null;
const devices=[{id:'a',type:'balise',name:'Engineering 777',position:p(0),route:'',number:0,valid:true},{id:'b',type:'switch',name:'Switch 301',position:p(100),route:'',number:0,valid:true},
{id:'station:s0',type:'station',name:'L1 first',position:p(0,5),route:'L1',number:10,valid:true},{id:'station:s1',type:'station',name:'L1 second',position:p(100,5),route:'L1',number:10,valid:true},
...[1,2,3].map(i=>({id:'sal:d'+i,type:'depot',name:'main/L'+i,position:p(20+i*10,15),route:'',number:0,valid:true}))];
const points=[p(0,5),p(100,5),p(100,45),p(0,45),p(0,5)];
function operating(){return {schemaVersion:1,status:unavailable?'UNAVAILABLE':'AVAILABLE',producerSession:'test',graphRevision:1,emittedAtMillis:Date.now()-(stale?10000:0),routes:['DRAFT','VALIDATED','PUBLISHED'].map((status,i)=>({name:'L'+(i+1),status,reason:'',segments:[{points:points.map(p=>({...p,z:p.z+i*50})),estimated:i===0}],stops:[]})),depots:[{name:'main',berths:[1,2,3].map(i=>({id:'main/L'+i,name:'L'+i,position:p(20+i*10,15),valid:true,parkedTrains:[],reservedBy:null})),parkedTrains:[],runningTrains:[],otherTrains:[]}],trains:[]};}
const graph={revision:1,nodes:[{id:'o',name:'Origin',type:'origin',rail:p(-10)},{id:'z',name:'End',type:'end',rail:p(110)},{id:'a',name:'Engineering 777',type:'balise',rail:p(0)},{id:'b',name:'Switch 301',type:'switch',rail:p(100),ports:{common:'west',straight:'east',diverging:'south'},state:'straight'}],edges:[{id:'e',from:'a',to:'b',distanceMeters:100,path:[{...p(0),distanceMeters:0},{...p(100),distanceMeters:100}]}]};
const server=http.createServer(async(req,res)=>{
 const json=(v,status=200)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(v));};
 if(req.url==='/api/v5/config')return json({updateMode:'poll',pollIntervalMillis:250,controlEnabled:true,requireHttpsForControl:false});
 if(req.url==='/api/v5/graph')return json(graph);
 if(req.url==='/api/v5/trains')return json({schemaVersion:5,serverTimeMillis:Date.now(),serviceStatus:'AVAILABLE',graphRevision:1,switchStates:{b:'straight'},trains:[],operatingNetwork:operating()});
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
  else if(r.action==='VALIDATE'){if(r.expectedRevision!==line.revision){status='CONFLICT';reason='STALE_REVISION_FROZEN';line.conflict=true;}else line.validated=true;}
  else if(r.action==='PUBLISH'){line.published=structuredClone(line.draft);line.publishedVersion++;}
  else if(r.action==='RETIRE')line.retired=true;
  else if(r.action==='CANCEL'){session=null;line.participants=[];}
  const working=line.participants[0]?.working||line.draft,closed=working.visits.at(-1)?.deviceId.startsWith('station:');
  if(r.action!=='AUTH')workspaceSequence++;
  const reply={workspaceRevision:workspaceSequence,requestId:r.requestId,status,reason,session,line,graphRevision:1,preview:[{points,estimated:!closed}],closed};
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
 function call(method,params={}){return new Promise((resolve,reject)=>{const id=++sequence;const timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout '+method));},15000);pending.set(id,{resolve,reject,timer});socket.send(JSON.stringify({id,method,params}));});}
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

  await wait(`document.querySelector('#graphRevision')?.textContent==='1'&&document.querySelectorAll('#salDeviceLayer .op-device').length===5`);
  assert.equal(await cdp.evaluate(`document.querySelectorAll('svg#railMap').length`),1);
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
  await cdp.evaluate(`document.querySelector('#enterRouteEditor').click();document.querySelector('#routeToken').value='wrong';document.querySelector('#routeAuthConfirm').click();true`);
  await wait(`document.querySelector('#routeAuthResult').textContent.length>0`);
  assert.equal(await cdp.evaluate(`document.querySelector('#routeEditorPanel').hidden`),true);
  await cdp.evaluate(`document.querySelector('#routeToken').value=${JSON.stringify(secret)};document.querySelector('#routeAuthConfirm').click();true`);
  await wait(`!document.querySelector('#routeEditorPanel').hidden`);
  await cdp.evaluate(`document.querySelector('#routeBegin').click();true`);
  await wait(`document.querySelectorAll('#routeVisits li').length===3&&!document.querySelector('#routeSave').disabled`);
  await cdp.evaluate(`window.PccOperatingMap.pick('node','a');true`);
  await wait(`document.querySelectorAll('#routeVisits li').length===4&&!document.querySelector('#routeSave').disabled`);
  assert.equal(line.draft.visits.length,3,'Staged changes must not become saved drafts');
  assert.equal(await cdp.evaluate(`document.querySelector('#routePreviewLayer .op-route').classList.contains('estimated')`),true,'Unclosed draft must be dashed');
  await cdp.evaluate(`document.querySelector('[data-device-id="station:s0"]').dispatchEvent(new MouseEvent('click',{bubbles:true}));true`);
  await wait(`document.querySelectorAll('#routeVisits li').length===5&&!document.querySelector('#routeSave').disabled`);
  assert.equal(await cdp.evaluate(`document.querySelector('#routePreviewLayer .op-route').classList.contains('estimated')`),false);
  await cdp.evaluate(`const input=document.querySelector('#routeVisits input[type=number]');input.value=17;input.dispatchEvent(new Event('change'));true`);
  await wait(`!document.querySelector('#routeSave').disabled`);
  await cdp.evaluate(`document.querySelector('#routeSave').click();true`);
  await wait(`document.querySelector('#routeSave').dataset.dirty==='false'&&!document.querySelector('#routeSave').disabled`);
  assert.equal(line.draft.visits.length,5);assert.equal(line.published.visits.length,3);assert.equal(line.draft.visits[0].dwellSeconds,17);
  await cdp.evaluate(`document.querySelector('#routeValidate').click();true`);await wait(`!document.querySelector('#routeValidate').disabled`);
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
  console.log('PASS unified map/layers, shared camera, token authentication, staging/save/publish separation, device selection and direction, frozen two-sided conflict/manual resolution, stale data and four-language UI');
 }finally{if(cdp){await cdp.call('Browser.close').catch(()=>{});cdp.close();}if(browser&&browser.exitCode==null)browser.kill();server.close();}
})().catch(error=>{console.error(error.stack);process.exitCode=1;});
