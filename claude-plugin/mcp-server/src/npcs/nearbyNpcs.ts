import { nameMatcher } from "../nameMatching.js";
import { type Ranked, type TilePosition, rankByDistance } from "../location/areaResolver.js";

/** Every NPC of one id sharing a name; the id fixes its options and combat level. */
export interface NpcVariant {
  id: number;
  /** 0 when the NPC has no combat level. */
  combatLevel: number;
  /** Right-click options in menu order, e.g. `["Cage", "Harpoon"]`. */
  actions: string[];
  /** `[x, y, plane]` tiles, nearest the index origin first. */
  tiles: [number, number, number][];
}

/**
 * TypeScript view of `contract/nearby-npcs.schema.json`. The schema is authoritative and is enforced at load
 * time; this type only describes what a validated index looks like.
 */
export interface NearbyNpcs {
  schemaVersion: 1;
  rsn: string;
  capturedAt: string;
  world: number;
  /** Tiles are instance coordinates (like the snapshot's position), not wiki coordinates. */
  instance: boolean;
  /** The player's tile when the plugin built the index. */
  origin: TilePosition & { regionId: number };
  /** The plugin's cap dropped the NPCs farthest from the origin. */
  truncated: boolean;
  /** NPC name to its variants, nearest variant first. */
  npcs: Record<string, NpcVariant[]>;
}

/** A search of the index: which names (and optionally which option) to match, from where, and how many. */
export interface NearbyNpcQuery {
  /** Omitted for "the closest NPC": every name qualifies, so the answer takes one call instead of guessing names. */
  name?: string;
  action?: string;
  origin: TilePosition;
  limit: number;
}

/** One matching NPC, shaped so Claude can pass its tile straight to guide_to and knows which option to click. */
export interface NearbyNpcRow extends TilePosition {
  name: string;
  id: number;
  combatLevel: number;
  actions: string[];
}

/** The nearest matches, plus how many matched in total so Claude knows when a limit hid some. */
export interface NearbyNpcMatches {
  totalMatches: number;
  results: Ranked<NearbyNpcRow>[];
}

/**
 * Finds NPCs whose name contains the query at a word start, ignoring case, nearest the origin first. Word
 * starts for the same reason as find_nearby_objects: "cow" must find "Dairy cow" but not "Scow", because the
 * first hit becomes a walking route. The optional action is matched whole, not by word start, so "Net" does
 * not pick a "Big Net" spot that catches different fish. Distance ignores plane, matching rank_by_distance.
 */
export function findNearbyNpcs(index: NearbyNpcs, { name, action, origin, limit }: NearbyNpcQuery): NearbyNpcMatches {
  const matches = name === undefined ? () => true : nameMatcher(name);
  const wantedAction = action?.trim().toLowerCase();
  const candidates = Object.entries(index.npcs)
    .filter(([npcName]) => matches(npcName))
    .flatMap(([npcName, variants]) =>
      variants
        .filter((variant) => wantedAction === undefined || variant.actions.some((offered) => offered.toLowerCase() === wantedAction))
        .flatMap(({ id, combatLevel, actions, tiles }) =>
          tiles.map(([x, y, plane]) => ({ name: npcName, id, combatLevel, actions, x, y, plane })),
        ),
    );
  return { totalMatches: candidates.length, results: rankByDistance(origin, candidates).slice(0, limit) };
}
