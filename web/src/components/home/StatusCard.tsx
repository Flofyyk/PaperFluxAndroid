import { Loader2, ShieldAlert, ShieldCheck, ShieldOff } from "lucide-react";
import { memo } from "react";
import type { ConnectionStatus, PaperFluxProfile } from "../../types";
import { transportPresentation } from "../../utils/transportPresentation";
import { Card } from "../ui/Card";
import { cn } from "../../utils/cn";

const meta: Record<
  ConnectionStatus,
  { title: string; desc: string; icon: typeof ShieldCheck; iconBg: string; iconColor: string }
> = {
  idle: {
    title: "Туннель отключён",
    desc: "VPN отключён. Трафик идёт напрямую.",
    icon: ShieldOff,
    iconBg: "bg-surface-container-high",
    iconColor: "text-on-surface-variant",
  },
  connecting: {
    title: "Установка туннеля…",
    desc: "Выполняется поэтапная инициализация транспорта.",
    icon: Loader2,
    iconBg: "bg-primary-container",
    iconColor: "text-on-primary-container",
  },
  connected: {
    title: "Туннель активен",
    desc: "Трафик маршрутизируется через защищённый туннель.",
    icon: ShieldCheck,
    iconBg: "bg-success-container",
    iconColor: "text-on-success-container",
  },
  error: {
    title: "Обрыв соединения",
    desc: "Туннель разорван, требуется повторное подключение.",
    icon: ShieldAlert,
    iconBg: "bg-error-container",
    iconColor: "text-on-error-container",
  },
  reconnecting: {
    title: "Переподключение…",
    desc: "PaperFlux пытается восстановить сессию автоматически.",
    icon: Loader2,
    iconBg: "bg-warning-container",
    iconColor: "text-on-warning-container",
  },
};

export const StatusCard = memo(function StatusCard({ status, profile }: { status: ConnectionStatus; profile?: PaperFluxProfile }) {
  const m = meta[status];
  const description = status === "connected" && profile
    ? `Трафик маршрутизируется через ${transportPresentation(profile).label}.`
    : m.desc;
  const Icon = m.icon;
  const spinning = status === "connecting" || status === "reconnecting";

  return (
    <Card key={status} className="flex items-center gap-4">
      <div className={cn("flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl", m.iconBg)}>
        <Icon className={cn("h-7 w-7", m.iconColor, spinning && "pf-spin")} strokeWidth={2} />
      </div>
      <div className="min-w-0">
        <p className="text-[15.5px] font-bold text-on-surface">{m.title}</p>
        <p className="mt-0.5 text-[13px] leading-snug text-on-surface-variant">{description}</p>
      </div>
    </Card>
  );
});
