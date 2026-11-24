const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const web = path.resolve(__dirname, '../src/main/resources/web');
const trainId = '10000000-0000-4000-8000-000000000001';
const switchId = '10000000-0000-4000-8000-000000000002';
const secret = 'browser-test-token-not-a-real-secret';
const rail = x => ({ world: 'world', x, y: 64, z: 0 });
const graph = { revision: 1, nodes: [
  { id: 'a', type: 'balise', name: 'A', rail: rail(0) },
  { id: switchId, type: 'switch', name: 'S1', rail: rail(100), state: 'straight',
    ports: { common: 'west', straight: 'east', diverging: 'south' } }
], edges: [{ id: 'e', from: 'a', to: switchId, distanceMeters: 100,
  path: [{ ...rail(0), distanceMeters: 0 }, { ...rail(100), distanceMeters: 100 }] }] };

(async () => {
  const browser = await chromium.launch({ headless: true,
    ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}) });
  try {
    for (const [policy, https, allowed] of [[undefined, false, false], [true, false, false],
      [false, false, true], [true, true, true]]) {
      const context = await browser.newContext();
      const page = await context.newPage();
      page.setDefaultTimeout(8000);
      const errors = [], posts = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.route('**/*', async route => {
        const url = new URL(route.request().url());
        const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
        if (url.pathname === '/api/v5/config') return json({ updateMode: 'poll', pollIntervalMillis: 250,
          controlEnabled: true, ...(policy === undefined ? {} : { requireHttpsForControl: policy }) });
        if (url.pathname === '/api/v5/graph') return json(graph);
        if (url.pathname === '/api/v5/trains') {
          const now = Date.now();
          return json({ schemaVersion: 5, serviceStatus: 'AVAILABLE', graphRevision: 1, serverTimeMillis: now,
            trains: [{ trainId, name: 'T1', mode: 'manual', edgeId: 'e', edgeOffsetMeters: 20,
              edgeLengthMeters: 100, speedMetersPerSecond: 0, observedAtMillis: now,
              quality: 'VALID', graphCurrent: true, stale: false, moving: false }],
            operationalMa: { version: 6, status: 'AVAILABLE', graphRevision: 1, emittedAtMillis: now,
              grants: [], pendingSr: [{ trainId, trainName: 'T1' }] } });
        }
        if (['/api/v5/switch', '/api/v6/sr', '/api/v6/ma/revoke'].includes(url.pathname)) {
          assert.equal(route.request().method(), 'POST');
          assert.equal(route.request().headers().authorization, `Bearer ${secret}`);
          const request = route.request().postDataJSON();
          assert.match(request.requestId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
          posts.push({ path: url.pathname, ...request });
          if (url.pathname === '/api/v5/switch') {
            const attempts = posts.filter(p => p.path === url.pathname);
            return json({ status: attempts.length === 1 ? 'PENDING' : 'COMPLETED', reason: 'VERIFYING' }, attempts.length === 1 ? 202 : 200);
          }
          return json({ status: 'APPLIED', reason: 'APPLIED' });
        }
        if (url.pathname.startsWith('/api/')) return json({ status: 'UNAVAILABLE' });
        const name = url.pathname === '/' ? 'index.html' : url.pathname.replace('/assets/', '');
        if (['index.html','app.js','operating-map.js','i18n.js','styles.css','day_logo.png','night_logo.png'].includes(name))
          return route.fulfill({ path: path.join(web, name) });
        return route.fulfill({ status: 404, body: '' });
      });
      try {
        await page.goto(`${https ? 'https' : 'http'}://pcc.test/`);
        await page.locator('.train-row').waitFor();
        assert.equal(await page.evaluate(() => window.isSecureContext), https);
        if (!https) assert.equal(await page.evaluate(() => typeof crypto.randomUUID), 'undefined');
        await page.locator(`[data-switch-id="${switchId}"]`).first().click();
        await page.locator('#infrastructureSwitch').click();
        assert.equal(await page.locator('#switchToken').isEnabled(), allowed);
        assert.equal(await page.locator('#switchConfirm').isEnabled(), allowed);
        if (allowed) {
          await page.locator('#switchToken').fill(secret);
          await page.locator('#switchConfirm').click();
          await page.waitForFunction(() => document.querySelector('#switchResult').textContent === PccI18n.t('Conversion confirmed'));
          assert.equal(await page.locator('#switchToken').inputValue(), '');
          const attempts = posts.filter(p => p.path === '/api/v5/switch');
          assert.equal(attempts.length, 2);
          assert.equal(attempts[0].requestId, attempts[1].requestId, 'Polling must retain the request UUID');
        }
        await page.locator('#switchClose').click();
        await page.selectOption('#srTrainSelect', trainId);
        await page.locator('#srApprove').click();
        assert.equal(await page.locator('#srToken').isEnabled(), allowed);
        assert.equal(await page.locator('#srConfirm').isEnabled(), allowed);
        if (allowed) {
          await page.locator('#srToken').fill(secret);
          await page.locator('#srConfirm').click();
          await page.waitForFunction(() => document.querySelector('#srResult').textContent === PccI18n.t('SR approved'));
          assert.equal(posts.find(p => p.path === '/api/v6/sr').targetNodeId, switchId);
          assert.equal(await page.locator('#srToken').inputValue(), '');
        }
        await page.locator('#srClose').click();
        await page.locator('.train-row').first().click();
        await page.locator('#revokeMa').click();
        assert.equal(await page.locator('#revokeDialog').isVisible(), allowed);
        if (allowed) {
          await page.locator('#revokeToken').fill(secret);
          await page.locator('#revokeConfirm').click();
          await page.waitForFunction(() => document.querySelector('#revokeResult').textContent.includes(PccI18n.code('APPLIED')));
          assert.equal(posts.find(p => p.path === '/api/v6/ma/revoke').trainId, trainId);
          assert.equal(new Set(posts.map(p => p.requestId)).size, 3);
          assert.equal(await page.locator('#revokeToken').inputValue(), '');
        } else assert.equal(posts.length, 0);
        assert.deepEqual(errors, []);
        console.log(`PASS policy=${policy ?? 'missing'} ${https ? 'HTTPS' : 'HTTP'}: controls ${allowed ? 'allowed' : 'blocked'}`);
      } finally { await context.close(); }
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
