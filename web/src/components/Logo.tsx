import { cn } from "../utils/cn";

export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 48 48" fill="none" className={className} aria-hidden>
      <defs>
        <linearGradient id="pf-logo-grad" x1="4" y1="2" x2="44" y2="46" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#8b7dff" />
          <stop offset="55%" stopColor="#5b4fe8" />
          <stop offset="100%" stopColor="#3f34b8" />
        </linearGradient>
      </defs>
      <rect x="2" y="2" width="44" height="44" rx="13" fill="url(#pf-logo-grad)" />
      <path d="M15 10h12l6 6v22H15V10z" fill="white" fillOpacity="0.96" />
      <path d="M27 10v7h6" fill="none" stroke="#5b4fe8" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M19 22h10M19 26h10M19 30h7" stroke="#5b4fe8" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M18 35h12" stroke="#c9c3ff" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

export function LogoWordmark({ className }: { className?: string }) {
  return (
    <div className={cn("flex items-center gap-2.5", className)}>
      <LogoMark className="h-9 w-9 drop-shadow-sm" />
      <div className="flex flex-col leading-none">
        <span className="text-[16.5px] font-extrabold tracking-tight text-on-surface">PaperFlux</span>
        <span className="text-[10.5px] font-medium tracking-wide text-on-surface-variant">Yandex Docs transport</span>
      </div>
    </div>
  );
}
