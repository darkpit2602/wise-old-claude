package com.wiseoldclaude.combat;

/**
 * Converts an actor's health bar into a percentage. The server only sends health as a fraction of a bar
 * ({@code Actor#getHealthRatio} over {@code Actor#getHealthScale}), and only once the bar has been shown.
 */
public final class HealthPercent
{
	private HealthPercent()
	{
	}

	/**
	 * Rounds up so a target with any health left never reads as 0% (dead).
	 *
	 * @param ratio the filled part of the bar, -1 when unknown
	 * @param scale the bar's full size, -1 when unknown
	 * @return the percentage from 0 to 100, or null when the bar has not been seen
	 */
	public static Integer of(int ratio, int scale)
	{
		if (ratio < 0 || scale <= 0)
		{
			return null;
		}
		return (int) Math.ceil(100.0 * ratio / scale);
	}
}
