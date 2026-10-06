import { type NamedPlace, type TileCoordinates, tileDistance } from "../location/areaResolver.js";
import type { DiaryTier, QuestState } from "../snapshot/playerSnapshot.js";
import type { GuideRow, GuideSubject, TrainingTarget } from "./guideParser.js";

/** What the guide view needs from the player's snapshot. */
export interface GuidePlayer {
  questStates: Record<string, QuestState>;
  /** Region to completed diary tiers; undefined when the plugin did not export diaries, which makes diary rows unknown. */
  diaries?: Record<string, DiaryTier[]>;
  /** Snapshot skills, keyed by upper-case skill name. */
  skills: Record<string, { level: number }>;
  position?: TileCoordinates;
}

/** "unknown" means the snapshot cannot tell, such as an unlock or a museum quiz; it is never a guess. */
export type RowStatus = "done" | "inProgress" | "todo" | "unknown";

export interface RowResolution {
  status: RowStatus;
  /** The game's name for the quest the row tracks, when the row is quest-backed. */
  quest?: string;
}

/** A quest's journal position, from the saved quest journal. */
export interface JournalStep {
  currentStep?: string;
  outdated?: boolean;
}

export interface GuideViewOptions {
  limit: number;
  places: readonly NamedPlace[];
  journal: (quest: string) => JournalStep | undefined;
}

export interface NextRow {
  row: number;
  name: string;
  status: Exclude<RowStatus, "done">;
  /** The row sits before the furthest row already done: the player skipped it. */
  behind?: true;
  /** The game's quest name, given only when it differs from the guide's label. */
  quest?: string;
  location?: { name: string; distance?: number };
  /** Guide levels the player has not reached yet. */
  trainFirst?: (TrainingTarget & { current?: number })[];
  notes?: string;
  afterwards?: string[];
}

export interface InProgressQuest {
  quest: string;
  /** The first guide row tracking the quest; absent when the guide does not list it. */
  row?: number;
  currentStep?: string;
  journalOutdated?: true;
}

export interface LocationGroup {
  location: string;
  distance?: number;
  steps: string[];
}

export interface QuestGuideView {
  rows: { total: number; done: number; unknownSkipped: number };
  /** The last quest row done; diaries are left out because players often do a late diary tier early. */
  furthestDone?: { row: number; name: string };
  next: NextRow[];
  inProgress: InProgressQuest[];
  byLocation: LocationGroup[];
}

/** Long checklists would crowd out the rest of the answer; the guide's URL is in the tool result for the full text. */
const NOTES_LIMIT = 600;

/**
 * The guides link Recipe for Disaster steps by their wiki subpage, which the game names differently; "Goblin
 * generals" in particular matches no rule, so the mapping is spelled out.
 */
const RECIPE_FOR_DISASTER_STEPS: Readonly<Record<string, string>> = {
  "another cook's quest": "Another Cook's Quest",
  "freeing the goblin generals": "Wartface & Bentnoze",
  "freeing the mountain dwarf": "Mountain Dwarf",
  "freeing evil dave": "Evil Dave",
  "freeing pirate pete": "Pirate Pete",
  "freeing the lumbridge guide": "Lumbridge Guide",
  "freeing skrach uglogwee": "Skrach Uglogwee",
  "freeing sir amik varze": "Sir Amik Varze",
  "freeing king awowogei": "King Awowogei",
  "defeating the culinaromancer": "Culinaromancer",
};
const RECIPE_FOR_DISASTER = "Recipe for Disaster/";

const DIARY_ROW = /^(easy|medium|hard|elite) (.+) diary$/i;
const ALL_DIARIES_ROW = /^all (easy|medium|hard|elite) achievement diaries$/i;

/**
 * Decides whether the player has done one guide row, from the snapshot alone: quest states for quest rows (a
 * "Partially complete" row only needs the quest started), completed diary tiers for diary rows, and "unknown" for
 * everything the game does not report.
 *
 * @param row a parsed guide row
 * @param player the player's quest states, diaries and skills
 */
export function resolveRow(row: GuideRow, player: GuidePlayer): RowResolution {
  return resolveWith(row, player, questNameIndex(player.questStates));
}

/**
 * Builds the multiquesting view: the next rows to do in guide order, the journal step of every quest in progress,
 * and the upcoming rows grouped by location so one trip can cover several of them. Unknown rows before the
 * first row not done are left out and counted, because the player has moved past everything up to there; later
 * ones stay, since a gap in the player's progress means they may still be undone.
 *
 * @param rows the parsed guide
 * @param player the player's state from the snapshot
 * @param options how many rows to return, the gazetteer for distances, and the saved journal lookup
 */
export function questGuideView(rows: readonly GuideRow[], player: GuidePlayer, options: GuideViewOptions): QuestGuideView {
  const names = questNameIndex(player.questStates);
  const resolved = rows.map((row) => ({ row, ...resolveWith(row, player, names) }));
  const furthest = resolved.filter((entry) => entry.status === "done" && entry.quest !== undefined).at(-1);
  const furthestRow = furthest?.row.row ?? 0;
  const pending = resolved.filter((entry) => entry.status !== "done");
  const firstOpenRow = pending.find((entry) => entry.status !== "unknown")?.row.row ?? furthestRow;
  const shown = pending.filter((entry) => entry.status !== "unknown" || entry.row.row > firstOpenRow);
  const places = placeIndex(options.places);
  const next = shown.slice(0, options.limit).map((entry) => nextRow(entry, { player, places, furthestRow }));
  return {
    rows: { total: rows.length, done: resolved.length - pending.length, unknownSkipped: pending.length - shown.length },
    ...(furthest === undefined ? {} : { furthestDone: { row: furthest.row.row, name: furthest.row.label } }),
    next,
    inProgress: inProgressQuests(player, resolved, options.journal),
    byLocation: groupByLocation(next),
  };
}

function resolveWith(row: GuideRow, player: GuidePlayer, names: Map<string, string>): RowResolution {
  const diary = diaryStatus(row.label, player.diaries);
  if (diary !== undefined) {
    return { status: diary };
  }
  for (const subject of row.subjects) {
    const quest = names.get(gameQuestName(subject.page).toLowerCase());
    const state = quest === undefined ? undefined : player.questStates[quest];
    if (quest !== undefined && state !== undefined) {
      return { status: questStatus(subject, state), quest };
    }
  }
  return { status: "unknown" };
}

function questNameIndex(questStates: Record<string, QuestState>): Map<string, string> {
  return new Map(Object.keys(questStates).map((name) => [name.toLowerCase(), name]));
}

function gameQuestName(page: string): string {
  if (!page.startsWith(RECIPE_FOR_DISASTER)) {
    return page;
  }
  const step = RECIPE_FOR_DISASTER_STEPS[page.slice(RECIPE_FOR_DISASTER.length).toLowerCase()];
  return step === undefined ? page : `Recipe for Disaster - ${step}`;
}

function questStatus(subject: GuideSubject, state: QuestState): Exclude<RowStatus, "unknown"> {
  if (subject.goal === "start") {
    return state === "NOT_STARTED" ? "todo" : "done";
  }
  switch (state) {
    case "FINISHED":
      return "done";
    case "IN_PROGRESS":
      return "inProgress";
    case "NOT_STARTED":
      return "todo";
  }
}

/** A diary row: one region's tier, or the tier in every region ("All Easy Achievement Diaries"). */
interface DiaryGoal {
  tier: DiaryTier;
  region?: string;
}

function diaryGoalOf(label: string): DiaryGoal | undefined {
  const single = DIARY_ROW.exec(label);
  if (single !== null) {
    return { tier: (single[1] ?? "").toUpperCase() as DiaryTier, region: single[2] ?? "" };
  }
  const all = ALL_DIARIES_ROW.exec(label);
  return all === null ? undefined : { tier: (all[1] ?? "").toUpperCase() as DiaryTier };
}

/** Undefined when the row is not a diary row; "unknown" when it is one but the snapshot has no diary data. */
function diaryStatus(label: string, diaries: Record<string, DiaryTier[]> | undefined): RowStatus | undefined {
  const goal = diaryGoalOf(label);
  if (goal === undefined) {
    return undefined;
  }
  if (diaries === undefined) {
    return "unknown";
  }
  const regions = goal.region === undefined ? Object.keys(diaries) : [goal.region];
  return regions.every((region) => diaryTiers(diaries, region).includes(goal.tier)) ? "done" : "todo";
}

function diaryTiers(diaries: Record<string, DiaryTier[]>, region: string): DiaryTier[] {
  const key = Object.keys(diaries).find((name) => name.toLowerCase() === region.toLowerCase());
  return key === undefined ? [] : (diaries[key] ?? []);
}

interface ResolvedRow extends RowResolution {
  row: GuideRow;
}

interface NextRowContext {
  player: GuidePlayer;
  places: Map<string, NamedPlace>;
  furthestRow: number;
}

function nextRow({ row, status, quest }: ResolvedRow, { player, places, furthestRow }: NextRowContext): NextRow {
  const trainFirst = missingLevels(row.trainFirst, player.skills);
  const location = row.location === undefined ? undefined : locationOf(row.location.label, row.location.pages, { places, from: player.position });
  return {
    row: row.row,
    name: row.label,
    status: status as NextRow["status"],
    ...(row.row < furthestRow ? { behind: true as const } : {}),
    ...(quest === undefined || quest === row.label ? {} : { quest }),
    ...(location === undefined ? {} : { location }),
    ...(trainFirst.length === 0 ? {} : { trainFirst }),
    ...(row.notes === "" ? {} : { notes: shorten(row.notes) }),
    ...(row.afterwards.length === 0 ? {} : { afterwards: row.afterwards }),
  };
}

function missingLevels(targets: TrainingTarget[], skills: GuidePlayer["skills"]): (TrainingTarget & { current?: number })[] {
  return targets.flatMap((target) => {
    const current = skills[target.skill.toUpperCase()]?.level;
    if (current !== undefined && current >= target.level) {
      return [];
    }
    return [{ skill: target.skill, level: target.level, ...(current === undefined ? {} : { current }), ...(target.note === undefined ? {} : { note: target.note }) }];
  });
}

function placeIndex(places: readonly NamedPlace[]): Map<string, NamedPlace> {
  const index = new Map<string, NamedPlace>();
  for (const place of places) {
    const key = place.name.toLowerCase();
    if (!index.has(key)) {
      index.set(key, place);
    }
  }
  return index;
}

function locationOf(
  name: string,
  pages: string[],
  { places, from }: { places: Map<string, NamedPlace>; from: TileCoordinates | undefined },
): NextRow["location"] {
  const place = pages.map((page) => places.get(page.toLowerCase())).find((candidate) => candidate !== undefined);
  return place === undefined || from === undefined ? { name } : { name, distance: tileDistance(from, place) };
}

function shorten(notes: string): string {
  return notes.length <= NOTES_LIMIT ? notes : `${notes.slice(0, NOTES_LIMIT).trimEnd()}…`;
}

function inProgressQuests(player: GuidePlayer, resolved: readonly ResolvedRow[], journal: GuideViewOptions["journal"]): InProgressQuest[] {
  return Object.entries(player.questStates)
    .filter(([, state]) => state === "IN_PROGRESS")
    .map(([quest]) => quest)
    .sort((a, b) => a.localeCompare(b))
    .map((quest) => {
      const row = resolved.find((entry) => entry.quest === quest)?.row.row;
      const step = journal(quest);
      return {
        quest,
        ...(row === undefined ? {} : { row }),
        ...(step?.currentStep === undefined ? {} : { currentStep: step.currentStep }),
        ...(step?.outdated === true ? { journalOutdated: true as const } : {}),
      };
    });
}

/** Nearest trip first; locations without a known tile keep guide order after the measured ones. */
function groupByLocation(next: readonly NextRow[]): LocationGroup[] {
  const groups = new Map<string, LocationGroup>();
  for (const row of next) {
    if (row.location !== undefined) {
      const group = groups.get(row.location.name) ?? { location: row.location.name, ...(row.location.distance === undefined ? {} : { distance: row.location.distance }), steps: [] };
      group.steps.push(row.name);
      groups.set(row.location.name, group);
    }
  }
  const measured = [...groups.values()].filter((group) => group.distance !== undefined);
  const unmeasured = [...groups.values()].filter((group) => group.distance === undefined);
  return [...measured.sort((a, b) => (a.distance ?? 0) - (b.distance ?? 0)), ...unmeasured];
}
