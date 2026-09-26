const colorGroups: { title: string; items: { name: string; varName: string; on?: string }[] }[] = [
  {
    title: "Основные",
    items: [
      { name: "Primary", varName: "--primary", on: "--on-primary" },
      { name: "Primary container", varName: "--primary-container", on: "--on-primary-container" },
      { name: "Surface", varName: "--surface", on: "--on-surface" },
      { name: "Surface container", varName: "--surface-container", on: "--on-surface" },
    ],
  },
  {
    title: "Статусы",
    items: [
      { name: "Success", varName: "--success", on: "--on-success" },
      { name: "Warning", varName: "--warning", on: "--on-warning" },
      { name: "Error", varName: "--error", on: "--on-error" },
    ],
  },
];

function PaletteColumn({ dark }: { dark: boolean }) {
  return (
    <div className={dark ? "pf-dark rounded-3xl bg-surface p-4 ring-1 ring-outline-variant/40" : "rounded-3xl bg-surface p-4 ring-1 ring-outline-variant/40"}>
      <p className="mb-3 text-[11px] font-bold uppercase tracking-wide text-on-surface-variant">
        {dark ? "Тёмная тема" : "Светлая тема"}
      </p>
      <div className="space-y-4">
        {colorGroups.map((g) => (
          <div key={g.title}>
            <p className="mb-2 text-[10.5px] font-semibold uppercase tracking-wide text-on-surface-variant/80">
              {g.title}
            </p>
            <div className="grid grid-cols-2 gap-2">
              {g.items.map((it) => (
                <div
                  key={it.name}
                  className="flex items-center gap-2 rounded-xl px-2.5 py-2 text-[11px] font-bold"
                  style={{
                    backgroundColor: `var(${it.varName})`,
                    color: it.on ? `var(${it.on})` : undefined,
                  }}
                >
                  <span>{it.name}</span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

const typeScale = [
  { label: "Title (экраны, заголовки)", size: "21px / 800", cls: "text-[21px] font-extrabold" },
  { label: "Headline (кнопка подключения)", size: "19px / 800", cls: "text-[19px] font-extrabold" },
  { label: "Card title", size: "14–15.5px / 700", cls: "text-[15px] font-bold" },
  { label: "Body / supporting text", size: "12–13px / 500", cls: "text-[13px] font-medium" },
  { label: "Caption / badge", size: "10.5–11px / 700, uppercase", cls: "text-[11px] font-bold uppercase tracking-wide" },
];

const spacingScale = [4, 8, 12, 16, 20, 26];
const radiusScale = [12, 16, 20, 26, 999];

const stateLegend: { label: string; color: string }[] = [
  { label: "Ожидание", color: "var(--outline)" },
  { label: "Выполняется", color: "var(--warning)" },
  { label: "Успешно", color: "var(--success)" },
  { label: "Ошибка", color: "var(--error)" },
];

export function UiKitSection() {
  return (
    <div className="w-full max-w-3xl rounded-[32px] bg-white p-6 shadow-sm ring-1 ring-slate-200 sm:p-8">
      <h2 className="text-[20px] font-extrabold tracking-tight text-slate-900">UI-kit PaperFlux</h2>
      <p className="mt-1 text-[13px] text-slate-500">
        Цветовые токены, типографика, отступы и состояния компонентов, используемые во всех экранах.
      </p>

      <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <PaletteColumn dark={false} />
        <PaletteColumn dark={true} />
      </div>

      <div className="mt-8">
        <h3 className="mb-3 text-[13px] font-bold uppercase tracking-wide text-slate-500">Типографика</h3>
        <div className="space-y-2.5 rounded-3xl bg-slate-50 p-4 ring-1 ring-slate-200">
          {typeScale.map((t) => (
            <div key={t.label} className="flex items-center justify-between gap-3 border-b border-slate-200 pb-2.5 last:border-0 last:pb-0">
              <span className={`${t.cls} text-slate-900`}>{t.label}</span>
              <span className="shrink-0 font-mono text-[11px] text-slate-400">{t.size}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <h3 className="mb-3 text-[13px] font-bold uppercase tracking-wide text-slate-500">Отступы (dp)</h3>
          <div className="flex flex-wrap items-end gap-3 rounded-3xl bg-slate-50 p-4 ring-1 ring-slate-200">
            {spacingScale.map((s) => (
              <div key={s} className="flex flex-col items-center gap-1.5">
                <div className="rounded-sm bg-indigo-500" style={{ width: s, height: s }} />
                <span className="font-mono text-[10px] text-slate-400">{s}</span>
              </div>
            ))}
          </div>
        </div>
        <div>
          <h3 className="mb-3 text-[13px] font-bold uppercase tracking-wide text-slate-500">Скругления (radius)</h3>
          <div className="flex flex-wrap items-end gap-3 rounded-3xl bg-slate-50 p-4 ring-1 ring-slate-200">
            {radiusScale.map((r) => (
              <div key={r} className="flex flex-col items-center gap-1.5">
                <div
                  className="h-8 w-8 bg-indigo-500"
                  style={{ borderRadius: r >= 999 ? 999 : r }}
                />
                <span className="font-mono text-[10px] text-slate-400">{r >= 999 ? "full" : r}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="mt-8">
        <h3 className="mb-3 text-[13px] font-bold uppercase tracking-wide text-slate-500">
          Состояния этапов подключения
        </h3>
        <div className="flex flex-wrap gap-4 rounded-3xl bg-slate-50 p-4 ring-1 ring-slate-200">
          {stateLegend.map((s) => (
            <div key={s.label} className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full" style={{ backgroundColor: s.color }} />
              <span className="text-[12.5px] font-semibold text-slate-700">{s.label}</span>
            </div>
          ))}
        </div>
      </div>

      <p className="mt-6 text-[11.5px] text-slate-400">
        Экран «Главная» доступен в состояниях loading / connected / disconnected / error / reconnecting —
        переключите сценарий через иконку колбы рядом с бейджем YANDEX и нажмите кнопку подключения.
      </p>
    </div>
  );
}
