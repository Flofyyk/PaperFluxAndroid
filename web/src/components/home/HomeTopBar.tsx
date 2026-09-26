import { LogoWordmark } from "../Logo";
import { Badge } from "../ui/Chip";

export function HomeTopBar() {
  return (
    <div className="flex items-center justify-between px-5 pb-2 pt-3">
      <LogoWordmark />
      <div className="flex items-center gap-2">
        <Badge tone="primary">YANDEX</Badge>
      </div>
    </div>
  );
}
