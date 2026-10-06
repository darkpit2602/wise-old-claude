import { TtlCache } from "../cache.js";
import type { PlayerSnapshot } from "../snapshot/playerSnapshot.js";
import { REQUEST_HEADERS, type ClientClockOptions, type FetchFn } from "./http.js";

/** Reuses the snapshot contract's account types so hiscore routing cannot drift from what RuneLite reports. */
export type AccountType = PlayerSnapshot["accountType"];

/**
 * Individual hiscore tables that answer `index_lite.json`. Skiller tables are deliberately absent: they answer
 * 200 with placeholder data for players who are not skillers, which would mislead Claude.
 */
export type HiscoreTable =
  | "hiscore_oldschool"
  | "hiscore_oldschool_ironman"
  | "hiscore_oldschool_ultimate"
  | "hiscore_oldschool_hardcore_ironman";

const TABLE_BY_ACCOUNT_TYPE: Record<AccountType, HiscoreTable> = {
  NORMAL: "hiscore_oldschool",
  IRONMAN: "hiscore_oldschool_ironman",
  ULTIMATE_IRONMAN: "hiscore_oldschool_ultimate",
  HARDCORE_IRONMAN: "hiscore_oldschool_hardcore_ironman",
  GROUP_IRONMAN: "hiscore_oldschool",
  HARDCORE_GROUP_IRONMAN: "hiscore_oldschool",
  UNRANKED_GROUP_IRONMAN: "hiscore_oldschool",
  UNKNOWN: "hiscore_oldschool",
};

/**
 * Picks the individual table a character of this type is ranked on. Group ironmen have no individual
 * ironman ranking (they 404 there and appear only on the main table), and every account is on the main
 * table, so it is the safe fallback for unknown types.
 */
export function hiscoreTableFor(accountType: AccountType): HiscoreTable {
  return TABLE_BY_ACCOUNT_TYPE[accountType];
}

/** A skill entry; `rank` is omitted when unranked so Claude never sees the service's -1 sentinel. */
export interface HiscoreSkill {
  level: number;
  xp: number;
  rank?: number;
}

/** A boss, clue or minigame score; `rank` is omitted for real scores still below the ranking threshold. */
export interface HiscoreActivity {
  score: number;
  rank?: number;
}

/** Compact hiscore view: every skill, but only activities (bosses, clues, minigames) with a real score. */
export interface HiscorePlayer {
  skills: Record<string, HiscoreSkill>;
  activities: Record<string, HiscoreActivity>;
}

/** `notFound` is a value, not an exception, because a 404 is the normal answer when querying the wrong table. */
export type HiscoreLookup =
  | { kind: "found"; rsn: string; table: HiscoreTable; player: HiscorePlayer }
  | { kind: "notFound"; rsn: string; table: HiscoreTable };

/** Shape of `index_lite.json` as recorded from the live service. */
export interface RawHiscores {
  name: string;
  skills: { id: number; name: string; rank: number; level: number; xp: number }[];
  activities: { id: number; name: string; rank: number; score: number }[];
}

const UNRANKED = -1;

/**
 * Rating-style entries ("LMS - Rank", "PvP Arena - Rank") report a default rating such as 2500 even for players
 * who never played, so a positive score only means something there when the player is actually ranked.
 */
const RATING_ACTIVITY = / - Rank$/;

/** Read-only client for the official OSRS hiscores JSON endpoint. */
export class HiscoresClient {
  private readonly cache: TtlCache<HiscoreLookup>;

  constructor(
    private readonly fetchFn: FetchFn = fetch,
    options: ClientClockOptions & { cacheTtlMs?: number } = {},
  ) {
    this.cache = new TtlCache(options.cacheTtlMs ?? 60 * 1000, options.now);
  }

  /**
   * Looks a player up on one table. A 404 means "not ranked on this table" (wrong account type, de-ironed,
   * or too new/low to be listed), which is an answer rather than a failure.
   *
   * @throws Error when the hiscores answer with any other non-success status.
   */
  lookup(target: { rsn: string; table: HiscoreTable }): Promise<HiscoreLookup> {
    const url = new URL(`https://secure.runescape.com/m=${target.table}/index_lite.json`);
    url.searchParams.set("player", target.rsn);
    return this.cache.getOrLoad(url.toString(), async () => {
      const response = await this.fetchFn(url, { headers: REQUEST_HEADERS });
      if (response.status === 404) {
        return { kind: "notFound", rsn: target.rsn, table: target.table };
      }
      if (!response.ok) {
        throw new Error(`OSRS hiscores request failed: HTTP ${response.status}`);
      }
      const raw = (await response.json()) as RawHiscores;
      return { kind: "found", rsn: target.rsn, table: target.table, player: summariseHiscores(raw) };
    });
  }
}

/**
 * Drops unranked placeholders so Claude sees only what the player has done. The service encodes "nothing"
 * as either `score: -1` or `rank: -1, score: 0` depending on the table, so both are filtered; a below-threshold
 * real score (unranked but positive) is kept.
 */
export function summariseHiscores(raw: RawHiscores): HiscorePlayer {
  const skills: Record<string, HiscoreSkill> = {};
  for (const skill of raw.skills) {
    skills[skill.name] = withRank({ level: skill.level, xp: skill.xp }, skill.rank);
  }
  const activities: Record<string, HiscoreActivity> = {};
  for (const activity of raw.activities.filter(isMeaningfulActivity)) {
    activities[activity.name] = withRank({ score: activity.score }, activity.rank);
  }
  return { skills, activities };
}

function isMeaningfulActivity(activity: RawHiscores["activities"][number]): boolean {
  if (activity.score <= 0) {
    return false;
  }
  return !(activity.rank === UNRANKED && RATING_ACTIVITY.test(activity.name));
}

function withRank<T extends object>(entry: T, rank: number): T & { rank?: number } {
  return rank === UNRANKED ? entry : { ...entry, rank };
}
