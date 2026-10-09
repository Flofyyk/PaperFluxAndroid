const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
const cache = new Map();
function load(file) {
  file = path.resolve(__dirname, file);
  if (cache.has(file)) return cache.get(file);
  const module = { exports: {} };
  cache.set(file, module.exports);
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  vm.runInNewContext(code, { module, exports: module.exports, require: name => {
    if (!name.startsWith('.')) return require(name);
    const target = path.resolve(path.dirname(file), name);
    return load(fs.existsSync(`${target}.tsx`) ? `${target}.tsx` : `${target}.ts`);
  }});
  return module.exports;
}
const { transportPresentation, transportStages } = load('../src/utils/transportPresentation.ts');
const { HomeTopBar } = load('../src/components/home/HomeTopBar.tsx');
const { StatusHint } = load('../src/components/home/StatusHint.tsx');
const { StatusCard } = load('../src/components/home/StatusCard.tsx');
const { StageList } = load('../src/components/home/StageList.tsx');
const render = (component, props) => renderToStaticMarkup(React.createElement(component, props));
const profile = transport => ({ id: 'test', name: 'Test', server: 'test', documentUrl: 'test', transport });

test('Mail.ru labels are consistent in header, connection card and stages', () => {
  const selected = profile('mailru');
  const stages = [{ id: 'transport', title: 'Yandex transport', description: 'Yandex Docs', status: 'running' }];
  const html = render(HomeTopBar, { profile: selected }) + render(StatusCard, { profile: selected, status: 'connected' })
    + render(StageList, { stages: transportStages(stages, selected) });
  assert.match(html, /MAIL.RU/);
  assert.match(html, /Mail.ru Документы/);
  assert.doesNotMatch(html, /Yandex|YANDEX|Яндекс|Engine.IO/);
  assert.equal(stages[0].title, 'Yandex transport');
  assert.equal(transportStages(stages, selected)[0].status, 'running');
});

test('Yandex, Volga, legacy, Cups and missing profile have appropriate labels', () => {
  for (const provider of ['yandex', 'vyandex', undefined]) {
    assert.equal(transportPresentation(profile(provider)).badge, 'YANDEX');
    assert.match(render(HomeTopBar, { profile: profile(provider) }), /Яндекс Документы/);
  }
  assert.equal(transportPresentation(profile('cupsonline')).badge, 'CUPS.ONLINE');
  const neutral = render(HomeTopBar, {});
  assert.match(neutral, /VPN/);
  assert.doesNotMatch(neutral, /YANDEX|MAIL.RU/);
});

test('idle hint no longer renders the technical subtitle', () => {
  const html = render(StatusHint, { status: 'idle' });
  assert.match(html, /Нажмите для подключения/);
  assert.equal((html.match(/<span/g) ?? []).length, 1);
  assert.doesNotMatch(html, /Engine.IO|transport/);
  assert.doesNotMatch(render(StatusCard, { status: 'idle', profile: profile('mailru') }), /Yandex|Яндекс/);
});

test('settings omit the tile-add button while the system tile remains registered', () => {
  const { SettingsScreen } = load('../src/screens/SettingsScreen.tsx');
  const { ToastProvider } = load('../src/hooks/useToast.tsx');
  const screen = React.createElement(SettingsScreen, {
    settings: { connectionMode: 'vpn', dnsPrimary: '77.88.8.8', dnsSecondary: '77.88.8.1', mtu: 1400, connectTimeoutSec: 15, autoReconnect: true, autoConnect: false },
    apps: [], onUpdate: () => '', onToggleApp: () => '', onReset: () => '',
  });
  const html = renderToStaticMarkup(React.createElement(ToastProvider, null, screen));
  assert.doesNotMatch(html, /Добавить плитку VPN|addQuickSettingsTile/);
  assert.match(html, /Проверить обновление/);
  const manifest = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/AndroidManifest.xml'), 'utf8');
  assert.match(manifest, /android:name="\.PaperFluxTileService"/);
  assert.match(manifest, /android.permission.BIND_QUICK_SETTINGS_TILE/);
  assert.match(manifest, /android.service.quicksettings.action.QS_TILE/);
  const activity = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/java/com/accar/openflux/MainActivity.kt'), 'utf8');
  assert.doesNotMatch(activity, /addQuickSettingsTile|requestAddTileService/);
});

test('proxy mode describes only SOCKS traffic, hides VPN-only settings, keeps provider label', () => {
  const { SettingsScreen } = load('../src/screens/SettingsScreen.tsx');
  const { ToastProvider } = load('../src/hooks/useToast.tsx');
  const screen = React.createElement(SettingsScreen, {
    settings: { connectionMode: 'proxy', dnsPrimary: '77.88.8.8', dnsSecondary: '77.88.8.1', mtu: 1400, connectTimeoutSec: 15, autoReconnect: true, autoConnect: false },
    apps: [], onUpdate: () => '', onToggleApp: () => '', onReset: () => '', routingMode: 'exclude', onRoutingMode: () => '',
  });
  const html = renderToStaticMarkup(React.createElement(ToastProvider, null, screen));
  assert.match(html, /VPN или прокси/);
  assert.match(html, /127.0.0.1:1080/);
  assert.match(html, /без UDP и IPv6/);
  assert.doesNotMatch(html, /Основной DNS|Максимальный размер пакета|Приложения и VPN/);
  assert.match(render(StatusCard, { status: 'connected', proxy: true }), /Только приложения/);
  assert.match(render(StatusHint, { status: 'connected', proxy: true }), /системный VPN не включён/);
  const stages = transportStages([{ id: 'vpn', title: 'VPN', description: 'TUN', status: 'running' }, { id: 'transport', title: 'Yandex', description: '', status: 'pending' }], profile('mailru'), 'proxy');
  assert.equal(stages[0].title, 'Локальный SOCKS5');
  assert.equal(stages[1].title, 'Mail.ru Документы');
  const manifest = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/AndroidManifest.xml'), 'utf8');
  const proxyService = manifest.match(/<service android:name="\.OpenFluxProxyService"[\s\S]*?<\/service>/)[0];
  assert.doesNotMatch(proxyService, /BIND_VPN_SERVICE|android.net.VpnService|exported="true"/);
});
