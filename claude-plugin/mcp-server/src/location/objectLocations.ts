import { nameMatcher } from "../nameMatching.js";
import { type Ranked, type TileCoordinates, type TilePosition, rankByDistance } from "./areaResolver.js";

/** Which game cache a static object table was generated from; `build` is comparable with the client's revision. */
export interface ObjectLocationSource {
  cacheId: number;
  build: number;
  cacheTimestamp: string;
  mapsRevision: number;
}

/**
 * Object name to a flat list of x, y, plane triples. Flat rather than nested tuples because the generated
 * table holds tens of thousands of tiles, and nested literals make the module larger and slower to typecheck.
 */
export type ObjectLocationTable = Readonly<Record<string, readonly number[]>>;

/** A search of an object table: which names to match, where "nearest" is measured from, and how many to return. */
export interface ObjectSearch {
  name: string;
  origin: TilePosition;
  limit: number;
}

/** One matching object, shaped so Claude can pass it straight to guide_to. */
export interface ObjectRow extends TilePosition {
  name: string;
}

/** The nearest matches, plus how many matched in total so Claude knows when a limit hid some. */
export interface ObjectMatches {
  totalMatches: number;
  results: Ranked<ObjectRow>[];
  /** Present when distances were measured from the surface above an underground origin. */
  measuredFrom?: TilePosition;
}

const TILE_FIELDS = 3;

/**
 * Searches the static world table nearest-first. Distance ignores plane like rank_by_distance does, because a
 * staircase away is still near and dungeons sit thousands of tiles away on the y axis anyway.
 */
export function findStaticObjects(table: ObjectLocationTable, { name, origin, limit }: ObjectSearch): ObjectMatches {
  const matches = nameMatcher(name);
  const candidates = Object.entries(table)
    .filter(([objectName]) => matches(objectName))
    .flatMap(([objectName, tiles]) => tilesOf(objectName, tiles));
  const from = surfaceEquivalent(origin);
  const reachable = isSurface(from) ? preferSurface(candidates) : candidates;
  return {
    totalMatches: candidates.length,
    results: rankByDistance(from, reachable).slice(0, limit),
    ...(from === origin ? {} : { measuredFrom: from }),
  };
}

/** The game keeps most dungeons 6400 tiles north of the surface they lie under. */
const UNDERGROUND_Y_OFFSET = 6400;

/** Tiles from here northwards are quest areas, instances and other off-surface maps rather than the mainland. */
const SURFACE_MAX_Y = 4160;

/**
 * Maps an underground tile to the surface tile above it, so "nearest oak" from a dungeon is measured from where
 * the player will be after climbing out rather than across the 6400-tile gap in coordinates. Only the dungeon
 * band maps onto the surface: further north (the Giants' Foundry, instances) the shifted tile would land on
 * another off-surface map, so the player's own tile is the honest origin there.
 */
function surfaceEquivalent(origin: TilePosition): TilePosition {
  const above = { ...origin, y: origin.y - UNDERGROUND_Y_OFFSET };
  return origin.y >= UNDERGROUND_Y_OFFSET && isSurface(above) ? above : origin;
}

function isSurface({ y }: TileCoordinates): boolean {
  return y < SURFACE_MAX_Y;
}

/**
 * Keeps surface tiles when any exist. Off-surface copies of an object (a quest mansion's oak) are coordinate-close
 * to nothing a player on the mainland can walk to, so they only win when the surface has none.
 */
function preferSurface<T extends TileCoordinates>(candidates: T[]): T[] {
  const surface = candidates.filter(isSurface);
  return surface.length > 0 ? surface : candidates;
}

function tilesOf(name: string, tiles: readonly number[]): ObjectRow[] {
  const rows: ObjectRow[] = [];
  for (let i = 0; i < tiles.length; i += TILE_FIELDS) {
    const [x, y, plane] = tiles.slice(i, i + TILE_FIELDS);
    if (x !== undefined && y !== undefined && plane !== undefined) {
      rows.push({ name, x, y, plane });
    }
  }
  return rows;
}

/**
 * Says when the static table may lag the game: the client's revision differs from the cache build the table
 * came from. An unknown revision (older plugin, no snapshot) yields no note rather than a guess.
 *
 * @returns a sentence for the tool output, or undefined when the builds match or the revision is unknown
 */
export function stalenessNote(source: ObjectLocationSource, liveRevision: number | undefined): string | undefined {
  if (liveRevision === undefined || liveRevision === source.build) {
    return undefined;
  }
  return `The game is on revision ${liveRevision} but this map data is from build ${source.build}; objects added or moved since may be missing or misplaced.`;
}
