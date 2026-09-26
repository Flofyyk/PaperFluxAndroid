import { Check, Loader2, X } from "lucide-react";
import { memo } from "react";
import type { Stage } from "../../types";
import { Card } from "../ui/Card";
import { stageStatusMeta } from "../../utils/status";
import { cn } from "../../utils/cn";

function StageDot({ status }: { status: Stage["status"] }) {
  const m = stageStatusMeta[status];
  return (
    <div
      className={cn(
        "relative flex h-8 w-8 shrink-0 items-center justify-center rounded-full ring-4 transition-colors duration-300",
        m.ring,
        m.dot,
      )}
    >
      {status === "running" && <Loader2 className="pf-spin h-4 w-4 text-white" strokeWidth={2.6} />}
      {status === "success" && <Check className="h-4 w-4 text-white" strokeWidth={3} />}
      {status === "error" && <X className="h-4 w-4 text-white" strokeWidth={3} />}
    </div>
  );
}

export const StageList = memo(function StageList({ stages }: { stages: Stage[] }) {
  return (
    <Card>
      <h3 className="mb-4 text-[14px] font-bold text-on-surface">Этапы подключения</h3>
      <div className="flex flex-col">
        {stages.map((s, i) => {
          const m = stageStatusMeta[s.status];
          const isLast = i === stages.length - 1;
          return (
            <div key={s.id} className="flex gap-3.5">
              <div className="flex flex-col items-center">
                <StageDot status={s.status} />
                {!isLast && (
                  <span
                    className={cn(
                      "my-1 w-[2px] flex-1 rounded-full transition-colors duration-300",
                      s.status === "success" ? "bg-success/50" : "bg-outline-variant",
                    )}
                  />
                )}
              </div>
              <div className={cn("min-w-0 flex-1", !isLast && "pb-4")}>
                <div className="flex items-center justify-between gap-2">
                  <p className="text-[13.5px] font-semibold text-on-surface">{s.title}</p>
                  <span className={cn("shrink-0 text-[11px] font-bold uppercase tracking-wide", m.text)}>
                    {m.label}
                  </span>
                </div>
                <p className="mt-0.5 text-[12px] leading-snug text-on-surface-variant">{s.description}</p>
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
});
