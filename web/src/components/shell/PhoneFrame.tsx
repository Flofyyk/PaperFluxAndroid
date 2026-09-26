import type { ReactNode } from "react";

export function PhoneFrame({
  width,
  height,
  dark,
  children,
}: {
  width: number;
  height: number;
  dark: boolean;
  children: ReactNode;
}) {
  return (
    <div
      className="relative shrink-0 rounded-[42px] bg-[#0b0b10] p-[9px] shadow-[0_30px_70px_-25px_rgba(20,15,60,0.55)] ring-1 ring-black/10"
      style={{ width: width + 18, height }}
    >
      <div className="absolute left-1/2 top-0 z-20 h-5 w-24 -translate-x-1/2 rounded-b-2xl bg-[#0b0b10]" />
      <div
        className={dark ? "pf-dark relative h-full w-full overflow-hidden rounded-[34px] ring-1 ring-white/5" : "relative h-full w-full overflow-hidden rounded-[34px] ring-1 ring-black/5"}
        style={{ width, backgroundColor: "var(--pf-bg)" }}
      >
        <div className="relative flex h-full w-full flex-col overflow-hidden bg-surface text-on-surface">
          {children}
        </div>
      </div>
    </div>
  );
}
