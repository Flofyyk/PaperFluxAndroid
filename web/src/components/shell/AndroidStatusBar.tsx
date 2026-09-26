import { Wifi, BatteryFull, SignalHigh } from "lucide-react";

export function AndroidStatusBar() {
  return (
    <div className="flex h-9 shrink-0 items-center justify-between px-5 pt-1.5 text-[12px] font-semibold text-on-surface">
      <span className="tabular-nums">21:47</span>
      <div className="flex items-center gap-1.5">
        <SignalHigh className="h-3.5 w-3.5" strokeWidth={2.4} />
        <Wifi className="h-3.5 w-3.5" strokeWidth={2.4} />
        <BatteryFull className="h-4 w-4" strokeWidth={2.2} />
      </div>
    </div>
  );
}
