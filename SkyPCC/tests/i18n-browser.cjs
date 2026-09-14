const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = path.join(__dirname,'../src/main/resources/web');
const graph={revision:1,lineModelVersion:1,nodes:[
  {id:'a',name:'0018',type:'balise',line:'test_up',mileageMeters:500,rail:{world:'world',x:0,y:64,z:0}},
  {id:'s',name:'201',type:'switch',line:'test_up',state:'straight',rail:{world:'world',x:100,y:64,z:0},ports:{straight:'east',diverging:'south'}}
],edges:[{id:'e',from:'a',to:'s',distanceMeters:100,path:[{x:0,z:0,distanceMeters:0},{x:100,z:0,distanceMeters:100}]}],
lineAssignments:{e:{status:'CONFIRMED',line:'test_up'}}};
const train={trainId:'t1',name:'Train 原名',trainNumber:'G001',mode:'manual',direction:'forward',driver:'Rin',line:'test_up',
  quality:'VALID',graphCurrent:true,stale:false,moving:false,speedMetersPerSecond:0,
  edgeId:'e',edgeOffsetMeters:30,edgeLengthMeters:100,currentMileageMeters:562,
  cab:{atpMode:'SHADOW',reverser:'FORWARD',powerNotch:0,brakeNotch:7,brakeHold:false}};
const posts=[];
const server=http.createServer((req,res)=>{
  const now=Date.now();
  let body, type='application/json';
  if(req.url==='/api/v1/config')body={updateMode:'poll',pollIntervalMillis:250,controlEnabled:true};
  else if(req.url==='/api/v1/graph')body=graph;
  else if(req.url==='/api/v1/trains')body={serviceStatus:'AVAILABLE',serverTimeMillis:now,graphRevision:1,
    trains:[{...train,observedAtMillis:now,ageMillis:0}],
    shadowMa:{version:4,simulationOnly:true,executable:false,status:'SHADOW',graphRevision:1,emittedAtMillis:now,
      authorities:[{trainId:'t1',state:'ALLOCATED_SHADOW',signedRemainingMeters:40,eoaEdgeId:'e',eoaOffsetMeters:70,reason:'SWITCH_UNKNOWN'}],sections:[
        {edgeId:'e',resourceId:'r',state:'OCCUPIED',occupants:['t1'],reservations:[],fromMeters:25,toMeters:30},
        {edgeId:'e',resourceId:'r',state:'RESERVED_SHADOW',occupants:[],reservations:['t1'],fromMeters:30,toMeters:70}]},
    operationalEvents:{status:'AVAILABLE',session:'test',latestSequence:5,evictedCount:0,events:[
      {session:'test',sequence:5,emittedAtMillis:now,trainId:'t1',trainName:'Train 原名',type:'ATP_MODE_CHANGED',reason:'MODE_COMMAND',details:{actorName:'Rin',previous:'RECOVERING',next:'ISOLATED'}},
      {session:'test',sequence:1,emittedAtMillis:now,trainId:'t1',trainName:'Train 原名',driverName:'Rin',type:'DRIVER_UNAVAILABLE',reason:'DISCONNECTED'},
      {session:'test',sequence:2,emittedAtMillis:now,trainId:'t1',trainName:'Train 原名',driverName:'Rin',type:'MA_REQUESTED',reason:'TRACK_END',details:{state:'ALLOCATED_SHADOW'}},
      {session:'test',sequence:3,emittedAtMillis:now,trainId:'t1',trainName:'Train 原名',driverName:'Rin',type:'MA_RELEASED',reason:'RELEASED'},
      {session:'test',sequence:4,emittedAtMillis:now,trainId:'t1',trainName:'Train 原名',driverName:'Rin',type:'MA_REQUESTED',reason:'NO_EXIT_CAPACITY',details:{state:'WAITING'}}]}};
  else if(req.url==='/api/v4/switch') { posts.push(req.method);body={status:'PENDING',reason:'VERIFYING'};res.statusCode=202; }
  else {
    const name=req.url==='/'?'index.html':req.url.startsWith('/assets/')?req.url.slice(8):'';
    if(!['index.html','app.js','i18n.js','styles.css','day_logo.png','night_logo.png'].includes(name)){res.writeHead(404);res.end();return;}
    type=name.endsWith('.js')?'text/javascript':name.endsWith('.css')?'text/css':name.endsWith('.png')?'image/png':'text/html';
    body=fs.readFileSync(path.join(root,name));
  }
  res.setHeader('Content-Type',type);res.end(Buffer.isBuffer(body)?body:JSON.stringify(body));
});
(async()=>{
  let browser;
  try {
    await new Promise(r=>server.listen(0,'127.0.0.1',r));
    browser=await chromium.launch({headless:true, ...(process.env.PLAYWRIGHT_CHANNEL ? {channel:process.env.PLAYWRIGHT_CHANNEL} : {})});
    const page=await browser.newPage({viewport:{width:1440,height:1000}});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.locator('.train-row').click();
    await page.locator('#followTrain').click();
    const out=path.resolve(__dirname,'../target/pcc-i18n');fs.mkdirSync(out,{recursive:true});
    for(const lang of ['zh','en','fr','ja']) {
      await page.selectOption('#languageSelect',lang);
      await page.waitForTimeout(150);
      assert.equal(await page.locator('#detailName').textContent(),'Train 原名');
      assert.equal(await page.locator('#detailLine').textContent(),'test_up');
      assert(await page.locator('#cancelFollow').isVisible());
      assert.equal(await page.locator('#detailMaReason').textContent(),await page.evaluate(()=>PccI18n.code('SWITCH_UNKNOWN')));
      assert.equal(await page.locator('.event-row').count(),5);
      assert.equal(await page.locator('.train-label').textContent(),'G001 | Train 原名 | 0.0 km/h');
      assert.equal(await page.locator('.interval-overlay[data-occupancy="OCCUPIED"]').getAttribute('points'),'25,0 30,0');
      assert.equal(await page.locator('.interval-overlay[data-occupancy="RESERVED_SHADOW"]').getAttribute('points'),'30,0 70,0');
      assert.equal(await page.locator('.rail-edge:not(.interval-overlay)').getAttribute('data-occupancy'),'UNALLOCATED');
      const eventsText=await page.locator('.event-row').allTextContents();
      assert(eventsText[0].includes(await page.evaluate(()=>PccI18n.t('{operator} changed ATP mode: {from} → {to}.',{operator:'Rin',from:PccI18n.code('RECOVERING'),to:PccI18n.code('ISOLATED')}))));
      assert(eventsText[1].includes(await page.evaluate(()=>PccI18n.code('WAITING'))));
      assert(eventsText[3].includes(await page.evaluate(()=>PccI18n.code('ALLOCATED_SHADOW'))));
      assert(eventsText[2].includes(await page.evaluate(()=>PccI18n.t('{driver} released forward shadow reservations. Train occupancy retained; no brake command.',{driver:'Rin'}))));
      for(const width of [1440,390]) {
        await page.setViewportSize({width,height:1000});
        await page.screenshot({path:path.join(out,`${lang}-${width}.png`)});
        const overflow=await page.locator('#languageSelect').evaluate(el=>el.getBoundingClientRect().right>innerWidth);
        assert(!overflow,`${lang} language menu overflow at ${width}`);
        const overlap=await page.locator('.event-row').first().evaluate(el=>{
          const severity=el.querySelector('.event-severity').getBoundingClientRect();
          const name=el.querySelector('button').getBoundingClientRect();
          return severity.right > name.left;
        });
        assert(!overlap,`${lang} event columns overlap at ${width}`);
      }
    }
    train.trainNumber='';
    await page.waitForFunction(()=>document.querySelector('.train-label')?.textContent.startsWith('-- |'));
    train.trainNumber='G001';
    await page.reload();await page.locator('.train-row').waitFor();
    assert.equal(await page.locator('#languageSelect').inputValue(),'ja');
    await page.setViewportSize({width:1440,height:1000});
    await page.locator('#themeToggle').click();
    await page.screenshot({path:path.join(out,'ja-light.png')});
    await page.locator('[data-switch-id="s"]').first().click();
    assert(await page.locator('#infrastructureInspector').isVisible());
    assert(!(await page.locator('#switchDialog').isVisible()));
    assert((await page.locator('#infrastructureInspector').textContent()).includes('201'));
    await page.locator('#infrastructureSwitch').click();
    await page.locator('#switchToken').fill('test-only');
    await page.locator('#switchConfirm').click();
    await page.waitForTimeout(200);
    // Change locale without resubmitting/replacing the pending control request.
    await page.evaluate(()=>{const el=document.getElementById('languageSelect');el.value='fr';el.dispatchEvent(new Event('change'));});
    assert((await page.locator('#switchResult').textContent()).includes('Vérification'));
    assert(await page.locator('#switchConfirm').isDisabled());
    assert.equal(await page.locator('#switchToken').inputValue(),'');
    await page.locator('#switchClose').click();
    const edgePoint = await page.locator('.edge-hit').evaluate(el => { const p = new DOMPoint(50,0).matrixTransform(el.getScreenCTM());return {x:p.x,y:p.y}; });
    await page.mouse.click(edgePoint.x,edgePoint.y);
    assert((await page.locator('#infrastructureInspector').textContent()).includes('25.0–30.0'));
    for (const lang of ['zh','en','fr','ja']) {
      await page.selectOption('#languageSelect',lang); await page.waitForTimeout(80);
      assert((await page.locator('#infrastructureInspector').textContent()).includes(await page.evaluate(()=>PccI18n.t('Endpoints'))));
    }
    const normalHeight = await page.locator('#eventList').evaluate(e=>e.clientHeight);
    await page.locator('#expandEvents').click();
    assert(await page.locator('#eventList').evaluate((e,h)=>e.clientHeight>h,normalHeight));
    assert.equal(await page.locator('#expandEvents').getAttribute('aria-expanded'),'true');
    await page.screenshot({path:path.join(out,'edge-expanded.png')});
    await page.setViewportSize({width:390,height:1000});
    await page.screenshot({path:path.join(out,'edge-expanded-mobile.png')});
    await page.locator('#expandEvents').click();
    await page.setViewportSize({width:1440,height:1000});
    await page.locator('[data-inspect-id="a"]').first().click();
    assert((await page.locator('#infrastructureInspector').textContent()).includes('0018'));
    assert((await page.locator('#infrastructureInspector').textContent()).includes('K0+500.0'));
    const map=await page.locator('#railMap').boundingBox();
    await page.mouse.move(map.x+30,map.y+40);await page.mouse.down();await page.mouse.move(map.x+60,map.y+60);await page.mouse.up();
    assert((await page.locator('#infrastructureInspector').textContent()).includes('0018'));
    await page.locator('#inspectorBack').click();
    assert(!(await page.locator('#infrastructureInspector').isVisible()));
    assert.deepEqual(errors,[]);
    console.log('PASS browser: four languages, desktop/mobile, selection/follow preserved, persistence, light theme, pending control; '+out);
  } finally {if(browser)await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;});
