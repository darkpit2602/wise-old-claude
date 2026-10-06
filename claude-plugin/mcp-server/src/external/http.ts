export type { FetchFn } from "../wiki/wikiClient.js";

/**
 * Every upstream here (OSRS Wiki prices, WikiSync, Jagex hiscores) asks for a descriptive User-Agent; the
 * prices API documents pre-emptive blocks on generic library agents.
 */
export const USER_AGENT = "wise-old-claude/0.1.0 (local Claude Code OSRS assistant)";

/** Headers sent with every external request, so the identification rule lives in one place. */
export const REQUEST_HEADERS = { "User-Agent": USER_AGENT };

/** Options shared by the external clients; `now` lets tests drive cache expiry deterministically. */
export interface ClientClockOptions {
  now?: () => number;
}
