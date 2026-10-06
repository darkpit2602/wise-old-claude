import { nameMatcher } from "../nameMatching.js";
import type { QuestState } from "../snapshot/playerSnapshot.js";
import { sameName } from "../snapshot/snapshotStore.js";
import { withoutUndefined } from "../toolResults.js";

/** One visual line of a quest journal. */
export interface JournalLine {
  text: string;
  /** Struck through in game: a finished step. */
  done: boolean;
}

/** A quest's journal as last seen in the client. */
export interface QuestJournal {
  /** ISO instant the text was read. */
  capturedAt: string;
  /** The quest's stage when the text was read; absent when the quest's stage is unknown. */
  stageAtCapture?: number;
  lines: JournalLine[];
}

/**
 * TypeScript view of `contract/quest-progress.schema.json`. The schema is authoritative and is enforced at load
 * time; this type only describes what a validated file looks like.
 */
export interface QuestProgress {
  schemaVersion: 1;
  rsn: string;
  capturedAt: string;
  /** Quest name to raw stage for in-progress quests; absent until the plugin read quests this session. */
  stages?: Record<string, number>;
  journals: Record<string, QuestJournal>;
}

/** A saved journal as Claude sees it: split into finished and remaining lines, with its age and freshness. */
export interface JournalView {
  ageSeconds: number;
  capturedAt: string;
  stageAtCapture?: number;
  /** The quest's stage moved since the journal was read, so the text may describe a step already done. */
  outdated: boolean;
  /** The first line not struck through: where the player is. Absent when every line is done. */
  currentStep?: string;
  completed: string[];
  remaining: string[];
}

export type QuestProgressView =
  | {
      kind: "found";
      quest: string;
      status?: QuestState;
      currentStage?: number;
      journal?: JournalView;
      note?: string;
    }
  | { kind: "ambiguous"; query: string; candidates: string[] }
  | { kind: "notFound"; query: string; note: string };

/**
 * Where the player is in one quest, from the saved journal, the live stage and the snapshot's quest list.
 * The journal is the game's own words, so it is the answer when present; the stage only says whether the
 * player has moved on since the journal was read, because stage values are undocumented.
 *
 * @param progress the character's quest-progress file, or undefined when the plugin has not written one
 * @param query quest name or the start of any of its words
 * @param context the current time for ages, and the snapshot's quest states so every quest can be named
 */
export function questProgressFor(
  progress: QuestProgress | undefined,
  query: string,
  { now, questStates }: { now: Date; questStates: Record<string, QuestState> },
): QuestProgressView {
  const names = new Set([...Object.keys(questStates), ...Object.keys(progress?.journals ?? {}), ...Object.keys(progress?.stages ?? {})]);
  const resolved = resolveQuest([...names], query);
  if (typeof resolved !== "string") {
    return resolved;
  }

  const status = questStates[resolved];
  const currentStage = progress?.stages?.[resolved];
  const saved = progress?.journals[resolved];
  const journal = saved === undefined ? undefined : journalView(saved, currentStage, now);
  return withoutUndefined({
    kind: "found",
    quest: resolved,
    status,
    currentStage,
    journal,
    note: noteFor(resolved, status, journal),
  });
}

/**
 * Picks the one quest a query names: an exact name wins, otherwise the query must match a single quest.
 *
 * @param names every quest name the player's data mentions
 * @param query quest name or the start of any of its words
 * @returns the quest's name, or the not-found or ambiguous answer to give instead
 */
function resolveQuest(names: string[], query: string): string | Exclude<QuestProgressView, { kind: "found" }> {
  const matches = names.filter(nameMatcher(query)).sort();
  const exact = matches.find((name) => sameName(name, query));
  const quest = exact ?? (matches.length === 1 ? matches[0] : undefined);
  if (quest !== undefined) {
    return quest;
  }
  return matches.length === 0
    ? { kind: "notFound", query, note: "No quest matches that name; check the spelling or use get_quests to list names." }
    : { kind: "ambiguous", query, candidates: matches };
}

function journalView(saved: QuestJournal, currentStage: number | undefined, now: Date): JournalView {
  const remaining = saved.lines.filter((line) => !line.done).map((line) => line.text);
  return {
    ageSeconds: Math.max(0, Math.round((now.getTime() - Date.parse(saved.capturedAt)) / 1000)),
    capturedAt: saved.capturedAt,
    ...(saved.stageAtCapture === undefined ? {} : { stageAtCapture: saved.stageAtCapture }),
    outdated: saved.stageAtCapture !== undefined && currentStage !== undefined && saved.stageAtCapture !== currentStage,
    ...(remaining.length === 0 ? {} : { currentStep: remaining[0] }),
    completed: saved.lines.filter((line) => line.done).map((line) => line.text),
    remaining,
  };
}

/** What Claude should tell or ask the player when the saved journal alone cannot answer. */
function noteFor(quest: string, status: QuestState | undefined, journal: JournalView | undefined): string | undefined {
  if (status === "FINISHED") {
    return "The player has finished this quest.";
  }
  if (journal === undefined) {
    return `No journal saved for ${quest}. Ask the player to open its quest journal once (Quest List, click the quest), then answer from this tool.`;
  }
  if (journal.outdated) {
    return `The quest has progressed since this journal was saved. Ask the player to reopen ${quest}'s quest journal to refresh it.`;
  }
  return undefined;
}
