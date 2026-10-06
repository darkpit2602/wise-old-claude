import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { contractMismatch, failure, json } from "../toolResults.js";
import { describeOpenInterface, recentMessages } from "./gameContext.js";
import type { GameContextLoadResult, GameContextStore } from "./gameContextStore.js";

/** Collaborators the game-context tools need; injected so tests can point them at a temp directory. */
export interface ContextToolDependencies {
  context: GameContextStore;
  /** The current character's name from the latest snapshot, if any, so a second account's file is not read. */
  player: () => string | undefined;
}

const DEFAULT_LIMIT = 10;
const MAX_LIMIT = 30;

const NO_CONTEXT =
  "No game context found. It is written by the Wise Old Claude plugin while logged in; update the plugin or log in.";

type Loaded = Extract<GameContextLoadResult, { kind: "found" }>;

/**
 * Registers the tools that tell Claude what just happened in game and what the player has open: the game's
 * own messages ("You need an axe to chop down this tree.") explain a failed action better than any guess, and
 * a shop's live stock answers "what does this shop sell?" where the wiki only knows the default stock.
 */
export function registerContextTools(server: McpServer, { context, player }: ContextToolDependencies): void {
  const load = (): Loaded | ReturnType<typeof failure> => {
    const loaded = context.load(player());
    if (loaded.kind === "missing") {
      return failure(NO_CONTEXT);
    }
    if (loaded.kind === "invalid") {
      return contractMismatch("Game context", loaded);
    }
    return loaded;
  };

  server.registerTool(
    "get_recent_messages",
    {
      title: "Get recent game messages",
      description:
        "The player's own recent game messages, oldest first with how many seconds ago each arrived: game " +
        'feedback ("You need an axe to chop down this tree.", "Your inventory is too full"), level-ups, NPC ' +
        "dialogue lines with the speaker, message boxes and examine texts. Never other players' chat. Use it to " +
        "understand what just happened before advising. Needs RuneLite running with the Wise Old Claude plugin.",
      inputSchema: {
        limit: z.number().int().min(1).max(MAX_LIMIT).optional().describe("Newest messages to return, default 10."),
      },
      annotations: { readOnlyHint: true },
    },
    ({ limit = DEFAULT_LIMIT }) => {
      const loaded = load();
      if (!("context" in loaded)) {
        return loaded;
      }
      return json({ rsn: loaded.context.rsn, ...recentMessages(loaded.context, { limit, now: context.currentTime() }) });
    },
  );

  server.registerTool(
    "get_open_interface",
    {
      title: "Get open interface",
      description:
        "What the player has open right now: a shop with its name and live stock (item, id, quantity; sold-out " +
        "items at 0; no prices, the player's Value option posts the price as a game message), the bank, the " +
        "Grand Exchange, or a dialogue with the speaker, line or options. kind is none when nothing tracked is " +
        "open and unknown when RuneLite has not confirmed it for over a minute. Needs RuneLite running with the " +
        "Wise Old Claude plugin.",
      inputSchema: {},
      annotations: { readOnlyHint: true },
    },
    () => {
      const loaded = load();
      if (!("context" in loaded)) {
        return loaded;
      }
      return json({ rsn: loaded.context.rsn, ...describeOpenInterface(loaded.context, loaded.ageSeconds) });
    },
  );
}
