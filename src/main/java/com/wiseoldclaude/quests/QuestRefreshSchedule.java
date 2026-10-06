package com.wiseoldclaude.quests;

/**
 * Decides when to re-read quest states. Reading one quest runs a client script, so all ~200 quests are
 * read on a fixed cadence instead of on every snapshot write. Quest progress changes on a scale of
 * minutes, so a refresh on the first tick after login plus one per interval keeps the export fresh enough.
 * Counts ticks itself rather than comparing client tick counts, so it does not depend on whether the
 * client's tick counter survives a login.
 */
public class QuestRefreshSchedule
{
	private final int intervalTicks;
	private int ticksUntilDue;

	/**
	 * Starts armed, so a plugin enabled mid-session reads quests on its first tick.
	 *
	 * @param intervalTicks game ticks between periodic refreshes
	 */
	public QuestRefreshSchedule(int intervalTicks)
	{
		this.intervalTicks = intervalTicks;
		onLogin();
	}

	/**
	 * Requests a refresh on the next tick. Quest varps are only trustworthy once the game has ticked
	 * after login, so the read is deferred to the tick rather than done in the login event itself.
	 */
	public void onLogin()
	{
		ticksUntilDue = 1;
	}

	/**
	 * Advances one game tick.
	 *
	 * @return true when quests should be read on this tick
	 */
	public boolean onTick()
	{
		ticksUntilDue--;
		if (ticksUntilDue > 0)
		{
			return false;
		}
		ticksUntilDue = intervalTicks;
		return true;
	}
}
