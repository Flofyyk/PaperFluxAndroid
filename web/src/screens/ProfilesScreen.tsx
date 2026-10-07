import { useEffect, useRef, useState } from "react";
import { Activity, ChevronDown, ClipboardPlus, Eye, EyeOff, FileKey, Globe2, LoaderCircle, Pencil, Plus, ScanQrCode, Share2, ShieldCheck, Trash2, Upload, X } from "lucide-react";
import type { PaperFluxProfile } from "../types";
import { ScreenHeader } from "../components/ui/ScreenHeader";
import { useToast } from "../hooks/useToast";

type Draft = { id: string; name: string; server: string; originalServer: string; token: string; isNew: boolean };
const native = () => (window as Window & { PaperFluxNative?: { shareProfile?: (id: string) => void; cancelManualProfile?: () => void } }).PaperFluxNative;
const providerName = (provider?: string) => provider === "mailru" ? "Mail.ru" : provider === "cupsonline" ? "Cups.online" : "Яндекс";
type Inspection = { id: string; address?: string; countryCode?: string; latencyMs?: number; error?: string; checking?: boolean; pinged?: boolean };
const flagFor = (code?: string) => code && /^[A-Z]{2}$/.test(code) ? String.fromCodePoint(...[...code].map(c => c.charCodeAt(0) + 127397)) : null;

export function ProfilesScreen({ profiles, activeId, inspections, onInspect, onClipboard, onPickFile, onQr, onSave, onSelect, onDelete, onRefresh }: {
  profiles: PaperFluxProfile[]; activeId: string; inspections: Record<string, Inspection>; onInspect: (id: string, force?: boolean) => void; onClipboard: () => string; onPickFile: () => void; onQr: () => void; onSave: (raw: string) => string;
  onSelect: (id: string) => string; onDelete: (id: string) => string; onRefresh: () => void;
}) {
  const { show } = useToast();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<PaperFluxProfile | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const request = useRef<string | null>(null);
  useEffect(() => {
    const observer = new IntersectionObserver(entries => {
      entries.forEach(entry => { if (entry.isIntersecting) onInspect((entry.target as HTMLElement).dataset.profileId || ""); });
    }, { rootMargin: "120px" });
    document.querySelectorAll("[data-profile-id]").forEach(card => observer.observe(card));
    return () => observer.disconnect();
  }, [profiles, onInspect]);
  useEffect(() => {
    const win = window as Window & { __paperFluxOnProfileFile?: (result: string) => void; __paperFluxOnManualProfile?: (raw: string) => void };
    win.__paperFluxOnProfileFile = result => { show(result); if (result.startsWith("Профиль добавлен")) onRefresh(); };
    win.__paperFluxOnManualProfile = raw => {
      try {
        const result = JSON.parse(raw) as { requestId: string; message: string; ok: boolean };
        if (result.requestId !== request.current) return;
        request.current = null; setBusy(false);
        if (result.ok) { setDraft(null); onRefresh(); show(result.message); }
        else setError(result.message);
      } catch { setBusy(false); setError("Не удалось прочитать ответ сервера"); }
    };
    return () => { native()?.cancelManualProfile?.(); delete win.__paperFluxOnProfileFile; delete win.__paperFluxOnManualProfile; };
  }, [onRefresh, show]);
  const close = () => { request.current = null; native()?.cancelManualProfile?.(); setDraft(null); setBusy(false); setError(null); };
  const edit = (p?: PaperFluxProfile) => {
    setError(null); setBusy(false);
    setDraft(p ? { id: p.id, name: p.name, server: p.server, originalServer: p.server, token: "", isNew: false } : { id: "", name: "", server: "", originalServer: "", token: "", isNew: true });
  };
  const save = () => {
    if (!draft || busy) return;
    if (!draft.name.trim()) { setError("Укажите название профиля"); return; }
    if (!draft.server.trim()) { setError("Укажите IP или домен сервера"); return; }
    if ((draft.isNew || draft.server.trim() !== draft.originalServer) && !draft.token.trim()) { setError("Вставьте пароль профиля с сервера"); return; }
    request.current = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`; setError(null); setBusy(true);
    const result = onSave(JSON.stringify({ ...draft, requestId: request.current }));
    if (result.startsWith("Загружаем")) return;
    setBusy(false); request.current = null;
    if (result.startsWith("Профиль сохранён") || result.startsWith("Профиль создан")) { setDraft(null); onRefresh(); show(result); }
    else setError(result);
  };
  const clipboard = () => { const result = onClipboard(); show(result); if (result.startsWith("Профиль добавлен")) onRefresh(); };
  return <div className="flex h-full min-h-0 flex-col">
    <ScreenHeader title="Профили" subtitle="Ваши подключения" />
    <div className="min-h-0 flex-1 overflow-y-auto px-5 pb-5">
      <div className="mb-6 grid grid-cols-3 gap-2">
        <ImportAction icon={ScanQrCode} label="Сканировать" onClick={onQr} />
        <ImportAction icon={ClipboardPlus} label="Из буфера" onClick={clipboard} />
        <ImportAction icon={Upload} label="Из файла" onClick={onPickFile} />
      </div>
      <div className="flex flex-col gap-3">
        {profiles.length === 0 && <div className="rounded-[28px] border border-dashed border-outline-variant p-8 text-center"><FileKey className="mx-auto mb-4 h-10 w-10 text-primary" /><p className="font-semibold text-on-surface">Добавьте первое подключение</p><p className="mt-2 text-[13px] leading-5 text-on-surface-variant">Отсканируйте QR-код или введите адрес сервера и пароль профиля.</p></div>}
        {profiles.map(p => <div key={p.id} data-profile-id={p.id} className={"overflow-hidden rounded-[24px] border bg-surface-container " + (p.id === activeId ? "border-primary/70" : "border-outline-variant/35")}>
          <div className="flex min-w-0 items-center px-3 pb-3 pt-3">
            <button onClick={() => { if (p.id !== activeId) show(onSelect(p.id)); }} className="flex min-h-14 min-w-0 flex-1 items-center gap-3 rounded-xl px-1 text-left active:bg-primary/10" aria-label={p.id === activeId ? `${p.name}, выбран` : `Выбрать профиль ${p.name}`}>
              <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-surface-container-high text-[23px] text-on-surface-variant">{flagFor(p.countryCode ?? inspections[p.id]?.countryCode) || <Globe2 className="h-6 w-6" />}</span>
              <span className="min-w-0 flex-1"><span className="flex flex-wrap items-center gap-2"><span className="break-words text-[16px] font-bold leading-5 text-on-surface">{p.name}</span>{p.id === activeId && <span className="rounded-full bg-primary/15 px-2 py-0.5 text-[10px] font-bold text-primary">ВЫБРАН</span>}</span><span className="mt-1 block break-all text-[12px] text-on-surface-variant">{p.server}</span></span>
            </button>
            <button aria-label={expandedId === p.id ? `Свернуть ${p.name}` : `Развернуть ${p.name}`} aria-expanded={expandedId === p.id} onClick={() => setExpandedId(expandedId === p.id ? null : p.id)} className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-on-surface-variant active:bg-primary/15"><ChevronDown className={"h-5 w-5 transition-transform " + (expandedId === p.id ? "rotate-180" : "")} /></button>
          </div>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 pb-3 text-[12px] text-on-surface-variant"><span className="inline-flex items-center gap-1.5"><ShieldCheck className="h-3.5 w-3.5" />{providerName(p.transport)}</span>{(p.serverCount ?? 1) > 1 && <span>{p.serverCount} сервера · авторезерв</span>}{(p.countryCode ?? inspections[p.id]?.countryCode) && <span title="Примерная страна по публичному IP">{p.countryCode ?? inspections[p.id]?.countryCode}</span>}</div>
          {expandedId === p.id && <div className="border-t border-outline-variant/30 px-3 py-2">
            <div className="grid grid-cols-4 gap-1">
              <CardAction icon={Activity} label={inspections[p.id]?.checking ? "Пингуем" : inspections[p.id]?.latencyMs ? `${inspections[p.id].latencyMs} мс` : inspections[p.id]?.pinged && inspections[p.id]?.error ? "Нет ответа" : "Пинг"} onClick={() => onInspect(p.id, true)} />
              <CardAction icon={Share2} label="Поделиться" onClick={() => native()?.shareProfile ? native()!.shareProfile!(p.id) : show("Обмен конфигурацией доступен в приложении")} />
              <CardAction icon={Pencil} label="Изменить" onClick={() => edit(p)} />
              <CardAction icon={Trash2} label="Удалить" onClick={() => setDeleting(p)} />
            </div>
          </div>}
        </div>)}
      </div>
    </div>
    <div className="shrink-0 px-5 pb-5 pt-2"><button onClick={() => edit()} className="flex min-h-12 w-full items-center justify-center gap-2 rounded-full bg-primary px-5 py-3 text-[14px] font-bold text-on-primary"><Plus className="h-5 w-5" />Добавить профиль</button></div>
    {draft && <Editor draft={draft} busy={busy} error={error} onChange={setDraft} onClose={close} onSave={save} />}
    {deleting && <div className="pf-profile-overlay"><div role="dialog" aria-modal="true" aria-labelledby="delete-profile-title" className="pf-profile-dialog p-6"><h2 id="delete-profile-title" className="text-xl font-bold text-on-surface">Удалить профиль?</h2><p className="mt-3 break-words text-[14px] text-on-surface-variant">{deleting.name}. Конфигурация будет удалена только с этого устройства.</p><div className="mt-6 flex justify-end gap-3"><button onClick={() => setDeleting(null)} className="min-h-11 rounded-full px-4 text-primary">Отмена</button><button onClick={() => { show(onDelete(deleting.id)); onRefresh(); setDeleting(null); }} className="min-h-11 rounded-full bg-error-container px-5 font-semibold text-on-error-container">Удалить</button></div></div></div>}
  </div>;
}
function ImportAction({ icon: Icon, label, onClick }: { icon: typeof Plus; label: string; onClick: () => void }) {
  return <button onClick={onClick} className="flex min-h-16 flex-col items-center justify-center gap-2 rounded-2xl bg-surface-container px-1 py-3 text-[11.5px] font-semibold text-on-surface-variant"><Icon className="h-5 w-5 text-primary" />{label}</button>;
}
function CardAction({ icon: Icon, label, onClick }: { icon: typeof Plus; label: string; onClick: () => void }) {
  return <button aria-label={label} onClick={onClick} className="flex min-h-14 min-w-0 flex-col items-center justify-center gap-1 rounded-xl px-1 text-[11px] font-semibold text-on-surface-variant active:bg-primary/15"><Icon className="h-4 w-4 shrink-0" /><span className="max-w-full truncate">{label}</span></button>;
}
function Editor({ draft, busy, error, onChange, onClose, onSave }: { draft: Draft; busy: boolean; error: string | null; onChange: (draft: Draft) => void; onClose: () => void; onSave: () => void }) {
  const [visible, setVisible] = useState(false);
  const update = (key: keyof Draft, value: string) => onChange({ ...draft, [key]: value });
  const input = "w-full min-w-0 rounded-2xl border border-outline-variant bg-surface-container px-4 py-3.5 text-[15px] text-on-surface outline-none placeholder:text-on-surface-variant/65 focus:border-primary disabled:opacity-60";
  return <div className="pf-profile-overlay"><form role="dialog" aria-modal="true" aria-labelledby="profile-editor-title" className="pf-profile-dialog" onSubmit={e => { e.preventDefault(); onSave(); }}>
    <div className="flex shrink-0 items-center justify-between gap-3 px-5 pb-3 pt-5"><h2 id="profile-editor-title" className="text-[23px] font-bold text-on-surface">{draft.isNew ? "Новый профиль" : "Изменить профиль"}</h2><button type="button" aria-label="Закрыть" onClick={onClose} className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-on-surface-variant"><X className="h-5 w-5" /></button></div>
    <div className="min-h-0 flex-1 overflow-y-auto px-5 pb-4">
      <div className="flex flex-col gap-4">
        <label className="block"><span className="mb-2 block text-[13px] font-medium text-on-surface-variant">Название</span><input disabled={busy} value={draft.name} onChange={e => update("name", e.target.value)} placeholder="Например, Основной" autoComplete="off" maxLength={80} className={input} /></label>
        <label className="block"><span className="mb-2 block text-[13px] font-medium text-on-surface-variant">IP или домен сервера</span><input disabled={busy} value={draft.server} onChange={e => update("server", e.target.value)} placeholder="Адрес вашего сервера" autoCapitalize="none" autoCorrect="off" spellCheck={false} autoComplete="off" maxLength={253} className={input} /></label>
        <label className="block"><span className="mb-2 block text-[13px] font-medium text-on-surface-variant">Пароль профиля</span><span className="relative block"><input disabled={busy} value={draft.token} onChange={e => update("token", e.target.value)} type={visible ? "text" : "password"} placeholder={draft.isNew ? "Ключ доступа с сервера" : "••••••••"} autoCapitalize="none" autoCorrect="off" autoComplete="off" spellCheck={false} maxLength={128} className={input + " pr-12"} /><button type="button" aria-label={visible ? "Скрыть пароль" : "Показать введённый пароль"} onClick={() => setVisible(!visible)} className="absolute right-1 top-1 flex h-11 w-11 items-center justify-center text-on-surface-variant">{visible ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}</button></span></label>
        <p className="text-[12px] leading-5 text-on-surface-variant">{draft.isNew ? "Параметры подключения загрузятся автоматически. Используйте ключ профиля, выданный сервером, а не SSH-пароль VPS." : "Сохранённый пароль остаётся без изменений. Чтобы изменить сервер или ключ, введите пароль нужного профиля."}</p>
        {error && <p role="alert" className="rounded-2xl bg-error-container p-3 text-[13px] leading-5 text-on-error-container">{error}</p>}
      </div>
    </div>
    <div className="flex shrink-0 items-center justify-end gap-2 border-t border-outline-variant/30 px-5 py-4"><button type="button" onClick={onClose} className="min-h-11 rounded-full px-4 text-[14px] font-semibold text-primary">Отмена</button><button disabled={busy} type="submit" className="flex min-h-11 items-center justify-center gap-2 rounded-full bg-primary px-5 py-3 text-[14px] font-bold text-on-primary disabled:opacity-65">{busy && <LoaderCircle className="h-4 w-4 animate-spin" />}{busy ? "Загружаем…" : "Сохранить"}</button></div>
  </form></div>;
}
