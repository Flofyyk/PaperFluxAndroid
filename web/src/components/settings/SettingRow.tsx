import type { ReactNode } from "react";
import { cn } from "../../utils/cn";

export function SettingRow({
  icon,
  title,
  supporting,
  trailing,
  className,
}: {
  icon?: ReactNode;
  title: string;
  supporting?: string;
  trailing?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex items-center gap-3.5 py-3.5", className)}>
      {icon && (
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-surface-container-high text-on-surface-variant">
          {icon}
        </div>
      )}
      <div className="min-w-0 flex-1">
        <p className="text-[13.5px] font-semibold text-on-surface">{title}</p>
        {supporting && <p className="mt-0.5 text-[12px] leading-snug text-on-surface-variant">{supporting}</p>}
      </div>
      {trailing && <div className="shrink-0">{trailing}</div>}
    </div>
  );
}

export function SettingsSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="pf-fade-up rounded-[26px] bg-surface-container p-5 shadow-sm shadow-black/[0.03] ring-1 ring-outline-variant/30">
      <h3 className="mb-1 text-[13px] font-bold uppercase tracking-wide text-on-surface-variant">{title}</h3>
      <div className="divide-y divide-outline-variant/30">{children}</div>
    </div>
  );
}
