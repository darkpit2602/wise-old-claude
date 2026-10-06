package com.wiseoldclaude.snapshot;

import com.wiseoldclaude.model.SessionXp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks XP gained since the player logged in. The baseline waits two ticks after a login or world hop, as
 * RuneLite's XP tracker does, because the client reports zero XP until the login stat sync lands. A hop keeps the
 * session; a fresh login starts a new one. XP never falls within one account profile, so a skill reading below its
 * baseline means a different profile (e.g. a seasonal world) and also starts a new session.
 * Not thread-safe: used on the client thread only.
 */
public class SessionXpTracker
{
	private static final int SYNC_TICKS = 2;

	private int ticksUntilSynced;
	private Map<String, Integer> baseline;
	private String since;

	/** Starts a new session once the login stat sync has landed. */
	public void onLogin()
	{
		baseline = null;
		ticksUntilSynced = SYNC_TICKS;
	}

	/** Pauses reporting while a world hop resyncs stats, keeping the session. */
	public void onHop()
	{
		ticksUntilSynced = SYNC_TICKS;
	}

	/** Counts down the stat sync after a login or hop. */
	public void onTick()
	{
		if (ticksUntilSynced > 0)
		{
			ticksUntilSynced--;
		}
	}

	/**
	 * Reports the session's gains, starting the session if there is none yet.
	 *
	 * @param xp current XP per skill name
	 * @param now when the XP was read, which becomes the session start for a new session
	 * @return gains per skill that gained XP, or null while stats are still syncing
	 */
	public SessionXp observe(Map<String, Integer> xp, Instant now)
	{
		if (ticksUntilSynced > 0)
		{
			return null;
		}
		if (baseline == null || anyBelowBaseline(xp))
		{
			baseline = new LinkedHashMap<>(xp);
			since = now.toString();
		}
		return new SessionXp(since, gains(xp));
	}

	private boolean anyBelowBaseline(Map<String, Integer> xp)
	{
		return xp.entrySet().stream().anyMatch(skill -> skill.getValue() < baseline.getOrDefault(skill.getKey(), 0));
	}

	private Map<String, Integer> gains(Map<String, Integer> xp)
	{
		Map<String, Integer> gained = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> skill : xp.entrySet())
		{
			int delta = skill.getValue() - baseline.getOrDefault(skill.getKey(), skill.getValue());
			if (delta > 0)
			{
				gained.put(skill.getKey(), delta);
			}
		}
		return gained;
	}
}
