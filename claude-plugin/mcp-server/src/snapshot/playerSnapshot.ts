/**
 * TypeScript view of `contract/player-snapshot.schema.json`. The schema is authoritative and is enforced at
 * load time; this type only describes what a validated snapshot looks like.
 */
export interface PlayerSnapshot {
  schemaVersion: 1;
  rsn: string;
  accountType:
    | "NORMAL"
    | "IRONMAN"
    | "ULTIMATE_IRONMAN"
    | "HARDCORE_IRONMAN"
    | "GROUP_IRONMAN"
    | "HARDCORE_GROUP_IRONMAN"
    | "UNRANKED_GROUP_IRONMAN"
    | "UNKNOWN";
  capturedAt: string;
  world: number;
  combatLevel: number;
  /** Inventory coins plus coins in the last-seen bank. */
  coins: number;
  position: { x: number; y: number; plane: number; regionId: number };
  skills: Record<string, { level: number; xp: number; boosted: number }>;
  inventory: ItemStack[];
  equipment: Record<string, ItemStack>;
  /** Null until the bank has been opened at least once with the plugin running. */
  bank: Bank | null;
  quests: Record<string, QuestState>;
  /** Absent from older plugins; null until the plugin's first quest refresh. */
  questPoints?: number | null;
  /**
   * Fairytale II far enough along to use fairy rings; travel still needs a dramen/lunar staff unless the elite
   * Lumbridge & Draynor diary is done. Absent from older plugins; null until the plugin's first quest refresh.
   */
  fairyRingsUnlocked?: boolean | null;
  /** Absent from older plugins. */
  combat?: CombatState | null;
  /** Absent from older plugins. */
  status?: PlayerStatus | null;
  /** Game build the client runs; absent from older plugins. */
  gameRevision?: number | null;
  /** Slayer, diaries, combat achievements and session XP read from the client; absent from older plugins. */
  progress?: Progress | null;
  /** Rulesets of the current world, empty on a standard free-to-play world; absent from older plugins. */
  worldTypes?: WorldType[] | null;
  /** Other players in the loaded scene, a count only; absent from older plugins, null while no scene is loaded. */
  nearbyPlayers?: number | null;
  /** Occupied Grand Exchange slots; null for ironmen, absent from older plugins. */
  grandExchange?: ExchangeOffer[] | null;
  /** Fossil Island bird houses as last read there; null until seen on the island, absent from older plugins. */
  birdhouses?: Birdhouses | null;
  /** When the cooldown-limited teleports can next be cast; absent from older plugins. */
  teleportCooldowns?: TeleportCooldowns | null;
}

/** One occupied Grand Exchange slot. */
export interface ExchangeOffer {
  /** 1 to 8, as the game numbers the slots. */
  slot: number;
  itemId: number;
  itemName: string;
  side: "BUY" | "SELL";
  /** COMPLETE and CANCELLED offers still hold items or coins until collected. */
  status: "ACTIVE" | "COMPLETE" | "CANCELLED";
  pricePerItem: number;
  quantity: number;
  quantityTraded: number;
  /** Coins spent on a buy offer, or received for a sell offer, so far. */
  coinsTraded: number;
}

/** The bird house spaces as last read on Fossil Island, which can be sessions before the snapshot. */
export interface Birdhouses {
  checkedAt: string;
  spaces: BirdhouseSpace[];
}

export interface BirdhouseSpace {
  location: string;
  /** BUILT is a bird house without seeds, which catches nothing. */
  state: "EMPTY" | "BUILT" | "SEEDED" | "UNKNOWN";
  birdhouse: string | null;
  /** When a seeded house is full; null when it was seeded while the plugin was not watching. */
  readyAt: string | null;
}

/**
 * Ready times derived from the game's record of the last cast, so a past time means ready now. The cast is stored to
 * the minute, so a ready time can be up to a minute early.
 */
export interface TeleportCooldowns {
  /** 30 minutes after the last Home Teleport; null when never cast. */
  homeReadyAt: string | null;
  /** 20 minutes after the last minigame teleport; null when never cast. */
  minigameReadyAt: string | null;
}

/** World rulesets as the plugin names them; `pvp` and `bounty_hunter` match find_nearest's `worldType`. */
export type WorldType =
  | "members"
  | "pvp"
  | "bounty_hunter"
  | "pvp_arena"
  | "skill_total"
  | "quest_speedrunning"
  | "high_risk"
  | "last_man_standing"
  | "beta"
  | "no_save"
  | "tournament"
  | "fresh_start"
  | "deadman"
  | "seasonal";

export type DiaryTier = "EASY" | "MEDIUM" | "HARD" | "ELITE";

export type CombatAchievementTier = "EASY" | "MEDIUM" | "HARD" | "ELITE" | "MASTER" | "GRANDMASTER";

/** Progress read straight from the client, so it does not depend on WikiSync. */
export interface Progress {
  slayer: SlayerProgress;
  /** In-game region name to completed tiers; every region present, empty when no tier is complete. */
  diaries: Record<string, DiaryTier[]>;
  combatAchievements: { points: number; completedTiers: CombatAchievementTier[] };
  /** Null while the login stat sync is landing. */
  sessionXp: SessionXp | null;
}

export interface SlayerProgress {
  /** Null when no task is assigned. */
  task: { creature: string | null; remaining: number; location: string | null } | null;
  points: number;
  /** The current master's streak; Krystilia and Mortimer keep their own. */
  streak: number;
}

/** XP gained since login; a world hop keeps the session. */
export interface SessionXp {
  since: string;
  /** Skill name to XP gained; skills without gains are left out. */
  gained: Record<string, number>;
}

/** Combat setup and the current fight. */
export interface CombatState {
  /** Null when not interacting with anything, which is how a finished fight reads. */
  target: CombatTarget | null;
  /** In-game style name ("Aggressive", "Defensive Casting"); null when unknown. */
  attackStyle: string | null;
  spellbook: "STANDARD" | "ANCIENT" | "LUNAR" | "ARCEUUS" | null;
  activePrayers: string[];
  specialAttackPercent: number;
}

/**
 * What the player is interacting with. Another player is never named: `name`, `npcId` and `hpPercent` are
 * always null for one.
 */
export interface CombatTarget {
  kind: "NPC" | "PLAYER";
  name: string | null;
  /** 0 for non-combat NPCs. */
  combatLevel: number;
  npcId: number | null;
  /** Health bar fill, rounded up; null until the bar has been shown. */
  hpPercent: number | null;
}

/** Status that changes what the player should do next. */
export interface PlayerStatus {
  runEnergyPercent: number;
  /** Negative with enough weight-reducing gear. */
  weightKg: number;
  poison: "NONE" | "POISONED" | "ENVENOMED";
  /** Next poison or venom hit; null when not poisoned. */
  poisonDamage: number | null;
  diseased: boolean;
  /**
   * Effect name to seconds left as of `capturedAt`. Multi-tick countdowns (antifires, overload, stamina,
   * antipoison, antivenom) are an upper bound accurate to one step.
   */
  effects: Record<string, number>;
}

export interface ItemStack {
  id: number;
  name: string;
  qty: number;
}

/**
 * The bank as last seen. The game only exposes it while open, so `capturedAt` can be much older than the
 * snapshot itself and should be surfaced when advising from it.
 */
export interface Bank {
  capturedAt: string;
  items: ItemStack[];
}

export type QuestState = "NOT_STARTED" | "IN_PROGRESS" | "FINISHED";
