import { TtlCache } from "../cache.js";
import { REQUEST_HEADERS, type ClientClockOptions, type FetchFn } from "./http.js";

const API_BASE = "https://sync.runescape.wiki/runelite/player/";

/** Main-game profile; the API also serves league profiles, which are not the character Claude is helping with. */
const PROFILE = "STANDARD";

/** Error code the API returns (with HTTP 400) for players who never synced with the WikiSync plugin. */
const NO_USER_DATA = "NO_USER_DATA";

/** Placeholder entry the API emits in `quests` that is not a real quest. */
const PLACEHOLDER_QUEST = ".";

/** Quest states as defined by the API's `QuestCompletionState` enum. */
const QUEST_IN_PROGRESS = 1;
const QUEST_FINISHED = 2;

/** Diary tiers in progression order; the API returns them alphabetically. */
const DIARY_TIERS = ["Easy", "Medium", "Hard", "Elite"] as const;

/** Response of `GET /runelite/player/{rsn}/STANDARD` as recorded from the live service. */
export interface RawWikiSyncProfile {
  username: string;
  timestamp: string;
  quests: Record<string, number>;
  achievement_diaries: Record<string, Record<string, { complete: boolean; tasks: boolean[] }>>;
  levels: Record<string, number>;
  music_tracks: Record<string, boolean>;
  /** Completed combat achievement task ids; null when the player's CA varps were never synced. */
  combat_achievements: number[] | null;
  league_tasks: number[] | null;
  bingo_tasks: number[];
  /** Obtained collection log item ids; empty until the player opens the collection log with WikiSync on. */
  collection_log: number[];
  /** Total collection log slots known to the client at sync time. */
  collectionLogItemCount: number | null;
}

/** Task counts alongside the completion flag let Claude spot tiers that only need the reward claimed. */
export interface DiaryTierProgress {
  /** Whether the game marks the tier complete (reward claimable); can be false with every task done. */
  complete: boolean;
  tasksDone: number;
  tasksTotal: number;
}

/** Compact progress view; raw id arrays are reduced to counts so they do not flood Claude's context. */
export interface WikiSyncProgress {
  quests: { completed: number; inProgress: string[]; notStarted: string[] };
  achievementDiaries: Record<string, Record<string, DiaryTierProgress>>;
  combatAchievements: { tasksCompleted: number } | null;
  collectionLog: { itemsObtained: number; totalItems: number | null } | null;
  musicTracks: { unlocked: number; total: number };
}

/** `notSynced` is a value because most players never install WikiSync; it is an answer, not a fault. */
export type WikiSyncLookup = { kind: "synced"; rsn: string; progress: WikiSyncProgress } | { kind: "notSynced"; rsn: string };

/**
 * Client for the WikiSync API, which holds quest, diary, combat achievement and collection log state that the
 * RuneLite WikiSync plugin uploads. That state is not in the hiscores or our own snapshot.
 */
export class WikiSyncClient {
  private readonly cache: TtlCache<WikiSyncLookup>;

  constructor(
    private readonly fetchFn: FetchFn = fetch,
    options: ClientClockOptions & { cacheTtlMs?: number } = {},
  ) {
    this.cache = new TtlCache(options.cacheTtlMs ?? 60 * 1000, options.now);
  }

  /**
   * Fetches a player's synced progress. Unsynced players are an expected outcome, not an error.
   *
   * @throws Error when the API answers with a non-success status other than the no-data case.
   */
  progress(rsn: string): Promise<WikiSyncLookup> {
    const url = `${API_BASE}${encodeURIComponent(rsn)}/${PROFILE}`;
    return this.cache.getOrLoad(url, async () => {
      const response = await this.fetchFn(url, { headers: REQUEST_HEADERS });
      const body = (await response.json().catch(() => undefined)) as
        | (RawWikiSyncProfile & { code?: string; error?: string })
        | undefined;
      if (body?.code === NO_USER_DATA) {
        return { kind: "notSynced", rsn };
      }
      if (!response.ok || body === undefined) {
        throw new Error(`WikiSync request failed: HTTP ${response.status}${body?.error ? ` (${body.error})` : ""}`);
      }
      return { kind: "synced", rsn, progress: summariseWikiSync(body) };
    });
  }
}

/** Reduces the raw profile to counts and short name lists Claude can reason over. */
export function summariseWikiSync(raw: RawWikiSyncProfile): WikiSyncProgress {
  return {
    quests: summariseQuests(raw.quests),
    achievementDiaries: summariseDiaries(raw.achievement_diaries),
    combatAchievements: raw.combat_achievements === null ? null : { tasksCompleted: raw.combat_achievements.length },
    collectionLog: summariseCollectionLog(raw),
    musicTracks: {
      unlocked: Object.values(raw.music_tracks).filter((unlocked) => unlocked).length,
      total: Object.keys(raw.music_tracks).length,
    },
  };
}

function summariseQuests(quests: Record<string, number>): WikiSyncProgress["quests"] {
  const entries = Object.entries(quests).filter(([name]) => name !== PLACEHOLDER_QUEST);
  const named = (state: number) => entries.filter(([, value]) => value === state).map(([name]) => name);
  return {
    completed: named(QUEST_FINISHED).length,
    inProgress: named(QUEST_IN_PROGRESS),
    notStarted: entries.filter(([, value]) => value !== QUEST_FINISHED && value !== QUEST_IN_PROGRESS).map(([name]) => name),
  };
}

function summariseDiaries(diaries: RawWikiSyncProfile["achievement_diaries"]): WikiSyncProgress["achievementDiaries"] {
  const result: WikiSyncProgress["achievementDiaries"] = {};
  for (const [region, tiers] of Object.entries(diaries)) {
    const ordered: Record<string, DiaryTierProgress> = {};
    for (const tier of DIARY_TIERS) {
      const progress = tiers[tier];
      if (progress !== undefined) {
        ordered[tier] = {
          complete: progress.complete,
          tasksDone: progress.tasks.filter((done) => done).length,
          tasksTotal: progress.tasks.length,
        };
      }
    }
    result[region] = ordered;
  }
  return result;
}

/** Null means the collection log was never synced, which is distinct from a genuinely empty log. */
function summariseCollectionLog(raw: RawWikiSyncProfile): WikiSyncProgress["collectionLog"] {
  if (raw.collection_log.length === 0 && raw.collectionLogItemCount === null) {
    return null;
  }
  return { itemsObtained: raw.collection_log.length, totalItems: raw.collectionLogItemCount };
}
