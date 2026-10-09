import type { AppExceptionItem, VpnSettings } from "../types";

export const DEFAULT_SETTINGS: VpnSettings = {
  connectionMode: "vpn",
  documentUrl: "",
  dnsPrimary: "77.88.8.8",
  dnsSecondary: "77.88.8.1",
  mtu: 1400,
  autoReconnect: true,
  autoConnect: false,
  connectTimeoutSec: 15,
};

export const DEFAULT_APPS: AppExceptionItem[] = [];
