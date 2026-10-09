const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function load(file, context = {}) {
  const cache = new Map();
  function moduleAt(target) {
    target = path.resolve(__dirname, target);
    if (cache.has(target)) return cache.get(target);
    const module = { exports: {} };
    const code = ts.transpileModule(fs.readFileSync(target, 'utf8'), { compilerOptions: {
      module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
    } }).outputText;
    vm.runInNewContext(code, { ...context, module, exports: module.exports, require: name => {
      if (name === 'react' && context.react) return context.react;
      if (!name.startsWith('.')) return require(name);
      const relative = path.resolve(path.dirname(target), name);
      return moduleAt(fs.existsSync(relative + '.tsx') ? relative + '.tsx' : relative + '.ts');
    } });
    cache.set(target, module.exports);
    return module.exports;
  }
  return moduleAt(file);
}

function hooks() {
  let cursor = 0;
  const slots = [];
  const effects = new Set();
  const react = {
    useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial;
      return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value; }]; },
    useRef(value) { const i = cursor++; if (!(i in slots)) slots[i] = { current: value }; return slots[i]; },
    useEffect(effect) { const i = cursor++; if (!effects.has(i)) { effects.add(i); effect(); } },
    useCallback(fn) { cursor++; return fn; },
  };
  return { react, render: fn => { cursor = 0; return fn(); } };
}

test('MTU field can be cleared and typed character-by-character without persisting 576', () => {
  const h = hooks();
  const { InlineNumberField } = load('../src/components/settings/InlineNumberField.tsx', { react: h.react });
  let value = 1400;
  const saved = [];
  const input = () => h.render(() => InlineNumberField({ value, min: 576, max: 1500,
    onChange: next => { value = next; saved.push(next); return ''; } })).props.children[0];
  input().props.onFocus();
  for (const draft of ['', '1', '12', '128', '1280']) {
    input().props.onChange({ target: { value: draft } });
    assert.equal(input().props.value, draft);
  }
  assert.deepEqual(saved, [1280]);
  input().props.onBlur();
  assert.equal(input().props.value, '1280');
  input().props.onFocus(); input().props.onChange({ target: { value: '9999' } });
  assert.equal(value, 1280);
  input().props.onBlur(); assert.equal(input().props.value, '1280');
});

test('invalid drafts and a failed save cannot overwrite the accepted number', () => {
  const { validNumberDraft } = load('../src/utils/numberDraft.ts');
  for (const raw of ['', '140', '1e3', '-1280', '1280.5', '1501']) assert.equal(validNumberDraft(raw, 576, 1500), null);
  assert.equal(validNumberDraft('1280', 576, 1500), 1280);
  const h = hooks();
  const { InlineNumberField } = load('../src/components/settings/InlineNumberField.tsx', { react: h.react });
  const input = () => h.render(() => InlineNumberField({ value: 1400, min: 576, max: 1500, onChange: () => 'Ошибка' })).props.children[0];
  input().props.onFocus(); input().props.onChange({ target: { value: '1280' } });
  input().props.onBlur(); assert.equal(input().props.value, '1400');
});

test('five-second command cooldown survives recreation and rejects repeated clicks', () => {
  const { VpnCommandGuard } = load('../src/utils/vpnCommandGuard.ts');
  let saved = null;
  const create = () => new VpnCommandGuard(() => saved, value => { saved = value; });
  const guard = create();
  assert.equal(guard.claim(10000), true);
  for (let i = 0; i < 100; i++) assert.equal(guard.claim(10000 + i), false);
  assert.equal(create().remaining(14000), 1000);
  assert.equal(create().claim(14999), false);
  assert.equal(create().claim(15000), true);
  saved = '99999999999999'; assert.equal(create().remaining(20000), 0);
});

test('real VPN hook sends one STOP and cannot START before stop confirmation even after five seconds', () => {
  const h = hooks(); let now = 10000; let stops = 0; let starts = 0;
  const saved = new Map();
  const window = { localStorage: { getItem: k => saved.get(k) ?? null, setItem: (k, v) => saved.set(k, v) },
    setInterval: () => 1, clearInterval: () => {}, PaperFluxNative: {
      getState: () => JSON.stringify({ state: 'CONNECTED' }), getSessionLogs: () => '{}',
      disconnect: () => stops++, connect: () => starts++,
    } };
  class Clock extends Date { static now() { return now; } }
  const { useVpn } = load('../src/hooks/useVpn.ts', { react: h.react, window, Date: Clock });
  const hook = () => h.render(() => useVpn({ autoReconnect: true, timeoutSec: 15 }));
  const vpn = hook();
  for (let i = 0; i < 100; i++) vpn.disconnect();
  assert.equal(stops, 1);
  now = 16000; hook().connect(); assert.equal(starts, 0);
  window.__paperFluxOnState(JSON.stringify({ state: 'DISCONNECTED' }));
  for (let i = 0; i < 100; i++) hook().connect();
  assert.equal(starts, 1);
});

test('routing mode and both lists persist; native errors do not fall back to browser storage', () => {
  let saved = { version: 2, mode: 'exclude', excluded: ['app.direct'], included: ['app.vpn'] };
  const window = { PaperFluxNative: {
    getAppRouting: () => JSON.stringify(saved), setAppRouting: raw => { saved = JSON.parse(raw); return ''; },
  } };
  const routing = load('../src/utils/appRouting.ts', { window });
  assert.equal(routing.saveAppRouting({ ...saved, mode: 'include' }), '');
  const reopened = load('../src/utils/appRouting.ts', { window }).loadAppRouting();
  assert.equal(reopened.mode, 'include');
  assert.equal(JSON.stringify(reopened.excluded), '["app.direct"]');
  assert.equal(JSON.stringify(reopened.included), '["app.vpn"]');
  window.PaperFluxNative.setAppRouting = () => 'Ошибка: запись';
  assert.match(routing.saveAppRouting({ ...saved, mode: 'exclude' }), /^Ошибка/);
  assert.equal(saved.mode, 'include');
});

test('the actual connection button is disabled and renders the countdown', () => {
  const React = require('react');
  const { renderToStaticMarkup } = require('react-dom/server');
  const { ConnectButton } = load('../src/components/home/ConnectButton.tsx');
  const html = renderToStaticMarkup(React.createElement(ConnectButton, { status: 'idle', onPress: () => {}, disabled: true, cooldownSeconds: 5 }));
  assert.match(html, /disabled=""/);
  assert.match(html, /Подождите 5 сек/);
  assert.match(html, />5<\/span>/);
});
