import { LogoWordmark } from "../Logo";
import { Badge } from "../ui/Chip";
import type { PaperFluxProfile } from "../../types";
import { transportPresentation } from "../../utils/transportPresentation";

export function HomeTopBar({ profile }: { profile?: PaperFluxProfile }) {
  const transport = transportPresentation(profile);
  return (
    <div className="flex items-center justify-between px-5 pb-2 pt-3">
      <LogoWordmark subtitle={transport.subtitle} />
      <div className="flex items-center gap-2" data-testid="home-transport-badge">
        <Badge tone="primary">{transport.badge}</Badge>
      </div>
    </div>
  );
}
