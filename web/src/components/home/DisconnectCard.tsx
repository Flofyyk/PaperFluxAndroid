import { AlertTriangle, RotateCw } from "lucide-react";

export function DisconnectCard({ reason, onRetry, disabled = false }: { reason: string; onRetry: () => void; disabled?: boolean }) {
  return (
    <div className="pf-fade-up flex flex-col gap-3 rounded-[26px] bg-error-container p-5 ring-1 ring-error/20">
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-error/15">
          <AlertTriangle className="h-5 w-5 text-error" strokeWidth={2.2} />
        </div>
        <div className="min-w-0">
          <p className="text-[14.5px] font-bold text-on-error-container">Обрыв соединения</p>
          <p className="mt-0.5 text-[13px] leading-snug text-on-error-container/85">Причина: {reason}</p>
        </div>
      </div>
      <button
        onClick={onRetry}
        disabled={disabled}
        className="flex items-center justify-center gap-2 rounded-full bg-error px-4 py-2.5 text-[13px] font-bold text-on-error transition-transform active:scale-[0.98]"
      >
        <RotateCw className="h-4 w-4" strokeWidth={2.4} />
        Повторить подключение
      </button>
    </div>
  );
}
