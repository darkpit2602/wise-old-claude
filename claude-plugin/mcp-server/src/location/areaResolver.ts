import { NAMED_AREAS, NAMED_PLACES } from "./areas.data.js";
import { wildernessAt } from "./wilderness.js";

/** A world tile column; plane is irrelevant to horizontal distance and region membership. */
export interface TileCoordinates {
  x: number;
  y: number;
}

/** A world tile as RuneLite reports it in the snapshot's `position`. */
export interface TilePosition extends TileCoordinates {
  plane: number;
}

/** How RuneLite classifies a named area; lets Claude tell a city from a boss lair or a wilderness band. */
export type AreaKind = "boss" | "raid" | "minigame" | "dungeon" | "city" | "region" | "wilderness";

/** A player-recognisable area defined as a set of 64x64 regions, which is the finest containment data available. */
export interface NamedArea {
  name: string;
  kind: AreaKind;
  regions: readonly number[];
}

/** A wiki location reduced to one representative tile, used to say what the player is standing near. */
export interface NamedPlace extends TilePosition {
  name: string;
  type?: string;
}

/** Any input carrying a tile; extra fields (urls, notes) survive ranking untouched. */
export type Ranked<T extends TileCoordinates> = T & { distance: number };

/** What a tile means to a player: the area it lies in, if named, and the closest named places. */
export interface LocationDescription {
  position: TilePosition;
  regionId: number;
  area?: { name: string; kind: AreaKind };
  /** Present only in the Wilderness; PvP combat range and the level 20/30 teleport limits depend on it. */
  wildernessLevel?: number;
  nearbyPlaces: Ranked<NamedPlace>[];
}

/** Datasets and tuning for an AreaResolver; injected so tests can use small synthetic gazetteers. */
export interface AreaResolverOptions {
  areas: readonly NamedArea[];
  places: readonly NamedPlace[];
  /** How many nearby places to report; a few give Claude context without flooding its window. */
  nearbyLimit?: number;
}

const DEFAULT_NEARBY_LIMIT = 3;

/**
 * Computes the 64x64 region id the same way RuneLite's WorldPoint.getRegionID does, so ids line up with
 * the snapshot's `regionId` and with the region-keyed area data.
 */
export function regionIdOf({ x, y }: TileCoordinates): number {
  return ((x >> 6) << 8) | (y >> 6);
}

/**
 * Tile distance as the game counts it: a diagonal step covers one tile, so the distance is the larger of
 * the axis deltas (Chebyshev). This mirrors RuneLite's WorldPoint.distanceTo2D and matches how many steps
 * or ticks of running a route needs on open ground, which Euclidean or Manhattan distance would misstate.
 */
export function tileDistance(from: TileCoordinates, to: TileCoordinates): number {
  return Math.max(Math.abs(from.x - to.x), Math.abs(from.y - to.y));
}

/**
 * Orders candidates nearest first so Claude can answer "where is the nearest X" from coordinates it found
 * on wiki pages instead of guessing. Ties fall back to name order so results are deterministic. Plane is
 * ignored because staircases make horizontal distance the useful signal; underground areas sit thousands
 * of tiles away on the y axis, so they separate naturally.
 */
export function rankByDistance<T extends TileCoordinates & { name: string }>(
  origin: TileCoordinates,
  candidates: readonly T[],
): Ranked<T>[] {
  return candidates
    .map((candidate) => ({ ...candidate, distance: tileDistance(origin, candidate) }))
    .sort((a, b) => a.distance - b.distance || a.name.localeCompare(b.name));
}

/**
 * Turns raw world coordinates into names a player would use. Containment is region-granular (RuneLite's
 * area data), and the nearest wiki places add finer context such as a market or bank within that area,
 * or stand in entirely when the region has no name.
 */
export class AreaResolver {
  private readonly areaByRegion = new Map<number, NamedArea>();
  private readonly places: readonly NamedPlace[];
  private readonly nearbyLimit: number;

  constructor({ areas, places, nearbyLimit = DEFAULT_NEARBY_LIMIT }: AreaResolverOptions) {
    for (const area of areas) {
      for (const region of area.regions) {
        this.areaByRegion.set(region, area);
      }
    }
    this.places = places;
    this.nearbyLimit = nearbyLimit;
  }

  /**
   * Describes a tile for Claude; the shape is stable so it can be embedded in other tool results. A
   * RuneLite-named area keeps its specific name inside the Wilderness (a boss lair stays a boss lair),
   * the Wilderness name only fills in where no region name exists, and the level is added either way.
   */
  describe(position: TilePosition): LocationDescription {
    const regionId = regionIdOf(position);
    const wilderness = wildernessAt(position);
    const area = this.areaByRegion.get(regionId) ?? wilderness;
    return {
      position: { x: position.x, y: position.y, plane: position.plane },
      regionId,
      ...(area === undefined ? {} : { area: { name: area.name, kind: area.kind } }),
      ...(wilderness === undefined ? {} : { wildernessLevel: wilderness.level }),
      nearbyPlaces: rankByDistance(position, this.places).slice(0, this.nearbyLimit),
    };
  }
}

/** Builds a resolver over the generated gazetteer shipped with the server (see areas.data.ts provenance). */
export function createBundledAreaResolver(): AreaResolver {
  return new AreaResolver({ areas: NAMED_AREAS, places: NAMED_PLACES });
}
