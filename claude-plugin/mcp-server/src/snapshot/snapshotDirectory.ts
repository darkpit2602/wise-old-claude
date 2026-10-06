import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

/** Where the RuneLite plugin writes: RuneLite's per-plugin data folder, named after the plugin's internal name. */
export const DEFAULT_SNAPSHOT_DIR = join(homedir(), ".runelite", "plugin-data", "wise-old-claude");

/** Where plugin versions before the Filepath migration wrote; the updated plugin moves it to the default once. */
export const LEGACY_SNAPSHOT_DIR = join(homedir(), ".runelite", "wise-old-claude");

/**
 * The directory every store reads from and the guidance writer writes to: a fixed path, or a function asked
 * again on every call so a folder the plugin moves while the server runs is followed without a restart.
 */
export type SnapshotDirectory = string | (() => string);

/**
 * Prefers the current folder and falls back to the legacy one only while the plugin has not moved it yet.
 * When neither exists the current folder wins, so a fresh install never creates the legacy folder.
 */
export function transitionalDirectory(current: string, legacy: string): () => string {
  return () => (existsSync(current) || !existsSync(legacy) ? current : legacy);
}

/** The directory to use for this call. */
export function resolveDirectory(directory: SnapshotDirectory): string {
  return typeof directory === "string" ? directory : directory();
}
