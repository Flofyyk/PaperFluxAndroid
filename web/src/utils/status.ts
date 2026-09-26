import type { LogLevel, StageStatus } from "../types";

export const stageStatusMeta: Record<
  StageStatus,
  { label: string; dot: string; text: string; ring: string }
> = {
  pending: {
    label: "Ожидание",
    dot: "bg-outline",
    text: "text-on-surface-variant",
    ring: "ring-outline-variant/50",
  },
  running: {
    label: "Выполняется",
    dot: "bg-warning",
    text: "text-warning",
    ring: "ring-warning/40",
  },
  success: {
    label: "Успешно",
    dot: "bg-success",
    text: "text-success",
    ring: "ring-success/40",
  },
  error: {
    label: "Ошибка",
    dot: "bg-error",
    text: "text-error",
    ring: "ring-error/40",
  },
};

export const logLevelMeta: Record<LogLevel, { text: string; dot: string; label: string }> = {
  success: { text: "text-success", dot: "bg-success", label: "OK" },
  warning: { text: "text-warning", dot: "bg-warning", label: "WARN" },
  error: { text: "text-error", dot: "bg-error", label: "ERR" },
  info: { text: "text-on-surface-variant", dot: "bg-outline", label: "INFO" },
};
