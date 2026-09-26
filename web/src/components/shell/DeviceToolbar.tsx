import { Moon, Sun } from "lucide-react";
import { cn } from "../../utils/cn";

const widths: { id: 360 | 412 | 600; label: string }[] = [
  { id: 360, label: "360dp" },
  { id: 412, label: "412dp" },
  { id: 600, label: "600dp" },
];

export function DeviceToolbar({
  width,
  onWidth,
  dark,
  onToggleDark,
}: {
  width: 360 | 412 | 600;
  onWidth: (w: 360 | 412 | 600) => void;
  dark: boolean;
  onToggleDark: () => void;
}) {
  return (
    <div className="flex w-full max-w-3xl flex-wrap items-center justify-between gap-3 rounded-3xl bg-white/70 px-4 py-3 shadow-sm ring-1 ring-slate-200 backdrop-blur">
      <div className="flex items-center gap-1 rounded-full bg-slate-100 p-1">
        {widths.map((w) => (
          <button
            key={w.id}
            onClick={() => onWidth(w.id)}
            className={cn(
              "rounded-full px-3.5 py-1.5 text-[12.5px] font-bold transition-colors",
              width === w.id ? "bg-white text-indigo-700 shadow-sm" : "text-slate-500",
            )}
          >
            {w.label}
          </button>
        ))}
      </div>
      <button
        onClick={onToggleDark}
        className="flex items-center gap-2 rounded-full bg-slate-900 px-3.5 py-2 text-[12.5px] font-bold text-white transition-transform active:scale-95"
      >
        {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        {dark ? "Светлая тема" : "Тёмная тема"}
      </button>
    </div>
  );
}
