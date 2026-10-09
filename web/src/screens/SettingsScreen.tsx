import { RefreshCw, RotateCcw, ShieldOff, Timer, Wifi } from "lucide-react";
import { memo, useDeferredValue, useEffect, useMemo, useRef, useState } from "react";
import type { AppExceptionItem, VpnSettings } from "../types";
import { ScreenHeader } from "../components/ui/ScreenHeader";
import { SettingRow, SettingsSection } from "../components/settings/SettingRow";
import { InlineNumberField, InlineTextField } from "../components/settings/InlineNumberField";
import { Switch } from "../components/ui/Switch";
import { AppExceptionRow } from "../components/settings/AppExceptionRow";
import { useToast } from "../hooks/useToast";
import type { AppRoutingMode } from "../utils/appRouting";

const APP_ROW_HEIGHT = 62;
const APP_ROW_OVERSCAN = 5;

export const SettingsScreen = memo(function SettingsScreen({
  settings,
  settingsError,
  onUpdate,
  apps,
  onToggleApp,
  onReset,
  routingMode,
  onRoutingMode,
}: {
  settings: VpnSettings;
  settingsError?: string;
  onUpdate: <K extends keyof VpnSettings>(key: K, value: VpnSettings[K]) => string;
  apps: AppExceptionItem[];
  onToggleApp: (id: string, value: boolean) => string;
  onReset: () => string;
  routingMode: AppRoutingMode;
  onRoutingMode: (mode: AppRoutingMode) => string;
}) {
  const { show } = useToast();
  const [appQuery, setAppQuery] = useState("");
  const deferredAppQuery = useDeferredValue(appQuery);
  const [appScrollTop, setAppScrollTop] = useState(0);
  const appList = useRef<HTMLDivElement>(null);
  const visibleApps = useMemo(() => {
    const query = deferredAppQuery.toLocaleLowerCase("ru-RU");
    return query ? apps.filter((app) => `${app.name} ${app.pkg}`.toLocaleLowerCase("ru-RU").includes(query)) : apps;
  }, [apps, deferredAppQuery]);
  useEffect(() => {
    setAppScrollTop(0);
    if (appList.current) appList.current.scrollTop = 0;
  }, [deferredAppQuery]);
  const firstApp = Math.max(0, Math.floor(appScrollTop / APP_ROW_HEIGHT) - APP_ROW_OVERSCAN);
  const lastApp = Math.min(visibleApps.length, firstApp + Math.ceil(288 / APP_ROW_HEIGHT) + APP_ROW_OVERSCAN * 2);
  const renderedApps = visibleApps.slice(firstApp, lastApp);
  const update = <K extends keyof VpnSettings,>(key: K, value: VpnSettings[K]) => {
    const error = onUpdate(key, value);
    if (error) show(error);
    return error;
  };

  return (
    <div className="h-full overflow-y-auto">
      <ScreenHeader title="Настройки" subtitle="Транспорт, сеть и поведение приложения" />

      <div className="flex flex-col gap-3.5 px-5 pb-8">
        {settingsError && <p role="alert" className="text-error">{settingsError}</p>}
        <SettingsSection title="Режим подключения">
          <div role="group" aria-label="VPN или прокси" className="grid grid-cols-2 gap-2 py-2">
            {([ ["vpn", "VPN"], ["proxy", "Прокси"] ] as const).map(([mode, label]) =>
              <button key={mode} type="button" aria-pressed={settings.connectionMode === mode}
                onClick={() => update("connectionMode", mode)}
                className={`min-h-11 rounded-xl px-3 py-2 text-[13px] font-semibold ${settings.connectionMode === mode ? "bg-primary text-on-primary" : "bg-surface-container text-on-surface"}`}>{label}</button>)}
          </div>
          <p className="py-2 text-[12px] leading-snug text-on-surface-variant">
            {settings.connectionMode === "proxy" ? "SOCKS5: 127.0.0.1:1080, без логина и пароля. Укажите этот адрес в нужном приложении. Поддерживается TCP и DNS через прокси, без UDP и IPv6. Системный VPN не включается; остальной трафик идёт обычным путём. Если другое приложение создаёт VPN, исключите PaperFlux из него, чтобы избежать петли подключения." : "Создаётся системный VPN-интерфейс с настройками DNS, MTU и выбором приложений."}
            {" "}Перед сменой режима отключите PaperFlux.
          </p>
        </SettingsSection>
        {settings.connectionMode === "vpn" &&
        <SettingsSection title="Сеть">
          <p className="py-2 text-[12px] text-on-surface-variant">DNS и MTU сохраняются сразу и применяются после переподключения VPN.</p>
          <SettingRow
            icon={<Wifi className="h-4.5 w-4.5" strokeWidth={2} />}
            title="Основной DNS"
            supporting="Используется для резолвинга внутри туннеля"
            trailing={
              <div className="w-[108px]">
                <InlineTextField value={settings.dnsPrimary} onChange={(v) => onUpdate("dnsPrimary", v)} onError={show} />
              </div>
            }
            className="items-start"
          />
          <SettingRow
            icon={<Wifi className="h-4.5 w-4.5" strokeWidth={2} />}
            title="Резервный DNS"
            supporting="Применяется при недоступности основного сервера"
            trailing={
              <div className="w-[108px]">
                <InlineTextField value={settings.dnsSecondary} onChange={(v) => onUpdate("dnsSecondary", v)} onError={show} />
              </div>
            }
            className="items-start"
          />
          <SettingRow
            icon={<RefreshCw className="h-4.5 w-4.5" strokeWidth={2} />}
            title="MTU"
            supporting="Максимальный размер пакета для VPN-интерфейса"
            trailing={<InlineNumberField value={settings.mtu} min={576} max={1500} suffix="Б" onChange={(v) => update("mtu", v)} />}
          />
          <SettingRow
            icon={<Timer className="h-4.5 w-4.5" strokeWidth={2} />}
            title="Таймаут подключения"
            supporting="Время ожидания ответа перед сигналом ошибки"
            trailing={
              <InlineNumberField
                value={settings.connectTimeoutSec}
                min={5}
                max={120}
                suffix="сек"
                onChange={(v) => update("connectTimeoutSec", v)}
              />
            }
          />
        </SettingsSection>}

        <SettingsSection title="Поведение">
          <button type="button" onClick={() => {
            const native = (window as Window & { PaperFluxNative?: { checkAppUpdates?: (manual: boolean) => void } }).PaperFluxNative;
            if (native?.checkAppUpdates) { native.checkAppUpdates(true); show("Проверяем GitHub…"); }
            else show("Проверка обновлений доступна в Android-приложении");
          }} className="w-full py-3 text-left text-[13.5px] font-semibold text-primary">Проверить обновление</button>
          <SettingRow
            title="Автоматическое переподключение"
            supporting="Восстанавливать туннель при обрыве без участия пользователя"
            trailing={
              <Switch
                checked={settings.autoReconnect}
                onChange={(v) => update("autoReconnect", v)}
                aria-label="Автоматическое переподключение"
              />
            }
          />
          <SettingRow
            title="Подключаться автоматически"
            supporting="Запускать выбранный режим сразу при открытии приложения"
            trailing={
              <Switch
                checked={settings.autoConnect}
                onChange={(v) => update("autoConnect", v)}
                aria-label="Подключаться автоматически"
              />
            }
          />
        </SettingsSection>

        {settings.connectionMode === "vpn" && <SettingsSection title="Приложения и VPN">
          <div role="group" aria-label="Режим приложений" className="mb-3 grid grid-cols-1 gap-2">
            {([ ["exclude", "Все, кроме выбранных"], ["include", "Только выбранные через VPN"] ] as const).map(([mode, label]) =>
              <button key={mode} type="button" aria-pressed={routingMode === mode} onClick={() => {
                const result = onRoutingMode(mode); if (result) show(result);
              }} className={`min-h-11 rounded-xl px-3 py-2 text-left text-[13px] font-semibold ${routingMode === mode ? "bg-primary text-on-primary" : "bg-surface-container text-on-surface"}`}>{label}</button>)}
          </div>
          <p className="pb-2 pt-1 text-[12px] leading-snug text-on-surface-variant">
            {routingMode === "include" ? "Только выбранные приложения используют VPN, остальные — обычный интернет. Выберите хотя бы одно приложение." : "Выбранные приложения используют обычный интернет, остальные — VPN."}
            {" "}Активный VPN кратко переподключится, чтобы применить изменения. Списки двух режимов сохраняются отдельно.
          </p>
          <input value={appQuery} onChange={(e) => setAppQuery(e.target.value)} placeholder="Поиск приложений" className="mb-2 w-full rounded-xl bg-surface-container-high px-3.5 py-2.5 text-[13px] text-on-surface outline-none ring-1 ring-outline/30 placeholder:text-on-surface-variant focus:ring-primary/70" />
          <div ref={appList} onScroll={(event) => setAppScrollTop(event.currentTarget.scrollTop)} className="max-h-72 overflow-y-auto rounded-2xl bg-surface-container-low px-3 ring-1 ring-outline/20">
            {visibleApps.length ? (
              <div style={{ height: visibleApps.length * APP_ROW_HEIGHT }}>
                <div style={{ transform: `translateY(${firstApp * APP_ROW_HEIGHT}px)` }}>
                  {renderedApps.map((app) => <AppExceptionRow key={app.id} app={app} include={routingMode === "include"} onToggle={(id, value) => {
                    const result = onToggleApp(id, value);
                    if (result) show(result);
                  }} />)}
                </div>
              </div>
            ) : <p className="py-5 text-center text-[12px] text-on-surface-variant">Приложения не найдены</p>}
          </div>
        </SettingsSection>}

        <button
          onClick={() => {
            const error = onReset();
            show(error || "Настройки сброшены");
          }}
          className="mt-1 flex items-center justify-center gap-2 rounded-full border border-error/50 py-3.5 text-[13px] font-bold text-error transition-colors active:bg-error-container/40"
        >
          <RotateCcw className="h-4 w-4" strokeWidth={2.2} />
          Сбросить настройки
        </button>
        <div className="flex items-center justify-center gap-1.5 pb-2 text-on-surface-variant">
          <ShieldOff className="h-3.5 w-3.5" strokeWidth={2} />
          <span className="text-[11.5px]">PaperFlux · версия прототипа 1.0</span>
        </div>
      </div>
    </div>
  );
});
