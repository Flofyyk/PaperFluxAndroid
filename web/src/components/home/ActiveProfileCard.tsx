import { ChevronRight, Globe2 } from "lucide-react";
import type { PaperFluxProfile } from "../../types";

export function ActiveProfileCard({ profile, onOpenProfiles }: { profile?: PaperFluxProfile; onOpenProfiles: () => void }) {
  const documents = profile?.documentUrl.split(",").map((item) => item.trim()).filter(Boolean).length ?? 0;
  const subtitle = profile
    ? (profile.serverCount ?? 1) > 1 ? `${profile.serverCount} сервера · авторезерв` : `${profile.transport === "mailru" ? "Mail.ru" : profile.transport === "cupsonline" ? "Cups.online" : "Яндекс"} · ${documents > 1 ? `${documents} канала` : "1 канал"}`
    : "Выберите конфигурацию для подключения";
  const country = profile?.countryCode && /^[A-Z]{2}$/.test(profile.countryCode)
    ? String.fromCodePoint(...[...profile.countryCode].map(c => c.charCodeAt(0) + 127397)) : null;

  return (
    <button
      type="button"
      onClick={onOpenProfiles}
      className="flex w-full items-center gap-3 rounded-[24px] border border-primary/35 bg-surface-container px-3.5 py-3 text-left transition-colors active:bg-surface-container-high"
      aria-label={profile ? `Открыть профиль ${profile.name}` : "Открыть профили"}
    >
      <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-surface-container-high text-[23px] text-on-surface-variant">
        {country || <Globe2 className="h-6 w-6" />}
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-2"><span className="truncate text-[14px] font-bold text-on-surface">{profile?.name ?? "Профиль не выбран"}</span>{profile && <span className="shrink-0 rounded-full bg-primary/15 px-2 py-0.5 text-[9px] font-bold text-primary">ВЫБРАН</span>}</span>
        <span className="mt-0.5 block truncate text-[11.5px] text-on-surface-variant">{profile?.server ? `${profile.server} · ${subtitle}` : subtitle}</span>
      </span>
      <ChevronRight className="h-5 w-5 shrink-0 text-on-surface-variant" />
    </button>
  );
}
