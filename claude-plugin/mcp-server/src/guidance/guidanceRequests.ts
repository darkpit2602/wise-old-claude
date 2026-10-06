import { existsSync, mkdirSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { resolveDirectory, type SnapshotDirectory } from "../snapshot/snapshotDirectory.js";

/** A tile to guide the player to, with an optional name the plugin shows in the game chat. */
export interface GuidanceTarget {
  x: number;
  y: number;
  plane: number;
  label?: string;
}

/**
 * Subdirectory of the snapshot directory holding the request. A sibling `*.json` would be listed by the
 * snapshot store as a character export (and could collide with a character's sanitised name), so the
 * request lives one level down where neither side's snapshot handling looks.
 */
const GUIDANCE_DIR = "guidance";
const REQUEST_FILE = "request.json";

/**
 * Hands guidance requests to the RuneLite plugin through the shared snapshot directory, mirroring how the
 * plugin hands snapshots to the server. The server is the file's only writer: the plugin only reads it, so
 * no locking is needed, and deleting it is how guidance is withdrawn. Shape: contract/guidance-request.schema.json.
 */
export class GuidanceRequests {

  constructor(
    private readonly snapshotDirectory: SnapshotDirectory,
    private readonly now: () => Date = () => new Date(),
  ) {}

  /** This call's folder, resolved afresh so the server follows a folder the plugin moved. */
  private get directory(): string {
    return join(resolveDirectory(this.snapshotDirectory), GUIDANCE_DIR);
  }

  /**
   * Replaces any current request with one for this target. Written to a temp file and renamed into place so
   * the plugin, polling on the game tick, never parses a half-written file.
   *
   * @returns path of the request file
   */
  request({ x, y, plane, label }: GuidanceTarget): string {
    const directory = this.directory;
    mkdirSync(directory, { recursive: true });
    const body = {
      schemaVersion: 1,
      target: { x, y, plane },
      ...(label === undefined ? {} : { label }),
      requestedAt: this.now().toISOString(),
    };
    const target = join(directory, REQUEST_FILE);
    const temp = join(directory, `.request-${process.pid}-${Date.now()}.tmp`);
    try {
      writeFileSync(temp, JSON.stringify(body, null, 2));
      renameSync(temp, target);
    } finally {
      rmSync(temp, { force: true });
    }
    return target;
  }

  /**
   * Withdraws the current request; the plugin clears the route and hint arrow on its next poll.
   *
   * @returns whether a request existed to withdraw
   */
  clear(): boolean {
    const path = this.requestPath();
    if (!existsSync(path)) {
      return false;
    }
    rmSync(path, { force: true });
    return true;
  }

  private requestPath(): string {
    return join(this.directory, REQUEST_FILE);
  }
}
