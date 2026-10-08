import { Home, ScrollText, Settings, Users } from "lucide-react";
import { memo } from "react";
import { cn } from "../../utils/cn";
import type { TabId } from "../../types";

const items: { id: TabId; label: string; icon: typeof Home }[] = [
  { id: "home", label: "Главная", icon: Home },
  { id: "profiles", label: "Профили", icon: Users },
  { id: "logs", label: "Журнал", icon: ScrollText },
  { id: "settings", label: "Настройки", icon: Settings },
];

export const BottomNav = memo(function BottomNav({ active, onChange }: { active: TabId; onChange: (t: TabId) => void }) {
  return (
    <nav aria-label="Навигация" className="pf-bottom-nav shrink-0 px-2 pb-2.5 pt-1.5">
      <div className="flex items-stretch justify-between">
        {items.map(({ id, label, icon: Icon }) => {
          const isActive = active === id;
          return (
            <button
              key={id}
              onClick={() => onChange(id)}
              className="flex flex-1 flex-col items-center gap-1 py-1 outline-none"
            >
              <span
                className={cn(
                  "flex h-8 w-16 items-center justify-center rounded-full transition-all duration-200",
                  isActive ? "bg-secondary-container" : "bg-transparent",
                )}
              >
                <Icon
                  className={cn(
                    "h-[19px] w-[19px] transition-colors",
                    isActive ? "text-on-secondary-container" : "text-on-surface-variant",
                  )}
                  strokeWidth={isActive ? 2.3 : 2}
                />
              </span>
              <span
                className={cn(
                  "text-[11px] transition-colors",
                  isActive ? "font-bold text-on-surface" : "font-medium text-on-surface-variant",
                )}
              >
                {label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
});
