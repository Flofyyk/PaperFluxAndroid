import { useEffect, useState } from "react";
import { Download, RefreshCw } from "lucide-react";
import { useToast } from "../../hooks/useToast";

type State = { phase: "idle" | "available" | "downloading" | "ready" | "error" | "current"; version?: string; message?: string; percent?: number };
type Bridge = { getAppUpdateState?: () => string; checkAppUpdates?: (manual: boolean) => void; downloadAppUpdate?: () => void; skipAppUpdate?: () => string; installAppUpdate?: () => void };
const native = () => (window as Window & { PaperFluxNative?: Bridge }).PaperFluxNative;

export function AppUpdateDialog() {
  const [state, setState] = useState<State>({ phase: "idle" });
  const { show } = useToast();
  useEffect(() => {
    const win = window as Window & { __paperFluxOnAppUpdate?: (raw: string) => void };
    const receive = (raw: string) => {
      try {
        const update = JSON.parse(raw) as State;
        if (!update.phase) return;
        if (update.phase === "current") { show(update.message || "У вас последняя версия"); setState({ phase: "idle" }); }
        else setState(update);
      } catch { /* Update diagnostics cannot affect VPN. */ }
    };
    win.__paperFluxOnAppUpdate = receive;
    const snapshot = native()?.getAppUpdateState?.();
    if (snapshot) receive(snapshot);
    const check = () => { if (document.visibilityState === "visible") native()?.checkAppUpdates?.(false); };
    check();
    // Re-offer after a 24h skip even when the Activity was left open all day.
    const timer = window.setInterval(check, 60_000);
    document.addEventListener("visibilitychange", check);
    return () => { delete win.__paperFluxOnAppUpdate; window.clearInterval(timer); document.removeEventListener("visibilitychange", check); };
  }, [show]);
  if (state.phase === "idle" || state.phase === "current") return null;
  const busy = state.phase === "downloading";
  const ready = state.phase === "ready";
  const skip = () => {
    const error = native()?.skipAppUpdate?.() ?? "Android-мост недоступен";
    if (error) show(error); else setState({ phase: "idle" });
  };
  return <div className="pf-profile-overlay" style={{ zIndex: 80 }}>
    <div role="dialog" aria-modal="true" aria-labelledby="app-update-title" className="pf-profile-dialog p-6">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-container text-primary"><Download className="h-6 w-6" /></div>
      <h2 id="app-update-title" className="text-[22px] font-bold text-on-surface">{busy ? "Скачиваем обновление" : ready ? "Обновление готово" : state.phase === "error" ? "Не удалось обновить" : "Доступно обновление"}</h2>
      <p className="mt-3 text-[14px] leading-6 text-on-surface-variant">{state.message || `Вышла PaperFlux ${state.version}. Установите новую версию с GitHub. Ваши профили и настройки сохранятся.`}</p>
      {busy && <div className="mt-5" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={state.percent ?? 0} aria-label="Загрузка APK">
        <div className="h-2 overflow-hidden rounded-full bg-surface-container-high"><div className="h-full rounded-full bg-primary" style={{ width: `${Math.max(0, Math.min(100, state.percent ?? 0))}%` }} /></div>
        <p className="mt-2 text-[13px] text-on-surface-variant">{state.percent ?? 0}%</p>
      </div>}
      {!busy && <p className="mt-3 text-[12px] leading-5 text-on-surface-variant">«Пропустить» отключает напоминание на 24 часа. Установку подтверждает Android.</p>}
      <div className="mt-6 flex flex-wrap justify-end gap-2">
        <button disabled={busy} onClick={skip} className="min-h-11 rounded-full px-4 text-[14px] font-semibold text-primary disabled:opacity-40">Пропустить</button>
        <button disabled={busy} onClick={() => ready ? native()?.installAppUpdate?.() : state.phase === "error" ? native()?.checkAppUpdates?.(true) : native()?.downloadAppUpdate?.()} className="flex min-h-11 items-center gap-2 rounded-full bg-primary px-5 py-3 text-[14px] font-bold text-on-primary disabled:opacity-60">
          {busy && <RefreshCw className="h-4 w-4 animate-spin" />}{busy ? "Скачиваем…" : ready ? "Установить" : state.phase === "error" ? "Повторить" : "Обновить"}
        </button>
      </div>
    </div>
  </div>;
}
