import type { GameContext } from "../context/gameContext.js";
import type { PlayerSnapshot } from "../snapshot/playerSnapshot.js";

type Position = PlayerSnapshot["position"];

/** How close counts as arrived when the caller gives no radius: the tile itself or a step from it. */
export const DEFAULT_ARRIVAL_RADIUS = 2;

export interface InventoryCondition {
  /** Partial item name, matched case-insensitively like search_bank. */
  item: string;
  /** Total carried across matching stacks; 1 when omitted. */
  quantity?: number;
}

export interface ArrivalCondition {
  x: number;
  y: number;
  /** Required plane; any plane when omitted. */
  plane?: number;
  /** Largest step count in either axis that still counts as arrived. */
  within?: number;
}

/** What a condition found when it holds; undefined while it does not. */
export type Evidence = Record<string, unknown> | undefined;

/**
 * Whether the inventory carries enough of an item.
 *
 * @returns the matching stacks and their total once there are enough
 */
export function inventoryHas(snapshot: PlayerSnapshot, { item, quantity = 1 }: InventoryCondition): Evidence {
  const needle = item.toLowerCase();
  const stacks = snapshot.inventory.filter((stack) => stack.name.toLowerCase().includes(needle));
  const total = stacks.reduce((sum, stack) => sum + stack.qty, 0);
  return total >= quantity ? { items: stacks.map(({ name, qty }) => ({ name, qty })), total } : undefined;
}

/**
 * Whether the player stands within the radius of a tile. Distance is the larger of the x and y steps, matching how
 * the game measures reach.
 *
 * @returns the player's tile and its distance once arrived
 */
export function arrivedAt(position: Position, { x, y, plane, within = DEFAULT_ARRIVAL_RADIUS }: ArrivalCondition): Evidence {
  if (plane !== undefined && position.plane !== plane) {
    return undefined;
  }
  const distance = Math.max(Math.abs(position.x - x), Math.abs(position.y - y));
  return distance <= within ? { position: { x: position.x, y: position.y, plane: position.plane }, distance } : undefined;
}

/**
 * Whether a game message containing the text arrived at or after a moment. Earlier messages never count, so a wait
 * started after "You smelt an iron bar." does not end on that old line.
 *
 * @param since the moment the wait began
 * @returns the first such message once it arrived
 */
export function messageArrived(context: GameContext, text: string, since: Date): Evidence {
  const needle = text.toLowerCase();
  const message = context.messages.find(
    (candidate) => Date.parse(candidate.at) >= since.getTime() && candidate.text.toLowerCase().includes(needle),
  );
  return message === undefined
    ? undefined
    : { message: { at: message.at, text: message.text, ...(message.speaker === undefined ? {} : { speaker: message.speaker }) } };
}
