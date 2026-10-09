import { memo } from "react";
import { RotateCw, Shield, ShieldAlert, ShieldCheck } from "lucide-react";
import type { ConnectionStatus } from "../../types";
import { cn } from "../../utils/cn";

const palette: Record<ConnectionStatus, { from: string; to: string; ring: string; glow: string }> = {
  idle: {
    from: "#7c6cff",
    to: "#4a3fd6",
    ring: "ring-primary/25",
    glow: "shadow-[0_18px_45px_-12px_rgba(91,79,232,0.55)]",
  },
  connecting: {
    from: "#7c6cff",
    to: "#4a3fd6",
    ring: "ring-primary/25",
    glow: "shadow-[0_18px_45px_-12px_rgba(91,79,232,0.55)]",
  },
  connected: {
    from: "#3fd68c",
    to: "#149960",
    ring: "ring-success/25",
    glow: "shadow-[0_18px_45px_-12px_rgba(27,138,90,0.55)]",
  },
  error: {
    from: "#ff6b6b",
    to: "#c8102e",
    ring: "ring-error/25",
    glow: "shadow-[0_18px_45px_-12px_rgba(213,37,63,0.55)]",
  },
  reconnecting: {
    from: "#ffb547",
    to: "#c07600",
    ring: "ring-warning/25",
    glow: "shadow-[0_18px_45px_-12px_rgba(154,99,0,0.5)]",
  },
};

export const ConnectButton = memo(function ConnectButton({
  status,
  onPress,
  disabled = false,
}: {
  status: ConnectionStatus;
  onPress: () => void;
  disabled?: boolean;
}) {
  const p = palette[status];
  const busy = status === "connecting" || status === "reconnecting";

  return (
    <div className="relative flex h-48 w-48 items-center justify-center @[420px]:h-56 @[420px]:w-56">
      {status === "connected" && (
        <span className="pf-pulse absolute inset-3 rounded-full text-success" />
      )}
      {busy && (
        <svg
          className="pf-spin absolute inset-0 h-full w-full"
          viewBox="0 0 100 100"
          fill="none"
        >
          <circle
            cx="50"
            cy="50"
            r="46"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeDasharray="70 190"
            className={status === "reconnecting" ? "text-warning" : "text-primary"}
            opacity="0.85"
          />
        </svg>
      )}
      <button
        onClick={onPress}
        disabled={disabled}
        aria-label="Переключить подключение"
        className={cn(
          "relative flex h-40 w-40 items-center justify-center rounded-full ring-8 transition-all duration-500 ease-out @[420px]:h-48 @[420px]:w-48",
          p.ring,
          p.glow,
          disabled && "cursor-not-allowed opacity-60",
        )}
        style={{
          backgroundImage: `radial-gradient(circle at 32% 26%, ${p.from}, ${p.to})`,
        }}
      >
        <span key={status} className="pf-icon-swap flex items-center justify-center">
            {status === "idle" && <Shield className="h-16 w-16 text-white" strokeWidth={1.8} />}
            {status === "connecting" && (
              <RotateCw className="pf-spin h-14 w-14 text-white" strokeWidth={1.8} />
            )}
            {status === "connected" && (
              <ShieldCheck className="h-16 w-16 text-white" strokeWidth={1.8} />
            )}
            {status === "error" && (
              <ShieldAlert className="h-16 w-16 text-white" strokeWidth={1.8} />
            )}
            {status === "reconnecting" && (
              <RotateCw className="pf-spin h-14 w-14 text-white" strokeWidth={1.8} />
            )}
        </span>
      </button>
    </div>
  );
});
