// Run with Playwright on NODE_PATH; real production CSS, not class-name mocks.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const { readFileSync, mkdirSync } = require('node:fs');
const { execFileSync } = require('node:child_process');
const http = require('node:http');
const path = require('node:path');

const project = path.resolve(__dirname, '../..');
const current = readFileSync(path.join(project, 'web/dist/index.html'));
// Optional old release comparison; future commits must not become a failing baseline.
const previous = process.env.PF_LAYOUT_BASELINE
  ? execFileSync('git', ['show', `${process.env.PF_LAYOUT_BASELINE}:app/src/main/assets/paperflux/index.html`], { cwd: project, maxBuffer: 2 ** 22 })
  : null;
const server = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.end(req.url === '/before' && previous ? previous : current);
});
const report = path.resolve(project, '../output/home-layout-20261007');

async function measure(page) {
  return page.getByRole('heading', { name: 'Этапы подключения' }).evaluate(heading => {
    const card = heading.parentElement;
    const rect = card.getBoundingClientRect();
    const parent = card.parentElement;
    const parentRect = parent.getBoundingClientRect();
    const css = getComputedStyle(parent);
    const available = parentRect.width - parseFloat(css.paddingLeft) - parseFloat(css.paddingRight);
    const rows = [...card.querySelectorAll('p')].filter(p => p.nextElementSibling?.tagName === 'SPAN');
    const overlaps = rows.filter(title => {
      const a = title.getBoundingClientRect(), b = title.nextElementSibling.getBoundingClientRect();
      return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
    }).length;
    const escaped = [...card.querySelectorAll('*')].filter(node => {
      const r = node.getBoundingClientRect();
      return r.left < rect.left - 1 || r.right > rect.right + 1;
    }).length;
    return { width: rect.width, available, overlaps, escaped };
  });
}

(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ headless: true, channel: process.env.PF_BROWSER || 'chrome' });
  try {
    const context = await browser.newContext({ viewport: { width: 432, height: 960 }, isMobile: true, deviceScaleFactor: 1 });
    await context.addInitScript(() => {
      window.PaperFluxNative = {
        getProfiles: () => JSON.stringify({ activeId: 'fixture', profiles: [{ id: 'fixture', name: 'Тестовый профиль', server: '203.0.113.10', documentUrl: '', transport: 'mailru', countryCode: 'NL' }] }),
        getState: () => JSON.stringify({ state: 'TRANSPORT', detail: 'Ожидаем защищённое соединение' }),
        getSessionLogs: () => '{"events":[]}', getAutoReconnect: () => true,
      };
    });
    const page = await context.newPage();
    const url = `http://127.0.0.1:${server.address().port}`;
    if (previous) {
      await page.goto(url + '/before');
      await page.getByRole('heading', { name: 'Этапы подключения' }).waitFor();
      const baseline = await measure(page);
      assert(baseline.width < baseline.available * .7, 'Original half-width regression must reproduce');
      console.log(JSON.stringify({ baseline }));
    }
    const cases = [];
    for (const width of [320, 360, 393, 420, 432, 480, 600, 800, 980]) {
      for (const textScale of [1, 1.5, 2]) {
        await page.setViewportSize({ width, height: 960 });
        await page.goto(url);
        await page.getByRole('heading', { name: 'Этапы подключения' }).waitFor();
        await page.evaluate(scale => {
          const nodes = [...document.querySelectorAll('*')].filter(node => [...node.childNodes].some(child => child.nodeType === 3 && child.textContent.trim()));
          const sizes = nodes.map(node => parseFloat(getComputedStyle(node).fontSize));
          nodes.forEach((node, i) => { node.style.fontSize = `${sizes[i] * scale}px`; });
        }, textScale);
        await page.getByRole('heading', { name: 'Этапы подключения' }).scrollIntoViewIfNeeded();
        const metrics = await measure(page);
        assert(metrics.width >= metrics.available * .98, `Card must fill width ${width}/${textScale}`);
        assert.equal(metrics.overlaps, 0, `Title/status overlap ${width}/${textScale}`);
        assert.equal(metrics.escaped, 0, `Content outside card ${width}/${textScale}`);
        cases.push({ viewportWidth: width, textScale, ...metrics });
        if (width === 432 && textScale === 1) {
          mkdirSync(report, { recursive: true });
          await page.screenshot({ path: path.join(report, 'home-stages-fixed.png') });
        }
      }
    }
    console.log(JSON.stringify({ passed: cases.length, cases }));
  } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
