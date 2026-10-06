package com.wiseoldclaude.model;

import java.util.List;
import lombok.Value;

/** Combat achievement standing: total points and the tiers already completed. */
@Value
public class CombatAchievements
{
	/** {@code VarbitID.CA_POINTS}. */
	int points;

	/** Completed tier names, easiest first (EASY, MEDIUM, HARD, ELITE, MASTER, GRANDMASTER). */
	List<String> completedTiers;
}
