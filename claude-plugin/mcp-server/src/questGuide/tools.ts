import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { NamedPlace } from "../location/areaResolver.js";
import { NAMED_PLACES } from "../location/areas.data.js";
import { type QuestProgress, questProgressFor } from "../quests/questProgress.js";
import type { QuestProgressStore } from "../quests/questProgressStore.js";
import type { PlayerSnapshot } from "../snapshot/playerSnapshot.js";
import { failure, json } from "../toolResults.js";
import type { WikiClient } from "../wiki/wikiClient.js";
import { parseQuestGuide } from "./guideParser.js";
import { type GuidePlayer, type JournalStep, questGuideView } from "./guideProgress.js";

/** The player fields the guide tool reads, so tests need not build a whole snapshot. */
export interface GuideToolPlayer extends GuidePlayer {
  rsn: string;
  accountType: PlayerSnapshot["accountType"];
}

/** Collaborators the guide tool needs; injected so tests can serve a saved guide and a temp journal folder. */
export interface QuestGuideToolDependencies {
  wiki: Pick<WikiClient, "fullPage">;
  player: () => GuideToolPlayer | undefined;
  progress: QuestProgressStore;
  /** Gazetteer for location distances; defaults to the bundled wiki locations. */
  places?: readonly NamedPlace[];
}

const GUIDE_TITLES = { main: "Optimal quest guide", ironman: "Optimal quest guide/Ironman" } as const;
type GuideName = keyof typeof GUIDE_TITLES;

/** A handful of rows is a trip's worth of planning; the guide's notes make each row a few hundred characters. */
const DEFAULT_LIMIT = 6;
const MAX_LIMIT = 15;

const NO_JOURNAL_HINT =
  "Quests in progress without currentStep have no saved journal: ask the player to open them once in the Quest List.";

/**
 * Registers the multiquesting tool: where the player stands in the wiki's optimal quest guide, what comes next,
 * and which upcoming steps share a trip. The guide is ~125,000 characters, far past what wiki_page returns, so it
 * is parsed here and only the player's next rows reach Claude.
 */
export function registerQuestGuideTools(server: McpServer, { wiki, player, progress, places = NAMED_PLACES }: QuestGuideToolDependencies): void {
  server.registerTool(
    "get_quest_guide",
    {
      title: "Get the next steps of the optimal quest guide",
      description:
        "The player's position in the OSRS Wiki optimal quest guide (the Ironman version for ironmen) and what to " +
        "do next: the next rows not done in guide order with the guide's notes, location and distance from the " +
        "player, and levels to train first (trainFirst, with the current level); every quest in progress with its " +
        "journal currentStep; and byLocation, the upcoming rows grouped nearest first so one trip covers several. " +
        "behind: true marks a row before the furthest quest done (skipped). status unknown means the game does not " +
        "report that step (unlocks, museum quiz, balloons): ask the player; unknown rows before the first row not " +
        "done are counted in rows.unknownSkipped, not listed. Needs the Wise Old Claude plugin.",
      inputSchema: {
        limit: z.number().int().min(1).max(MAX_LIMIT).optional().describe(`Rows to return, default ${DEFAULT_LIMIT}.`),
        guide: z.enum(["main", "ironman"]).optional().describe("Override the guide picked from the account type."),
      },
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    async ({ limit = DEFAULT_LIMIT, guide }) => {
      const current = player();
      if (current === undefined) {
        return failure("No character snapshot found. Is RuneLite running with the Wise Old Claude plugin enabled?");
      }
      const title = GUIDE_TITLES[guide ?? guideFor(current.accountType)];
      try {
        const page = await wiki.fullPage(title);
        const rows = parseQuestGuide(page.wikitext);
        if (rows.length === 0) {
          return failure(`${page.url} no longer has the guide table this tool reads; read it with wiki_page instead.`);
        }
        const journal = journalLookup(savedProgress(progress, current.rsn), current, progress.currentTime());
        const view = questGuideView(rows, current, { limit, places, journal });
        const missingJournal = view.inProgress.some((quest) => quest.currentStep === undefined);
        return json({ rsn: current.rsn, guide: { title: page.title, url: page.url }, ...view, ...(missingJournal ? { hint: NO_JOURNAL_HINT } : {}) });
      } catch (error) {
        return failure(String(error));
      }
    },
  );
}

function guideFor(accountType: GuideToolPlayer["accountType"]): GuideName {
  return accountType.includes("IRONMAN") ? "ironman" : "main";
}

/** A broken quest-progress file costs only the journal steps; the guide answer stands without them. */
function savedProgress(progress: QuestProgressStore, rsn: string): QuestProgress | undefined {
  const loaded = progress.load(rsn);
  return loaded.kind === "found" ? loaded.progress : undefined;
}

function journalLookup(saved: QuestProgress | undefined, current: GuideToolPlayer, now: Date): (quest: string) => JournalStep | undefined {
  return (quest) => {
    const view = questProgressFor(saved, quest, { now, questStates: current.questStates });
    if (view.kind !== "found" || view.journal === undefined) {
      return undefined;
    }
    return {
      ...(view.journal.currentStep === undefined ? {} : { currentStep: view.journal.currentStep }),
      outdated: view.journal.outdated,
    };
  };
}
