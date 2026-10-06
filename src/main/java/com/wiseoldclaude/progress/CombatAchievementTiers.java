package com.wiseoldclaude.progress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarbitID;

/**
 * Decodes which combat achievement tiers are complete from the game's per-tier status varbits. A status of 2 is
 * what RuneLite's timers and buffs plugin treats as a completed tier (the Master tier's doubled thrall duration);
 * other values are not reported as complete. Per-tier task counts are not decoded: the per-task
 * {@code CA_TASK_*_COMPLETED} varbits do not say which tier a task belongs to without cache data.
 */
public final class CombatAchievementTiers
{
	private static final int COMPLETE = 2;

	private static final Map<String, Integer> TIER_STATUS = new LinkedHashMap<>();

	static
	{
		TIER_STATUS.put(TierNames.EASY, VarbitID.CA_TIER_STATUS_EASY);
		TIER_STATUS.put(TierNames.MEDIUM, VarbitID.CA_TIER_STATUS_MEDIUM);
		TIER_STATUS.put(TierNames.HARD, VarbitID.CA_TIER_STATUS_HARD);
		TIER_STATUS.put(TierNames.ELITE, VarbitID.CA_TIER_STATUS_ELITE);
		TIER_STATUS.put(TierNames.MASTER, VarbitID.CA_TIER_STATUS_MASTER);
		TIER_STATUS.put(TierNames.GRANDMASTER, VarbitID.CA_TIER_STATUS_GRANDMASTER);
	}

	private CombatAchievementTiers()
	{
	}

	/**
	 * Lists the completed tiers.
	 *
	 * @param varbits reads a varbit's current value, {@code Client#getVarbitValue} in the live client
	 * @return completed tier names, easiest first
	 */
	public static List<String> completed(IntUnaryOperator varbits)
	{
		List<String> completed = new ArrayList<>();
		for (Map.Entry<String, Integer> tier : TIER_STATUS.entrySet())
		{
			if (varbits.applyAsInt(tier.getValue()) == COMPLETE)
			{
				completed.add(tier.getKey());
			}
		}
		return completed;
	}
}
