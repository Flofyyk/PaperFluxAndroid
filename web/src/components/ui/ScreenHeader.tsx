import type { ReactNode } from "react";

export function ScreenHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-3 px-5 pb-3 pt-4">
      <div className="min-w-0">
        <h1 className="text-[21px] font-extrabold tracking-tight text-on-surface">{title}</h1>
        {subtitle && <p className="mt-0.5 text-[13px] text-on-surface-variant">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}
