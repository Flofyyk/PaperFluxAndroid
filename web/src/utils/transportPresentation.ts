import type { PaperFluxProfile, Stage } from "../types";

export function transportPresentation(profile?: PaperFluxProfile) {
  if (!profile) return { badge: "VPN", label: "документный канал", subtitle: "Документный VPN" };
  switch (profile.transport) {
    case "mailru": return { badge: "MAIL.RU", label: "Mail.ru Документы", subtitle: "Mail.ru Документы" };
    case "cupsonline": return { badge: "CUPS.ONLINE", label: "Cups.online", subtitle: "Cups.online" };
    // Legacy profiles without a transport field use Yandex as well.
    default: return { badge: "YANDEX", label: "Яндекс Документы", subtitle: "Яндекс Документы" };
  }
}

export function transportStages(stages: Stage[], profile?: PaperFluxProfile): Stage[] {
  const { label } = transportPresentation(profile);
  return stages.map(stage => stage.id === "transport"
    ? { ...stage, title: profile ? label : "Документный канал", description: `Установка защищённого соединения через ${label}` }
    : stage);
}
