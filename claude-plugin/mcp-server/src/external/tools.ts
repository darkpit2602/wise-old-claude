import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { HiscoresClient, hiscoreTableFor, type AccountType, type HiscoreLookup } from "./hiscoresClient.js";
import type { FetchFn } from "./http.js";
import { PriceClient } from "./priceClient.js";
import { WikiSyncClient, type WikiSyncLookup } from "./wikiSyncClient.js";
import { failure, json, text } from "../toolResults.js";

/** The character Claude is currently helping, as known from the RuneLite snapshot. */
export interface CurrentPlayer {
  rsn: string;
  accountType: AccountType;
}

/**
 * Collaborators for the external data tools. `currentPlayer` is a provider rather than a value because the
 * snapshot changes while the server runs (the player can log into another character).
 */
export interface ExternalDataDependencies {
  wikiSync: WikiSyncClient;
  prices: PriceClient;
  hiscores: HiscoresClient;
  currentPlayer: () => CurrentPlayer | undefined;
}

/** Builds the default clients sharing one fetch implementation; the server entry point only supplies the player. */
export function createExternalDataDependencies(options: {
  currentPlayer: () => CurrentPlayer | undefined;
  fetchFn?: FetchFn;
}): ExternalDataDependencies {
  const fetchFn = options.fetchFn ?? fetch;
  return {
    wikiSync: new WikiSyncClient(fetchFn),
    prices: new PriceClient(fetchFn),
    hiscores: new HiscoresClient(fetchFn),
    currentPlayer: options.currentPlayer,
  };
}

const ACCOUNT_TYPES = [
  "NORMAL",
  "IRONMAN",
  "ULTIMATE_IRONMAN",
  "HARDCORE_IRONMAN",
  "GROUP_IRONMAN",
  "HARDCORE_GROUP_IRONMAN",
  "UNRANKED_GROUP_IRONMAN",
  "UNKNOWN",
] as const satisfies readonly AccountType[];


const NO_PLAYER_MESSAGE =
  "No rsn given and no current character is known (no RuneLite snapshot). Pass the player's rsn explicitly.";

/**
 * Decides whose hiscores to read and on which account type: an explicit type wins; otherwise the current
 * character's type applies when the name is theirs; anyone else is assumed NORMAL because every account
 * is ranked on the main table.
 */
export function resolveHiscoreTarget(
  request: { rsn?: string; accountType?: AccountType },
  current: CurrentPlayer | undefined,
): CurrentPlayer | undefined {
  const rsn = request.rsn ?? current?.rsn;
  if (rsn === undefined) {
    return undefined;
  }
  const isCurrent = current !== undefined && current.rsn.toLowerCase() === rsn.toLowerCase();
  const fallbackType: AccountType = isCurrent ? current.accountType : "NORMAL";
  return { rsn, accountType: request.accountType ?? fallbackType };
}

/**
 * Registers the read-only tools backed by third-party OSRS data (WikiSync, GE prices, hiscores) on an
 * existing server, keeping them separate from the snapshot and wiki tools so each can evolve on its own.
 */
export function registerExternalDataTools(server: McpServer, deps: ExternalDataDependencies): void {
  const rsnParam = z
    .string()
    .min(1)
    .optional()
    .describe("Character name. Omit for the current RuneLite character.");

  server.registerTool(
    "get_wikisync_progress",
    {
      title: "Get WikiSync progress",
      description:
        "Quest, achievement diary, combat achievement, collection log and music progress from the WikiSync " +
        "RuneLite plugin. Use for quest/diary/CA/clog questions (\"what diaries can I finish\", \"which quests are " +
        "left\"); this data is not in the snapshot or hiscores.",
      inputSchema: { rsn: rsnParam },
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    async ({ rsn }) => {
      const name = rsn ?? deps.currentPlayer()?.rsn;
      if (name === undefined) {
        return failure(NO_PLAYER_MESSAGE);
      }
      try {
        return describeWikiSync(await deps.wikiSync.progress(name));
      } catch (error) {
        return failure(String(error));
      }
    },
  );

  server.registerTool(
    "get_item_prices",
    {
      title: "Get Grand Exchange prices",
      description:
        "Live Grand Exchange prices from the OSRS Wiki real-time API: latest instant-buy (high) and instant-sell " +
        "(low) with trade times, GE buy limit and high alch value. Use for item value, profit or alching questions. " +
        "Accepts item names (any case) or ids, many at once.",
      inputSchema: {
        items: z
          .array(z.union([z.string().min(1), z.number().int().positive()]))
          .min(1)
          .max(50)
          .describe("Exact item names (case-insensitive) or item ids."),
      },
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    async ({ items }) => {
      try {
        return json(await deps.prices.lookup(items));
      } catch (error) {
        return failure(String(error));
      }
    },
  );

  server.registerTool(
    "get_hiscores",
    {
      title: "Get OSRS hiscores",
      description:
        "Official OSRS hiscores: rank/level/xp per skill plus boss kill counts, clue scroll counts and minigame " +
        "scores (unranked entries omitted). Use for KC, clue or rank questions, or to look up another player. " +
        "Uses the current character's account-type table by default.",
      inputSchema: {
        rsn: rsnParam,
        accountType: z
          .enum(ACCOUNT_TYPES)
          .optional()
          .describe("Which hiscore table to read. Omit to use the current character's type, or NORMAL for others."),
      },
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    async (request) => {
      const target = resolveHiscoreTarget(request, deps.currentPlayer());
      if (target === undefined) {
        return failure(NO_PLAYER_MESSAGE);
      }
      try {
        return describeHiscores(
          await deps.hiscores.lookup({ rsn: target.rsn, table: hiscoreTableFor(target.accountType) }),
        );
      } catch (error) {
        return failure(String(error));
      }
    },
  );
}

function describeWikiSync(result: WikiSyncLookup) {
  if (result.kind === "notSynced") {
    return text(
      `WikiSync has no data for "${result.rsn}". The player needs the WikiSync plugin (RuneLite Plugin Hub) ` +
        "enabled while logged in; collection log data also requires opening the collection log in-game once.",
    );
  }
  return json({ rsn: result.rsn, ...result.progress });
}

function describeHiscores(result: HiscoreLookup) {
  if (result.kind === "notFound") {
    return text(
      `"${result.rsn}" is not on the ${result.table} hiscores. The name may be wrong, the account type may differ ` +
        "(try another accountType), or the account is too new or low-level to be ranked.",
    );
  }
  return json({ rsn: result.rsn, table: result.table, ...result.player });
}
