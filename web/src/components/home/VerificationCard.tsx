import { ShieldAlert, ArrowUpRight } from "lucide-react";
import type { VerificationState } from "../../utils/verification";

export function VerificationCard({ verification, onOpen }: {
  verification: VerificationState; onOpen: () => void;
}) {
  return (
    <section aria-label="Проверка Яндекса" data-verification-card className="flex flex-col gap-3 rounded-[26px] bg-warning-container p-5 ring-1 ring-warning/30">
      <div className="flex items-start gap-3">
        <ShieldAlert aria-hidden="true" className="mt-0.5 h-6 w-6 shrink-0 text-warning" />
        <div className="min-w-0">
          <p className="text-[15px] font-bold text-warning">Требуется подтверждение Яндекса</p>
          <p className="mt-1 text-[13px] font-semibold text-on-warning-container">{verification.carrier} · {verification.side}</p>
          <p className="mt-1 text-[13px] leading-snug text-on-warning-container/85">
            Подключение ожидает подтверждения доступа.
          </p>
          <p className="mt-2 text-[12px] leading-snug text-on-warning-container/80">
            {verification.automatic ? "Окно проверки откроется автоматически." : "Окно этого канала уже показывалось. Повторный запрос не открывает его снова."}
            {" "}После подтверждения результат передастся автоматически.
          </p>
        </div>
      </div>
      <button type="button" onClick={onOpen} className="flex min-h-11 items-center justify-center gap-2 rounded-full bg-warning px-4 py-2.5 text-[13px] font-bold text-on-warning transition-transform active:scale-[0.98]">
        Открыть проверку <ArrowUpRight aria-hidden="true" className="h-4 w-4" />
      </button>
    </section>
  );
}
