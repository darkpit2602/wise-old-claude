import { type TilePosition, rankByDistance } from "../location/areaResolver.js";
import { type ObjectMatches, type ObjectSearch } from "../location/objectLocations.js";
import { nameMatcher } from "../nameMatching.js";

/**
 * TypeScript view of `contract/nearby-objects.schema.json`. The schema is authoritative and is enforced at
 * load time; this type only describes what a validated index looks like.
 */
export interface NearbyObjects {
  schemaVersion: 1;
  rsn: string;
  capturedAt: string;
  world: number;
  /** Tiles are instance coordinates (like the snapshot's position), not wiki coordinates. */
  instance: boolean;
  /** The player's tile when the plugin built the index. */
  origin: TilePosition & { regionId: number };
  /** The plugin's cap dropped the objects farthest from the origin. */
  truncated: boolean;
  /** Object name to `[x, y, plane, objectId]` tiles, nearest the origin first. */
  objects: Record<string, [number, number, number, number][]>;
}

/**
 * Finds objects in the live index nearest the origin first, matching names as the static world table does
 * (nameMatcher), so "oak" answers the same whichever table serves it.
 */
export function findNearbyObjects(index: NearbyObjects, { name, origin, limit }: ObjectSearch): ObjectMatches {
  const matches = nameMatcher(name);
  const candidates = Object.entries(index.objects)
    .filter(([objectName]) => matches(objectName))
    .flatMap(([objectName, tiles]) => tiles.map(([x, y, plane]) => ({ name: objectName, x, y, plane })));
  return { totalMatches: candidates.length, results: rankByDistance(origin, candidates).slice(0, limit) };
}
