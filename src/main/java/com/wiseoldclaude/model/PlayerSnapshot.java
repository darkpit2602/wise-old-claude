package com.wiseoldclaude.model;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import net.runelite.api.QuestState;
import lombok.Value;

/**
 * Point-in-time view of the logged-in character, serialized verbatim to the file the MCP server reads.
 * Field names and shape are shared with the MCP server; change both together
 * and bump {@link #SCHEMA_VERSION} on any breaking change.
 */
@Value
@Builder(toBuilder = true)
public class PlayerSnapshot
{
	public static final int SCHEMA_VERSION = 1;

	@Builder.Default
	int schemaVersion = SCHEMA_VERSION;
	String rsn;
	AccountType accountType;
	String capturedAt;
	int world;
	int combatLevel;
	long coins;
	Position position;
	Map<String, SkillLevel> skills;
	List<ItemStack> inventory;
	Map<String, ItemStack> equipment;
	Bank bank;
	Map<String, QuestState> quests;

	/**
	 * Total quest points. Boxed so "not yet read" is written as null rather than a misleading 0, which is a
	 * real total for a fresh account.
	 */
	Integer questPoints;

	/**
	 * Whether Fairytale II has progressed far enough to use fairy rings, refreshed alongside quests. Boxed so
	 * "not yet read" is written as null rather than a false "locked".
	 */
	Boolean fairyRingsUnlocked;

	/** Current target, attack style, spellbook, prayers and special attack energy. */
	CombatState combat;

	/** Run energy, weight, poison, disease and timed effects. */
	PlayerStatus status;

	/**
	 * The game build the client runs ({@code Client#getRevision}), so the MCP server can tell when its bundled
	 * game data predates the game the player is on.
	 */
	Integer gameRevision;

	/** Slayer task, diaries, combat achievements and session XP, read from the client on every write. */
	Progress progress;

	/**
	 * The current world's rulesets ({@code members}, {@code pvp}, {@code bounty_hunter}, {@code deadman}, ...),
	 * empty on a standard free-to-play world; see {@code WorldTypes}.
	 */
	List<String> worldTypes;

	/**
	 * Other players in the loaded scene. Only the count leaves the client: never names, gear or positions.
	 * Boxed so a missing scene is written as null rather than a misleading "alone".
	 */
	Integer nearbyPlayers;
	/**
	 * Occupied Grand Exchange slots, in slot order. Null for ironmen, who cannot trade, so their snapshots carry
	 * nothing they could not act on.
	 */
	List<ExchangeOffer> grandExchange;
	/** Fossil Island bird houses as last seen; null until the character has been seen on the island. */
	Birdhouses birdhouses;
	/** When Home Teleport and the minigame teleport can next be cast. */
	TeleportCooldowns teleportCooldowns;
}
