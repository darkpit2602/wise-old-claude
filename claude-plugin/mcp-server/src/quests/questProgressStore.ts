import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { compileContract } from "../contractValidator.js";
import { resolveDirectory, type SnapshotDirectory } from "../snapshot/snapshotDirectory.js";
import { readRsn, sameName } from "../snapshot/snapshotStore.js";
import type { QuestProgress } from "./questProgress.js";
import { questProgressSchema } from "./questProgress.schema.js";

/**
 * Subdirectory of the snapshot directory the plugin writes quest progress to. One level down so the snapshot
 * store's listing of `*.json` characters never mistakes a quest file for a character.
 */
const QUESTS_DIR = "quests";

export type QuestProgressLoadResult =
  | { kind: "found"; progress: QuestProgress; path: string }
  | { kind: "missing"; directory: string }
  | { kind: "invalid"; path: string; errors: string };

/**
 * Reads the quest-progress files the RuneLite plugin writes, one per character. Every file is validated against
 * the shared contract so a plugin/server version mismatch surfaces as a clear error instead of a wrong answer.
 * Shape: contract/quest-progress.schema.json.
 */
export class QuestProgressStore {
  private readonly validate;

  constructor(
    private readonly snapshotDirectory: SnapshotDirectory,
    private readonly now: () => Date = () => new Date(),
  ) {
    this.validate = compileContract(questProgressSchema);
  }

  /** This call's folder, resolved afresh so the server follows a folder the plugin moved. */
  private get directory(): string {
    return join(resolveDirectory(this.snapshotDirectory), QUESTS_DIR);
  }

  /** The clock journal ages are measured against. */
  currentTime(): Date {
    return this.now();
  }

  /**
   * Loads one character's quest progress.
   *
   * @param rsn character name, matched like the snapshot store matches it; omitted means the most recently
   *   written file
   */
  load(rsn?: string): QuestProgressLoadResult {
    const files = this.listByRecency();
    const chosen = rsn === undefined ? files[0] : files.find((file) => sameName(readRsn(file), rsn));
    if (chosen === undefined) {
      return { kind: "missing", directory: this.directory };
    }

    const data: unknown = JSON.parse(readFileSync(chosen, "utf8"));
    if (!this.validate(data)) {
      return { kind: "invalid", path: chosen, errors: JSON.stringify(this.validate.errors) };
    }
    return { kind: "found", progress: data as QuestProgress, path: chosen };
  }

  private listByRecency(): string[] {
    if (!existsSync(this.directory)) {
      return [];
    }
    return readdirSync(this.directory)
      .filter((name) => name.endsWith(".json"))
      .map((name) => join(this.directory, name))
      .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs);
  }
}
