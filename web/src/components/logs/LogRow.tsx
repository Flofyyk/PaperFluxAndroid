import type { LogEntry } from "../../types";
import { memo } from "react";
import { logLevelMeta } from "../../utils/status";
import { cn } from "../../utils/cn";

const badgeClasses: Record<LogEntry["level"], string> = {
  success: "bg-success/20 text-success",
  warning: "bg-warning/20 text-warning",
  error: "bg-error/20 text-error",
  info: "bg-surface-container-high text-on-surface-variant",
};

const textClasses: Record<LogEntry["level"], string> = {
  success: "text-success",
  warning: "text-warning",
  error: "text-error",
  info: "text-on-surface",
};

export const LogRow = memo(function LogRow({ entry }: { entry: LogEntry }) {
  const m = logLevelMeta[entry.level];
  return (
    <div className="pf-log-row mx-2 my-1.5 flex gap-2.5 rounded-2xl border border-outline-variant/40 bg-surface-container px-3.5 py-3 shadow-sm">
      <span className={cn("mt-1.5 h-2 w-2 shrink-0 rounded-full", m.dot)} />
      <div className="min-w-0 flex-1 font-mono text-[11.5px] leading-relaxed">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-on-surface-variant/70">{entry.time}</span>
          <span className={cn("rounded px-1.5 py-[1px] text-[9.5px] font-bold uppercase tracking-wide", badgeClasses[entry.level])}>
            {m.label}
          </span>
          <span className="text-on-surface-variant">[{entry.stage}]</span>
        </div>
        <p className={cn("mt-0.5 break-words", textClasses[entry.level])}>{entry.message}</p>
      </div>
    </div>
  );
});
