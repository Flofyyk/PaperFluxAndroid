import type { LogLevel } from "../types";

export interface VerificationState { carrier: string; side: "телефон" | "VPS"; automatic: boolean }

export function readVerification(value: unknown): VerificationState | null {
  if (!value || typeof value !== "object") return null;
  const v = value as Partial<VerificationState>;
  if (typeof v.carrier !== "string" || (v.side !== "телефон" && v.side !== "VPS")) return null;
  return { carrier: v.carrier.slice(0, 48), side: v.side, automatic: v.automatic === true };
}

export function isVerificationWarning(message: string): boolean {
  return /Яндекс.*(?:требует|требуется).*(?:провер|подтвержд)/i.test(message) || message.startsWith("Результат передан. Проверяем доступ к Яндексу");
}

export function detailLevel(state: string | undefined, message: string): LogLevel {
  if (isVerificationWarning(message)) return "warning";
  if (state === "ERROR") return "error";
  if (state === "CONNECTED") return "success";
  return ["WAITING_NETWORK", "TRANSPORT", "RECONNECTING"].includes(state ?? "") ? "warning" : "info";
}
