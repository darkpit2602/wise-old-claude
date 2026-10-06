import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { json } from "../toolResults.js";
import type { GuidanceRequests } from "./guidanceRequests.js";

/** Collaborators the guidance tools need; injected so tests can point them at a temp directory. */
export interface GuidanceToolDependencies {
  requests: GuidanceRequests;
  /**
   * Whether the plugin appears to be running (it wrote a snapshot recently). Requests are still written when
   * it is not, since the client may simply be starting up, but Claude should tell the user nothing will show yet.
   */
  pluginRunning: () => boolean;
}

const tile = z.number().int().min(0);

const PLUGIN_NOT_RUNNING =
  "No recent snapshot: RuneLite or the Wise Old Claude plugin does not seem to be running, so nothing will " +
  "appear in-game until it is. The request stays valid for a few minutes.";

/**
 * Registers the tools that turn a destination Claude has worked out into something the player can follow
 * in-game. They only ask the plugin to draw; the plugin never moves or clicks for the player.
 */
export function registerGuidanceTools(server: McpServer, { requests, pluginRunning }: GuidanceToolDependencies): void {
  server.registerTool(
    "guide_to",
    {
      title: "Guide the player to a tile in-game",
      description:
        "Draws the route to a world tile inside the user's RuneLite client: the Shortest Path plugin's lines on " +
        "the floor, minimap and world map when it is installed, plus the game's yellow hint arrow. Requires " +
        "RuneLite running with the Wise Old Claude plugin enabled; it only draws, it never moves the player. " +
        "Replaces any previous destination; cleared automatically on arrival or with clear_guidance. Get the tile " +
        "from find_nearest, describe_location or wiki {{Map}} coordinates.",
      inputSchema: {
        x: tile,
        y: tile,
        plane: z.number().int().min(0).max(3).optional().describe("Floor level; omit for ground level (0)."),
        label: z.string().min(1).max(100).optional().describe("Short destination name shown in the game chat."),
      },
      annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: false },
    },
    ({ x, y, plane = 0, label }) => {
      const target = { x, y, plane };
      const requestFile = requests.request({ ...target, label });
      return json({
        target,
        ...(label === undefined ? {} : { label }),
        requestFile,
        ...(pluginRunning() ? {} : { warning: PLUGIN_NOT_RUNNING }),
      });
    },
  );

  server.registerTool(
    "clear_guidance",
    {
      title: "Clear in-game guidance",
      description: "Removes the route and hint arrow that guide_to drew in RuneLite.",
      inputSchema: {},
      annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: false },
    },
    () => json({ cleared: requests.clear() }),
  );
}
