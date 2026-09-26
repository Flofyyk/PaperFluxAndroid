import { cn } from "../../utils/cn";

export function Switch({
  checked,
  onChange,
  disabled,
  "aria-label": ariaLabel,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
  "aria-label"?: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={ariaLabel}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        "relative h-[26px] w-[44px] shrink-0 rounded-full border transition-colors duration-200 ease-out",
        checked
          ? "border-primary bg-primary"
          : "border-outline bg-transparent",
        disabled && "opacity-40",
      )}
    >
      <span
        className={cn(
          "absolute top-1/2 h-[18px] w-[18px] -translate-y-1/2 rounded-full shadow-sm transition-all duration-200 ease-out",
          checked
            ? "left-[23px] bg-on-primary scale-100"
            : "left-[4px] bg-outline scale-90",
        )}
      />
    </button>
  );
}
