/** One line of the player's own game feed. */
export interface GameMessage {
  /** ISO instant the message last arrived. */
  at: string;
  /** NPC or player who said a dialogue line; absent for other messages. */
  speaker?: string;
  text: string;
  /** Back-to-back arrivals of the same message. */
  repeats: number;
}

/** A shop item and its quantity in stock (0 when sold out). */
export interface ShopItem {
  id: number;
  name: string;
  qty: number;
}

/** The tracked interface the player has open; fields that do not apply to its kind are absent. */
export interface OpenInterface {
  kind: "shop" | "bank" | "grandExchange" | "dialogue";
  name?: string;
  stock?: ShopItem[];
  speaker?: string;
  text?: string;
  options?: string[];
}

/**
 * TypeScript view of `contract/game-context.schema.json`. The schema is authoritative and is enforced at load
 * time; this type only describes what a validated file looks like.
 */
export interface GameContext {
  schemaVersion: 1;
  rsn: string;
  capturedAt: string;
  /** Oldest first. */
  messages: GameMessage[];
  openInterface?: OpenInterface;
}

/**
 * The plugin rewrites the file every 30 seconds while an interface is open; a file older than twice that
 * means the client closed or logged out, so an interface it lists can no longer be trusted as open.
 */
export const STALE_AFTER_SECONDS = 60;

/** A message as Claude sees it: how long ago instead of a timestamp, and `repeats` only when it repeated. */
export interface RecentMessage {
  ageSeconds: number;
  speaker?: string;
  text: string;
  repeats?: number;
}

/**
 * The newest messages, oldest first so they read as a conversation. Ages come from each message's own time,
 * so they stay right however old the file is.
 */
export function recentMessages(
  context: GameContext,
  { limit, now }: { limit: number; now: Date },
): { totalMessages: number; messages: RecentMessage[] } {
  const newest = context.messages.slice(-limit);
  return {
    totalMessages: context.messages.length,
    messages: newest.map((message) => ({
      ageSeconds: Math.max(0, Math.round((now.getTime() - Date.parse(message.at)) / 1000)),
      ...(message.speaker === undefined ? {} : { speaker: message.speaker }),
      text: message.text,
      ...(message.repeats > 1 ? { repeats: message.repeats } : {}),
    })),
  };
}

export type OpenInterfaceView =
  | (OpenInterface & { ageSeconds: number })
  | { kind: "none"; ageSeconds: number }
  | { kind: "unknown"; lastSeen: OpenInterface["kind"]; ageSeconds: number; note: string };

/**
 * What the player has open, unless the file is too old to say. Only an open interface goes stale: the plugin
 * rewrites the file on every change, so "nothing open" stays true until something opens.
 *
 * @param fileAgeSeconds seconds since the plugin last wrote the file
 */
export function describeOpenInterface(context: GameContext, fileAgeSeconds: number): OpenInterfaceView {
  const open = context.openInterface;
  if (open === undefined) {
    return { kind: "none", ageSeconds: fileAgeSeconds };
  }
  if (fileAgeSeconds > STALE_AFTER_SECONDS) {
    return {
      kind: "unknown",
      lastSeen: open.kind,
      ageSeconds: fileAgeSeconds,
      note: "RuneLite has not confirmed this interface recently; the client may be closed or logged out.",
    };
  }
  return { ...open, ageSeconds: fileAgeSeconds };
}
