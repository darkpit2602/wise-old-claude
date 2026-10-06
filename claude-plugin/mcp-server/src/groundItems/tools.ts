import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { TilePosition } from "../location/areaResolver.js";
import { sceneIndexHeader } from "../sceneIndexHeader.js";
import { contractMismatch, failure, json, withoutUndefined } from "../toolResults.js";
import { findGroundItems, restrictionFor } from "./groundItems.js";
import type { GroundItemsStore } from "./groundItemsStore.js";

/** Collaborators the ground-item tool needs; injected so tests can point them at a temp directory. */
export interface GroundItemToolDependencies {
  groundItems: GroundItemsStore;
  /**
   * The current character, tile and account type from the latest snapshot, if any. The snapshot follows the
   * player more closely than the index's origin, so it is the better origin; the account type decides
   * whether other players' drops are offered at all.
   */
  player: () => { rsn: string; position: TilePosition; accountType?: string } | undefined;
}

const DEFAULT_LIMIT = 10;
const MAX_LIMIT = 50;

const COVERAGE =
  "Only items in the scene RuneLite has loaded are listed: about 50 tiles each way from indexOrigin. " +
  "Ground items despawn (a drop lasts about a minute for its owner before others see it, then a few more " +
  "minutes), so an old index can list items already gone.";

const NO_INDEX =
  "No ground-item index found. It is written by the Wise Old Claude plugin while logged in; update the plugin " +
  "or log in.";

/**
 * Registers the tool that lists the items lying around the player (a kill's drop, spawns, loot left behind),
 * which no other source knows: the wiki lists drop tables, not what is on the ground right now.
 */
export function registerGroundItemTools(server: McpServer, { groundItems, player }: GroundItemToolDependencies): void {
  server.registerTool(
    "find_ground_items",
    {
      title: "Find ground items",
      description:
        "Lists items lying on the ground in the area RuneLite has loaded around the player (about 50 tiles each " +
        "way): drops, loot and item spawns, with quantity, tile, tile distance, item id, ownership and the " +
        "stack's Grand Exchange and high alchemy value. Without a name, answers \"what dropped?\" and \"any loot " +
        "here?\": the most valuable stacks first (by the better of GE and alch value), then nearest. With a " +
        "name (matched at word starts, case-insensitive), the nearest matching stacks first. For ironman " +
        "accounts other players' drops are left out (they cannot be picked up) and counted in othersLootHidden. " +
        "Ownership is self, group, other or none; it never names another player. Results are tiles ready for " +
        "guide_to. Needs RuneLite running with the Wise Old Claude plugin.",
      inputSchema: {
        name: z.string().trim().min(1).max(100).optional().describe('Partial item name, e.g. "bones", "rune", "coins".'),
        limit: z.number().int().min(1).max(MAX_LIMIT).optional(),
      },
      annotations: { readOnlyHint: true },
    },
    ({ name, limit = DEFAULT_LIMIT }) => {
      const current = player();
      const loaded = groundItems.load(current?.rsn);
      if (loaded.kind === "missing") {
        return failure(NO_INDEX);
      }
      if (loaded.kind === "invalid") {
        return contractMismatch("Ground-item index", loaded);
      }
      const { index, ageSeconds } = loaded;
      const { origin, header } = sceneIndexHeader({
        index,
        ageSeconds,
        playerPosition: current?.position,
        coverage: COVERAGE,
      });
      const { othersLootHidden, ...found } = findGroundItems(
        index,
        withoutUndefined({ name, origin, limit, restriction: restrictionFor(current?.accountType) }),
      );
      return json({
        ...withoutUndefined({ query: name }),
        order: name === undefined ? "value" : "nearest",
        ...header,
        ...(othersLootHidden > 0 ? { othersLootHidden } : {}),
        ...found,
      });
    },
  );
}
