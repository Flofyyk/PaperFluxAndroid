import { RefreshCw, RotateCcw, ShieldOff, Timer, Wifi } from "lucide-react";
import { memo, useDeferredValue, useEffect, useMemo, useRef, useState } from "react";
import type { AppExceptionItem, VpnSettings } from "../types";
import { ScreenHeader } from "../components/ui/ScreenHeader";
import { SettingRow, SettingsSection } from "../components/settings/SettingRow";
import { InlineNumberField, InlineTextField } from "../components/settings/InlineNumberField";
import { Switch } from "../components/ui/Switch";
import { AppExceptionRow } from "../components/settings/AppExceptionRow";
import { useToast } from "../hooks/useToast";

const APP_ROW_HEIGHT = 62;
const APP_ROW_OVERSCAN = 5;

export const SettingsScreen = memo(function SettingsScreen({
  settings,
  onUpdate,
  apps,
  onToggleApp,
  onReset,
}: {
  settings: VpnSettings;
  onUpdate: <K extends keyof VpnSettings>(key: K, value: VpnSettings[K]) => void;
  apps: AppExceptionItem[];
  onToggleApp: (id: string, value: boolean) => string;
  onReset: () => void;
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

  return (
    <div className="h-full overflow-y-auto">
      <ScreenHeader title="Настройки" subtitle="Транспорт, сеть и поведение приложения" />

      <div className="flex flex-col gap-3.5 px-5 pb-8">
        <SettingsSection title="Сеть">
          <SettingRow
            icon={<Wifi className="h-4.5 w-4.5" strokeWidth={2} />}
            title="Основной DNS"
            supporting="Используется для резолвинга внутри туннеля"
            trailing={
              <div className="w-[108px]">
                <InlineTextField value={settings.dnsPrimary} onChange={(v) => onUpdate("dnsPrimary", v)} />
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
                <InlineTextField value={settings.dnsSecondary} onChange={(v) => onUpdate("dnsSecondary", v)} />
              </div>
            }
            className="items-start"
          />
          <SettingRow
            icon={<RefreshCw className="h-4.5 w-4.5" strokeWidth={2} />}
            title="MTU"
            supporting="Максимальный размер пакета для VPN-интерфейса"
            trailing={<InlineNumberField value={settings.mtu} min={576} max={1500} suffix="Б" onChange={(v) => onUpdate("mtu", v)} />}
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
                onChange={(v) => onUpdate("connectTimeoutSec", v)}
              />
            }
          />
        </SettingsSection>

        <SettingsSection title="Поведение">
          <SettingRow
            title="Автоматическое переподключение"
            supporting="Восстанавливать туннель при обрыве без участия пользователя"
            trailing={
              <Switch
                checked={settings.autoReconnect}
                onChange={(v) => onUpdate("autoReconnect", v)}
                aria-label="Автоматическое переподключение"
              />
            }
          />
          <SettingRow
            title="Подключаться автоматически"
            supporting="Запускать VPN сразу при открытии приложения"
            trailing={
              <Switch
                checked={settings.autoConnect}
                onChange={(v) => onUpdate("autoConnect", v)}
                aria-label="Подключаться автоматически"
              />
            }
          />
        </SettingsSection>

        <SettingsSection title="Исключения приложений">
          <p className="pb-2 pt-1 text-[12px] leading-snug text-on-surface-variant">
            Выбранные приложения используют обычный интернет. Активный VPN кратко переподключится, чтобы применить изменения.
          </p>
          <input value={appQuery} onChange={(e) => setAppQuery(e.target.value)} placeholder="Поиск приложений" className="mb-2 w-full rounded-xl bg-surface-container-high px-3.5 py-2.5 text-[13px] text-on-surface outline-none ring-1 ring-outline/30 placeholder:text-on-surface-variant focus:ring-primary/70" />
          <div ref={appList} onScroll={(event) => setAppScrollTop(event.currentTarget.scrollTop)} className="max-h-72 overflow-y-auto rounded-2xl bg-surface-container-low px-3 ring-1 ring-outline/20">
            {visibleApps.length ? (
              <div style={{ height: visibleApps.length * APP_ROW_HEIGHT }}>
                <div style={{ transform: `translateY(${firstApp * APP_ROW_HEIGHT}px)` }}>
                  {renderedApps.map((app) => <AppExceptionRow key={app.id} app={app} onToggle={(id, value) => {
                    const result = onToggleApp(id, value);
                    if (result) show(result);
                  }} />)}
                </div>
              </div>
            ) : <p className="py-5 text-center text-[12px] text-on-surface-variant">Приложения не найдены</p>}
          </div>
        </SettingsSection>

        <button
          onClick={() => {
            onReset();
            show("Настройки сброшены");
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
