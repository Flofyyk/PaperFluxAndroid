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
import { transportStages } from "../utils/transportPresentation";

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
  controlDisabled,
  disconnecting,
  connectionMode,
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
  controlDisabled: boolean;
  disconnecting: boolean;
  connectionMode: "vpn" | "proxy";
}) {
  const active = status === "connected";

  return (
    <div className="flex h-full flex-col">
      <HomeTopBar profile={profile} />

      <div className="@container flex-1 overflow-y-auto">
        <div className="flex flex-col items-center gap-3 px-5 pb-6 pt-4">
          <ActiveProfileCard profile={profile} onOpenProfiles={onOpenProfiles} />
          {connectionMode === "proxy" && <p className="text-center text-[13px] font-semibold text-primary">SOCKS5 · 127.0.0.1:1080 · без системного VPN</p>}
          <ConnectButton status={status} onPress={onToggleConnection} disabled={controlDisabled} />
          {disconnecting ? <p role="status" className="text-sm text-on-surface-variant">Отключаем {connectionMode === "proxy" ? "прокси" : "VPN"}…</p> : <StatusHint status={status} proxy={connectionMode === "proxy"} />}
          <StatsGrid stats={stats} active={active} />
          {verification && !active && <div className="w-full"><VerificationCard verification={verification} onOpen={onOpenVerification} /></div>}
        </div>

        <div className="flex flex-col gap-3.5 px-5 pb-8 @[420px]:gap-4 @[420px]:px-6">
          <div>
            <StatusCard status={status} profile={profile} proxy={connectionMode === "proxy"} />
          </div>

          {(status === "error" || status === "reconnecting") && errorReason && (
            <div>
              <DisconnectCard
                reason={errorReason}
                onRetry={onRetry}
                disabled={controlDisabled}
              />
            </div>
          )}

          <StageList stages={transportStages(stages, profile, connectionMode)} />
        </div>
      </div>

    </div>
  );
}
