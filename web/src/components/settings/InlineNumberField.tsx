export function InlineNumberField({
  value,
  onChange,
  suffix,
  min = 0,
  max = 100000,
  width = "w-20",
}: {
  value: number;
  onChange: (v: number) => void;
  suffix?: string;
  min?: number;
  max?: number;
  width?: string;
}) {
  return (
    <div className="flex items-center gap-1.5 rounded-xl border border-outline-variant bg-surface px-3 py-1.5">
      <input
        type="number"
        value={value}
        min={min}
        max={max}
        onChange={(e) => {
          const v = Number(e.target.value);
          if (!Number.isNaN(v)) onChange(Math.min(max, Math.max(min, v)));
        }}
        className={`${width} bg-transparent text-right text-[13px] font-bold text-on-surface outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none`}
      />
      {suffix && <span className="text-[12px] font-medium text-on-surface-variant">{suffix}</span>}
    </div>
  );
}

export function InlineTextField({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <input
      type="text"
      value={value}
      placeholder={placeholder}
      onChange={(e) => onChange(e.target.value)}
      className="w-full rounded-xl border border-outline-variant bg-surface px-3 py-2 text-[12.5px] font-medium text-on-surface outline-none focus:border-primary"
    />
  );
}
