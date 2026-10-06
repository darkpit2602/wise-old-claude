import type {
  Birdhouses,
  CombatAchievementTier,
  CombatState,
  DiaryTier,
  ExchangeOffer,
  ItemStack,
  PlayerSnapshot,
  PlayerStatus,
  Progress,
  QuestState,
  TeleportCooldowns,
  WorldType,
} from "./playerSnapshot.js";

/** An item as Claude needs it: ids only matter to the plugin, names and quantities to the answer. */
export interface NamedStack {
  name: string;
  qty: number;
}

/** A skill, carrying `boosted` only when a potion or damage makes it differ from the real level. */
export interface SkillSummary {
  level: number;
  xp: number;
  boosted?: number;
}

/**
 * The character as returned by `get_player_snapshot`. The full snapshot (bank of hundreds of stacks, every
 * quest) exceeds Claude Code's inline tool-output limit, which forces a file read-back on every question;
 * this keeps what nearly every answer needs inline and leaves the bulk to on-demand tools.
 */
export interface SnapshotSummary {
  rsn: string;
  accountType: PlayerSnapshot["accountType"];
  combatLevel: number;
  world: number;
  capturedAt: string;
  coins: number;
  position: PlayerSnapshot["position"];
  skills: Record<string, SkillSummary>;
  inventory: NamedStack[];
  equipment: Record<string, NamedStack>;
  quests: QuestSummary;
  bank: { capturedAt: string; stacks: number } | null;
  combat?: CombatSummary;
  status?: StatusSummary;
  progress?: ProgressSummary;
  worldTypes?: WorldType[];
  nearbyPlayers?: number;
  grandExchange?: OfferSummary[];
  birdhouses?: BirdhouseSummary;
  teleportCooldowns?: TeleportCooldownSummary;
}

/** A Grand Exchange slot as the player reads it; the item id stays in the file. */
export type OfferSummary = Omit<ExchangeOffer, "itemId">;

/**
 * Bird houses with readiness worked out against the current time, since Claude cannot tell the time itself. A
 * seeded space without `readyAt` was seeded while the plugin was not watching, so its fill time is unknown.
 */
export interface BirdhouseSummary {
  checkedAt: string;
  spaces: {
    location: string;
    state: Birdhouses["spaces"][number]["state"];
    birdhouse?: string;
    readyAt?: string;
    ready?: boolean;
    minutesLeft?: number;
  }[];
}

/** Readiness of one cooldown-limited teleport; a teleport never cast is ready and carries no `readyAt`. */
export type TeleportReadiness = { readyAt?: string } & ({ ready: true } | { ready: false; minutesLeft: number });

/** Home Teleport and minigame teleport readiness against the current time, since Claude cannot tell the time itself. */
export interface TeleportCooldownSummary {
  home: TeleportReadiness;
  minigame: TeleportReadiness;
}

/**
 * Progress with everything not yet achieved left out, so a fresh account costs almost nothing and Claude reads
 * every present field as an achievement. Diary regions appear only once a tier is complete.
 */
export interface ProgressSummary {
  slayer?: SlayerSummary;
  diaries?: Record<string, DiaryTier[]>;
  combatAchievements?: { points: number; completedTiers?: CombatAchievementTier[] };
  sessionXp?: { since: string; gained?: Record<string, number> };
}

/** The slayer task without unknown parts; `task` is absent when none is assigned. */
export interface SlayerSummary {
  task?: { creature?: string; remaining: number; location?: string };
  points: number;
  streak: number;
}

/**
 * The fight and combat setup with every unknown or empty part left out, so an idle player costs one field and
 * Claude never reads a null as a fact. An NPC target keeps its name for wiki lookups; its id stays in the file.
 */
export interface CombatSummary {
  target?: { kind: "NPC" | "PLAYER"; name?: string; combatLevel: number; hpPercent?: number };
  attackStyle?: string;
  spellbook?: NonNullable<CombatState["spellbook"]>;
  activePrayers?: string[];
  specialAttackPercent: number;
}

/** Status with the healthy defaults (not poisoned, not diseased, no effects) left out. */
export interface StatusSummary {
  runEnergyPercent: number;
  weightKg: number;
  poison?: Exclude<PlayerStatus["poison"], "NONE">;
  poisonDamage?: number;
  diseased?: true;
  effects?: Record<string, number>;
}

/**
 * Quest progress reduced to counts plus the active quest names. `points` and `fairyRingsUnlocked` are absent
 * rather than null when the plugin has not reported them, so Claude never mistakes "unknown" for a real total
 * or a locked network. Fairy ring access lives here because it is quest progress (Fairytale II) read on the
 * same refresh, and one flag does not earn a separate object in the inline summary.
 */
export interface QuestSummary {
  points?: number;
  fairyRingsUnlocked?: boolean;
  finished: number;
  notStarted: number;
  inProgress: string[];
}

/** Bank search results per requested term; `capturedAt` is null when the bank has never been seen. */
export interface BankSearch {
  capturedAt: string | null;
  matches: Record<string, NamedStack[]>;
}

/**
 * Condenses a snapshot to what fits inline in a tool result.
 *
 * @param snapshot the validated snapshot
 * @param now the current time, which bird house and teleport readiness is measured against
 * @returns the summary, with bank and quest detail reduced to counts
 */
export function summariseSnapshot(snapshot: PlayerSnapshot, now: Date = new Date()): SnapshotSummary {
  return {
    rsn: snapshot.rsn,
    accountType: snapshot.accountType,
    combatLevel: snapshot.combatLevel,
    world: snapshot.world,
    capturedAt: snapshot.capturedAt,
    coins: snapshot.coins,
    position: snapshot.position,
    skills: Object.fromEntries(Object.entries(snapshot.skills).map(([name, skill]) => [name, summariseSkill(skill)])),
    inventory: snapshot.inventory.map(named),
    equipment: Object.fromEntries(Object.entries(snapshot.equipment).map(([slot, item]) => [slot, named(item)])),
    quests: summariseQuests(snapshot),
    bank: snapshot.bank === null ? null : { capturedAt: snapshot.bank.capturedAt, stacks: snapshot.bank.items.length },
    ...(isReported(snapshot.combat) ? { combat: summariseCombat(snapshot.combat) } : {}),
    ...(isReported(snapshot.status) ? { status: summariseStatus(snapshot.status) } : {}),
    ...(isReported(snapshot.progress) ? { progress: summariseProgress(snapshot.progress) } : {}),
    ...(isReported(snapshot.worldTypes) ? { worldTypes: snapshot.worldTypes } : {}),
    ...(isReported(snapshot.nearbyPlayers) ? { nearbyPlayers: snapshot.nearbyPlayers } : {}),
    ...(isReported(snapshot.grandExchange) ? { grandExchange: snapshot.grandExchange.map(summariseOffer) } : {}),
    ...summariseTimers(snapshot, now),
  };
}

/** The timers whose readiness depends on the current time: bird houses and teleport cooldowns. */
function summariseTimers(snapshot: PlayerSnapshot, now: Date): Pick<SnapshotSummary, "birdhouses" | "teleportCooldowns"> {
  return {
    ...(isReported(snapshot.birdhouses) ? { birdhouses: summariseBirdhouses(snapshot.birdhouses, now) } : {}),
    ...(isReported(snapshot.teleportCooldowns)
      ? { teleportCooldowns: summariseTeleportCooldowns(snapshot.teleportCooldowns, now) }
      : {}),
  };
}

/**
 * Finds banked items whose names contain each term, case-insensitively. Terms are answered separately so one
 * call can check a whole shopping list ("nettles", "gloves", "bucket").
 *
 * @param snapshot the validated snapshot
 * @param terms partial item names
 * @returns matches keyed by the term as given
 */
export function searchBank(snapshot: PlayerSnapshot, terms: readonly string[]): BankSearch {
  const bank = snapshot.bank;
  if (bank === null) {
    return { capturedAt: null, matches: {} };
  }
  const matches = Object.fromEntries(
    terms.map((term) => {
      const needle = term.trim().toLowerCase();
      return [term, bank.items.filter((item) => item.name.toLowerCase().includes(needle)).map(named)];
    }),
  );
  return { capturedAt: bank.capturedAt, matches };
}

/**
 * Names of quests in one state, alphabetically, so lists are stable and easy to scan.
 *
 * @param snapshot the validated snapshot
 * @param status the quest state to select
 */
export function questsByStatus(snapshot: PlayerSnapshot, status: QuestState): string[] {
  return Object.entries(snapshot.quests)
    .filter(([, state]) => state === status)
    .map(([name]) => name)
    .sort((a, b) => a.localeCompare(b));
}

function summariseQuests(snapshot: PlayerSnapshot): QuestSummary {
  const counts = {
    finished: questsByStatus(snapshot, "FINISHED").length,
    notStarted: questsByStatus(snapshot, "NOT_STARTED").length,
    inProgress: questsByStatus(snapshot, "IN_PROGRESS"),
  };
  const { questPoints: points, fairyRingsUnlocked } = snapshot;
  return {
    ...(isReported(points) ? { points } : {}),
    ...(isReported(fairyRingsUnlocked) ? { fairyRingsUnlocked } : {}),
    ...counts,
  };
}

function summariseCombat({ target, attackStyle, spellbook, activePrayers, specialAttackPercent }: CombatState): CombatSummary {
  return {
    ...(target === null ? {} : { target: summariseTarget(target) }),
    ...(attackStyle === null ? {} : { attackStyle }),
    ...(spellbook === null ? {} : { spellbook }),
    ...(activePrayers.length === 0 ? {} : { activePrayers }),
    specialAttackPercent,
  };
}

function summariseTarget({ kind, name, combatLevel, hpPercent }: NonNullable<CombatState["target"]>): CombatSummary["target"] {
  return {
    kind,
    ...(name === null ? {} : { name }),
    combatLevel,
    ...(hpPercent === null ? {} : { hpPercent }),
  };
}

function summariseStatus({ runEnergyPercent, weightKg, poison, poisonDamage, diseased, effects }: PlayerStatus): StatusSummary {
  return {
    runEnergyPercent,
    weightKg,
    ...(poison === "NONE" ? {} : { poison }),
    ...(poisonDamage === null ? {} : { poisonDamage }),
    ...(diseased ? { diseased } : {}),
    ...(Object.keys(effects).length === 0 ? {} : { effects }),
  };
}

function summariseProgress({ slayer, diaries, combatAchievements, sessionXp }: Progress): ProgressSummary {
  const completedDiaries = Object.fromEntries(Object.entries(diaries).filter(([, tiers]) => tiers.length > 0));
  const { points, completedTiers } = combatAchievements;
  const hasSlayerProgress = slayer.task !== null || slayer.points > 0 || slayer.streak > 0;
  return {
    ...(hasSlayerProgress ? { slayer: summariseSlayer(slayer) } : {}),
    ...(Object.keys(completedDiaries).length === 0 ? {} : { diaries: completedDiaries }),
    ...(points === 0 && completedTiers.length === 0
      ? {}
      : { combatAchievements: { points, ...(completedTiers.length === 0 ? {} : { completedTiers }) } }),
    ...(sessionXp === null ? {} : { sessionXp: summariseSessionXp(sessionXp) }),
  };
}

function summariseSlayer({ task, points, streak }: Progress["slayer"]): SlayerSummary {
  if (task === null) {
    return { points, streak };
  }
  const { creature, remaining, location } = task;
  return {
    task: { ...(creature === null ? {} : { creature }), remaining, ...(location === null ? {} : { location }) },
    points,
    streak,
  };
}

function summariseOffer({ itemId: _itemId, ...offer }: ExchangeOffer): OfferSummary {
  return offer;
}

function summariseBirdhouses({ checkedAt, spaces }: Birdhouses, now: Date): BirdhouseSummary {
  return {
    checkedAt,
    spaces: spaces.map(({ location, state, birdhouse, readyAt }) => ({
      location,
      state,
      ...(birdhouse === null ? {} : { birdhouse }),
      ...(readyAt === null ? {} : { readyAt, ...readiness(new Date(readyAt), now) }),
    })),
  };
}

function summariseTeleportCooldowns({ homeReadyAt, minigameReadyAt }: TeleportCooldowns, now: Date): TeleportCooldownSummary {
  return { home: teleportReadiness(homeReadyAt, now), minigame: teleportReadiness(minigameReadyAt, now) };
}

function teleportReadiness(readyAt: string | null, now: Date): TeleportReadiness {
  return readyAt === null ? { ready: true } : { readyAt, ...readiness(new Date(readyAt), now) };
}

function readiness(readyAt: Date, now: Date): { ready: true } | { ready: false; minutesLeft: number } {
  const msLeft = readyAt.getTime() - now.getTime();
  return msLeft <= 0 ? { ready: true } : { ready: false, minutesLeft: Math.ceil(msLeft / 60_000) };
}

function summariseSessionXp({ since, gained }: NonNullable<Progress["sessionXp"]>): ProgressSummary["sessionXp"] {
  return { since, ...(Object.keys(gained).length === 0 ? {} : { gained }) };
}

/**
 * Whether an optional snapshot field carries a real value: absent means an older plugin, null means not yet
 * read, and neither may reach Claude as if it were a fact.
 */
function isReported<T>(value: T | null | undefined): value is T {
  return value !== undefined && value !== null;
}

function summariseSkill({ level, xp, boosted }: PlayerSnapshot["skills"][string]): SkillSummary {
  return boosted === level ? { level, xp } : { level, xp, boosted };
}

function named({ name, qty }: ItemStack): NamedStack {
  return { name, qty };
}
