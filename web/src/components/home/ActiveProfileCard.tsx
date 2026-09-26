import { ChevronRight, FileKey2, Pencil } from "lucide-react";
import type { PaperFluxProfile } from "../../types";

export function ActiveProfileCard({ profile, onOpenProfiles }: { profile?: PaperFluxProfile; onOpenProfiles: () => void }) {
  const documents = profile?.documentUrl.split(",").map((item) => item.trim()).filter(Boolean).length ?? 0;
  const subtitle = profile
    ? `Yandex Docs · ${documents > 1 ? `${documents} канала` : "1 канал"}`
    : "Выберите конфигурацию для подключения";

  return (
    <button
      type="button"
      onClick={onOpenProfiles}
      className="flex w-full items-center gap-3 rounded-[22px] border border-outline-variant/25 bg-surface-container-high/80 px-3 py-2.5 text-left shadow-[0_8px_18px_rgba(0,0,0,0.12)] transition-colors active:bg-surface-container-high"
      aria-label={profile ? `Открыть профиль ${profile.name}` : "Открыть профили"}
    >
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-primary-container text-on-primary-container">
        <FileKey2 className="h-5 w-5" strokeWidth={2.25} />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[14px] font-bold text-on-surface">{profile?.name ?? "Профиль не выбран"}</span>
        <span className="mt-0.5 block truncate text-[11.5px] text-on-surface-variant">{subtitle}</span>
      </span>
      <span className="flex shrink-0 items-center gap-2 border-l border-outline-variant/40 pl-2.5 text-on-surface-variant">
        <Pencil className="h-4 w-4" strokeWidth={2.2} />
        <ChevronRight className="h-4 w-4" strokeWidth={2.4} />
      </span>
    </button>
  );
}
