package com.wiseoldclaude.progress;

import java.util.Set;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Decides which variable changes make the snapshot's progress stale. A new slayer task or a task kill earns no
 * XP when it changes the count alone, and points change at a master without moving the player, so without these
 * the snapshot would keep the old task until the player next moved. Diary and combat achievement tiers are not
 * watched: they complete rarely, alongside a kill or task that already causes a write.
 */
public final class ProgressVarWatch
{
	private static final Set<Integer> VARPS = Set.of(
		VarPlayerID.SLAYER_TARGET,
		VarPlayerID.SLAYER_COUNT,
		VarPlayerID.SLAYER_AREA,
		VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);

	private static final Set<Integer> VARBITS = Set.of(
		VarbitID.SLAYER_TARGET_BOSSID,
		VarbitID.SLAYER_POINTS,
		VarbitID.SLAYER_TASKS_COMPLETED,
		VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED,
		VarbitID.CA_POINTS);

	private ProgressVarWatch()
	{
	}

	/**
	 * Whether a {@code VarbitChanged} event touches exported progress.
	 *
	 * @param varpId the changed varp
	 * @param varbitId the changed varbit, -1 when the event is for a whole varp
	 * @return true when the snapshot should be rewritten
	 */
	public static boolean affectsSnapshot(int varpId, int varbitId)
	{
		return VARPS.contains(varpId) || VARBITS.contains(varbitId);
	}
}
