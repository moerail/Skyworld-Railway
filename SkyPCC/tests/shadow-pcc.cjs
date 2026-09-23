const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const web = path.resolve(__dirname, '../src/main/resources/web');
const out = path.join(__dirname, '../target/shadow-pcc');
fs.mkdirSync(out, { recursive: true });
const ids = Array.from({ length: 5 }, (_, i) => `00000000-0000-4000-8000-00000000000${i}`);
const trainId = '10000000-0000-4000-8000-000000000001';
let pointState = 'straight', stale = false, posts = 0, schemaVersion = 5, messageId = 1003;
const nodes = ids.map((id, i) => ({ id, name: ['Origin', '0020', '203', '0022', 'End'][i], type: i === 2 ? 'switch' : i === 4 ? 'end' : 'balise',
  line: 'Main', rail: { world: 'world', x: i * 100, y: 64, z: i === 4 ? 60 : 0 }, ports: { common: 'west', straight: 'east', diverging: 'south' }, state: 'straight' }));
const edges = Array.from({ length: 4 }, (_, i) => ({ id: `e${i}`, from: ids[i], to: ids[i + 1], distanceMeters: 100,
  sourcePort: 'east', targetPort: 'west', path: [{ ...nodes[i].rail, distanceMeters: 0 }, { ...nodes[i + 1].rail, distanceMeters: 100 }] }));
function snapshot() {
  const now = Date.now();
  return { schemaVersion, serviceStatus: 'AVAILABLE', graphRevision: 1, serverTimeMillis: now,
    operationalEvents: { status: 'AVAILABLE', session: 'test', latestSequence: 0, events: [] },
    trains: [{ trainId, name: 'Test 01', edgeId: 'e0', edgeOffsetMeters: 35, edgeLengthMeters: 100, observedAtMillis: now,
      x: 35, y: 64, z: 0, graphCurrent: true, stale: false, quality: 'VALID', line: 'Main', mode: 'manual',
      driver: 'Test driver', direction: 'forward', speedMetersPerSecond: 0, moving: false,
      cab: { atpMode: 'SHADOW', reverser: 'FORWARD', powerNotch: 0, brakeNotch: 7, emergencyBrake: false, brakeHold: false } }],
    shadowMa: { version:5, simulationOnly: true, executable: false, session: 'test', sequence: 1, emittedAtMillis: now - (stale ? 10000 : 0),
      status: 'SHADOW', graphRevision: 1, sections: edges.map((e, i) => ({ edgeId: e.id, resourceId: e.id,
        state: ['OCCUPIED', 'RESERVED_SHADOW', 'UNCERTAIN', 'UNALLOCATED'][i], occupants: i === 0 ? [trainId] : [], reservations: i === 1 ? [trainId] : [] })),
      authorities: [{ trainId, state: 'ALLOCATED_SHADOW', NID_MESSAGE: messageId, NID_PACKET: 1015, reason: 'RESOURCE_CONFLICT', signedRemainingMeters: 140,
        eoaEdgeId: 'e1', eoaOffsetMeters: 75, path: [] }] } };
}
const server = http.createServer(async (req, res) => {
  const json = value => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(value)); };
  if (req.url === '/api/v5/config') return json({ updateMode: 'poll', pollIntervalMillis: 250, controlEnabled: true });
  if (req.url === '/api/v5/graph') { nodes[2].state = pointState; return json({ revision: 1, nodes, edges }); }
  if (req.url === '/api/v5/trains') return json(snapshot());
  if (req.url === '/api/v5/switch') {
    posts++; let raw = ''; for await (const chunk of req) raw += chunk;
    const data = JSON.parse(raw); assert.equal(data.switchId, ids[2]); assert.equal(data.expectedState, 'straight');
    assert.equal(req.headers.authorization, 'Bearer 12345678901234567890123456789012');
    pointState = 'diverging'; return json({ requestId: data.requestId, status: 'COMPLETED', reason: 'APPLIED' });
  }
  const asset = req.url === '/' ? 'index.html' : req.url.replace('/assets/', '');
  if (!['index.html', 'styles.css', 'app.js', 'i18n.js', 'day_logo.png', 'night_logo.png'].includes(asset)) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', asset.endsWith('.js') ? 'text/javascript' : asset.endsWith('.css') ? 'text/css' : asset.endsWith('.png') ? 'image/png' : 'text/html');
  res.end(fs.readFileSync(path.join(web, asset)));
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHANNEL ? {channel: process.env.PLAYWRIGHT_CHANNEL} : {}) });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = []; page.on('pageerror', e => errors.push(e.message));
    await page.goto(`http://127.0.0.1:${server.address().port}`);
    await page.waitForSelector('.rail-edge[data-occupancy="RESERVED_SHADOW"]', { state: 'attached' });
    await page.locator('.train-row').click();
    await page.waitForFunction(() => document.querySelector('#detailMa').textContent.includes('140.0'));
    await page.waitForSelector('.eoa-marker');
    messageId = 2001;
    await page.waitForSelector('.rail-edge[data-occupancy="UNKNOWN"]', { state: 'attached' });
    assert.equal(await page.locator('.eoa-marker').count(), 0);
    messageId = 1003;
    await page.waitForSelector('.eoa-marker');
    schemaVersion = 1;
    await page.waitForFunction(() => document.querySelectorAll('.train-row').length === 0);
    assert.equal(await page.locator('.eoa-marker').count(), 0);
    schemaVersion = 5;
    await page.waitForSelector('.train-row');
    await page.locator('.train-row').click();
    await page.waitForSelector('.eoa-marker');
    assert.equal(await page.locator('.rail-edge[data-occupancy="OCCUPIED"]').count(), 1);
    assert.equal(await page.locator('.rail-edge[data-occupancy="UNCERTAIN"]').count(), 1);
    const map = await page.locator('#railMap').boundingBox();
    await page.mouse.move(map.x + 40, map.y + 40); await page.mouse.down(); await page.mouse.move(map.x + 70, map.y + 70); await page.mouse.up();
    assert.equal(await page.locator('#detailName').textContent(), 'Test 01');
    await page.screenshot({ path: path.join(out, 'shadow-dark.png') });
    await page.locator('#themeToggle').click();
    await page.screenshot({ path: path.join(out, 'shadow-light.png') });
    await page.locator(`.rail-node[data-switch-id="${ids[2]}"]`).click();
    await page.locator('#infrastructureSwitch').click();
    await page.locator('#switchToken').fill('12345678901234567890123456789012');
    await page.locator('#switchConfirm').click();
    await page.waitForFunction(() => document.querySelector('#switchResult').textContent === 'Conversion confirmed');
    assert.equal(posts, 1); assert.equal(await page.locator('#switchToken').inputValue(), '');
    await page.screenshot({ path: path.join(out, 'shadow-switch.png') });
    await page.locator('#switchClose').click();
    await page.waitForSelector('.switch-direction.diverging');
    stale = true;
    await page.waitForSelector('.rail-edge[data-occupancy="UNKNOWN"]', { state: 'attached' });
    assert.equal(await page.locator('.eoa-marker').count(), 0);
    stale = false;
    await page.setViewportSize({ width: 390, height: 844 });
    await page.locator('#inspectorBack').click(); await page.locator('#fitButton').evaluate(b => b.click());
    await page.waitForSelector('.rail-edge[data-occupancy="RESERVED_SHADOW"]', { state: 'attached' });
    await page.screenshot({ path: path.join(out, 'shadow-mobile.png') });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    assert.deepEqual(errors, []);
    console.log('PASS browser: MA colors, EoA, sticky selection, point click + confirmation, stale expiry, dark/light/mobile');
  } finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(e => { console.error(e); process.exitCode = 1; });
