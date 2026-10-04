import { useCallback, useEffect, useRef, useState } from "react";
import type { ConnectionStatus, LogCategory, LogEntry, LogLevel, SessionStats, Stage, StageStatus } from "../types";
import { nowTime, uid } from "../utils/format";
import { detailLevel, isVerificationWarning, readVerification } from "../utils/verification";
import type { VerificationState } from "../utils/verification";

const STAGES: Omit<Stage, "status">[] = [
  { id: "vpn", title: "VPN-интерфейс", description: "Создание системного туннеля устройства" },
  { id: "transport", title: "Yandex transport", description: "Установка Engine.IO соединения с Yandex Docs" },
  { id: "auth", title: "Авторизация документа", description: "Проверка доступа к документу-контейнеру" },
  { id: "dns", title: "DNS и TCP", description: "Настройка маршрутов, DNS и проверка TCP" },
];
const freshStages = (): Stage[] => STAGES.map((stage) => ({ ...stage, status: "pending" as StageStatus }));

export function useVpn(_opts: { autoReconnect: boolean; timeoutSec: number }) {
  const [status, setStatus] = useState<ConnectionStatus>("idle");
  const [stages, setStages] = useState<Stage[]>(freshStages());
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [errorReason, setErrorReason] = useState<string | null>(null);
  const [verification, setVerification] = useState<VerificationState | null>(null);
  const [stats, setStats] = useState<SessionStats>({ durationSec: 0, ping: null, rxBytes: 0, txBytes: 0, rxRate: 0, txRate: 0 });
  const durationTimer = useRef<number | null>(null);
	const trafficSnapshot = useRef<{ rxBytes: number; txBytes: number; at: number } | null>(null);

  const pushLog = useCallback((message: string, level: LogLevel, category: LogCategory, stage: string) => {
    setLogs((previous) => {
      // A live broadcast may be the same event that was restored from the
      // session file just before WebView opened. Do not show it twice.
      if (previous[0]?.message === message && previous[0]?.stage === stage) return previous;
      return [{ id: uid(), time: nowTime(), stage, message, level, category }, ...previous].slice(0, 300);
    });
  }, []);
  const setStage = useCallback((id: Stage["id"], next: StageStatus) => {
    setStages((previous) => {
      const target = previous.find((stage) => stage.id === id);
      if (!target || target.status === next) return previous;
      return previous.map((stage) => (stage.id === id ? { ...stage, status: next } : stage));
    });
  }, []);
  const stopStats = useCallback(() => {
    if (durationTimer.current !== null) window.clearInterval(durationTimer.current);
    durationTimer.current = null;
  }, []);
  const startStats = useCallback(() => {
    // Session time is authored by the foreground VPN service and restored
    // from its atomic snapshot. Never reset it merely because WebView was
    // recreated after the user returned to the app.
    if (durationTimer.current !== null) return;
    // Native service is the source of truth for the session clock.  Updating
    // the whole React tree every second made older phones stutter while the
    // tunnel was active; five-second refreshes match native stats updates.
    durationTimer.current = window.setInterval(() => setStats((previous) => ({ ...previous, durationSec: previous.durationSec + 5 })), 5000);
  }, []);

  useEffect(() => {
    const native = (window as unknown as { PaperFluxNative?: { connect?: () => void; disconnect?: () => void; getState?: () => string; getSessionLogs?: () => string; clearSessionLogs?: () => void }; __paperFluxOnState?: (payload: string) => void });
    if (!native) return;
    const handleState = (raw: string) => {
      try {
        const event = JSON.parse(raw) as { state?: string; detail?: string; log?: string; rxBytes?: number; txBytes?: number; ping?: number; durationSec?: number; verification?: unknown };
        // Log/stat broadcasts deliberately omit `state`. They must never
        // reset a successfully connected screen back to "connecting".
        const state = event.state;
        if (Object.prototype.hasOwnProperty.call(event, "verification")) setVerification(readVerification(event.verification));
        else if (state === "DISCONNECTED" || state === "CONNECTING") setVerification(null);
        const mapped: ConnectionStatus | null = state === undefined ? null : state === "CONNECTED" ? "connected" : state === "ERROR" ? "error" : state === "DISCONNECTED" ? "idle" : state === "TRANSPORT" || state === "RECONNECTING" || state === "WAITING_NETWORK" ? "reconnecting" : "connecting";
        if (mapped) setStatus((previous) => previous === mapped ? previous : mapped);
        if (state === "CONNECTING") {
          // A deliberate new session starts from zero.  Do not retain a
          // previous session's totals merely because its snapshot arrived
          // shortly before this state broadcast.
          stopStats();
			trafficSnapshot.current = null;
          setStats({ durationSec: 0, ping: null, rxBytes: 0, txBytes: 0, rxRate: 0, txRate: 0 });
        }
        if (event.rxBytes !== undefined || event.txBytes !== undefined || event.ping !== undefined) {
          // Traffic counters are absolute/cumulative. Ping broadcasts can be
          // 0 while a probe is pending, so never erase the last good RTT.
          const now = Date.now();
          setStats((previous) => {
			const rxBytes = event.rxBytes !== undefined ? Math.max(previous.rxBytes, event.rxBytes) : previous.rxBytes;
			const txBytes = event.txBytes !== undefined ? Math.max(previous.txBytes, event.txBytes) : previous.txBytes;
			const sample = trafficSnapshot.current;
			const elapsed = sample ? (now - sample.at) / 1000 : 0;
			const canMeasure = elapsed >= 0.25 && rxBytes >= sample!.rxBytes && txBytes >= sample!.txBytes;
			const rxRate = canMeasure ? (rxBytes - sample!.rxBytes) / elapsed : previous.rxRate;
			const txRate = canMeasure ? (txBytes - sample!.txBytes) / elapsed : previous.txRate;
			trafficSnapshot.current = { rxBytes, txBytes, at: now };
            const next = {
				rxBytes,
				txBytes,
				rxRate,
				txRate,
              ping: event.ping !== undefined && event.ping > 0 ? event.ping : previous.ping,
              durationSec: event.durationSec !== undefined ? Math.max(0, event.durationSec) : previous.durationSec,
            };
            return next.rxBytes === previous.rxBytes && next.txBytes === previous.txBytes &&
				next.rxRate === previous.rxRate && next.txRate === previous.txRate &&
				next.ping === previous.ping && next.durationSec === previous.durationSec ? previous : next;
          });
        }
        if (mapped === "connected") {
          setStages((previous) => previous.every((stage) => stage.status === "success")
            ? previous
            : previous.map((stage) => ({ ...stage, status: "success" })));
          startStats();
        }
        else if (mapped === "idle" || state === "WAITING_NETWORK") { stopStats(); setStages(freshStages()); }
        else if (mapped === "error") stopStats();
        if (state === "TUN") setStage("vpn", "running");
        if (state === "TRANSPORT") setStage("transport", "running");
        if (state === "AUTH") setStage("auth", "running");
        if (state === "DNS") setStage("dns", "running");
        if (state === "ERROR") setStages((previous) => previous.map((stage) => stage.status === "running" ? { ...stage, status: "error" } : stage));
        if (mapped) setErrorReason(mapped === "error" ? (event.detail ?? "Соединение прервано") : null);
        if (event.detail) pushLog(event.detail, detailLevel(state, event.detail), state === "WAITING_NETWORK" ? "network" : "connection", state ?? "Система");
        if (event.log) pushLog(event.log, /error|failed|обрыв|refused|1005/i.test(event.log) ? "error" : "info", "system", state ?? "Система");
      } catch { /* ignore malformed native events */ }
    };
    native.__paperFluxOnState = handleState;
    try {
      const journal = native.PaperFluxNative?.getSessionLogs?.();
      if (journal) {
        const entries = (JSON.parse(journal).events ?? []).map((item: { id?: string; at?: number; message?: string; level?: LogLevel; category?: LogCategory; stage?: string }, index: number) => ({
          id: item.id ?? `restored-${item.at ?? 0}-${index}`,
          time: new Date(item.at ?? Date.now()).toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit", second: "2-digit" }),
          message: item.message ?? "Событие PaperFlux",
          level: isVerificationWarning(item.message ?? "") ? "warning" : item.level ?? "info",
          category: item.category ?? "system",
          stage: item.stage ?? "Система",
        } as LogEntry));
        if (entries.length) setLogs(entries);
      }
    } catch { /* journal can be unavailable in the preview */ }
    // The Activity can be recreated while the foreground VPN service remains
    // alive. Restore its persisted state so the UI cannot stay on
    // "Подключение..." after a successful handshake.
    try { const snapshot = native.PaperFluxNative?.getState?.(); if (snapshot) handleState(snapshot); } catch { /* preview */ }
    return () => { native.__paperFluxOnState = undefined; stopStats(); };
  }, [pushLog, setStage, startStats, stopStats]);

  const connect = useCallback(() => {
    const native = (window as unknown as { PaperFluxNative?: { connect?: () => void } }).PaperFluxNative;
    stopStats(); setStatus("connecting"); setErrorReason(null); setVerification(null); setStages(freshStages());
	trafficSnapshot.current = null;
    setStats({ durationSec: 0, ping: null, rxBytes: 0, txBytes: 0, rxRate: 0, txRate: 0 });
    setLogs([]);
    pushLog("Запуск PaperFlux", "info", "connection", "Система");
    if (native?.connect) native.connect();
    else { setStatus("error"); setErrorReason("Нативный VPN-мост недоступен"); pushLog("Нативный VPN-мост недоступен", "error", "system", "Система"); }
  }, [pushLog, stopStats]);
  const disconnect = useCallback(() => {
    // The VPN interface lives in a separate Android process.  Keep the last
    // confirmed state until that process closes the TUN and broadcasts
    // DISCONNECTED; optimistic "off" was the source of a misleading UI
    // while Android still showed an active VPN.
    pushLog("Отключаем туннель…", "info", "connection", "Система");
    (window as unknown as { PaperFluxNative?: { disconnect?: () => void } }).PaperFluxNative?.disconnect?.();
  }, [pushLog]);
  const retry = useCallback(() => connect(), [connect]);
  const clearLogs = useCallback(() => {
    setLogs([]);
    (window as unknown as { PaperFluxNative?: { clearSessionLogs?: () => void } }).PaperFluxNative?.clearSessionLogs?.();
  }, []);
  const openVerification = useCallback(() => {
    (window as unknown as { PaperFluxNative?: { openVerification?: () => void } }).PaperFluxNative?.openVerification?.();
  }, []);
  return { status, stages, logs, errorReason, authRequired: verification !== null, verification, openVerification, stats, connect, disconnect, retry, clearLogs, pushLog };
}
