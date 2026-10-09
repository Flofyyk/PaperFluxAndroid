export type AppRoutingMode = "exclude" | "include";
export type AppRouting = { version: 2; mode: AppRoutingMode; excluded: string[]; included: string[] };
export const emptyRouting = (): AppRouting => ({ version: 2, mode: "exclude", excluded: [], included: [] });
type Bridge = { getAppRouting?: () => string; setAppRouting?: (raw: string) => string };
const bridge = () => (window as Window & { PaperFluxNative?: Bridge }).PaperFluxNative;
const key = "paperflux.app-routing.v2";
export function decodeRouting(raw: string): AppRouting {
  const value = JSON.parse(raw);
  if (value.error) throw new Error(value.error);
  if (value.version !== 2 || !["exclude", "include"].includes(value.mode) ||
      ![value.excluded, value.included].every(a => Array.isArray(a) && a.length <= 2048 && a.every(p => typeof p === "string")))
    throw new Error("Некорректный режим приложений");
  return value;
}
export function loadAppRouting(): AppRouting {
  const native = bridge();
  if (native) {
    if (!native.getAppRouting) throw new Error("Обновите клиент для выбора режима приложений");
    return decodeRouting(native.getAppRouting());
  }
  const raw = window.localStorage.getItem(key);
  return raw ? decodeRouting(raw) : emptyRouting();
}
export function saveAppRouting(routing: AppRouting): string {
  try {
    const raw = JSON.stringify(routing);
    decodeRouting(raw);
    const native = bridge();
    if (native) return native.setAppRouting?.(raw) ?? "Ошибка: режим приложений недоступен";
    window.localStorage.setItem(key, raw);
    return "Режим приложений сохранён";
  } catch { return "Ошибка: не удалось сохранить режим приложений"; }
}
