/**
 * Minimal in-memory cache with per-entry expiry. Wiki content changes rarely within a session, and repeat
 * lookups are common while Claude reasons, so this spares the wiki redundant requests.
 */
export class TtlCache<V> {
  private readonly entries = new Map<string, { expiresAt: number; value: V }>();

  constructor(
    private readonly ttlMs: number,
    private readonly now: () => number = Date.now,
  ) {}

  /** Returns the cached value, or computes, stores and returns it on a miss or expiry. */
  async getOrLoad(key: string, load: () => Promise<V>): Promise<V> {
    const hit = this.entries.get(key);
    if (hit !== undefined && hit.expiresAt > this.now()) {
      return hit.value;
    }
    const value = await load();
    this.entries.set(key, { expiresAt: this.now() + this.ttlMs, value });
    return value;
  }
}
