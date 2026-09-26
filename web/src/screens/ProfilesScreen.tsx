import { useEffect, useMemo, useState } from "react";
import { Check, ClipboardPlus, FileKey, Link2, Network, Pencil, Plus, ScanQrCode, Server, Trash2, Upload, X } from "lucide-react";
import type { PaperFluxProfile } from "../types";
import { ScreenHeader } from "../components/ui/ScreenHeader";
import { useToast } from "../hooks/useToast";

type Provider = "yandex" | "vyandex" | "mailru" | "cupsonline";
type Draft = { id: string; name: string; server: string; documentUrl: string; clientIp: string; token: string; transport: Provider; isNew: boolean };
const providerName: Record<Provider, string> = { yandex: "Яндекс", vyandex: "Яндекс", mailru: "Mail.ru Документы", cupsonline: "Cups.online" };
const newToken = () => {
  const native = (window as Window & { PaperFluxNative?: { generateProfileToken?: () => string } }).PaperFluxNative;
  if (native?.generateProfileToken) return native.generateProfileToken();
  const bytes = new Uint8Array(32); crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};
// A virtual address is required by Android's VpnService but should not make a
// first manual profile look broken. The value can still be changed for a
// profile which needs its own address.
const blankDraft = (): Draft => ({ id: "", name: "", server: "", documentUrl: "", clientIp: "10.10.10.2", token: newToken(), transport: "yandex", isNew: true });

export function ProfilesScreen({ profiles, activeId, onClipboard, onPickFile, onQr, onSave, onSelect, onDelete, onRefresh }: {
  profiles: PaperFluxProfile[]; activeId: string; onClipboard: () => string; onPickFile: () => void; onQr: () => void; onSave: (raw: string) => string;
  onSelect: (id: string) => string; onDelete: (id: string) => string; onRefresh: () => void;
}) {
  const { show } = useToast();
  const [draft, setDraft] = useState<Draft | null>(null);
  useEffect(() => {
    const win = window as Window & { __paperFluxOnProfileFile?: (result: string) => void };
    win.__paperFluxOnProfileFile = (result) => { show(result); if (result.startsWith("Профиль добавлен")) onRefresh(); };
    return () => { delete win.__paperFluxOnProfileFile; };
  }, [onRefresh, show]);
  const addClipboard = () => { const result = onClipboard(); show(result); if (result.startsWith("Профиль добавлен")) onRefresh(); };
  const edit = (p: PaperFluxProfile) => setDraft({ id: p.id, name: p.name, server: p.server, documentUrl: p.documentUrl, clientIp: p.clientIp ?? "", token: "", transport: p.transport ?? "yandex", isNew: false });
  const save = () => {
    if (!draft) return;
    const result = onSave(JSON.stringify(draft)); show(result);
    if (result.startsWith("Профиль сохранён") || result.startsWith("Профиль создан")) { setDraft(null); onRefresh(); }
  };
  return <div className="h-full overflow-y-auto">
    <ScreenHeader title="Профили" subtitle="Конфигурации PaperFlux на этом устройстве" />
    <div className="flex flex-col gap-3.5 px-5 pb-8">
      <div className="grid grid-cols-2 gap-2 min-[390px]:grid-cols-4">
        <Action icon={ScanQrCode} label="QR-код" onClick={onQr} />
        <Action icon={ClipboardPlus} label="Буфер" onClick={addClipboard} />
        <Action icon={Upload} label="Файл" onClick={() => { onPickFile(); show("Выберите файл конфига"); }} />
        <Action icon={Plus} label="Вручную" onClick={() => setDraft(blankDraft())} />
      </div>
      <p className="px-1 text-[11.5px] leading-4 text-on-surface-variant">Проще всего импортировать готовый профиль из бота по QR-коду или из буфера. Ключ хранится зашифрованным на устройстве.</p>
      {profiles.length === 0 ? <div className="rounded-[26px] bg-surface-container p-8 text-center text-on-surface-variant"><FileKey className="mx-auto mb-3 h-9 w-9 opacity-50" /><p className="text-[14px] font-semibold">Профилей пока нет</p><p className="mt-1 text-[12px]">Добавьте конфиг из буфера, файла или вручную</p></div> : profiles.map(p => <div key={p.id} className={`flex items-center gap-3 rounded-[22px] bg-surface-container p-3 pl-4 ring-1 ring-outline-variant/30 ${p.id === activeId ? "ring-primary/70" : ""}`}>
        <button onClick={() => show(onSelect(p.id))} className="flex min-w-0 flex-1 items-center gap-3 text-left"><span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-primary-container text-on-primary-container"><FileKey className="h-5 w-5" /></span><span className="min-w-0 flex-1"><span className="block truncate text-[14px] font-bold text-on-surface">{p.name}</span><span className="block truncate text-[11.5px] text-on-surface-variant">{providerName[p.transport ?? "yandex"]} · ID {p.id}</span></span>{p.id === activeId && <Check className="h-5 w-5 text-success" />}</button>
        <button aria-label={`Изменить ${p.name}`} onClick={() => edit(p)} className="rounded-full p-2.5 text-on-surface-variant hover:bg-primary/15 hover:text-primary"><Pencil className="h-4 w-4" /></button>
        <button aria-label={`Удалить ${p.name}`} onClick={() => { if (window.confirm(`Удалить профиль «${p.name}»?`)) { show(onDelete(p.id)); onRefresh(); } }} className="rounded-full p-2.5 text-on-surface-variant hover:bg-error/15 hover:text-error"><Trash2 className="h-4 w-4" /></button>
      </div>)}
    </div>
    {draft && <Editor draft={draft} onChange={setDraft} onClose={() => setDraft(null)} onSave={save} />}
  </div>;
}

function Action({ icon: Icon, label, onClick }: { icon: typeof Plus; label: string; onClick: () => void }) {
  return <button onClick={onClick} className="flex flex-col items-center gap-1.5 rounded-2xl bg-primary-container/70 px-2 py-3 text-[12px] font-bold text-on-primary-container active:scale-[0.98]"><Icon className="h-5 w-5" />{label}</button>;
}

function Editor({ draft, onChange, onClose, onSave }: { draft: Draft; onChange: (value: Draft) => void; onClose: () => void; onSave: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const validation = useMemo(() => {
    if (!/^[1-9]\d*$/.test(draft.id.trim())) return "Укажите числовой ID профиля";
    const docs = draft.documentUrl.split(/[;,\n]+/).map(value => value.trim()).filter(Boolean);
    if (draft.transport === "yandex" && (docs.length < 1 || docs.length > 2 || docs.some(value => !/^https:\/\/disk\.yandex\.ru\//.test(value)))) return "Добавьте одну или две ссылки disk.yandex.ru";
    if (draft.transport === "vyandex" && (docs.length !== 1 || !/^https:\/\/disk\.yandex\.ru\//.test(docs[0]))) return "Добавьте одну ссылку на отдельный пустой документ disk.yandex.ru";
    if (draft.transport === "mailru" && (docs.length !== 1 || !/^https:\/\/cloud\.mail\.ru\/public\//.test(docs[0]))) return "Добавьте публичную ссылку cloud.mail.ru";
    if (draft.transport === "cupsonline" && (docs.length !== 1 || !/^[A-Za-z0-9_-]{20,2048}$/.test(docs[0]))) return "Вставьте список комнат Cups.online из серверного профиля";
    if (!/^10\.10\.10\.(?:[1-9]|[1-9]\d|1\d\d|2[0-4]\d|25[0-4])$/.test(draft.clientIp.trim())) return "Проверьте виртуальный IP";
    if (!draft.server.trim()) return "Укажите адрес сервера профиля";
    if (draft.isNew && draft.token.trim().length < 32) return "Сгенерируйте ключ доступа для сервера и приложения";
    return null;
  }, [draft]);
  const update = (key: keyof Draft, value: string) => onChange({ ...draft, [key]: value });
  const field = (label: string, key: keyof Draft, placeholder: string, icon?: React.ElementType, secret = false, disabled = false) => {
    const Icon = icon;
    return <label className="block"><span className="mb-1.5 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide text-on-surface-variant">{Icon && <Icon className="h-3.5 w-3.5" />}{label}</span><input disabled={disabled} value={draft[key] as string} onChange={e => update(key, e.target.value)} type={secret ? "password" : "text"} placeholder={placeholder} className="w-full rounded-xl border border-outline-variant/60 bg-surface-container-high px-3.5 py-3 text-[13px] text-on-surface outline-none placeholder:text-on-surface-variant/60 focus:border-primary disabled:opacity-60" /></label>;
  };
  const save = () => { if (validation) { setError(validation); return; } setError(null); onSave(); };
  return <div className="fixed inset-0 z-50 flex items-end bg-black/65 px-3 pt-3 pb-[calc(0.75rem+var(--pf-inset-bottom))] backdrop-blur-sm"><div className="flex max-h-[calc(100dvh-var(--pf-inset-top)-var(--pf-inset-bottom)-1.5rem)] w-full flex-col overflow-hidden rounded-[28px] bg-surface shadow-2xl ring-1 ring-outline-variant/40">
    <div className="min-h-0 flex-1 overflow-y-auto p-5 pb-3"><div className="mb-5 flex items-start justify-between"><div><h2 className="text-[19px] font-bold text-on-surface">{draft.isNew ? "Новый профиль" : "Изменить профиль"}</h2><p className="mt-1 text-[12px] text-on-surface-variant">{draft.isNew ? "Для своего сервера; конфиг из бота удобнее импортировать по QR" : "Секретный ключ сохранён и здесь не показывается"}</p></div><button onClick={onClose} className="rounded-full p-2 text-on-surface-variant"><X className="h-5 w-5" /></button></div>
    <div className="flex flex-col gap-3.5">
      {field("Название", "name", "Например, Основной", FileKey)}
      {field("ID профиля", "id", "Например, 4", HashIcon, false, !draft.isNew)}
      <label className="block"><span className="mb-1.5 block text-[11px] font-bold uppercase tracking-wide text-on-surface-variant">Транспорт</span><select value={draft.transport === "vyandex" ? "yandex" : draft.transport} onChange={e => update("transport", e.target.value === "yandex" && draft.transport === "vyandex" ? "vyandex" : e.target.value)} className="w-full rounded-xl border border-outline-variant/60 bg-surface-container-high px-3.5 py-3 text-[13px] text-on-surface"><option value="yandex">Яндекс</option><option value="mailru">Mail.ru Документы</option><option value="cupsonline">Cups.online</option></select></label>
      {field(draft.transport === "cupsonline" ? "Комнаты Cups.online" : "Ссылка на документ", "documentUrl", draft.transport === "mailru" ? "https://cloud.mail.ru/public/..." : draft.transport === "cupsonline" ? "Список комнат с сервера" : "https://disk.yandex.ru/i/...", Link2)}
      <p className="-mt-2 text-[11px] leading-4 text-on-surface-variant">{draft.transport === "yandex" ? "Можно указать две ссылки через запятую или с новой строки." : draft.transport === "cupsonline" ? "Комнаты сначала создаются на сервере. Скопируйте выданный сервером список." : "Документ должен быть открыт по публичной ссылке."}</p>
      {draft.transport === "vyandex" && <p className="rounded-xl bg-error-container px-3 py-2 text-[12px] text-on-error-container">Этот профиль передаёт данные через содержимое документа. Используйте отдельный пустой документ; параметры должны совпадать с сервером.</p>}
      <div className="grid grid-cols-2 gap-3">{field("Виртуальный IP", "clientIp", "10.10.10.2", Network)}{field("Сервер профиля", "server", "IP или домен", Server)}</div>
      <p className="-mt-2 text-[11px] leading-4 text-on-surface-variant">Укажите адрес своего exit-node. Профиль на нём должен быть создан с теми же ID, токеном и документами.</p>
      {draft.isNew && <><div className="flex items-center justify-between"><span className="text-[11px] font-bold uppercase tracking-wide text-on-surface-variant">Ключ доступа</span><button type="button" onClick={() => update("token", newToken())} className="text-[12px] font-semibold text-primary">Сгенерировать новый</button></div>{field("Ключ для сервера и приложения", "token", "Создаётся автоматически", HashIcon)}<p className="-mt-2 text-[11px] leading-4 text-on-surface-variant">Этот же ID, ключ и транспорт должны быть настроены на вашем сервере. Не отправляйте ключ посторонним.</p></>}
      {error && <p role="alert" className="rounded-xl bg-error-container px-3 py-2.5 text-[12px] font-semibold text-on-error-container">{error}</p>}
    </div></div>
    <div className="shrink-0 border-t border-outline-variant/35 bg-surface p-4 pt-3"><button onClick={save} className="flex w-full items-center justify-center gap-2 rounded-full bg-primary py-3.5 text-[14px] font-bold text-on-primary"><Check className="h-5 w-5" />Сохранить профиль</button></div>
  </div></div>;
}

function HashIcon({ className }: { className?: string }) { return <span className={className}>#</span>; }
