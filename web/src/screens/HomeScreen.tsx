import type { ConnectionStatus, PaperFluxProfile, SessionStats, Stage } from "../types";
import { HomeTopBar } from "../components/home/HomeTopBar";
import { ConnectButton } from "../components/home/ConnectButton";
import { StatusHint } from "../components/home/StatusHint";
import { StatusCard } from "../components/home/StatusCard";
import { StageList } from "../components/home/StageList";
import { DisconnectCard } from "../components/home/DisconnectCard";
import { StatsGrid } from "../components/home/StatsGrid";
import { ActiveProfileCard } from "../components/home/ActiveProfileCard";

export function HomeScreen({
  status,
  stages,
  stats,
  errorReason,
  profile,
  onOpenProfiles,
  onToggleConnection,
  onRetry,
}: {
  status: ConnectionStatus;
  stages: Stage[];
  stats: SessionStats;
  errorReason: string | null;
  profile?: PaperFluxProfile;
  onOpenProfiles: () => void;
  onToggleConnection: () => void;
  onRetry: () => void;
}) {
  const active = status === "connected";

  return (
    <div className="flex h-full flex-col">
      <HomeTopBar />

      <div className="@container flex-1 overflow-y-auto">
        <div className="flex flex-col items-center gap-3 px-5 pb-6 pt-4">
          <ActiveProfileCard profile={profile} onOpenProfiles={onOpenProfiles} />
          <ConnectButton status={status} onPress={onToggleConnection} />
          <StatusHint status={status} />
          <StatsGrid stats={stats} active={active} />
        </div>

        <div className="flex flex-col gap-3.5 px-5 pb-8 @[420px]:grid @[420px]:grid-cols-2 @[420px]:gap-4 @[420px]:px-6">
          <div className="@[420px]:col-span-2">
            <StatusCard status={status} />
          </div>

          {(status === "error" || status === "reconnecting") && errorReason && (
            <div className="@[420px]:col-span-2">
              <DisconnectCard
                reason={errorReason}
                onRetry={onRetry}
              />
            </div>
          )}

          <StageList stages={stages} />
        </div>
      </div>

    </div>
  );
}
