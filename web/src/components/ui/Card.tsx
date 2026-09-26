import type { ReactNode } from "react";
import { cn } from "../../utils/cn";

export function Card({
  children,
  className,
  tone = "default",
}: {
  children: ReactNode;
  className?: string;
  tone?: "default" | "outline";
}) {
  return (
    <div
      className={cn(
        "pf-fade-up rounded-[26px] p-5",
        tone === "default" && "bg-surface-container shadow-sm shadow-black/[0.03] ring-1 ring-outline-variant/30",
        tone === "outline" && "border border-outline-variant/60 bg-transparent",
        className,
      )}
    >
      {children}
    </div>
  );
}

export function SectionLabel({ children }: { children: ReactNode }) {
  return (
    <h3 className="mb-3 px-1 text-[13px] font-bold uppercase tracking-wide text-on-surface-variant">
      {children}
    </h3>
  );
}
