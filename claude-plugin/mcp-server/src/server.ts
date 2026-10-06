import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { GameContextStore } from "./context/gameContextStore.js";
import { registerContextTools } from "./context/tools.js";
import { registerEventTools, type WaitClock } from "./events/tools.js";
import { type ExternalDataDependencies, registerExternalDataTools } from "./external/tools.js";
import type { GroundItemsStore } from "./groundItems/groundItemsStore.js";
import { registerGroundItemTools } from "./groundItems/tools.js";
import type { GuidanceRequests } from "./guidance/guidanceRequests.js";
import { registerGuidanceTools } from "./guidance/tools.js";
import { SERVER_INSTRUCTIONS } from "./instructions.js";
import type { AreaResolver } from "./location/areaResolver.js";
import type { SpecialWorldType } from "./location/pointsOfInterest.js";
import { registerLocationTools } from "./location/tools.js";
import type { NearbyNpcsStore } from "./npcs/nearbyNpcsStore.js";
import { registerNpcTools } from "./npcs/tools.js";
import type { NearbyObjectsStore } from "./objects/nearbyObjectsStore.js";
import { registerObjectTools } from "./objects/tools.js";
import { registerQuestGuideTools } from "./questGuide/tools.js";
import type { QuestProgressStore } from "./quests/questProgressStore.js";
import { registerQuestTools } from "./quests/tools.js";
import type { QuestState } from "./snapshot/playerSnapshot.js";
import type { LoadResult, SnapshotStore } from "./snapshot/snapshotStore.js";
import { questsByStatus, searchBank, summariseSnapshot } from "./snapshot/snapshotView.js";
import { contractMismatch, failure, json } from "./toolResults.js";
import { SERVER_VERSION } from "./version.js";
import type { WikiClient } from "./wiki/wikiClient.js";

/** Snapshots older than this mean the client is closed or the plugin is off; live state may have moved on. */
const STALE_AFTER_SECONDS = 120;

const QUEST_STATES: [QuestState, ...QuestState[]] = ["FINISHED", "IN_PROGRESS", "NOT_STARTED"];

export interface ServerDependencies {
  store: SnapshotStore;
  guidance: GuidanceRequests;
  objects: NearbyObjectsStore;
  npcs: NearbyNpcsStore;
  groundItems: GroundItemsStore;
  context: GameContextStore;
  questProgress: QuestProgressStore;
  wiki: WikiClient;
  resolver: AreaResolver;
  external: ExternalDataDependencies;
  /** Time for wait_for; the real clock when omitted. */
  waitClock?: WaitClock;
}

/**
 * Builds the MCP server with its tools and standing instructions, unconnected, so tests can attach an
 * in-memory transport and observe exactly what a client receives.
 */
export function createServer({
  store,
  guidance,
  objects,
  npcs,
  groundItems,
  context,
  questProgress,
  wiki,
  resolver,
  external,
  waitClock,
}: ServerDependencies): McpServer {
  const server = new McpServer({ name: "wise-old-claude", version: SERVER_VERSION }, { instructions: SERVER_INSTRUCTIONS });

  server.registerTool(
    "get_player_snapshot",
    {
      title: "Get player snapshot",
      description:
        "Call first for every OSRS question. Returns the player's live character from RuneLite: account type, " +
        "combat level, world, coins (inventory + bank), tile position with the named area and nearest wiki locations, " +
        "skills (boosted shown only when it differs), inventory, worn equipment, quest points and counts with " +
        "the names of quests in progress, the bank's size and capture time, Grand Exchange offers, bird house timers and " +
        "Home/minigame teleport cooldowns. " +
        "Bank contents: search_bank. Quest lists: get_quests.",
      inputSchema: { rsn: z.string().optional().describe("Character name. Omit for the most recently played character.") },
      annotations: { readOnlyHint: true },
    },
    ({ rsn }) =>
      withSnapshot(store.load(rsn), (found) =>
        json({
          ageSeconds: found.ageSeconds,
          stale: found.ageSeconds > STALE_AFTER_SECONDS,
          location: resolver.describe(found.snapshot.position),
          snapshot: summariseSnapshot(found.snapshot),
        }),
      ),
  );

  server.registerTool(
    "search_bank",
    {
      title: "Search the player's bank",
      description:
        "Finds items in the player's last-seen bank by partial name, case-insensitive. Pass every item you need " +
        "to check in one call. Returns quantities per term and the bank's capture time.",
      inputSchema: {
        items: z.array(z.string().min(1)).min(1).max(20).describe("Partial item names, e.g. [\"nettle\", \"gloves\"]"),
        rsn: z.string().optional(),
      },
      annotations: { readOnlyHint: true },
    },
    ({ items, rsn }) => withSnapshot(store.load(rsn), (found) => json(searchBank(found.snapshot, items))),
  );

  server.registerTool(
    "get_quests",
    {
      title: "List the player's quests by status",
      description: "Names of the player's quests in one state (FINISHED, IN_PROGRESS or NOT_STARTED), alphabetically.",
      inputSchema: { status: z.enum(QUEST_STATES), rsn: z.string().optional() },
      annotations: { readOnlyHint: true },
    },
    ({ status, rsn }) =>
      withSnapshot(store.load(rsn), (found) => json({ status, quests: questsByStatus(found.snapshot, status) })),
  );

  server.registerTool(
    "wiki_search",
    {
      title: "Search the OSRS Wiki",
      description: "Full-text search of the Old School RuneScape Wiki. Returns titles, snippets and URLs.",
      inputSchema: {
        query: z.string().min(1),
        limit: z.number().int().min(1).max(25).optional(),
      },
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    async ({ query, limit }) => {
      try {
        return json(await wiki.search(query, limit));
      } catch (error) {
        return failure(String(error));
      }
    },
  );

  server.registerTool(
    "wiki_page",
    {
      title: "Read an OSRS Wiki page",
      description:
        "Raw wikitext of an OSRS Wiki article (redirects followed). Infobox templates hold item stats, " +
        "requirements and locations. Long articles are truncated; prefer specific page titles.",
      inputSchema: { title: z.string().min(1) },
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    async ({ title }) => {
      try {
        return json(await wiki.page(title));
      } catch (error) {
        return failure(String(error));
      }
    },
  );

  registerLocationTools(server, {
    resolver,
    playerPosition: () => playerPositionFrom(store.load()),
    playerWorldType: () => specialWorldFrom(store.load()),
  });
  registerExternalDataTools(server, external);
  registerGuidanceTools(server, { requests: guidance, pluginRunning: () => isFresh(store.load()) });
  registerObjectTools(server, {
    objects,
    player: () => currentPlayerFrom(store.load()),
    liveRevision: () => gameRevisionFrom(store.load()),
  });
  registerNpcTools(server, { npcs, player: () => currentPlayerFrom(store.load()) });
  registerGroundItemTools(server, { groundItems, player: () => tradingPlayerFrom(store.load()) });
  registerContextTools(server, { context, player: () => currentPlayerFrom(store.load())?.rsn });
  registerQuestTools(server, { progress: questProgress, player: () => questPlayerFrom(store.load()) });
  registerQuestGuideTools(server, { wiki, progress: questProgress, player: () => guidePlayerFrom(store.load()) });
  registerEventTools(server, { store, context, clock: waitClock });
  return server;
}

type FoundSnapshot = Extract<LoadResult, { kind: "found" }>;

/** A recently written snapshot is the only sign the server has that RuneLite and the plugin are running. */
function isFresh(result: LoadResult): boolean {
  return result.kind === "found" && result.ageSeconds <= STALE_AFTER_SECONDS;
}

/** The player's tile, or undefined when no valid snapshot exists, letting location tools default their origin. */
function playerPositionFrom(result: LoadResult) {
  if (result.kind !== "found") {
    return undefined;
  }
  const { x, y, plane } = result.snapshot.position;
  return { x, y, plane };
}

/** The character and tile of a valid snapshot, which the object search measures from and picks its index by. */
/** The client's game build, letting static map data say when it was generated from an older build. */
function gameRevisionFrom(result: LoadResult): number | undefined {
  return result.kind === "found" ? (result.snapshot.gameRevision ?? undefined) : undefined;
}

/** The special world the player is on, so place searches can include that world's restricted chests. */
function specialWorldFrom(result: LoadResult): SpecialWorldType | undefined {
  const worldTypes = result.kind === "found" ? (result.snapshot.worldTypes ?? []) : [];
  return SPECIAL_WORLDS.find((special) => worldTypes.includes(special));
}

const SPECIAL_WORLDS: readonly SpecialWorldType[] = ["pvp", "bounty_hunter"];

/** The current player plus account type, which decides whether other players' drops may be offered. */
function tradingPlayerFrom(result: LoadResult) {
  const player = currentPlayerFrom(result);
  return player === undefined || result.kind !== "found" ? player : { ...player, accountType: result.snapshot.accountType };
}

/** The character and its quest states, so quest progress is read for the right account and every quest can be named. */
function questPlayerFrom(result: LoadResult) {
  return result.kind === "found" ? { rsn: result.snapshot.rsn, questStates: result.snapshot.quests } : undefined;
}

/** What the quest guide matches rows against: quest states, diaries, levels and the tile distances are measured from. */
function guidePlayerFrom(result: LoadResult) {
  if (result.kind !== "found") {
    return undefined;
  }
  const { rsn, accountType, quests, skills, progress, position } = result.snapshot;
  return { rsn, accountType, questStates: quests, skills, position, ...(progress?.diaries === undefined ? {} : { diaries: progress.diaries }) };
}

function currentPlayerFrom(result: LoadResult) {
  const position = playerPositionFrom(result);
  return result.kind === "found" && position !== undefined ? { rsn: result.snapshot.rsn, position } : undefined;
}

/**
 * Runs a tool body against a loaded snapshot, answering missing or contract-violating files with the same
 * explanatory errors for every snapshot-backed tool.
 */
function withSnapshot(result: LoadResult, render: (found: FoundSnapshot) => ReturnType<typeof json>) {
  switch (result.kind) {
    case "found":
      return render(result);
    case "missing":
      return failure(
        `No matching snapshot in ${result.directory}. Exported characters: ` +
          `${result.available.join(", ") || "none"}. Is RuneLite running with the Wise Old Claude plugin enabled?`,
      );
    case "invalid":
      return contractMismatch("Snapshot", result);
  }
}
