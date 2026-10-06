import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { GameContext } from "../context/gameContext.js";
import type { GameContextStore } from "../context/gameContextStore.js";
import type { PlayerSnapshot } from "../snapshot/playerSnapshot.js";
import type { SnapshotStore } from "../snapshot/snapshotStore.js";
import { contractMismatch, failure, json } from "../toolResults.js";
import { arrivedAt, type Evidence, inventoryHas, messageArrived } from "./waitConditions.js";

/**
 * The longest single wait. Claude Code aborts a stdio tool call that stays silent for 30 minutes, and it may not ask
 * for progress notifications, so a wait ends before that and Claude calls again if the user still wants it.
 */
export const MAX_WAIT_MINUTES = 25;

/** How often the plugin's files are re-read; the plugin itself writes at most every few game ticks. */
export const POLL_MS = 2000;

/** How often a waiting call reports progress, when the client asked for it, so the idle timer never runs out. */
const PROGRESS_EVERY_MS = 30_000;

/** Time as the wait sees it; injected so tests can run a long wait without sleeping. */
export interface WaitClock {
  now(): Date;
  /** Resolves after the delay, or early once the signal aborts. */
  sleep(ms: number, signal: AbortSignal): Promise<void>;
}

export const realClock: WaitClock = {
  now: () => new Date(),
  sleep: (ms, signal) =>
    new Promise((resolve) => {
      const timer = setTimeout(resolve, ms);
      signal.addEventListener("abort", () => (clearTimeout(timer), resolve()), { once: true });
    }),
};

/** Collaborators the wait tool needs; injected so tests can point them at a temp directory and a fake clock. */
export interface EventToolDependencies {
  store: SnapshotStore;
  context: GameContextStore;
  clock?: WaitClock;
}

const CONDITIONS = ["inventory_has", "arrived_at", "message_contains"] as const;

const inputSchema = {
  until: z.enum(CONDITIONS).describe("What to wait for"),
  item: z.string().min(1).optional().describe("inventory_has: partial item name, e.g. \"iron bar\""),
  quantity: z.number().int().min(1).optional().describe("inventory_has: how many to carry in total, default 1"),
  x: z.number().int().optional().describe("arrived_at: destination tile x"),
  y: z.number().int().optional().describe("arrived_at: destination tile y"),
  plane: z.number().int().min(0).max(3).optional().describe("arrived_at: required plane; any when omitted"),
  within: z.number().int().min(0).max(30).optional().describe("arrived_at: tiles away that still count, default 2"),
  text: z.string().min(1).optional().describe("message_contains: part of a game message, e.g. \"You smelt\""),
  timeoutMinutes: z.number().int().min(1).max(MAX_WAIT_MINUTES).optional().describe(`Give up after this long, default ${MAX_WAIT_MINUTES}`),
};

type WaitArgs = z.infer<z.ZodObject<typeof inputSchema>>;

type Check = { kind: "met"; evidence: Record<string, unknown> } | { kind: "waiting" } | { kind: "failed"; result: ReturnType<typeof failure> };

/**
 * Registers `wait_for`, which lets Claude act on something the player does later ("when the iron bar is in my
 * inventory, guide me to a furnace") without the user prompting again. The call returns once, when the condition
 * holds or the wait times out; Claude Code moves a call that runs past two minutes into the background and wakes
 * Claude with its result, so the conversation stays usable and only the event Claude asked for ever reaches it.
 */
export function registerEventTools(server: McpServer, { store, context, clock = realClock }: EventToolDependencies): void {
  server.registerTool(
    "wait_for",
    {
      title: "Wait for something in game",
      description:
        "Waits until the player does something, then returns once with what happened: an item in the inventory " +
        "(until: inventory_has, item, optional quantity), arriving near a tile (until: arrived_at, x, y, optional plane " +
        "and within) or a new game message (until: message_contains, text; only messages after the call count). Use it " +
        "when the user asks to act once something happens. Returns at once when already true. Long waits run in the " +
        `background; after timeoutMinutes (max ${MAX_WAIT_MINUTES}) it returns outcome timed_out and can be called again.`,
      inputSchema,
      annotations: { readOnlyHint: true },
    },
    async (args, extra) => {
      const started = clock.now();
      const check = checkFor(args, { store, context }, started);
      if (typeof check === "string") {
        return failure(check);
      }
      const progressToken = extra._meta?.progressToken;
      const report =
        progressToken === undefined
          ? undefined
          : (elapsedMs: number) =>
              extra.sendNotification({
                method: "notifications/progress",
                params: { progressToken, progress: Math.round(elapsedMs / 1000), message: `waiting for ${args.until}` },
              });
      return waitUntil(check, { clock, signal: extra.signal, started, timeoutMinutes: args.timeoutMinutes ?? MAX_WAIT_MINUTES, report });
    },
  );
}

/**
 * Builds the check for the requested condition, or says which argument is missing.
 *
 * @param since when the wait began, the earliest message that counts
 */
function checkFor(args: WaitArgs, { store, context }: Omit<EventToolDependencies, "clock">, since: Date): (() => Check) | string {
  const rsn = currentRsn(store);
  switch (args.until) {
    case "inventory_has": {
      const { item, quantity } = args;
      return item === undefined ? "inventory_has needs item" : snapshotCheck(store, rsn, (snapshot) => inventoryHas(snapshot, { item, quantity }));
    }
    case "arrived_at": {
      const { x, y, plane, within } = args;
      if (x === undefined || y === undefined) {
        return "arrived_at needs x and y";
      }
      return snapshotCheck(store, rsn, (snapshot) => arrivedAt(snapshot.position, { x, y, plane, within }));
    }
    case "message_contains": {
      const { text } = args;
      return text === undefined
        ? "message_contains needs text"
        : contextCheck(context, rsn, (loaded) => messageArrived(loaded, text, since));
    }
  }
}

/** The character to follow for the whole wait, so a second account's files never end it. */
function currentRsn(store: SnapshotStore): string | undefined {
  const loaded = store.load();
  return loaded.kind === "found" ? loaded.snapshot.rsn : undefined;
}

function snapshotCheck(
  store: SnapshotStore,
  rsn: string | undefined,
  holds: (snapshot: PlayerSnapshot) => Evidence,
): () => Check {
  return () => {
    const loaded = store.load(rsn);
    if (loaded.kind === "missing") {
      return { kind: "failed", result: failure(`No snapshot in ${loaded.directory}. Is RuneLite running with the Wise Old Claude plugin enabled?`) };
    }
    if (loaded.kind === "invalid") {
      return { kind: "failed", result: contractMismatch("Snapshot", loaded) };
    }
    const evidence = holds(loaded.snapshot);
    return evidence === undefined ? { kind: "waiting" } : { kind: "met", evidence };
  };
}

function contextCheck(context: GameContextStore, rsn: string | undefined, holds: (context: GameContext) => Evidence): () => Check {
  return () => {
    const loaded = context.load(rsn);
    if (loaded.kind === "missing") {
      return { kind: "failed", result: failure("No game context found. It is written by the Wise Old Claude plugin while logged in.") };
    }
    if (loaded.kind === "invalid") {
      return { kind: "failed", result: contractMismatch("Game context", loaded) };
    }
    const evidence = holds(loaded.context);
    return evidence === undefined ? { kind: "waiting" } : { kind: "met", evidence };
  };
}

interface WaitOptions {
  clock: WaitClock;
  signal: AbortSignal;
  started: Date;
  timeoutMinutes: number;
  report?: (elapsedMs: number) => Promise<void>;
}

/** Re-checks every poll until the condition holds, the wait times out or the client cancels the call. */
async function waitUntil(check: () => Check, { clock, signal, started, timeoutMinutes, report }: WaitOptions) {
  const deadline = started.getTime() + timeoutMinutes * 60_000;
  let reported = started.getTime();
  for (let polls = 0; ; polls++) {
    const result = check();
    const elapsedMs = clock.now().getTime() - started.getTime();
    if (result.kind === "failed") {
      return result.result;
    }
    if (result.kind === "met") {
      return json({ outcome: "met", alreadyMet: polls === 0, waitedSeconds: Math.round(elapsedMs / 1000), ...result.evidence });
    }
    if (clock.now().getTime() >= deadline) {
      return json({ outcome: "timed_out", waitedSeconds: Math.round(elapsedMs / 1000) });
    }
    await clock.sleep(POLL_MS, signal);
    if (signal.aborted) {
      return failure("Wait cancelled.");
    }
    if (report !== undefined && clock.now().getTime() - reported >= PROGRESS_EVERY_MS) {
      reported = clock.now().getTime();
      await report(reported - started.getTime());
    }
  }
}
