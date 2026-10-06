package com.wiseoldclaude.quests;

import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.QuestState;

/**
 * Keeps the snapshot's quest list, quest points and fairy ring access current. Every read runs client scripts, so
 * they are read on login and then on a fixed schedule rather than with every snapshot. Call {@link #onTick} on the
 * client thread.
 */
public class QuestStateRefresh
{
	/**
	 * One minute between periodic reads: ~200 script runs are cheap, and quest completion is rare enough that a
	 * minute of staleness does not mislead the assistant.
	 */
	static final int INTERVAL_TICKS = 100;

	private final QuestStateSource source;
	private final Consumer<Map<String, QuestState>> listener;
	private final QuestRefreshSchedule schedule = new QuestRefreshSchedule(INTERVAL_TICKS);
	private QuestSummary summary = QuestSummary.UNREAD;

	/**
	 * Built by the plugin rather than injected, so every read reaches the very {@link QuestProgressRefresh} instance
	 * the plugin ticks; that class is not a singleton.
	 *
	 * @param source the quest reads
	 * @param listener receives every quest list read, changed or not
	 */
	public QuestStateRefresh(QuestStateSource source, Consumer<Map<String, QuestState>> listener)
	{
		this.source = source;
		this.listener = listener;
	}

	/**
	 * Reads again on the next tick, since quests may have moved while logged out.
	 */
	public void onLogin()
	{
		schedule.onLogin();
	}

	/**
	 * Reads the quests when the schedule is due.
	 *
	 * @return true when the quest list, quest points or fairy ring access changed, so the snapshot needs rewriting
	 */
	public boolean onTick()
	{
		if (!schedule.onTick())
		{
			return false;
		}
		Map<String, QuestState> latest = source.quests();
		listener.accept(latest);
		QuestSummary read = new QuestSummary(latest, source.questPoints(), source.fairyRingsUnlocked());
		if (read.equals(summary))
		{
			return false;
		}
		summary = read;
		return true;
	}

	/**
	 * @return the quests as last read, or {@link QuestSummary#UNREAD} before the first read
	 */
	public QuestSummary summary()
	{
		return summary;
	}
}
