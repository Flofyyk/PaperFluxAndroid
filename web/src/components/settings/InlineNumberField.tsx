import { useEffect, useRef, useState } from "react";
import { validNumberDraft } from "../../utils/numberDraft";

export function InlineNumberField({
  value,
  onChange,
  suffix,
  min = 0,
  max = 100000,
  width = "w-20",
}: {
  value: number;
  onChange: (v: number) => string;
  suffix?: string;
  min?: number;
  max?: number;
  width?: string;
}) {
  const [draft, setDraft] = useState(String(value));
  const focused = useRef(false);
  useEffect(() => { if (!focused.current) setDraft(String(value)); }, [value]);
  const finish = () => { focused.current = false; setDraft(String(value)); };
  return (
    <div className="flex items-center gap-1.5 rounded-xl border border-outline-variant bg-surface px-3 py-1.5">
      <input
        type="number"
        value={draft}
        min={min}
        max={max}
        onChange={(e) => {
          const raw = e.target.value;
          setDraft(raw);
          const next = validNumberDraft(raw, min, max);
          if (next !== null) onChange(next);
        }}
        onFocus={() => { focused.current = true; }}
        onBlur={finish}
        onKeyDown={(e) => { if (e.key === "Enter") e.currentTarget.blur(); }}
        inputMode="numeric"
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
  onError,
}: {
  value: string;
  onChange: (v: string) => string;
  placeholder?: string;
  onError?: (message: string) => void;
}) {
  const [draft, setDraft] = useState(value);
  const error = useRef("");
  useEffect(() => { setDraft(value); }, [value]);
  return (
    <input
      type="text"
      value={draft}
      placeholder={placeholder}
      onChange={(e) => {
        setDraft(e.target.value);
        // Persist every complete valid address immediately, not on blur: an
        // Activity/process crash must not discard a finished edit.
        error.current = onChange(e.target.value);
      }}
      onBlur={() => { if (error.current) { onError?.(error.current); setDraft(value); error.current = ""; } }}
      onKeyDown={(e) => { if (e.key === "Enter") e.currentTarget.blur(); }}
      inputMode="decimal"
      autoComplete="off"
      className="w-full rounded-xl border border-outline-variant bg-surface px-3 py-2 text-[12.5px] font-medium text-on-surface outline-none focus:border-primary"
    />
  );
}
