export class VpnCommandGuard {
  private until = 0;
  constructor(private read: () => string | null, private write: (value: string) => void) {}
  remaining(now: number): number {
    let saved = this.until;
    try { saved = Math.max(saved, Number(this.read()) || 0); } catch {}
    return saved > now && saved <= now + 5000 ? saved - now : 0;
  }
  claim(now: number): boolean {
    if (this.remaining(now) > 0) return false;
    this.until = now + 5000;
    try { this.write(String(this.until)); } catch {}
    return true;
  }
}
