import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { compileContract } from "../contractValidator.js";
import { resolveDirectory, type SnapshotDirectory } from "../snapshot/snapshotDirectory.js";
import { readRsn, sameName } from "../snapshot/snapshotStore.js";
import type { NearbyNpcs } from "./nearbyNpcs.js";
import { nearbyNpcsSchema } from "./nearbyNpcs.schema.js";

/**
 * Subdirectory of the snapshot directory the plugin writes NPC indexes to. One level down so the snapshot
 * store's listing of `*.json` characters never mistakes an index for a character.
 */
const NPCS_DIR = "npcs";

export type NpcIndexLoadResult =
  | { kind: "found"; index: NearbyNpcs; ageSeconds: number; path: string }
  | { kind: "missing"; directory: string }
  | { kind: "invalid"; path: string; errors: string };

/**
 * Reads the nearby-NPC indexes the RuneLite plugin writes, one per character. Every file is validated
 * against the shared contract so a plugin/server version mismatch surfaces as a clear error instead of a
 * wrong answer. Shape: contract/nearby-npcs.schema.json.
 */
export class NearbyNpcsStore {
  private readonly validate;

  constructor(
    private readonly snapshotDirectory: SnapshotDirectory,
    private readonly now: () => Date = () => new Date(),
  ) {
    this.validate = compileContract(nearbyNpcsSchema);
  }

  /** This call's folder, resolved afresh so the server follows a folder the plugin moved. */
  private get directory(): string {
    return join(resolveDirectory(this.snapshotDirectory), NPCS_DIR);
  }

  /**
   * Loads one character's index.
   *
   * @param rsn character name, matched like the snapshot store matches it; omitted means the most recently
   *   written index
   */
  load(rsn?: string): NpcIndexLoadResult {
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
    return { kind: "found", index: data as NearbyNpcs, ageSeconds, path: chosen };
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
