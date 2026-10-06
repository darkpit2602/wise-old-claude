import { existsSync, mkdirSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { resolveDirectory, type SnapshotDirectory } from "../snapshot/snapshotDirectory.js";

/** How often a running server refreshes its status, so the plugin can tell "running now" from "set up once". */
export const BEACON_INTERVAL_MS = 60_000;

/** Kept in a subfolder, like guidance/, so the snapshot store never lists it as a character export. */
const COMPANION_DIR = "companion";
const STATUS_FILE = "status.json";

/**
 * Tells the RuneLite plugin that the Claude Code side exists and is running, by writing companion/status.json into
 * the plugin's folder. The plugin cannot look for Claude Code itself (Plugin Hub plugins only read their own folder),
 * so this file is how it knows to stop pointing the player at the setup page. Shape:
 * contract/companion-status.schema.json.
 */
export class CompanionBeacon {
  constructor(
    private readonly snapshotDirectory: SnapshotDirectory,
    private readonly serverVersion: string,
    private readonly now: () => Date = () => new Date(),
  ) {}

  /**
   * Writes the status, atomically so the plugin never reads half a file. Only when the plugin's folder already
   * exists: creating it early would stop RuneLite from moving an old plugin folder into place on first run.
   *
   * @returns whether the status was written
   */
  touch(): boolean {
    const base = resolveDirectory(this.snapshotDirectory);
    if (!existsSync(base)) {
      return false;
    }
    const directory = join(base, COMPANION_DIR);
    mkdirSync(directory, { recursive: true });
    const body = { schemaVersion: 1, lastSeen: this.now().toISOString(), serverVersion: this.serverVersion };
    const temp = join(directory, `.status-${process.pid}-${Date.now()}.tmp`);
    try {
      writeFileSync(temp, JSON.stringify(body, null, 2));
      renameSync(temp, join(directory, STATUS_FILE));
    } finally {
      rmSync(temp, { force: true });
    }
    return true;
  }
}
