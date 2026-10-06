#!/usr/bin/env node
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { BEACON_INTERVAL_MS, CompanionBeacon } from "./companion/companionBeacon.js";
import { createExternalDataDependencies } from "./external/tools.js";
import { GuidanceRequests } from "./guidance/guidanceRequests.js";
import { createBundledAreaResolver } from "./location/areaResolver.js";
import { GameContextStore } from "./context/gameContextStore.js";
import { GroundItemsStore } from "./groundItems/groundItemsStore.js";
import { NearbyNpcsStore } from "./npcs/nearbyNpcsStore.js";
import { NearbyObjectsStore } from "./objects/nearbyObjectsStore.js";
import { QuestProgressStore } from "./quests/questProgressStore.js";
import { createServer } from "./server.js";
import {
  DEFAULT_SNAPSHOT_DIR,
  LEGACY_SNAPSHOT_DIR,
  type SnapshotDirectory,
  transitionalDirectory,
} from "./snapshot/snapshotDirectory.js";
import { SnapshotStore } from "./snapshot/snapshotStore.js";
import { SERVER_VERSION } from "./version.js";
import { WikiClient } from "./wiki/wikiClient.js";

const snapshotDir: SnapshotDirectory =
  process.env.WOC_SNAPSHOT_DIR ?? transitionalDirectory(DEFAULT_SNAPSHOT_DIR, LEGACY_SNAPSHOT_DIR);
const store = new SnapshotStore(snapshotDir);

const currentPlayer = () => {
  const result = store.load();
  return result.kind === "found" ? { rsn: result.snapshot.rsn, accountType: result.snapshot.accountType } : undefined;
};

const server = createServer({
  store,
  guidance: new GuidanceRequests(snapshotDir),
  objects: new NearbyObjectsStore(snapshotDir),
  npcs: new NearbyNpcsStore(snapshotDir),
  groundItems: new GroundItemsStore(snapshotDir),
  context: new GameContextStore(snapshotDir),
  questProgress: new QuestProgressStore(snapshotDir),
  wiki: new WikiClient(),
  resolver: createBundledAreaResolver(),
  external: createExternalDataDependencies({ currentPlayer }),
});

await server.connect(new StdioServerTransport());

const beacon = new CompanionBeacon(snapshotDir, SERVER_VERSION);

/**
 * Tells the RuneLite plugin the Claude side is running. A failed write must never take the server down, so the error
 * is dropped: the plugin then simply keeps showing its setup hint.
 */
function announce(): void {
  try {
    beacon.touch();
  } catch {
    return;
  }
}

announce();
setInterval(announce, BEACON_INTERVAL_MS).unref();
