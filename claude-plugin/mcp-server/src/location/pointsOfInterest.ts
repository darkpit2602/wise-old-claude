import { rankByDistance } from "./areaResolver.js";
import type { Ranked, TileCoordinates, TilePosition } from "./areaResolver.js";

/**
 * The kinds of place find_nearest can search. Each has a curated coordinate source (see
 * pointsOfInterest.data.ts); categories without one, such as cooking ranges, are left out rather than
 * guessed, so Claude falls back to the wiki instead of trusting incomplete data.
 */
export const POINT_OF_INTEREST_TYPES = [
  "bank",
  "deposit_box",
  "altar",
  "runecrafting_altar",
  "anvil",
  "furnace",
  "fairy_ring",
  "spirit_tree",
  "agility_course",
  "minigame",
] as const;

/** One searchable category; deposit boxes are separate from banks because they cannot withdraw. */
export type PointOfInterestType = (typeof POINT_OF_INTEREST_TYPES)[number];

/**
 * The special world types (PvP, Bounty Hunter) whose players can use places that every other world lacks. A
 * PvP chest is the nearest bank at the Falador respawn on a PvP world and absent everywhere else, so such
 * places must be told apart rather than ranked as if they were always there.
 */
export const SPECIAL_WORLD_TYPES = ["pvp", "bounty_hunter"] as const;

/** One special world type; a place carrying one exists only on worlds of that type. */
export type SpecialWorldType = (typeof SPECIAL_WORLD_TYPES)[number];

/** A typed place with one representative tile, as baked into the generated data module. */
export interface PointOfInterest extends TilePosition {
  type: PointOfInterestType;
  name: string;
  /** Present only when the source states it; absence means unknown, not free-to-play. */
  members?: boolean;
  /** The fairy ring dial code, so Claude can tell the player what to dial. */
  code?: string;
  /** Present only for places that exist on one special world type; absence means every world has it. */
  worlds?: SpecialWorldType;
}

/** What to look for and from where; the limit keeps answers small enough not to crowd Claude's context. */
export interface NearestQuery {
  type: PointOfInterestType;
  origin: TileCoordinates;
  limit: number;
  /** The special world the player is on; omitted means an ordinary world, where special-world places do not exist. */
  worldType?: SpecialWorldType;
}

/**
 * Answers "where is the nearest X" from the bundled data: filters to one type and to places that exist on the
 * player's kind of world, then reuses the game-tile ranking so distances agree with rank_by_distance and
 * describe_location. Special-world places are left out unless asked for because ranking a PvP-only chest first
 * sends an ordinary-world player to a bank that is not there.
 */
export function findNearest(points: readonly PointOfInterest[], { type, origin, limit, worldType }: NearestQuery): Ranked<PointOfInterest>[] {
  return rankByDistance(
    origin,
    points.filter((point) => point.type === type && (point.worlds === undefined || point.worlds === worldType)),
  ).slice(0, limit);
}
