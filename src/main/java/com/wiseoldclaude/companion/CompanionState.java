package com.wiseoldclaude.companion;

import java.time.Duration;
import java.time.Instant;

/**
 * Whether the Claude Code side is set up and running, judged from when its MCP server last wrote its status. The
 * server rewrites the status every minute while it runs, so a few minutes of silence means it stopped.
 */
public final class CompanionState
{
	/** How the companion stands. */
	public enum Kind
	{
		NOT_SET_UP,
		RUNNING,
		IDLE
	}

	private static final Duration RUNNING_WITHIN = Duration.ofMinutes(3);

	private final Kind kind;
	private final Duration sinceLastSeen;

	private CompanionState(Kind kind, Duration sinceLastSeen)
	{
		this.kind = kind;
		this.sinceLastSeen = sinceLastSeen;
	}

	/**
	 * @param lastSeen when the server last wrote its status, or null when it never has
	 * @param now the current time
	 * @return the state at that time
	 */
	public static CompanionState of(Instant lastSeen, Instant now)
	{
		if (lastSeen == null)
		{
			return new CompanionState(Kind.NOT_SET_UP, null);
		}
		Duration since = Duration.between(lastSeen, now);
		return new CompanionState(since.compareTo(RUNNING_WITHIN) <= 0 ? Kind.RUNNING : Kind.IDLE, since);
	}

	public Kind getKind()
	{
		return kind;
	}

	/**
	 * @return one sentence for the player, e.g. "Claude Code was last active 3 hours ago."
	 */
	public String describe()
	{
		switch (kind)
		{
			case NOT_SET_UP:
				return "Claude Code is not set up yet.";
			case RUNNING:
				return "Claude Code is running.";
			default:
				return "Claude Code was last active " + ago(sinceLastSeen) + ".";
		}
	}

	private static String ago(Duration since)
	{
		long minutes = since.toMinutes();
		if (minutes < 60)
		{
			return count(minutes, "minute");
		}
		long hours = since.toHours();
		return hours < 24 ? count(hours, "hour") : count(since.toDays(), "day");
	}

	private static String count(long amount, String unit)
	{
		return amount + " " + unit + (amount == 1 ? "" : "s") + " ago";
	}
}
