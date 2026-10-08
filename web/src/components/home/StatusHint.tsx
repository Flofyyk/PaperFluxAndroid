import type { ConnectionStatus } from "../../types";
import { memo } from "react";
import { cn } from "../../utils/cn";

const text: Record<ConnectionStatus, string> = {
  idle: "Нажмите для подключения",
  connecting: "Подключение…",
  connected: "Подключено",
  error: "Обрыв соединения",
  reconnecting: "Переподключение…",
};

const sub: Record<ConnectionStatus, string> = {
  idle: "",
  connecting: "Проходим этапы инициализации туннеля",
  connected: "Сессия защищена, трафик маршрутизируется",
  error: "Проверьте журнал событий для деталей",
  reconnecting: "Повторная попытка восстановления сессии",
};

const color: Record<ConnectionStatus, string> = {
  idle: "text-on-surface",
  connecting: "text-primary",
  connected: "text-success",
  error: "text-error",
  reconnecting: "text-warning",
};

export const StatusHint = memo(function StatusHint({ status }: { status: ConnectionStatus }) {
  return (
    <div key={status} className="pf-fade-up flex flex-col items-center gap-1 text-center">
      <span className={cn("text-[19px] font-extrabold tracking-tight", color[status])}>{text[status]}</span>
      {sub[status] && <span className="text-[12.5px] font-medium text-on-surface-variant">{sub[status]}</span>}
    </div>
  );
});
