const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const web = path.resolve(__dirname, '../src/main/resources/web');
const rail = x => ({ world: 'world', x, y: 64, z: 0 });
let mode, revision, switchState, graphRequests, failedRequests, failNext, delay, requestsInFlight, maxInFlight;
const graph = rev => ({ revision: rev, nodes: [
  { id: 'a', type: 'balise', name: 'West', rail: rail(0) },
  { id: 'b', type: 'switch', name: '203', rail: rail(100), state: 'straight', ports: { straight: 'east', diverging: 'south' } }
], edges: [{ id: 'e1', from: 'a', to: 'b', distanceMeters: 100,
  path: [{ ...rail(0), distanceMeters: 0 }, { ...rail(100), distanceMeters: 100 }] }] });
const payload = () => ({ schemaVersion: 5, graphRevision: revision, serverTimeMillis: Date.now(),
  serviceStatus: 'AVAILABLE', trains: [], switchStates: { b: switchState } });
const server = http.createServer((req, res) => {
  const json = data => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(data)); };
  if (req.url === '/api/v5/config') return json({ updateMode: mode, pollIntervalMillis: 250 });
  if (req.url === '/api/v5/trains') return json(payload());
  if (req.url === '/api/v5/graph') {
    graphRequests++;
    const requestedRevision = revision;
    const fail = failNext; failNext = false;
    requestsInFlight++; maxInFlight = Math.max(maxInFlight, requestsInFlight);
    return setTimeout(() => {
      requestsInFlight--;
      if (fail) { failedRequests++; res.writeHead(503); res.end('{}'); }
      else json(graph(requestedRevision));
    }, delay);
  }
  if (req.url === '/api/v5/events') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream' });
    const send = () => res.write(`event: snapshot\ndata: ${JSON.stringify(payload())}\n\n`);
    send(); const timer = setInterval(send, 100);
    res.on('close', () => clearInterval(timer)); return;
  }
  const file = req.url === '/' ? 'index.html' : req.url.replace('/assets/', '');
  if (!['index.html', 'app.js', 'operating-map.js', 'i18n.js', 'styles.css', 'day_logo.png', 'night_logo.png'].includes(file)) {
    res.writeHead(404); res.end(); return;
  }
  res.writeHead(200, { 'Content-Type': file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css'
    : file.endsWith('.png') ? 'image/png' : 'text/html' });
  res.end(fs.readFileSync(path.join(web, file)));
});

(async () => {
  let browser;
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    browser = await chromium.launch({ headless: true,
      ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}) });
    for (mode of ['poll', 'sse']) {
      revision = 1; switchState = 'diverging'; graphRequests = 0; failedRequests = 0;
      failNext = false; delay = 650; requestsInFlight = 0; maxInFlight = 0;
      const page = await browser.newPage();
      const errors = []; page.on('pageerror', error => errors.push(error.message));
      const waitRevision = rev => page.waitForFunction(rev => document.querySelector('#graphRevision').textContent === String(rev), rev);
      await page.goto(`http://127.0.0.1:${server.address().port}`);
      await waitRevision(1);
      // A snapshot arrives before the slow topology download. Its point state must win.
      await page.waitForSelector('.switch-direction.diverging');
      await page.waitForTimeout(2300);
      assert.equal(graphRequests, 1, `${mode}: unchanged topology downloaded again`);
      switchState = 'straight';
      await page.waitForSelector('.switch-direction.straight');
      assert.equal(graphRequests, 1, `${mode}: switching points downloaded topology`);

      // Fail one rebuild download: the next snapshot must retry, without duplicate requests.
      failNext = true; revision = 2;
      await waitRevision(2);
      assert.equal(failedRequests, 1);
      assert.equal(graphRequests, 3, `${mode}: rebuild should fail once then recover`);

      // A second rebuild while a slow download is pending must eventually converge.
      revision = 3;
      await page.waitForFunction(() => document.querySelector('#graphRevision').textContent === '2');
      for (let attempt = 0; graphRequests < 4 && attempt < 100; attempt++)
        await new Promise(resolve => setTimeout(resolve, 20));
      assert.equal(graphRequests, 4, `${mode}: new revision did not start a download`);
      revision = 4; switchState = 'diverging';
      await waitRevision(4);
      await page.waitForSelector('.switch-direction.diverging');
      assert.equal(graphRequests, 5);
      assert.equal(maxInFlight, 1, `${mode}: concurrent topology requests`);
      await page.waitForTimeout(2300);
      assert.equal(graphRequests, 5, `${mode}: rebuilt topology downloaded again`);
      await page.reload();
      await waitRevision(4);
      await page.waitForSelector('.switch-direction.diverging');
      assert.equal(graphRequests, 6, `${mode}: new visit must fetch topology once`);
      assert.deepEqual(errors, []);
      await page.close();
      console.log(`PASS ${mode}: one initial graph, live points, rebuild, retry, in-flight rebuild and reload`);
    }
  } finally {
    if (browser) await browser.close();
    server.closeAllConnections(); await new Promise(resolve => server.close(resolve));
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
