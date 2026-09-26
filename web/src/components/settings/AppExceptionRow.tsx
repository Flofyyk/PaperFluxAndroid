import type { AppExceptionItem } from "../../types";
import { Switch } from "../ui/Switch";
import { memo, useEffect, useRef, useState } from "react";

export const AppExceptionRow = memo(function AppExceptionRow({
  app,
  onToggle,
}: {
  app: AppExceptionItem;
  onToggle: (id: string, value: boolean) => void;
}) {
  const [icon, setIcon] = useState(app.icon ?? "");
  const row = useRef<HTMLDivElement>(null);
  useEffect(() => {
    setIcon(app.icon ?? "");
    if (app.icon) return;
    const load = () => {
      const native = (window as unknown as { PaperFluxNative?: { getAppIcon?: (pkg: string) => string } }).PaperFluxNative;
      const value = native?.getAppIcon?.(app.pkg) ?? "";
      if (value) setIcon(value);
    };
    const observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting) { observer.disconnect(); load(); }
    }, { root: row.current?.parentElement?.parentElement ?? null, rootMargin: "96px" });
    if (row.current) observer.observe(row.current);
    return () => observer.disconnect();
  }, [app.icon, app.pkg]);
  return (
    <div ref={row} className="flex items-center gap-3.5 py-3">
      {icon ? (
        <img src={icon} alt="" className="h-9 w-9 shrink-0 rounded-xl object-contain" />
      ) : (
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-[13px] font-extrabold text-white" style={{ backgroundColor: app.color }}>
          {app.glyph}
        </div>
      )}
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13px] font-semibold text-on-surface">{app.name}</p>
        <p className="truncate text-[11.5px] text-on-surface-variant">{app.pkg}</p>
      </div>
      <Switch checked={app.excluded} onChange={(v) => onToggle(app.id, v)} aria-label={`Исключить ${app.name}`} />
    </div>
  );
});
