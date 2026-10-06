import { nameMatcher } from "../nameMatching.js";
import { type Ranked, type TilePosition, rankByDistance } from "../location/areaResolver.js";

/** Whose drop a stack is; never which player. Ironmen may take every kind except `other`. */
export type Ownership = "none" | "self" | "other" | "group";

/** Every stack of one item id with one ownership; the id fixes its prices. */
export interface GroundItemVariant {
  id: number;
  ownership: Ownership;
  /** Grand Exchange guide price of one item, 0 when untradeable or unknown. */
  gePrice: number;
  /** High alchemy value of one item. */
  haPrice: number;
  /** `[x, y, plane, quantity]` stacks, nearest the index origin first. */
  stacks: [number, number, number, number][];
}

/**
 * TypeScript view of `contract/ground-items.schema.json`. The schema is authoritative and is enforced at
 * load time; this type only describes what a validated index looks like.
 */
export interface GroundItems {
  schemaVersion: 1;
  rsn: string;
  capturedAt: string;
  world: number;
  /** Tiles are instance coordinates (like the snapshot's position), not wiki coordinates. */
  instance: boolean;
  /** The player's tile when the plugin built the index. */
  origin: TilePosition & { regionId: number };
  /** The plugin's cap dropped the stacks farthest from the origin. */
  truncated: boolean;
  /** Item name to its variants, nearest variant first. */
  items: Record<string, GroundItemVariant[]>;
}

/**
 * Who the answer is for. `ironman` leaves out other players' drops, which an ironman cannot pick up, the
 * same rule as RuneLite's Ground Items "Takeable" filter.
 */
export type LootRestriction = "none" | "ironman";

/** A search of the index: an optional name, from where, how many, and for which kind of account. */
export interface GroundItemQuery {
  /** Omitted means every item, most valuable first. */
  name?: string;
  origin: TilePosition;
  limit: number;
  restriction: LootRestriction;
}

/** One stack, shaped so Claude can pass its tile straight to guide_to and weigh whether it is worth taking. */
export interface GroundItemRow extends TilePosition {
  name: string;
  id: number;
  quantity: number;
  ownership: Ownership;
  /** Grand Exchange value of the whole stack. */
  geValue: number;
  /** High alchemy value of the whole stack. */
  haValue: number;
}

/** The chosen stacks, plus counts so Claude knows when a limit or the ironman rule hid some. */
export interface GroundItemMatches {
  /** Matching stacks the player may take, before the limit. */
  totalMatches: number;
  /** Matching stacks left out because they are another player's drop and the player is an ironman. */
  othersLootHidden: number;
  results: Ranked<GroundItemRow>[];
}

/**
 * Finds stacks on the ground. With a name, matched at word starts like every other "find" tool, the nearest
 * come first: the player knows what they want and needs the closest one. Without a name the most valuable
 * come first, by the better of Grand Exchange and high alchemy value, then nearest: "what dropped?" or "any
 * loot here?" is about what is worth picking up, and nearest-first would bury a rune drop under the bones
 * of the kill tile. Distance ignores plane, matching rank_by_distance.
 */
export function findGroundItems(index: GroundItems, { name, origin, limit, restriction }: GroundItemQuery): GroundItemMatches {
  const matches = name === undefined ? () => true : nameMatcher(name);
  const candidates = Object.entries(index.items)
    .filter(([itemName]) => matches(itemName))
    .flatMap(([itemName, variants]) => variants.flatMap((variant) => rowsOf(itemName, variant)));
  const takeable = candidates.filter((row) => mayTake(row.ownership, restriction));
  const ranked = rankByDistance(origin, takeable);
  const ordered = name === undefined ? [...ranked].sort((a, b) => valueOf(b) - valueOf(a) || a.distance - b.distance) : ranked;
  return {
    totalMatches: takeable.length,
    othersLootHidden: candidates.length - takeable.length,
    results: ordered.slice(0, limit),
  };
}

/**
 * Whether an account type may only take its own, its group's and unowned items. Every ironman variant
 * carries IRONMAN in its name; UNKNOWN is treated as unrestricted because hiding loot from a main would be
 * the worse error than showing an ironman a stack labelled `other`.
 *
 * @param accountType the snapshot's account type, if a snapshot exists
 */
export function restrictionFor(accountType: string | undefined): LootRestriction {
  return accountType?.includes("IRONMAN") === true ? "ironman" : "none";
}

function rowsOf(name: string, { id, ownership, gePrice, haPrice, stacks }: GroundItemVariant): GroundItemRow[] {
  return stacks.map(([x, y, plane, quantity]) => ({
    name,
    id,
    quantity,
    ownership,
    geValue: gePrice * quantity,
    haValue: haPrice * quantity,
    x,
    y,
    plane,
  }));
}

function mayTake(ownership: Ownership, restriction: LootRestriction): boolean {
  return restriction === "none" || ownership !== "other";
}

function valueOf(row: GroundItemRow): number {
  return Math.max(row.geValue, row.haValue);
}
