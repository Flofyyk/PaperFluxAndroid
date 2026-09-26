import { ArrowDown, ArrowUp, Gauge } from "lucide-react";
import type { SessionStats } from "../../types";
import { formatBytes, formatRate } from "../../utils/format";

function TrafficMetric({ icon: Icon, total, rate, active }: { icon: typeof ArrowDown; total: number; rate: number; active: boolean }) {
  return (
    <div className="flex min-w-0 items-center gap-2.5">
      <Icon className="h-4 w-4 shrink-0 text-on-surface-variant" strokeWidth={2.25} />
      <div className="min-w-0 leading-tight">
        <div className="truncate text-[13px] font-extrabold tabular-nums text-on-surface">{active ? formatBytes(total) : "—"}</div>
        <div className="mt-0.5 text-[10.5px] font-medium tabular-nums text-on-surface-variant">{active ? formatRate(rate) : "Нет сессии"}</div>
      </div>
    </div>
  );
}

export function StatsGrid({ stats, active }: { stats: SessionStats; active: boolean }) {
  return (
    <section className="grid grid-cols-[minmax(0,1fr)_auto] gap-2.5" aria-label="Показатели соединения">
      <div className="grid min-w-0 grid-cols-2 gap-2 rounded-[22px] bg-surface-container-high/80 px-3.5 py-3">
        <TrafficMetric icon={ArrowDown} total={stats.rxBytes} rate={stats.rxRate} active={active} />
        <TrafficMetric icon={ArrowUp} total={stats.txBytes} rate={stats.txRate} active={active} />
      </div>
      <div className="flex min-w-[88px] items-center justify-center gap-2 rounded-[22px] bg-surface-container-high/80 px-3.5 py-3 text-on-surface">
        <Gauge className="h-4 w-4 text-primary" strokeWidth={2.5} />
        <span className="text-[13px] font-extrabold tabular-nums">{active && stats.ping ? `${stats.ping} мс` : "—"}</span>
      </div>
    </section>
  );
}
