package com.wiseoldclaude.progress;

import java.util.Locale;

/**
 * Rules for reading the slayer task varps, as RuneLite's {@code SlayerPlugin#updateTask} applies them. Kept apart
 * from the database lookups so the decisions are testable without a client.
 */
public final class SlayerTasks
{
	/**
	 * {@code VarPlayerID.SLAYER_TARGET} value meaning "a boss task": the boss itself is named by
	 * {@code VarbitID.SLAYER_TARGET_BOSSID} through the slayer task sublist table (cs2
	 * {@code [proc,helper_slayer_current_assignment]}, cited by RuneLite's slayer plugin).
	 */
	private static final int BOSS_TASK = 98;

	private SlayerTasks()
	{
	}

	/**
	 * Whether the player has a task: the game keeps the target varp after a task ends, so only kills left count.
	 *
	 * @param remaining {@code VarPlayerID.SLAYER_COUNT}
	 * @return true while kills remain
	 */
	public static boolean isAssigned(int remaining)
	{
		return remaining > 0;
	}

	/**
	 * Whether the target must be resolved through the boss sublist rather than the task table.
	 *
	 * @param targetId {@code VarPlayerID.SLAYER_TARGET}
	 * @return true for a boss task
	 */
	public static boolean isBossTask(int targetId)
	{
		return targetId == BOSS_TASK;
	}

	/**
	 * Whether the task is restricted to an area (Konar's tasks); 0 means anywhere.
	 *
	 * @param areaId {@code VarPlayerID.SLAYER_AREA}
	 * @return true when an area applies
	 */
	public static boolean hasArea(int areaId)
	{
		return areaId > 0;
	}

	/**
	 * Capitalises the cache's task name the way the slayer plugin's infobox shows it.
	 *
	 * @param cacheName the slayer task table's name column
	 * @return the display name, or null when the cache gave none
	 */
	public static String displayName(String cacheName)
	{
		if (cacheName == null || cacheName.isEmpty())
		{
			return null;
		}
		return cacheName.substring(0, 1).toUpperCase(Locale.ROOT) + cacheName.substring(1);
	}
}
