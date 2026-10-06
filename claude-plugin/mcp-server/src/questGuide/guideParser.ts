import { wikitextToPlain } from "./wikitextToPlain.js";

/** A wiki page a guide row is about, and whether the row finishes it or only starts it ("Partially complete:"). */
export interface GuideSubject {
  page: string;
  goal: "finish" | "start";
}

export interface GuideLocation {
  /** The location cell as a reader sees it. */
  label: string;
  /** Wiki pages the cell links to, in order. */
  pages: string[];
}

/** A level the guide says to reach before a row, from its `{{Optimal quest/train|skill|level}}` lines. */
export interface TrainingTarget {
  /** Lower-case skill name as the guide writes it. */
  skill: string;
  level: number;
  /** The guide's aside after the line, such as "boost to 47"; absent when it has none. */
  note?: string;
}

/** One row of an optimal quest guide table: a quest, a partial quest, a diary or another step. */
export interface GuideRow {
  /** 1-based position in the guide. */
  row: number;
  /** The quest cell as a reader sees it; multi-line cells are joined with "; ". */
  label: string;
  subjects: GuideSubject[];
  /** The guide's additional steps and information, as plain text; empty when the row has none. */
  notes: string;
  location?: GuideLocation;
  trainFirst: TrainingTarget[];
  /** One-off actions the guide lists right after this row, as plain text. */
  afterwards: string[];
}

/** Column order shared by the main and ironman guides: quest, quick guide, levels, quest points, notes, location. */
const QUEST_CELL = 0;
const NOTES_CELL = 4;
const LOCATION_CELL = 5;

/** Both guides tag their questing table, which tells it apart from the unlock tables earlier on the page. */
const GUIDE_TABLE_MARKER = /data-tableid="OQG|oqg-table/;

const TRAINING_LINE = /^\{\{optimal quest\/train\|([^|}]+)\|(\d+)\}\}(.*)$/i;
/** MediaWiki opens a cell on a "|" line even after leading spaces; "|-", "|}" and "|+" are row, end and caption. */
const CELL_START = /^\s*\|(?![-}+])/;
const ACTION_LINE = /^\{\{optimal quest\/action\|.*\}\}$/i;
const PAGE_LINK = /\[\[([^\]|#]+)[^\]]*\]\]/g;
const START_GOAL = /^\s*(?:partial|partially|start)\b/i;

/**
 * Splits an OSRS Wiki optimal quest guide ("Optimal quest guide", "Optimal quest guide/Ironman") into rows. The
 * format is the wiki's, not a contract, so parsing is positional and lenient: row ids are ignored (one is missing
 * its closing quote, and the main guide's first row id differs from its quest), and training lines between rows
 * are read as prerequisites of the row that follows them, which is how the guide places them.
 *
 * @param wikitext the full article wikitext
 * @returns the rows in guide order; empty when the page has no guide table
 */
export function parseQuestGuide(wikitext: string): GuideRow[] {
  const table = guideTable(wikitext);
  if (table === undefined) {
    return [];
  }
  const rows: GuideRow[] = [];
  let pendingTraining: TrainingTarget[] = [];
  for (const block of table.split(/^\|-/m).slice(1)) {
    const { cellLines, training, actions } = separateFollowUps(block.split("\n").slice(1));
    const cells = splitCells(cellLines);
    if (cells[QUEST_CELL] !== undefined) {
      rows.push(buildRow(rows.length + 1, cells, { trainFirst: pendingTraining, afterwards: actions }));
      pendingTraining = training;
    }
  }
  return rows;
}

function guideTable(wikitext: string): string | undefined {
  const marker = wikitext.search(GUIDE_TABLE_MARKER);
  if (marker === -1) {
    return undefined;
  }
  const start = wikitext.lastIndexOf("{|", marker);
  const end = wikitext.indexOf("\n|}", marker);
  return wikitext.slice(start === -1 ? marker : start, end === -1 ? undefined : end);
}

/** Pulls the training and follow-up action lines, which sit after the location cell, out of a row's cell text. */
function separateFollowUps(lines: string[]): { cellLines: string[]; training: TrainingTarget[]; actions: string[] } {
  const cellLines: string[] = [];
  const training: TrainingTarget[] = [];
  const actions: string[] = [];
  for (const line of lines) {
    const trimmed = line.trim();
    const target = TRAINING_LINE.exec(trimmed);
    if (target !== null) {
      training.push(trainingTarget(target[1] ?? "", target[2] ?? "", target[3] ?? ""));
    } else if (ACTION_LINE.test(trimmed)) {
      actions.push(wikitextToPlain(trimmed));
    } else {
      cellLines.push(line);
    }
  }
  return { cellLines, training, actions };
}

function trainingTarget(skill: string, level: string, aside: string): TrainingTarget {
  const note = wikitextToPlain(aside).replace(/^\((.*)\)$/, "$1");
  return { skill: skill.trim().toLowerCase(), level: Number(level), ...(note === "" ? {} : { note }) };
}

/**
 * Groups lines into cells: a line starting with "|" opens a cell unless it is inside a template, so checklist
 * bodies spanning many lines stay in their cell.
 */
function splitCells(lines: string[]): string[] {
  const cells: string[][] = [];
  let templateDepth = 0;
  for (const line of lines) {
    if (templateDepth === 0 && CELL_START.test(line)) {
      cells.push([stripCellAttributes(line.replace(CELL_START, ""))]);
    } else {
      cells.at(-1)?.push(line);
    }
    templateDepth = Math.max(0, templateDepth + occurrences(line, "{{") - occurrences(line, "}}"));
  }
  return cells.map((cell) => cell.join("\n"));
}

/** Drops a leading attribute list such as ` data-sort-value="Lost Tribe, The" |`. */
function stripCellAttributes(cell: string): string {
  return cell.replace(/^\s*(?:[\w-]+="[^"]*"\s*)+\|(?!\|)/, "");
}

function occurrences(text: string, token: string): number {
  return text.split(token).length - 1;
}

function buildRow(row: number, cells: string[], extras: Pick<GuideRow, "trainFirst" | "afterwards">): GuideRow {
  const questLines = (cells[QUEST_CELL] ?? "").split("\n").filter((line) => line.trim() !== "");
  const location = locationOf(cells[LOCATION_CELL] ?? "");
  return {
    row,
    label: questLines.map(wikitextToPlain).join("; "),
    subjects: questLines.flatMap(subjectsOf),
    notes: wikitextToPlain(cells[NOTES_CELL] ?? ""),
    ...(location === undefined ? {} : { location }),
    ...extras,
  };
}

function subjectsOf(line: string): GuideSubject[] {
  const goal = START_GOAL.test(line) ? "start" : "finish";
  return linkedPages(line).map((page) => ({ page, goal }));
}

function locationOf(cell: string): GuideLocation | undefined {
  const label = wikitextToPlain(cell);
  return label === "" ? undefined : { label, pages: linkedPages(cell) };
}

function linkedPages(wikitext: string): string[] {
  return [...wikitext.matchAll(PAGE_LINK)]
    .map((match) => (match[1] ?? "").trim())
    .filter((page) => page !== "" && !/^(?:file|image):/i.test(page));
}
