import type { TilePosition } from "./location/areaResolver.js";
import { withoutUndefined } from "./toolResults.js";

/** The parts of a scene index (NPCs, ground items) that every answer about it repeats. */
export interface SceneIndex {
  origin: { x: number; y: number; plane: number };
  instance?: boolean;
  truncated?: boolean;
}

/** What a scene-index answer needs to describe where its distances are measured from and how fresh it is. */
export interface SceneIndexAnswer {
  index: SceneIndex;
  ageSeconds: number;
  /** The player's current tile from the snapshot, which follows the player more closely than the index origin. */
  playerPosition: TilePosition | undefined;
  coverage: string;
}

/**
 * Picks the origin for distances and builds the header fields shared by every scene-index tool answer.
 *
 * @param answer the loaded index, its age, the player's tile if known and the tool's coverage note
 * @returns the origin to search from, and the header fields to spread into the answer
 */
export function sceneIndexHeader({ index, ageSeconds, playerPosition, coverage }: SceneIndexAnswer) {
  const indexOrigin = { x: index.origin.x, y: index.origin.y, plane: index.origin.plane };
  const origin = playerPosition ?? indexOrigin;
  return {
    origin,
    header: withoutUndefined({
      origin: { x: origin.x, y: origin.y, plane: origin.plane },
      originSource: playerPosition === undefined ? "index" : "player",
      indexOrigin,
      indexAgeSeconds: ageSeconds,
      instance: index.instance ? true : undefined,
      truncated: index.truncated ? true : undefined,
      coverage,
    }),
  };
}
