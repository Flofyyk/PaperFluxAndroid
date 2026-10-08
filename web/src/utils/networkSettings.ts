import type { VpnSettings } from "../types";
import { DEFAULT_SETTINGS } from "../data/defaults";

export type NetworkSettings = Omit<VpnSettings, "documentUrl">;
type Bridge = { getNetworkSettings?: () => string; setNetworkSetting?: (key: string, value: string) => string; resetNetworkSettings?: () => string };
const storageKey = "paperflux.network-settings.v1";
const keys = ["dnsPrimary", "dnsSecondary", "mtu", "autoReconnect", "autoConnect", "connectTimeoutSec"] as const;
const bridge = () => (window as Window & { PaperFluxNative?: Bridge }).PaperFluxNative;

function decode(raw: string): NetworkSettings {
  const data = JSON.parse(raw);
  if (data.error) throw new Error(data.error);
  const result = { ...DEFAULT_SETTINGS };
  for (const key of keys) {
    if (typeof data[key] !== typeof DEFAULT_SETTINGS[key]) throw new Error("Некорректные сохранённые настройки");
    Object.assign(result, { [key]: data[key] });
  }
  return result;
}

export function loadNetworkSettings(): NetworkSettings {
  const native = bridge();
  if (native) {
    if (!native.getNetworkSettings) throw new Error("Обновите Android-клиент для сохранения настроек");
    return decode(native.getNetworkSettings());
  }
  const raw = window.localStorage.getItem(storageKey);
  return raw ? decode(raw) : { ...DEFAULT_SETTINGS };
}

export function saveNetworkSetting(key: keyof NetworkSettings, value: string | number | boolean): string {
  try {
    const native = bridge();
    if (native) return native.setNetworkSetting?.(key, String(value)) ?? "Ошибка: Android-мост сохранения недоступен";
    if (key === "dnsPrimary" || key === "dnsSecondary") {
      const parts = String(value).trim().split(".");
      if (parts.length !== 4 || parts.some(p => !/^\d{1,3}$/.test(p) || Number(p) > 255) || Number(parts[0]) < 1 || Number(parts[0]) > 223 || Number(parts[0]) === 127)
        return "Ошибка: укажите IPv4-адрес DNS, например 1.1.1.1";
    }
    window.localStorage.setItem(storageKey, JSON.stringify({ ...loadNetworkSettings(), [key]: value }));
    return "";
  } catch { return "Ошибка: не удалось сохранить настройку"; }
}

export function resetNetworkSettings(): string {
  try {
    const native = bridge();
    if (native) return native.resetNetworkSettings?.() ?? "Ошибка: Android-мост сохранения недоступен";
    window.localStorage.removeItem(storageKey);
    return "";
  } catch { return "Ошибка: не удалось сбросить настройки"; }
}
