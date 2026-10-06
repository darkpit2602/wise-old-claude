package com.wiseoldclaude.model;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Progress read straight from the client, so advice about slayer, diaries and combat achievements does not depend
 * on the player having synced WikiSync.
 */
@Value
@Builder(toBuilder = true)
public class Progress
{
	SlayerProgress slayer;

	/**
	 * In-game diary region name to its completed tiers (EASY, MEDIUM, HARD, ELITE); every region is present, with
	 * an empty list when no tier is complete.
	 */
	Map<String, List<String>> diaries;

	CombatAchievements combatAchievements;

	/** Null while the login stat sync is still landing. */
	SessionXp sessionXp;
}
