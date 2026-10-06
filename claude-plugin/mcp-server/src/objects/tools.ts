import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { TilePosition } from "../location/areaResolver.js";
import {
  type ObjectLocationSource,
  type ObjectLocationTable,
  findStaticObjects,
  stalenessNote,
} from "../location/objectLocations.js";
import { OBJECT_LOCATIONS, OBJECT_LOCATIONS_SOURCE } from "../location/objectLocations.data.js";
import { contractMismatch, failure, json } from "../toolResults.js";
import { type NearbyObjects, findNearbyObjects } from "./nearbyObjects.js";
import type { IndexLoadResult, NearbyObjectsStore } from "./nearbyObjectsStore.js";

/** The static world table and the cache build it came from. */
export interface WorldObjectTable {
  table: ObjectLocationTable;
  source: ObjectLocationSource;
}

/** Collaborators the object tools need; injected so tests can point them at a temp directory. */
export interface ObjectToolDependencies {
  objects: NearbyObjectsStore;
  /**
   * The current character and tile from the latest snapshot, if any. The snapshot is rewritten as the player
   * moves while the index is rewritten only when the scene changes, so it is the better origin.
   */
  player: () => { rsn: string; position: TilePosition } | undefined;
  /**
   * The game revision the client reports, so answers from the static table can say when the game has moved
   * past the cache build the table came from. Omitted or undefined means unknown, and no note is added.
   */
  liveRevision?: () => number | undefined;
  /** Defaults to the bundled table generated from the game cache; tests inject small ones. */
  world?: WorldObjectTable;
}

const DEFAULT_LIMIT = 5;
const MAX_LIMIT = 25;

const COVERAGE =
  "Only objects in the scene RuneLite has loaded are listed: about 50 tiles each way from indexOrigin. " +
  "Farther objects exist but are not in the index.";

const NO_INDEX =
  "No nearby-object index found. It is written by the Wise Old Claude plugin while logged in; update the plugin " +
  "or log in, and meanwhile use the wiki or find_nearest.";

const NO_ORIGIN =
  "No player position known: neither a character snapshot nor a nearby-object index exists. Log in with the " +
  "Wise Old Claude plugin enabled, or use the wiki or find_nearest.";

const STATIC_NOTE = "Static map data from the game cache; objects may be depleted, moved or changed in the game.";
const NO_LIVE_MATCH = "Nothing matching in the scene RuneLite has loaded (about 50 tiles), so the world table was searched.";
const NO_INDEX_FALLBACK = "No nearby-object index from the plugin, so the world table was searched.";

type Scope = "nearby" | "world";
type Current = ReturnType<ObjectToolDependencies["player"]>;

interface Search {
  name: string;
  limit: number;
}

/** Where distances are measured from, and whether that tile came from the snapshot or the index. */
interface Origin {
  position: TilePosition;
  originSource: "player" | "index";
}

/**
 * Registers the tool that finds individual game objects (a specific oak, a range, a door) around the player,
 * which no wiki-backed tool can: the wiki lists where a kind of object grows, not which one is closest. The
 * live scene index answers first because it shows the game as it is (a chopped tree is a stump); the static
 * world table, generated from the game cache, answers beyond the scene.
 */
export function registerObjectTools(server: McpServer, dependencies: ObjectToolDependencies): void {
  server.registerTool(
    "find_nearby_objects",
    {
      title: "Find nearby game objects",
      description:
        "Finds trees, rocks, ranges, doors, ladders, bank booths and other interactable objects by name (matched at word starts) " +
        "(case-insensitive), nearest first with tile distance. Searches the area RuneLite has loaded around the player " +
        "(about 50 tiles each way) live: a chopped tree shows as a stump until it regrows. When nothing there matches, " +
        'or with scope "world", it searches a world-wide table of skilling objects and utilities (trees, rocks, ranges, ' +
        "altars, banks, deposit boxes, furnaces, anvils, spinning wheels, looms, pottery, water sources) from the game " +
        'cache, marked source "static". Results are tiles ready for guide_to.',
      inputSchema: {
        name: z.string().trim().min(1).max(100).describe('Partial object name, e.g. "oak", "range", "bank booth".'),
        limit: z.number().int().min(1).max(MAX_LIMIT).optional(),
        scope: z
          .enum(["nearby", "world"])
          .optional()
          .describe('"nearby" (default): live scene first, world table if nothing matches. "world": world table only.'),
      },
      annotations: { readOnlyHint: true },
    },
    ({ name, limit = DEFAULT_LIMIT, scope = "nearby" }) => answer(dependencies, { name, limit }, scope),
  );
}

function answer(dependencies: ObjectToolDependencies, search: Search, scope: Scope) {
  const current = dependencies.player();
  const loaded = dependencies.objects.load(current?.rsn);
  if (loaded.kind === "invalid") {
    return contractMismatch("Object index", loaded);
  }
  const index = loaded.kind === "found" ? loaded.index : undefined;
  const origin = originOf(current, index);
  if (origin === undefined) {
    return failure(scope === "world" ? NO_ORIGIN : NO_INDEX);
  }
  if (scope === "world") {
    return json(staticAnswer(dependencies, { search, origin }));
  }
  if (loaded.kind === "missing") {
    return json(staticAnswer(dependencies, { search, origin, reason: NO_INDEX_FALLBACK }));
  }
  const live = liveAnswer(loaded, { search, origin });
  if (live.totalMatches > 0 || loaded.index.instance) {
    return json(live);
  }
  return json(staticAnswer(dependencies, { search, origin, reason: NO_LIVE_MATCH }));
}

function originOf(current: Current, index: NearbyObjects | undefined): Origin | undefined {
  if (current !== undefined) {
    return { position: tileOf(current.position), originSource: "player" };
  }
  return index === undefined ? undefined : { position: tileOf(index.origin), originSource: "index" };
}

function tileOf({ x, y, plane }: TilePosition): TilePosition {
  return { x, y, plane };
}

/**
 * The live answer, unchanged in shape from before the world table existed apart from `source`. Inside an
 * instance the world table is never consulted: tiles there are instance coordinates.
 */
function liveAnswer(loaded: Extract<IndexLoadResult, { kind: "found" }>, { search, origin }: { search: Search; origin: Origin }) {
  const { index, ageSeconds } = loaded;
  return {
    query: search.name,
    source: "live" as const,
    origin: origin.position,
    originSource: origin.originSource,
    indexOrigin: tileOf(index.origin),
    indexAgeSeconds: ageSeconds,
    ...(index.instance ? { instance: true } : {}),
    ...(index.truncated ? { truncated: true } : {}),
    coverage: COVERAGE,
    ...findNearbyObjects(index, { name: search.name, origin: origin.position, limit: search.limit }),
  };
}

function staticAnswer(
  { world = { table: OBJECT_LOCATIONS, source: OBJECT_LOCATIONS_SOURCE }, liveRevision }: ObjectToolDependencies,
  { search, origin, reason }: { search: Search; origin: Origin; reason?: string },
) {
  const staleness = stalenessNote(world.source, liveRevision?.());
  return {
    query: search.name,
    source: "static" as const,
    note: STATIC_NOTE,
    ...(reason === undefined ? {} : { reason }),
    ...(staleness === undefined ? {} : { staleness }),
    dataBuild: world.source.build,
    origin: origin.position,
    originSource: origin.originSource,
    ...findStaticObjects(world.table, { name: search.name, origin: origin.position, limit: search.limit }),
  };
}
