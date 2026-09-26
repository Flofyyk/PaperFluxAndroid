import type { ReactNode } from "react";
import { cn } from "../../utils/cn";

export function Chip({
  active,
  onClick,
  children,
  icon,
}: {
  active?: boolean;
  onClick?: () => void;
  children: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full border px-3.5 py-1.5 text-[12.5px] font-semibold transition-all duration-150",
        active
          ? "border-primary bg-primary-container text-on-primary-container"
          : "border-outline-variant bg-transparent text-on-surface-variant hover:bg-surface-container-high",
      )}
    >
      {icon}
      {children}
    </button>
  );
}

export function Badge({
  tone = "neutral",
  children,
}: {
  tone?: "neutral" | "success" | "warning" | "error" | "primary";
  children: ReactNode;
}) {
  const tones: Record<string, string> = {
    neutral: "bg-surface-container-high text-on-surface-variant",
    success: "bg-success-container text-on-success-container",
    warning: "bg-warning-container text-on-warning-container",
    error: "bg-error-container text-on-error-container",
    primary: "bg-primary-container text-on-primary-container",
  };
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold uppercase tracking-wide",
        tones[tone],
      )}
    >
      {children}
    </span>
  );
}
