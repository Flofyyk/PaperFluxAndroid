export type TabId = "home" | "profiles" | "logs" | "settings";

export interface PaperFluxProfile { id: string; name: string; server: string; documentUrl: string; clientIp?: string; transport?: "yandex" | "vyandex" | "cupsonline" | "mailru"; }

export type ConnectionStatus =
  | "idle"
  | "connecting"
  | "connected"
  | "error"
  | "reconnecting";

export type StageStatus = "pending" | "running" | "success" | "error";

export interface Stage {
  id: "vpn" | "transport" | "auth" | "dns";
  title: string;
  description: string;
  status: StageStatus;
}

export type LogLevel = "success" | "warning" | "error" | "info";

export type LogCategory = "connection" | "network" | "system";

export interface LogEntry {
  id: string;
  time: string;
  stage: string;
  message: string;
  level: LogLevel;
  category: LogCategory;
}

export type LogFilter = "all" | "errors" | "connection" | "network";

export type Scenario = "stable" | "drop" | "auth-error" | "flaky";

export interface SessionStats {
  durationSec: number;
  ping: number | null;
  rxBytes: number;
  txBytes: number;
	/** Measured from consecutive native cumulative counter snapshots. */
	rxRate: number;
	txRate: number;
}

export interface AppExceptionItem {
  id: string;
  name: string;
  pkg: string;
  excluded: boolean;
  glyph: string;
  color: string;
  icon?: string;
}

export interface VpnSettings {
  documentUrl: string;
  dnsPrimary: string;
  dnsSecondary: string;
  mtu: number;
  autoReconnect: boolean;
  autoConnect: boolean;
  connectTimeoutSec: number;
}
