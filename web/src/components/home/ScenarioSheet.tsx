import { CheckCircle2 } from "lucide-react";
import type { Scenario } from "../../types";
import { BottomSheet } from "../ui/BottomSheet";
import { cn } from "../../utils/cn";

const options: { id: Scenario; title: string; desc: string }[] = [
  {
    id: "stable",
    title: "Стабильное подключение",
    desc: "Все этапы проходят успешно, туннель держится без сбоев.",
  },
  {
    id: "drop",
    title: "Обрыв соединения",
    desc: "Подключение установится, но сессия прервётся через несколько секунд.",
  },
  {
    id: "auth-error",
    title: "Ошибка авторизации документа",
    desc: "Транспорт откажет в доступе на этапе проверки документа.",
  },
  {
    id: "flaky",
    title: "Нестабильная сеть",
    desc: "Первая попытка провалится по таймауту, затем автопереподключение.",
  },
];

export function ScenarioSheet({
  open,
  onClose,
  value,
  onChange,
}: {
  open: boolean;
  onClose: () => void;
  value: Scenario;
  onChange: (s: Scenario) => void;
}) {
  return (
    <BottomSheet open={open} onClose={onClose} title="Сценарий предпросмотра">
      <p className="mb-4 -mt-2 text-[12.5px] leading-snug text-on-surface-variant">
        Влияет на поведение следующей попытки подключения. Используется только в этом прототипе.
      </p>
      <div className="space-y-2">
        {options.map((o) => {
          const selected = o.id === value;
          return (
            <button
              key={o.id}
              onClick={() => {
                onChange(o.id);
                onClose();
              }}
              className={cn(
                "flex w-full items-start gap-3 rounded-2xl border p-3.5 text-left transition-colors",
                selected
                  ? "border-primary bg-primary-container/60"
                  : "border-outline-variant/60 bg-surface-container",
              )}
            >
              <div className="min-w-0 flex-1">
                <p className="text-[13.5px] font-bold text-on-surface">{o.title}</p>
                <p className="mt-0.5 text-[12px] leading-snug text-on-surface-variant">{o.desc}</p>
              </div>
              {selected && <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-primary" strokeWidth={2.2} />}
            </button>
          );
        })}
      </div>
    </BottomSheet>
  );
}
