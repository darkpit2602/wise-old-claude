import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { TilePosition } from "../location/areaResolver.js";
import { sceneIndexHeader } from "../sceneIndexHeader.js";
import { contractMismatch, failure, json, withoutUndefined } from "../toolResults.js";
import { findNearbyNpcs } from "./nearbyNpcs.js";
import type { NearbyNpcsStore } from "./nearbyNpcsStore.js";

/** Collaborators the NPC tools need; injected so tests can point them at a temp directory. */
export interface NpcToolDependencies {
  npcs: NearbyNpcsStore;
  /**
   * The current character and tile from the latest snapshot, if any. The snapshot follows the player more
   * closely than the NPC index's origin, so it is the better origin.
   */
  player: () => { rsn: string; position: TilePosition } | undefined;
}

const DEFAULT_LIMIT = 5;
const MAX_LIMIT = 25;

const COVERAGE =
  "Only NPCs in the scene RuneLite has loaded are listed: about 50 tiles each way from indexOrigin. " +
  "Farther NPCs exist but are not in the index. NPCs walk, so a tile can be a few tiles off by the time the player arrives.";

const NO_INDEX =
  "No nearby-NPC index found. It is written by the Wise Old Claude plugin while logged in; update the plugin " +
  "or log in, and meanwhile use the wiki or find_nearest.";

/**
 * Registers the tool that finds individual NPCs (the nearest lobster fishing spot, a cow to train on, a
 * shopkeeper) around the player, which no wiki-backed tool can: the wiki lists where a kind of NPC lives,
 * not which one is closest right now. Fishing spots are NPCs, so they are only findable here.
 */
export function registerNpcTools(server: McpServer, { npcs, player }: NpcToolDependencies): void {
  server.registerTool(
    "find_nearby_npcs",
    {
      title: "Find nearby NPCs",
      description:
        "Finds fishing spots, monsters, shopkeepers, bankers and other NPCs by name (matched at word starts, " +
        "case-insensitive) in the area RuneLite has loaded around the player (about 50 tiles each way), nearest " +
        "first with tile distance, npc id, combat level and right-click options. Filter by option to pick the " +
        'right kind of fishing spot: action "Cage" for lobsters, "Harpoon" for tuna, swordfish or sharks, ' +
        '"Lure" for trout and salmon, "Net" for shrimps. Never lists other players. Results are tiles ready ' +
        "for guide_to. Needs RuneLite running with the Wise Old Claude plugin.",
      inputSchema: {
        name: z
          .string()
          .trim()
          .min(1)
          .max(100)
          .optional()
          .describe('Partial NPC name, e.g. "fishing spot", "cow", "banker". Omit to list the nearest NPCs of any kind.'),
        action: z
          .string()
          .trim()
          .min(1)
          .max(50)
          .optional()
          .describe('Exact right-click option the NPC must offer, case-insensitive, e.g. "Cage", "Attack", "Trade".'),
        limit: z.number().int().min(1).max(MAX_LIMIT).optional(),
      },
      annotations: { readOnlyHint: true },
    },
    ({ name, action, limit = DEFAULT_LIMIT }) => {
      const current = player();
      const loaded = npcs.load(current?.rsn);
      if (loaded.kind === "missing") {
        return failure(NO_INDEX);
      }
      if (loaded.kind === "invalid") {
        return contractMismatch("NPC index", loaded);
      }
      const { index, ageSeconds } = loaded;
      const { origin, header } = sceneIndexHeader({
        index,
        ageSeconds,
        playerPosition: current?.position,
        coverage: COVERAGE,
      });
      return json({
        ...withoutUndefined({ query: name, action }),
        ...header,
        ...findNearbyNpcs(index, withoutUndefined({ name, action, origin, limit })),
      });
    },
  );
}
