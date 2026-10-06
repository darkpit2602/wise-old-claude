import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { rankByDistance } from "./areaResolver.js";
import type { AreaResolver, TileCoordinates, TilePosition } from "./areaResolver.js";
import { POINTS_OF_INTEREST } from "./pointsOfInterest.data.js";
import { POINT_OF_INTEREST_TYPES, SPECIAL_WORLD_TYPES, findNearest } from "./pointsOfInterest.js";
import type { PointOfInterest, PointOfInterestType, SpecialWorldType } from "./pointsOfInterest.js";
import { failure, json } from "../toolResults.js";

/** Collaborators the location tools need; the resolver is injected so tests can supply a tiny gazetteer. */
export interface LocationToolDependencies {
  resolver: AreaResolver;
  /** Searchable places for find_nearest; defaults to the generated bundle so existing callers need no change. */
  pointsOfInterest?: readonly PointOfInterest[];
  /**
   * The player's current tile, if a snapshot is loaded; lets find_nearest answer "nearest bank" without
   * Claude first copying coordinates out of the snapshot. Omitted means no fallback origin.
   */
  playerPosition?: () => TilePosition | undefined;
  /**
   * The special world the player is on, if any, so find_nearest offers PvP chests on a PvP world without Claude
   * having to pass worldType itself. An explicit worldType argument still wins.
   */
  playerWorldType?: () => SpecialWorldType | undefined;
}

/** Bounds a ranking request so a runaway list of wiki coordinates cannot bloat Claude's context. */
const MAX_CANDIDATES = 500;

/** Default and ceiling for find_nearest; a handful of options answers the question without flooding context. */
const DEFAULT_NEAREST_LIMIT = 5;
const MAX_NEAREST_LIMIT = 25;

const tile = z.number().int().min(0);

/** Where a find_nearest search started, so Claude can tell whether it measured from the player or its own guess. */
type OriginSource = "argument" | "player";

interface NearestSearch {
  type: PointOfInterestType;
  origin: TilePosition;
  originSource: OriginSource;
  limit: number;
  worldType?: SpecialWorldType;
}

function resolveOrigin(
  origin: (TileCoordinates & { plane?: number }) | undefined,
  playerPosition: () => TilePosition | undefined,
): { origin: TilePosition; originSource: OriginSource } | undefined {
  if (origin !== undefined) {
    return { origin: { x: origin.x, y: origin.y, plane: origin.plane ?? 0 }, originSource: "argument" };
  }
  const player = playerPosition();
  return player === undefined ? undefined : { origin: { x: player.x, y: player.y, plane: player.plane }, originSource: "player" };
}

/**
 * Shapes find_nearest output: the type is stated once rather than per row, and each row gains the named
 * area it lies in (when RuneLite names that region) because a bare fairy ring code or coordinate means
 * little to a player.
 */
function nearestResult(points: readonly PointOfInterest[], resolver: AreaResolver, search: NearestSearch) {
  const results = findNearest(points, search).map(({ type: _type, ...point }) => {
    const area = resolver.describe(point).area;
    return area === undefined ? point : { ...point, area: area.name };
  });
  return { type: search.type, origin: search.origin, originSource: search.originSource, results };
}


/**
 * Registers the tools that let Claude reason about where things are: naming a tile, and ordering
 * candidate coordinates by how far the player would have to travel. Raw coordinates alone are
 * meaningless to Claude, which is why "nearby" answers were unreliable before these existed.
 */
export function registerLocationTools(
  server: McpServer,
  {
    resolver,
    pointsOfInterest = POINTS_OF_INTEREST,
    playerPosition = () => undefined,
    playerWorldType = () => undefined,
  }: LocationToolDependencies,
): void {
  server.registerTool(
    "describe_location",
    {
      title: "Describe a world tile",
      description:
        "Names a world tile: the area it lies in (city, dungeon, boss lair, region; 64x64-tile granularity) and " +
        "the nearest OSRS Wiki locations with their tile distance (diagonal steps count as one tile). In the " +
        "Wilderness it adds wildernessLevel (PvP combat range; most teleports fail above level 20, nearly all above 30).",
      inputSchema: { x: tile, y: tile, plane: z.number().int().min(0).max(3) },
      annotations: { readOnlyHint: true },
    },
    ({ x, y, plane }) => json(resolver.describe({ x, y, plane })),
  );

  server.registerTool(
    "rank_by_distance",
    {
      title: "Rank places by distance",
      description:
        "Orders candidate tiles (for example coordinates read from wiki {{Map}} or location data) nearest first " +
        "from an origin, usually the player's snapshot position. Distance is in game tiles, ignoring plane.",
      inputSchema: {
        origin: z.object({ x: tile, y: tile, plane: z.number().int().min(0).max(3).optional() }),
        candidates: z
          .array(z.object({ name: z.string().min(1), x: tile, y: tile, plane: z.number().int().min(0).max(3).optional() }))
          .min(1)
          .max(MAX_CANDIDATES),
      },
      annotations: { readOnlyHint: true },
    },
    ({ origin, candidates }) => json(rankByDistance(origin, candidates)),
  );

  server.registerTool(
    "find_nearest",
    {
      title: "Find the nearest place of a type",
      description:
        "Lists the nearest banks, deposit boxes, prayer altars, runecrafting altars, anvils, furnaces, fairy rings " +
        "(with dial code), spirit trees, agility courses or minigames, nearest first with tile distance and the " +
        "named area. Origin defaults to the player's snapshot position. Distance is straight-line game tiles " +
        "ignoring plane, walls and quest or level requirements; cooking ranges are not covered (use the wiki). " +
        "Places that exist only on PvP or Bounty Hunter worlds (PvP chests, Daimon's Crater chests) are left out " +
        "unless worldType names the world the player is on; such rows carry worlds.",
      inputSchema: {
        type: z.enum(POINT_OF_INTEREST_TYPES),
        origin: z.object({ x: tile, y: tile, plane: z.number().int().min(0).max(3).optional() }).optional(),
        limit: z.number().int().min(1).max(MAX_NEAREST_LIMIT).optional(),
        worldType: z.enum(SPECIAL_WORLD_TYPES).optional(),
      },
      annotations: { readOnlyHint: true },
    },
    ({ type, origin, limit = DEFAULT_NEAREST_LIMIT, worldType }) => {
      const resolved = resolveOrigin(origin, playerPosition);
      if (resolved === undefined) {
        return failure("No origin given and the player's position is unknown (no snapshot loaded); pass origin {x, y, plane}.");
      }
      return json(nearestResult(pointsOfInterest, resolver, { type, limit, worldType: worldType ?? playerWorldType(), ...resolved }));
    },
  );
}
