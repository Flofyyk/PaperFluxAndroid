import { createContext, useCallback, useContext, useRef, useState } from "react";
import type { ReactNode } from "react";
import { CheckCircle2 } from "lucide-react";

interface ToastCtx {
  show: (message: string) => void;
}

const Ctx = createContext<ToastCtx | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string | null>(null);
  const timer = useRef<number | null>(null);

  const show = useCallback((msg: string) => {
    setMessage(msg);
    if (timer.current) window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setMessage(null), 2200);
  }, []);

  return (
    <Ctx.Provider value={{ show }}>
      {children}
      <div
        className="pointer-events-none absolute inset-x-0 bottom-24 z-50 flex justify-center px-6"
        aria-live="polite"
      >
        {message && (
          <div className="pf-fade-up pointer-events-auto flex items-center gap-2 rounded-2xl bg-surface-container-highest px-4 py-3 text-on-surface shadow-lg shadow-black/10 ring-1 ring-outline-variant/40">
            <CheckCircle2 className="h-4 w-4 shrink-0 text-success" />
            <span className="text-[13px] font-medium">{message}</span>
          </div>
        )}
      </div>
    </Ctx.Provider>
  );
}

export function useToast() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useToast must be used within ToastProvider");
  return ctx;
}
