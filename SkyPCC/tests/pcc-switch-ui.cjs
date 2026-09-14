const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = path.resolve(__dirname, '..');
const web = path.join(root, 'src/main/resources/web');
const out = path.join(root, 'target/pcc-switch-ui');
const id = '10000000-0000-4000-8000-000000000001';
const nodes = [
  { id: 'a', type: 'balise', name: '001', line: 'test', rail: { world: 'world', x: 0, y: 64, z: 0 } },
  { id, type: 'switch', name: '203', line: 'test', state: 'straight', ports: { common: 'west', straight: 'east', diverging: 'south' }, rail: { world: 'world', x: 100, y: 64, z: 0 } },
  { id: 'b', type: 'end', name: 'END', line: 'test', rail: { world: 'world', x: 200, y: 64, z: 0 } }
];
const graph = { revision: 12, nodes, edges: [0, 1].map(i => ({ id: `edge${i}`, from: nodes[i].id, to: nodes[i + 1].id,
  distanceMeters: 100, path: [nodes[i].rail, nodes[i + 1].rail].map((p, j) => ({ ...p, distanceMeters: j * 100 })) })) };
(async () => {
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHANNEL ? {channel: process.env.PLAYWRIGHT_CHANNEL} : {}) });
  try {
    for (const [theme, size] of [['dark', { width: 1440, height: 900 }], ['light', { width: 390, height: 844 }]]) {
      const context = await browser.newContext({ viewport: size });
      await context.addInitScript(theme => localStorage.setItem('skypcc.theme', theme), theme);
      const page = await context.newPage(); const errors = []; const requests = [];
      page.on('pageerror', e => errors.push(e.message));
      let complete = false;
      await page.route('**/*', async route => {
        const url = new URL(route.request().url());
        const json = data => route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) });
        if (url.pathname === '/api/v1/graph') return json(graph);
        if (url.pathname === '/api/v1/config') return json({ updateMode: 'poll', pollIntervalMillis: 1000, controlEnabled: true });
        if (url.pathname === '/api/v1/trains') return json({ trains: [], emittedAtMillis: Date.now() });
        if (url.pathname === '/api/v4/switch') {
          const request = route.request().postDataJSON(); requests.push(request);
          assert.deepEqual(request.position, nodes[1].rail);
          assert.equal(request.graphRevision, 12);
          assert.equal(route.request().headers().authorization, 'Bearer test-token-not-a-real-secret');
          return route.fulfill({ status: complete ? 200 : 202, contentType: 'application/json', body: JSON.stringify({ requestId: request.requestId,
            status: complete ? 'COMPLETED' : 'PENDING', reason: complete ? 'COMPLETED' : 'LOADING_CHUNKS' }) });
        }
        const name = url.pathname === '/' ? 'index.html' : url.pathname.replace('/assets/', '');
        if (['index.html', 'app.js', 'styles.css', 'day_logo.png', 'night_logo.png'].includes(name))
          return route.fulfill({ path: path.join(web, name) });
        return route.fulfill({ status: 404, body: '' });
      });
      await page.goto('http://127.0.0.1:18767/');
      await page.locator('.switch-hit').first().click();
      await page.locator('#switchToken').fill('test-token-not-a-real-secret');
      await page.locator('#switchConfirm').click();
      await page.waitForFunction(() => document.querySelector('#switchResult').textContent === 'PENDING: Loading target chunks');
      assert.equal(await page.locator('#switchConfirm').isDisabled(), true);
      assert.equal(await page.locator('#switchToken').inputValue(), '');
      const dialog = await page.locator('#switchDialog').boundingBox();
      assert(dialog.x >= 0 && dialog.x + dialog.width <= size.width + 1);
      await page.screenshot({ path: path.join(out, `${theme}-pending.png`), fullPage: true });
      await page.waitForTimeout(650);
      complete = true;
      await page.waitForFunction(() => document.querySelector('#switchResult').textContent === 'Conversion confirmed');
      assert(requests.length >= 2);
      assert.equal(new Set(requests.map(r => r.requestId)).size, 1);
      assert.deepEqual(errors, []);
      await page.screenshot({ path: path.join(out, `${theme}-completed.png`), fullPage: true });
      await context.close();
    }
    console.log('PASS PCC desktop/mobile themes, coordinate payload, pending, same-ID polling, token clearing and completion');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
