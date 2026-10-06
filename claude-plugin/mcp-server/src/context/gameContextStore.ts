import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { compileContract } from "../contractValidator.js";
import { resolveDirectory, type SnapshotDirectory } from "../snapshot/snapshotDirectory.js";
import { readRsn, sameName } from "../snapshot/snapshotStore.js";
import type { GameContext } from "./gameContext.js";
import { gameContextSchema } from "./gameContext.schema.js";

/**
 * Subdirectory of the snapshot directory the plugin writes game context to. One level down so the snapshot
 * store's listing of `*.json` characters never mistakes a context file for a character.
 */
const CONTEXT_DIR = "context";

export type GameContextLoadResult =
  | { kind: "found"; context: GameContext; ageSeconds: number; path: string }
  | { kind: "missing"; directory: string }
  | { kind: "invalid"; path: string; errors: string };

/**
 * Reads the game-context files the RuneLite plugin writes, one per character. Every file is validated against
 * the shared contract so a plugin/server version mismatch surfaces as a clear error instead of a wrong answer.
 * Shape: contract/game-context.schema.json.
 */
export class GameContextStore {
  private readonly validate;

  constructor(
    private readonly snapshotDirectory: SnapshotDirectory,
    private readonly now: () => Date = () => new Date(),
  ) {
    this.validate = compileContract(gameContextSchema);
  }

  /** This call's folder, resolved afresh so the server follows a folder the plugin moved. */
  private get directory(): string {
    return join(resolveDirectory(this.snapshotDirectory), CONTEXT_DIR);
  }

  /** The clock the store measures ages against, shared with the tools so message ages agree with file ages. */
  currentTime(): Date {
    return this.now();
  }

  /**
   * Loads one character's context.
   *
   * @param rsn character name, matched like the snapshot store matches it; omitted means the most recently
   *   written context
   */
  load(rsn?: string): GameContextLoadResult {
    const files = this.listByRecency();
    const chosen = rsn === undefined ? files[0] : files.find((file) => sameName(readRsn(file), rsn));
    if (chosen === undefined) {
      return { kind: "missing", directory: this.directory };
    }

    const data: unknown = JSON.parse(readFileSync(chosen, "utf8"));
    if (!this.validate(data)) {
      return { kind: "invalid", path: chosen, errors: JSON.stringify(this.validate.errors) };
    }
    const ageSeconds = Math.round((this.now().getTime() - statSync(chosen).mtime.getTime()) / 1000);
    return { kind: "found", context: data as GameContext, ageSeconds, path: chosen };
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
