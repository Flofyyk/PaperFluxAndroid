import { Cloud, Plug } from "lucide-react";
import { Card } from "../ui/Card";
import { Badge } from "../ui/Chip";

export function ChannelCard() {
  return (
    <Card>
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-[14px] font-bold text-on-surface">Канал передачи</h3>
        <Badge tone="primary">YANDEX</Badge>
      </div>
      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-surface-container-high">
            <Cloud className="h-5 w-5 text-primary" strokeWidth={2} />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[13px] font-semibold text-on-surface">Yandex Docs</p>
            <p className="truncate text-[12px] text-on-surface-variant">Документ как транспортный контейнер</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-surface-container-high">
            <Plug className="h-5 w-5 text-primary" strokeWidth={2} />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[13px] font-semibold text-on-surface">Engine.IO transport</p>
            <p className="truncate text-[12px] text-on-surface-variant">WebSocket / polling fallback</p>
          </div>
        </div>
      </div>
    </Card>
  );
}
