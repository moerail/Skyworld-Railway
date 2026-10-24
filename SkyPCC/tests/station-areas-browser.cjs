const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = path.join(__dirname, '../src/main/resources/web');
const points = Array.from({length:121}, (_,x)=>({world:'world',x,y:64,z:0,distanceMeters:x}));
const graph={schemaVersion:3,revision:4,lineModelVersion:1,nodes:[
  {id:'s1',name:'S01',type:'switch',line:'Main',state:'straight',rail:{world:'world',x:30,y:64,z:0},ports:{straight:'east',diverging:'south'}},
  {id:'s2',name:'S02',type:'switch',line:'Main',state:'straight',rail:{world:'world',x:90,y:64,z:0},ports:{straight:'east',diverging:'south'}}],
  edges:[{id:'e',from:'s1',to:'s2',distanceMeters:120,path:points}],lineAssignments:{e:{status:'CONFIRMED',line:'Main'}}};
const areas={version:7,messageId:6502,packetId:5002,status:'AVAILABLE',producerSession:'test',configurationRevision:1,graphRevision:4,areas:[{
  id:'中央站/主场',station:'中央站',yard:'主场',state:'READY_TO_CONFIRM',issues:[],intervals:[{edgeId:'e',fromMeters:10,toMeters:110}],
  boundaries:[{id:'b1',rail:{world:'world',x:10,y:64,z:0},inside:'left',preference:'IN',penalty:9999},
    {id:'b2',rail:{world:'world',x:110,y:64,z:0},inside:'right',preference:'OUT',penalty:9999}],
  tracks:[{id:'t1',name:'IIG',rail:{world:'world',x:60,y:64,z:0},state:'VALID',intervals:[{edgeId:'e',fromMeters:30,toMeters:90}],issues:[]}]
}]};
let noProvider=false, posts=0;
const server=http.createServer((req,res)=>{
  if(req.method!=='GET') posts++;
  const now=Date.now();let body,type='application/json';
  if(req.url==='/api/v5/config') body={updateMode:'poll',pollIntervalMillis:250,controlEnabled:false};
  else if(req.url==='/api/v5/graph') body=graph;
  else if(req.url==='/api/v5/trains') body={schemaVersion:5,serviceStatus:'AVAILABLE',serverTimeMillis:now,graphRevision:4,trains:[],
    stationAreas:noProvider?{status:'UNAVAILABLE',areas:[]}:{...areas,emittedAtMillis:now},
    operationalEvents:{status:'AVAILABLE',session:'test',latestSequence:0,events:[]}};
  else {
    const name=req.url==='/'?'index.html':req.url.startsWith('/assets/')?req.url.slice(8):'';
    if(!['index.html','app.js','i18n.js','styles.css','day_logo.png','night_logo.png'].includes(name)){res.writeHead(404);res.end();return;}
    body=fs.readFileSync(path.join(root,name));
    type=name.endsWith('.js')?'text/javascript':name.endsWith('.css')?'text/css':name.endsWith('.png')?'image/png':'text/html';
  }
  res.setHeader('Content-Type',type);res.end(Buffer.isBuffer(body)?body:JSON.stringify(body));
});
(async()=>{
  let browser;
  try {
    await new Promise(r=>server.listen(0,'127.0.0.1',r));
    browser=await chromium.launch({headless:true,...(process.env.PLAYWRIGHT_CHANNEL?{channel:process.env.PLAYWRIGHT_CHANNEL}:{})});
    const page=await browser.newPage({viewport:{width:1440,height:960}});
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.waitForFunction(()=>document.querySelector('#areaFilter').options.length===2);
    assert.equal(await page.locator('#areaFilter').inputValue(),'');
    assert.equal(await page.locator('#areaLayer>*').count(),0);
    await page.selectOption('#areaFilter','中央站/主场');
    await page.waitForSelector('.station-track-label');
    assert.equal(await page.locator('.station-track-label').textContent(),'IIG');
    assert.equal(await page.locator('.station-boundary-marker').count(),2);
    const values=(await page.locator('.station-area-path').getAttribute('points')).split(' ');
    assert.equal(values[0],'10,0');assert.equal(values.at(-1),'110,0');
    await page.selectOption('#lineFilter','Main');
    assert.equal(await page.locator('.station-area-path').count(),1);
    const out=path.resolve(__dirname,'../target/cbi-display');fs.mkdirSync(out,{recursive:true});
    for(const lang of ['zh','en','fr','ja']) {
      await page.selectOption('#languageSelect',lang);
      assert.equal(await page.locator('.station-track-label').textContent(),'IIG');
      assert((await page.locator('#areaStatus').textContent()).includes(await page.evaluate(()=>PccI18n.t('READY_TO_CONFIRM'))));
      await page.screenshot({path:path.join(out,`${lang}-station-area.png`)});
    }
    areas.areas[0].state='INVALID';areas.areas[0].issues=['AREA_OVERLAP:其他站'];areas.configurationRevision++;
    await page.waitForSelector('.station-area-path.invalid',{state:'attached'});
    await page.selectOption('#languageSelect','zh');
    assert((await page.locator('#areaStatus').textContent()).includes('站区轨道范围重叠'));
    areas.graphRevision=5;
    await page.waitForFunction(()=>document.querySelector('#areaLayer').childElementCount===0);
    assert((await page.locator('#areaStatus').textContent()).includes('站区不可用'));
    areas.graphRevision=4;areas.areas[0].state='READY_TO_CONFIRM';areas.areas[0].issues=[];
    await page.waitForSelector('.station-area-path',{state:'attached'});
    areas.areas=[];
    await page.waitForFunction(()=>document.querySelector('#areaFilter').options.length===1);
    assert.equal(await page.locator('#areaLayer>*').count(),0);
    noProvider=true;
    await page.waitForTimeout(300);
    assert.equal(posts,0);assert.deepEqual(errors,[]);
    console.log('PASS browser station areas: default hidden, exact intervals, tracks, boundaries, line filter, four languages, overlap, revision mismatch, deletion and optional provider; no writes');
  } finally {if(browser)await browser.close();await new Promise(r=>server.close(r));}
})().catch(error=>{console.error(error);process.exitCode=1;});
