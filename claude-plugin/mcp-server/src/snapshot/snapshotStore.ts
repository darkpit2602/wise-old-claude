import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { compileContract } from "../contractValidator.js";
import { DEFAULT_SNAPSHOT_DIR, resolveDirectory, type SnapshotDirectory } from "./snapshotDirectory.js";
import type { PlayerSnapshot } from "./playerSnapshot.js";
import { playerSnapshotSchema } from "./playerSnapshot.schema.js";

export type LoadResult =
  | { kind: "found"; snapshot: PlayerSnapshot; ageSeconds: number; path: string }
  | { kind: "missing"; directory: string; available: string[] }
  | { kind: "invalid"; path: string; errors: string };

/**
 * Reads character snapshots exported by the RuneLite plugin. Every file is validated against the shared
 * contract so a plugin/server version mismatch surfaces as a clear error instead of a wrong answer.
 */
export class SnapshotStore {
  private readonly validate;

  constructor(
    private readonly snapshotDirectory: SnapshotDirectory = DEFAULT_SNAPSHOT_DIR,
    private readonly now: () => Date = () => new Date(),
  ) {
    this.validate = compileContract(playerSnapshotSchema);
  }

  /** This call's folder, resolved afresh so the server follows a folder the plugin moved. */
  private get directory(): string {
    return resolveDirectory(this.snapshotDirectory);
  }

  /**
   * Loads one character's snapshot.
   *
   * @param rsn character name, matched case- and separator-insensitively; omitted means the most recently
   *   written snapshot, i.e. the character last played
   */
  load(rsn?: string): LoadResult {
    const files = this.listByRecency();
    const chosen = rsn === undefined ? files[0] : files.find((f) => sameName(readRsn(f), rsn));
    if (chosen === undefined) {
      return { kind: "missing", directory: this.directory, available: files.map(readRsn) };
    }

    const data: unknown = JSON.parse(readFileSync(chosen, "utf8"));
    if (!this.validate(data)) {
      return { kind: "invalid", path: chosen, errors: JSON.stringify(this.validate.errors) };
    }
    const ageSeconds = Math.round((this.now().getTime() - statSync(chosen).mtime.getTime()) / 1000);
    return { kind: "found", snapshot: data as PlayerSnapshot, ageSeconds, path: chosen };
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

/** The `rsn` a plugin-written file declares, or "" when unreadable; shared by every per-character file reader. */
export function readRsn(path: string): string {
  try {
    const rsn = (JSON.parse(readFileSync(path, "utf8")) as { rsn?: unknown }).rsn;
    return typeof rsn === "string" ? rsn : "";
  } catch {
    return "";
  }
}

/** Whether two character names refer to the same character, as players type them loosely. */
export function sameName(a: string, b: string): boolean {
  const normalize = (s: string) => s.trim().toLowerCase().replace(/[\s_-]+/g, " ");
  return normalize(a) === normalize(b);
}
