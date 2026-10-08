const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function load(window) {
  function moduleAt(file) {
    const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
    const module = { exports: {} };
    vm.runInNewContext(code, { window, module, exports: module.exports,
      require: name => moduleAt(path.resolve(path.dirname(file), `${name}.ts`)) });
    return module.exports;
  }
  return moduleAt(path.resolve(__dirname, '../src/utils/networkSettings.ts'));
}
const defaults = { dnsPrimary: '77.88.8.8', dnsSecondary: '77.88.8.1', mtu: 1400, autoReconnect: true, autoConnect: false, connectTimeoutSec: 15 };

test('native settings, including false switches, survive UI reload', () => {
  let saved = { ...defaults };
  const window = { PaperFluxNative: {
    getNetworkSettings: () => JSON.stringify(saved),
    setNetworkSetting: (key, value) => { saved[key] = typeof saved[key] === 'boolean' ? value === 'true' : typeof saved[key] === 'number' ? Number(value) : value; return ''; },
  }};
  const settings = load(window);
  assert.equal(settings.saveNetworkSetting('dnsPrimary', '1.1.1.1'), '');
  assert.equal(settings.saveNetworkSetting('autoReconnect', false), '');
  assert.equal(settings.saveNetworkSetting('mtu', 1280), '');
  const reopened = load(window).loadNetworkSettings();
  assert.equal(reopened.dnsPrimary, '1.1.1.1');
  assert.equal(reopened.dnsSecondary, '77.88.8.1');
  assert.equal(reopened.autoReconnect, false);
  assert.equal(reopened.mtu, 1280);
});

test('failed persistence and unreadable native file are not reported as success', () => {
  const settings = load({ PaperFluxNative: {
    getNetworkSettings: () => JSON.stringify({ error: 'Cannot read settings' }),
    setNetworkSetting: () => 'Ошибка: не удалось сохранить настройку',
    resetNetworkSettings: () => 'Ошибка: не удалось сбросить настройки',
  }});
  assert.match(settings.saveNetworkSetting('dnsPrimary', '1.1.1.1'), /^Ошибка/);
  assert.match(settings.resetNetworkSettings(), /^Ошибка/);
  assert.throws(() => settings.loadNetworkSettings(), /Cannot read settings/);
});

test('native bridge never silently falls back to browser storage', () => {
  const settings = load({ PaperFluxNative: {} });
  assert.match(settings.saveNetworkSetting('dnsPrimary', '1.1.1.1'), /^Ошибка/);
  assert.throws(() => settings.loadNetworkSettings(), /Android/);
});

test('browser preview persists valid DNS, rejects incomplete edits and resets', () => {
  const values = new Map();
  const window = { localStorage: { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) } };
  const settings = load(window);
  assert.equal(settings.saveNetworkSetting('dnsPrimary', '9.9.9.9'), '');
  assert.match(settings.saveNetworkSetting('dnsPrimary', '9.9.'), /^Ошибка/);
  assert.equal(load(window).loadNetworkSettings().dnsPrimary, '9.9.9.9');
  assert.equal(settings.resetNetworkSettings(), '');
  assert.equal(load(window).loadNetworkSettings().dnsPrimary, '77.88.8.8');
});
