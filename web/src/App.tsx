import { useCallback, useEffect, useRef, useState } from "react";
import type { AppExceptionItem, PaperFluxProfile, TabId, VpnSettings } from "./types";
import { DEFAULT_APPS, DEFAULT_SETTINGS } from "./data/defaults";
import { useVpn } from "./hooks/useVpn";
import { ToastProvider } from "./hooks/useToast";
import { loadNetworkSettings, saveNetworkSetting, resetNetworkSettings } from "./utils/networkSettings";

import { BottomNav } from "./components/shell/BottomNav";
import { AppUpdateDialog } from "./components/shell/AppUpdateDialog";

import { HomeScreen } from "./screens/HomeScreen";
import { LogsScreen } from "./screens/LogsScreen";
import { SettingsScreen } from "./screens/SettingsScreen";
import { ProfilesScreen } from "./screens/ProfilesScreen";

type Inspection = { id: string; address?: string; countryCode?: string; latencyMs?: number; error?: string; checking?: boolean; pinged?: boolean };

export default function App() {
  const [dark] = useState(true);
  const [activeTab, setActiveTab] = useState<TabId>("home");

  const [settings, setSettings] = useState<VpnSettings>(DEFAULT_SETTINGS);
  const [settingsError, setSettingsError] = useState("");
  const [apps, setApps] = useState<AppExceptionItem[]>(DEFAULT_APPS);
  const appsRef = useRef<AppExceptionItem[]>(DEFAULT_APPS);
  const [profiles, setProfiles] = useState<PaperFluxProfile[]>([]);
  const [activeProfile, setActiveProfile] = useState("");
  const [inspections, setInspections] = useState<Record<string, Inspection>>({});
  const inspected = useRef(new Set<string>());
  const inspectProfile = useCallback((id: string, force = false) => {
    const native = (window as Window & { PaperFluxNative?: { inspectProfile?: (id: string, ping: boolean) => void } }).PaperFluxNative;
    if (!id || !native?.inspectProfile || (inspected.current.has(id) && !force)) return;
    inspected.current.add(id);
    setInspections(current => ({ ...current, [id]: { ...current[id], id, checking: force, pinged: force || current[id]?.pinged } }));
    native.inspectProfile(id, force);
  }, []);
  useEffect(() => {
    const win = window as Window & { __paperFluxOnProfileInspection?: (raw: string) => void };
    win.__paperFluxOnProfileInspection = raw => {
      try { const result = JSON.parse(raw) as Inspection; setInspections(current => ({ ...current, [result.id]: { ...current[result.id], ...result, checking: false } })); }
      catch { /* Diagnostics cannot affect connection state. */ }
    };
    return () => { delete win.__paperFluxOnProfileInspection; };
  }, []);
  useEffect(() => { if (activeProfile) inspectProfile(activeProfile); }, [activeProfile, inspectProfile]);

  const refreshProfiles = useCallback(() => {
    const native = (window as unknown as { PaperFluxNative?: { getProfiles?: () => string } }).PaperFluxNative;
    if (!native?.getProfiles) return;
    const snapshot = JSON.parse(native.getProfiles()) as { profiles: PaperFluxProfile[]; activeId: string };
    setProfiles(snapshot.profiles); setActiveProfile(snapshot.activeId);
    setSettings(previous => ({ ...previous, documentUrl: snapshot.profiles.find(p => p.id === snapshot.activeId)?.documentUrl ?? "" }));
  }, []);
  useEffect(() => { refreshProfiles(); }, []);
  const changeProfile = useCallback((id: string, remove = false): string => {
    const native = (window as unknown as { PaperFluxNative?: { selectProfile?: (id: string) => string; deleteProfile?: (id: string) => string } }).PaperFluxNative;
    const result = (remove ? native?.deleteProfile?.(id) : native?.selectProfile?.(id)) ?? "Android-мост недоступен";
    refreshProfiles();
    return result;
  }, [refreshProfiles]);

  useEffect(() => {
    const native = (window as unknown as { PaperFluxNative?: { getDocumentUrl?: () => string } }).PaperFluxNative;
    try {
      const documentUrl = native?.getDocumentUrl?.();
      if (documentUrl) setSettings((previous) => ({ ...previous, documentUrl }));
    } catch { /* preview */ }
  }, []);
  useEffect(() => {
    try {
      const saved = loadNetworkSettings();
      setSettings(previous => ({ ...previous, ...saved, documentUrl: previous.documentUrl }));
    } catch (error) { setSettingsError(error instanceof Error ? error.message : "Не удалось прочитать настройки"); }
  }, []);

  const addProfileFromClipboard = useCallback(() => {
    const native = (window as unknown as { PaperFluxNative?: { getClipboardConfig?: () => string; importConfig?: (v: string) => string; getDocumentUrl?: () => string } }).PaperFluxNative;
    const result = native?.importConfig?.(native?.getClipboardConfig?.() ?? "") ?? "Буфер обмена недоступен";
    if (result.startsWith("Профиль добавлен")) {
      refreshProfiles();
    }
    return result;
  }, [refreshProfiles]);
  const pickProfileFile = useCallback(() => {
    (window as unknown as { PaperFluxNative?: { pickConfigFile?: () => void } }).PaperFluxNative?.pickConfigFile?.();
  }, []);
  const scanProfileQr = useCallback(() => {
    (window as unknown as { PaperFluxNative?: { scanQr?: () => void } }).PaperFluxNative?.scanQr?.();
  }, []);
  const saveProfile = useCallback((raw: string): string => {
    const native = (window as unknown as { PaperFluxNative?: { updateProfile?: (value: string) => string } }).PaperFluxNative;
    const result = native?.updateProfile?.(raw) ?? "Android-мост недоступен";
    if (result.startsWith("Профиль сохранён") || result.startsWith("Профиль создан")) refreshProfiles();
    return result;
  }, [refreshProfiles]);

  useEffect(() => {
    if (activeTab !== "settings") return;
    const native = (window as unknown as { PaperFluxNative?: { getInstalledApps?: () => string } }).PaperFluxNative;
    if (!native?.getInstalledApps) return;
    // Icon extraction is CPU-heavy on Android. Defer it until after the first
    // frame so the home screen is rendered immediately on cold start.
    const loadApps = () => {
      try {
        const parsed = JSON.parse(native.getInstalledApps!());
        if (Array.isArray(parsed)) { appsRef.current = parsed; setApps(parsed); }
      } catch { /* native bridge unavailable during preview */ }
    };
    // First response is deliberately icon-free; two light retries replace it
    // with the background-cached icon set without freezing Settings.
    const timers = [180, 800, 1800].map((delay) => window.setTimeout(loadApps, delay));
    return () => timers.forEach(window.clearTimeout);
  }, [activeTab]);

  const vpn = useVpn({ autoReconnect: settings.autoReconnect, timeoutSec: settings.connectTimeoutSec });

  const updateSetting = useCallback(<K extends keyof VpnSettings,>(key: K, value: VpnSettings[K]) => {
    if (key !== "documentUrl") {
      const error = saveNetworkSetting(key, value);
      if (error) return error;
      setSettingsError("");
    }
    setSettings((prev) => ({ ...prev, [key]: value }));
    if (key === "documentUrl") {
      (window as unknown as { PaperFluxNative?: { setDocumentUrl?: (url: string) => void } }).PaperFluxNative?.setDocumentUrl?.(value as string);
    }
    return "";
  }, []);

  const toggleApp = useCallback((id: string, value: boolean): string => {
    const next = appsRef.current.map((a) => (a.id === id ? { ...a, excluded: value } : a));
    const native = (window as unknown as { PaperFluxNative?: { setExcludedApps?: (raw: string) => string } }).PaperFluxNative;
    const result = native?.setExcludedApps?.(JSON.stringify(next.filter((a) => a.excluded).map((a) => a.pkg))) ?? "";
    if (result.startsWith("Ошибка")) return result;
    appsRef.current = next;
    setApps(next);
    return result;
  }, []);

  const resetSettings = useCallback(() => {
    const error = resetNetworkSettings();
    if (error) return error;
    setSettings(previous => ({ ...DEFAULT_SETTINGS, documentUrl: previous.documentUrl }));
    setSettingsError("");
    const native = (window as unknown as { PaperFluxNative?: { setExcludedApps?: (raw: string) => string } }).PaperFluxNative;
    const result = native?.setExcludedApps?.("[]") ?? "";
    if (result.startsWith("Ошибка")) return result;
    const next = appsRef.current.map(app => ({ ...app, excluded: false }));
    appsRef.current = next;
    setApps(next);
    return "";
  }, []);

  const handleToggleConnection = useCallback(() => {
    if (vpn.status === "idle" || vpn.status === "error") {
      vpn.connect();
    } else if (vpn.status === "connected") {
      vpn.disconnect();
    } else if (vpn.status === "connecting" || vpn.status === "reconnecting") {
      vpn.disconnect();
    }
  }, [vpn.connect, vpn.disconnect, vpn.status]);
  const selectedProfile = profiles.find(profile => profile.id === activeProfile);

  return (
    <div className={dark ? "pf-dark pf-app-shell w-full" : "pf-app-shell w-full"}>
      <div className={dark ? "pf-app-frame flex w-full flex-col overflow-hidden bg-[#08070d] text-slate-100" : "pf-app-frame flex w-full flex-col overflow-hidden bg-[#fbfaff] text-slate-900"}>
        <ToastProvider>
          <div className="min-h-0 flex-1 overflow-hidden">
            {activeTab === "home" && (
              <HomeScreen
                status={vpn.status}
                stages={vpn.stages}
                stats={vpn.stats}
                errorReason={vpn.errorReason}
                verification={vpn.verification}
                onOpenVerification={vpn.openVerification}
                profile={selectedProfile && { ...selectedProfile, countryCode: selectedProfile.countryCode ?? inspections[activeProfile]?.countryCode }}
                onOpenProfiles={() => setActiveTab("profiles")}
                onToggleConnection={handleToggleConnection}
                onRetry={vpn.retry}
              />
            )}
            {activeTab === "logs" && <LogsScreen logs={vpn.logs} onClear={vpn.clearLogs} />}
            {activeTab === "profiles" && <ProfilesScreen profiles={profiles} activeId={activeProfile} inspections={inspections} onInspect={inspectProfile} onClipboard={addProfileFromClipboard} onPickFile={pickProfileFile} onQr={scanProfileQr} onSave={saveProfile} onSelect={id => changeProfile(id)} onDelete={id => changeProfile(id, true)} onRefresh={refreshProfiles} />}
            {activeTab === "settings" && (
              <SettingsScreen
                settings={settings}
                settingsError={settingsError}
                onUpdate={updateSetting}
                apps={apps}
                onToggleApp={toggleApp}
                onReset={resetSettings}
              />
            )}
          </div>
          <BottomNav active={activeTab} onChange={setActiveTab} />
          <AppUpdateDialog />
        </ToastProvider>
      </div>
    </div>
  );
}
