import type { ConnectionStatus, PaperFluxProfile, SessionStats, Stage } from "../types";
import { HomeTopBar } from "../components/home/HomeTopBar";
import { ConnectButton } from "../components/home/ConnectButton";
import { StatusHint } from "../components/home/StatusHint";
import { StatusCard } from "../components/home/StatusCard";
import { StageList } from "../components/home/StageList";
import { DisconnectCard } from "../components/home/DisconnectCard";
import { StatsGrid } from "../components/home/StatsGrid";
import { ActiveProfileCard } from "../components/home/ActiveProfileCard";
import { VerificationCard } from "../components/home/VerificationCard";
import type { VerificationState } from "../utils/verification";

export function HomeScreen({
  status,
  stages,
  stats,
  errorReason,
  verification,
  onOpenVerification,
  profile,
  onOpenProfiles,
  onToggleConnection,
  onRetry,
}: {
  status: ConnectionStatus;
  stages: Stage[];
  stats: SessionStats;
  errorReason: string | null;
  verification: VerificationState | null;
  onOpenVerification: () => void;
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
          {verification && !active && <div className="w-full"><VerificationCard verification={verification} onOpen={onOpenVerification} /></div>}
        </div>

        <div className="flex flex-col gap-3.5 px-5 pb-8 @[420px]:gap-4 @[420px]:px-6">
          <div>
            <StatusCard status={status} />
          </div>

          {(status === "error" || status === "reconnecting") && errorReason && (
            <div>
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
