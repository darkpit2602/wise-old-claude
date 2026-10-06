package com.wiseoldclaude.common;

/**
 * Coalesces bursts of state changes into at most one snapshot write per interval. Inventory and stat
 * events fire many times per tick while skilling; writing on each would thrash the disk for no gain.
 * Tick-driven rather than clock-driven so it stays deterministic and testable.
 */
public class WriteThrottle
{
	private final int intervalTicks;
	private boolean dirty;
	private int lastWriteTick = Integer.MIN_VALUE;

	public WriteThrottle(int intervalTicks)
	{
		this.intervalTicks = intervalTicks;
	}

	/**
	 * Records that the snapshot is out of date.
	 */
	public void markDirty()
	{
		dirty = true;
	}

	/**
	 * Claims the right to write on this tick, clearing the pending change if granted.
	 *
	 * @param tick current game tick count
	 * @return true when a change is pending and the interval since the last write has elapsed
	 */
	public boolean tryAcquire(int tick)
	{
		boolean intervalElapsed = lastWriteTick == Integer.MIN_VALUE || tick - lastWriteTick >= intervalTicks;
		if (!dirty || !intervalElapsed)
		{
			return false;
		}
		dirty = false;
		lastWriteTick = tick;
		return true;
	}
}
