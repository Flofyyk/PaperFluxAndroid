import { useMemo, useState } from "react";
import { ClipboardCopy, ScrollText, Trash2 } from "lucide-react";
import type { LogEntry, LogFilter } from "../types";
import { ScreenHeader } from "../components/ui/ScreenHeader";
import { Chip } from "../components/ui/Chip";
import { LogRow } from "../components/logs/LogRow";
import { useToast } from "../hooks/useToast";

const filters: { id: LogFilter; label: string }[] = [
  { id: "all", label: "Все" },
  { id: "errors", label: "Ошибки" },
  { id: "connection", label: "Подключение" },
  { id: "network", label: "Сеть" },
];

export function LogsScreen({ logs, onClear }: { logs: LogEntry[]; onClear: () => void }) {
  const [filter, setFilter] = useState<LogFilter>("all");
  const { show } = useToast();

  const filtered = useMemo(() => {
    switch (filter) {
      case "errors":
        return logs.filter((l) => l.level === "error" || l.level === "warning");
      case "connection":
        return logs.filter((l) => l.category === "connection");
      case "network":
        return logs.filter((l) => l.category === "network");
      default:
        return logs;
    }
  }, [logs, filter]);

  const handleCopy = async () => {
    const text = filtered
      .slice()
      .reverse()
      .map((l) => `[${l.time}] [${l.level.toUpperCase()}] [${l.stage}] ${l.message}`)
      .join("\n");
    try {
      await navigator.clipboard.writeText(text || "Событий пока нет");
    } catch {
      /* clipboard may be unavailable in preview sandbox */
    }
    show("Журнал скопирован");
  };

  const handleClear = () => {
    onClear();
    show("Журнал очищен");
  };

  return (
    <div className="flex h-full flex-col">
      <ScreenHeader title="Журнал событий" subtitle={`${logs.length} записей за сессию`} />

      <div className="flex gap-2 overflow-x-auto px-5 pb-3">
        {filters.map((f) => (
          <Chip key={f.id} active={filter === f.id} onClick={() => setFilter(f.id)}>
            {f.label}
          </Chip>
        ))}
      </div>

      <div className="min-h-0 flex-1 px-5">
        <div className="h-full overflow-y-auto rounded-[22px] bg-[#131018] ring-1 ring-white/[0.06]">
          {filtered.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center gap-2 px-6 py-16 text-center">
              <ScrollText className="h-9 w-9 text-white/20" strokeWidth={1.6} />
              <p className="text-[13.5px] font-semibold text-white/50">Событий пока нет</p>
              <p className="text-[12px] text-white/30">
                Подключитесь к VPN, чтобы увидеть подробный журнал этапов
              </p>
            </div>
          ) : (
            <div>
              {filtered.map((entry) => (
                <LogRow key={entry.id} entry={entry} />
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="flex gap-2.5 px-5 py-4">
        <button
          onClick={handleClear}
          className="flex flex-1 items-center justify-center gap-2 rounded-full border border-outline-variant py-3 text-[13px] font-bold text-on-surface transition-colors active:bg-surface-container-high"
        >
          <Trash2 className="h-4 w-4" strokeWidth={2.2} />
          Очистить журнал
        </button>
        <button
          onClick={handleCopy}
          className="flex flex-1 items-center justify-center gap-2 rounded-full bg-secondary-container py-3 text-[13px] font-bold text-on-secondary-container transition-transform active:scale-[0.98]"
        >
          <ClipboardCopy className="h-4 w-4" strokeWidth={2.2} />
          Скопировать
        </button>
      </div>
    </div>
  );
}
