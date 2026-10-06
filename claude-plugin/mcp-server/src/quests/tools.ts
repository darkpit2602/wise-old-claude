import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { QuestState } from "../snapshot/playerSnapshot.js";
import { contractMismatch, json } from "../toolResults.js";
import { questProgressFor } from "./questProgress.js";
import type { QuestProgressStore } from "./questProgressStore.js";

/** Collaborators the quest tools need; injected so tests can point them at a temp directory. */
export interface QuestToolDependencies {
  progress: QuestProgressStore;
  /**
   * The current character and its quest states from the latest snapshot, if any, so a second account's file is
   * not read and quests without a saved journal can still be named.
   */
  player: () => { rsn: string; questStates: Record<string, QuestState> } | undefined;
}

/**
 * Registers the tool that answers "where am I in this quest?" from the quest journal the player last opened,
 * in the game's own words, instead of guessing a step from the wiki's full walkthrough.
 */
export function registerQuestTools(server: McpServer, { progress, player }: QuestToolDependencies): void {
  server.registerTool(
    "get_quest_progress",
    {
      title: "Get progress inside a quest",
      description:
        "Where the player is inside one quest: the quest journal text they last opened, split into completed " +
        "(struck through) and remaining lines, with currentStep = the first remaining line, how old the capture " +
        "is, and outdated: true when the quest's stage moved since then. Quests whose journal was never opened " +
        "return only their status and a note asking the player to open it. Needs the Wise Old Claude plugin.",
      inputSchema: { quest: z.string().min(1).describe("Quest name or the start of its words, e.g. \"fenkenstrain\".") },
      annotations: { readOnlyHint: true },
    },
    ({ quest }) => {
      const current = player();
      const loaded = progress.load(current?.rsn);
      if (loaded.kind === "invalid") {
        return contractMismatch("Quest progress", loaded);
      }
      const saved = loaded.kind === "found" ? loaded.progress : undefined;
      const view = questProgressFor(saved, quest, { now: progress.currentTime(), questStates: current?.questStates ?? {} });
      return json({ rsn: current?.rsn ?? saved?.rsn, ...view });
    },
  );
}
