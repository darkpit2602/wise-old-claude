package com.wiseoldclaude.snapshot;

import com.wiseoldclaude.common.WriteThrottle;

/**
 * Decides when the snapshot is written: soon after anything in it changes, but no more often than the configured
 * interval, and at least once a minute while logged in even when nothing changed. The keep-alive lets the MCP server
 * tell an idle player from a client that is not running, since both would otherwise leave the file untouched.
 */
public class SnapshotSchedule
{
	/** One minute, half the age at which the MCP server calls a snapshot stale. */
	static final int KEEPALIVE_TICKS = 100;

	private final WriteThrottle throttle;
	private int lastWriteTick = Integer.MIN_VALUE;

	/**
	 * Starts dirty, so the first tick after starting writes a snapshot.
	 *
	 * @param intervalTicks minimum ticks between writes
	 */
	public SnapshotSchedule(int intervalTicks)
	{
		this.throttle = new WriteThrottle(intervalTicks);
		throttle.markDirty();
	}

	/**
	 * Records that something in the snapshot changed.
	 */
	public void markDirty()
	{
		throttle.markDirty();
	}

	/**
	 * @param tick current game tick count
	 * @return true when a snapshot should be written on this tick
	 */
	public boolean isDue(int tick)
	{
		if (lastWriteTick != Integer.MIN_VALUE && tick - lastWriteTick >= KEEPALIVE_TICKS)
		{
			throttle.markDirty();
		}
		if (!throttle.tryAcquire(tick))
		{
			return false;
		}
		lastWriteTick = tick;
		return true;
	}
}
