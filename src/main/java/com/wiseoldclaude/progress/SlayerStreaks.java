package com.wiseoldclaude.progress;

/**
 * The game keeps separate task streaks for Krystilia's wilderness tasks and Mortimer's tasks; the one that counts
 * is the current master's, as RuneLite's slayer plugin picks it for its task tooltip.
 */
public class SlayerStreaks
{
	/** {@code VarbitID.SLAYER_MASTER} value for Krystilia, from RuneLite's {@code SlayerPlugin}. */
	private static final int KRYSTILIA = 7;

	/** {@code VarbitID.SLAYER_MASTER} value for Mortimer, from RuneLite's {@code SlayerPlugin}. */
	private static final int MORTIMER = 10;

	private final int standard;
	private final int wilderness;
	private final int mortimer;

	/**
	 * @param standard {@code VarbitID.SLAYER_TASKS_COMPLETED}
	 * @param wilderness {@code VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED}
	 * @param mortimer {@code VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED}
	 */
	public SlayerStreaks(int standard, int wilderness, int mortimer)
	{
		this.standard = standard;
		this.wilderness = wilderness;
		this.mortimer = mortimer;
	}

	/**
	 * Picks the streak that the current master's next task extends.
	 *
	 * @param masterId {@code VarbitID.SLAYER_MASTER}
	 * @return consecutive completed tasks
	 */
	public int forMaster(int masterId)
	{
		switch (masterId)
		{
			case KRYSTILIA:
				return wilderness;
			case MORTIMER:
				return mortimer;
			default:
				return standard;
		}
	}
}
