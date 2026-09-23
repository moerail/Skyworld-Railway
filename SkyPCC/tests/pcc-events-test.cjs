const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const web = path.resolve(__dirname, '../src/main/resources/web');
const out = path.resolve(__dirname, '../target/pcc-events');
fs.mkdirSync(out, { recursive: true });
const tid = '11111111-1111-1111-1111-111111111111';
const rail = (x, z) => ({ world: 'world', x, y: 64, z });
const graph = { revision: 1, nodes: [
  { id: 'a', type: 'ORIGIN', name: 'West', line: 'A', rail: rail(0, 0) },
  { id: 'b', type: 'SWITCH', name: '203', line: 'A', rail: rail(100, 0), state: 'straight', ports: { straight: 'east', diverging: 'north' } },
  { id: 'c', type: 'END', name: 'East', line: 'A', rail: rail(200, -60) }
], edges: [
  { id: 'e1', from: 'a', to: 'b', distanceMeters: 100, path: [{ ...rail(0, 0), distanceMeters: 0 }, { ...rail(100, 0), distanceMeters: 100 }] },
  { id: 'e2', from: 'b', to: 'c', distanceMeters: 160, path: [{ ...rail(100, 0), distanceMeters: 0 }, { ...rail(160, 0), distanceMeters: 60 }, { ...rail(160, -60), distanceMeters: 120 }, { ...rail(200, -60), distanceMeters: 160 }] }
] };
let status = 'AVAILABLE', epoch = 'session-one', mode = 'poll';
let offset = 40, stale = false;
let awaitingPosition = false;
let cab = { atpMode: 'RECOVERING', reverser: 'NEUTRAL', powerNotch: 4, brakeNotch: 0, emergencyBrake: false, brakeHold: true };
let rows = [
  { sequence: 1, type: 'DRIVER_ACQUIRED', reason: 'EXPLICIT_DRIVE', driverName: 'Saionji_Rin' },
  { sequence: 2, type: 'DRIVER_UNAVAILABLE', reason: 'DISCONNECTED', driverName: 'Saionji_Rin' },
  { sequence: 3, type: 'DRIVER_RELEASED', reason: 'EXPLICIT_RELEASE', driverName: '<img src=x onerror=window.xss=1>' }
];
const payload = () => ({ schemaVersion:5, serverTimeMillis: Date.now(), graphRevision: 1, serviceStatus: 'AVAILABLE',
  trains: awaitingPosition ? [{ trainId: tid, name: 'test1', quality: 'AWAITING_POSITION',
    stale: true, graphCurrent: false, mode: 'unknown', memberCount: 3 }] : [{ trainId: tid, name: 'test1', line: 'A', edgeId: 'e1', edgeOffsetMeters: offset,
    edgeLengthMeters: 100, graphCurrent: true, stale, moving: false, speedMetersPerSecond: 0,
    observedAtMillis: Date.now(), mode: 'manual', driver: '', direction: 'forward', cab }],
  operationalEvents: { status, session: epoch, latestSequence: rows.length, evictedCount: 0,
    events: rows.map(e => ({ session: epoch, emittedAtMillis: Date.now() - 5000, source: 'STF', trainId: tid, trainName: 'test1', ...e })) }
});
const server = http.createServer((req, res) => {
  if (req.url === '/api/v5/events') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store' });
    const send = () => res.write(`event: snapshot\ndata: ${JSON.stringify(payload())}\n\n`);
    send(); const timer = setInterval(send, 150); req.on('close', () => clearInterval(timer)); return;
  }
  let json;
  if (req.url === '/api/v5/config') json = { updateMode: mode, pollIntervalMillis: 150 };
  if (req.url === '/api/v5/graph') json = graph;
  if (req.url === '/api/v5/trains') json = payload();
  if (json) { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(json)); return; }
  const file = ({ '/': 'index.html', '/assets/app.js': 'app.js', '/assets/styles.css': 'styles.css',
    '/assets/day_logo.png': 'day_logo.png', '/assets/night_logo.png': 'night_logo.png' })[req.url];
  if (!file) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'Content-Type': file.endsWith('.png') ? 'image/png' : file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html' });
  res.end(fs.readFileSync(path.join(web, file)));
});

(async () => {
  let browser;
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHANNEL ? {channel: process.env.PLAYWRIGHT_CHANNEL} : {}) });
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    const url = `http://127.0.0.1:${server.address().port}`;
    await page.goto(url);
    await page.waitForFunction(() => document.querySelectorAll('.event-row').length === 3);
    await page.waitForTimeout(700);
    assert.equal(await page.locator('.event-row').count(), 3, 'Polling duplicated history');
    assert.equal(await page.evaluate(() => window.xss), undefined, 'Event text executed HTML');
    assert.equal(await page.locator('.event-row img').count(), 0);
    await page.locator('.train-row').first().click();
    assert.equal(await page.locator('#detailAtp').textContent(), 'Recovering');
    assert.equal(await page.locator('#detailHandle').textContent(), 'P4 / B0');
    assert.equal(await page.locator('#detailBrakeHold').textContent(), 'Active');
    assert.equal(await page.locator('#detailReverser').textContent(), 'Neutral');
    assert.equal(await page.locator('#detailDirection').textContent(), 'Forward');
    await page.waitForFunction(() => document.querySelector('#brandLogo').naturalWidth > 0);
    assert.ok((await page.locator('#brandLogo').getAttribute('src')).includes('night_logo'));
    await page.locator('#followTrain').click();
    const nearCentre = async x => page.waitForFunction(x => {
      const v = document.querySelector('#railMap').viewBox.baseVal;
      return Math.abs(v.x + v.width / 2 - x) < .2 && Math.abs(v.y + v.height / 2) < .2;
    }, x);
    await nearCentre(40);
    offset = 75;
    await nearCentre(75);
    stale = true;
    await page.waitForFunction(() => document.querySelector('#followStatus').textContent.includes('waiting'));
    const frozenView = await page.locator('#railMap').getAttribute('viewBox');
    offset = 85;
    await page.waitForTimeout(500);
    assert.equal(await page.locator('#railMap').getAttribute('viewBox'), frozenView, 'Stale position moved camera');
    stale = false;
    await nearCentre(85);
    await page.locator('#cancelFollow').click();
    await page.waitForFunction(() => document.querySelector('#followStatus').textContent === 'Camera free');
    const freeView = await page.locator('#railMap').getAttribute('viewBox');
    offset = 60;
    await page.waitForTimeout(500);
    assert.equal(await page.locator('#railMap').getAttribute('viewBox'), freeView, 'Cancelled follow moved camera');
    const map = await page.locator('#railMap').boundingBox();
    await page.mouse.move(map.x + 30, map.y + 30); await page.mouse.down();
    await page.mouse.move(map.x + 90, map.y + 70); await page.mouse.up();
    assert.equal(await page.locator('#detailName').textContent(), 'test1');
    // New operational events, including a switch event without a train target.
    epoch = 'switch-session';
    rows = [
      { sequence: 1, type: 'SWITCH_CHANGED', reason: 'AUTO_APPROACH', trainId: null, trainName: '',
        details: { switchName: '203', switchId: 'sw203', previous: 'EAST_WEST', next: 'DIVERGING' } },
      { sequence: 2, type: 'SWITCH_RUN_THROUGH_SUSPECTED', reason: 'OBSERVED_TRAILING_ENTRY',
        details: { switchName: '203', previous: 'STRAIGHT', entry: 'DIVERGING' } },
      { sequence: 3, type: 'EMERGENCY_BRAKE_APPLIED', reason: 'EB_INPUT' }
    ];
    await page.waitForFunction(() => document.querySelector('#eventList').textContent.includes('Possible switch run-through'));
    assert.ok((await page.locator('#eventList').textContent()).includes('Conversion completed'));
    assert.ok((await page.locator('#eventList').textContent()).includes('EB state entered'));
    await page.locator('#eventFilter').selectOption('warning');
    assert.equal(await page.locator('.event-row').count(), 1);
    await page.locator('#eventFilter').selectOption('all');
    assert.equal(await page.locator('#inspector').isVisible(), true);
    await page.locator('#eventFilter').selectOption('warning');
    assert.equal(await page.locator('.event-row').count(), 1);
    await page.locator('#eventFilter').selectOption('all');
    assert.equal(await page.locator('.rail-edge').count(), 2);
    assert.equal(await page.locator('.train-marker').count(), 1);
    assert.equal(await page.locator('.switch-label').count(), 1);
    assert.equal(await page.locator('.switch-direction').count(), 1);
    assert.equal(await page.locator('#switchLayer rect').count(), 0);
    assert.equal(await page.locator('.switch-direction').evaluate(e => getComputedStyle(e).fill), 'rgb(196, 161, 255)');
    assert.equal(await page.locator('.switch-label').evaluate(e => getComputedStyle(e).fill), 'rgb(17, 17, 17)');
    assert.equal(await page.locator('.switch-label-bg').first().evaluate(e => getComputedStyle(e).fill), 'rgb(255, 224, 90)');
    assert.ok(await page.locator('.switch-label').evaluate(e => Number(getComputedStyle(e).fontWeight) >= 700));
    await page.screenshot({ path: path.join(out, 'pcc-events-desktop.png') });
    await page.locator('#themeToggle').click();
    await page.waitForFunction(() => document.querySelector('#brandLogo').complete && document.querySelector('#brandLogo').naturalWidth > 0);
    assert.equal(await page.locator('html').getAttribute('data-theme'), 'light');
    assert.equal(await page.locator('.switch-direction').evaluate(e => getComputedStyle(e).fill), 'rgb(113, 60, 183)');
    graph.nodes[1].state = 'diverging';
    await page.waitForFunction(() => document.querySelector('.switch-direction.diverging'));
    assert.equal(await page.locator('.switch-direction').evaluate(e => getComputedStyle(e).fill), 'rgb(169, 75, 0)');
    assert.ok((await page.locator('#brandLogo').getAttribute('src')).includes('day_logo'));
    assert.equal(await page.locator('.map-shell').evaluate(e => getComputedStyle(e).backgroundColor), 'rgb(245, 247, 248)');
    await page.screenshot({ path: path.join(out, 'pcc-light-desktop.png') });
    await page.reload();
    await page.waitForFunction(() => document.querySelectorAll('.train-row').length === 1);
    assert.equal(await page.locator('html').getAttribute('data-theme'), 'light', 'Theme not retained');
    await page.locator('.train-row').first().click();
    cab = null;
    await page.waitForFunction(() => document.querySelector('#detailAtp').textContent === '--');
    assert.equal(await page.locator('#detailHandle').textContent(), '--');
    cab = { atpMode: 'BYPASS', reverser: 'BACKWARD', powerNotch: 0, brakeNotch: 7, emergencyBrake: true, brakeHold: false };
    await page.waitForFunction(() => document.querySelector('#detailHandle').textContent === 'P0 / EB');
    assert.equal(await page.locator('#detailAtp').textContent(), 'Bypass');
    const before = (await page.locator('#railMap').boundingBox()).height;
    await page.locator('#eventPanel summary').click();
    assert.ok((await page.locator('#railMap').boundingBox()).height > before);
    await page.locator('#eventPanel summary').click();
    status = 'UNAVAILABLE';
    await page.waitForFunction(() => document.querySelector('#eventStatus').textContent.includes('cached'));
    assert.equal(await page.locator('.event-row').count(), 3);
    status = 'AVAILABLE'; epoch = 'session-two'; rows = [rows[0]];
    await page.waitForFunction(() => document.querySelectorAll('.event-row').length === 1);
    mode = 'sse'; rows = [...rows, { sequence: 2, type: 'DRIVER_UNAVAILABLE', reason: 'DISMOUNTED', driverName: 'Passenger_1' }];
    await page.reload();
    await page.waitForFunction(() => document.querySelectorAll('.event-row').length === 2);
    await page.waitForTimeout(600);
    assert.equal(await page.locator('.event-row').count(), 2, 'SSE duplicated history');
    await page.setViewportSize({ width: 390, height: 844 });
    await page.waitForTimeout(200);
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    const eventBox = await page.locator('#eventPanel').boundingBox();
    const mapBox = await page.locator('#railMap').boundingBox();
    assert.ok(mapBox.height > 180 && eventBox.y >= mapBox.y + mapBox.height);
    await page.screenshot({ path: path.join(out, 'pcc-events-mobile.png') });
    await page.locator('.train-row').first().click();
    assert.ok(await page.locator('#inspector').isVisible());
    await page.locator('#followTrain').click();
    await nearCentre(offset);
    await page.screenshot({ path: path.join(out, 'pcc-follow-mobile.png') });
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.locator('#cancelFollow').click();
    await page.locator('#inspectorBack').click();
    assert.ok(await page.locator('.train-list').isVisible());
    awaitingPosition = true;
    await page.reload();
    await page.waitForFunction(() => document.querySelector('.train-row small')?.textContent === 'Awaiting position');
    assert.equal(await page.locator('.train-row .speed').textContent(), '--');
    assert.equal(await page.locator('.train-marker').count(), 0);
    await page.locator('.train-row').click();
    assert.equal(await page.locator('#detailMode').textContent(), '--');
    assert.equal(await page.locator('#detailSpeed').textContent(), '--');
    assert.equal(await page.locator('#followTrain').isDisabled(), true);
    awaitingPosition = false;
    await page.waitForFunction(() => !document.querySelector('#followTrain').disabled);
    assert.equal(await page.locator('#detailName').textContent(), 'test1');
    assert.deepEqual(errors, []);
    console.log('PASS: poll/SSE, event history, theme persistence + logos, switch highlights, follow/cancel/stale, cab/legacy telemetry, desktop/mobile');
  } finally {
    if (browser) await browser.close();
    server.closeAllConnections(); await new Promise(resolve => server.close(resolve));
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
