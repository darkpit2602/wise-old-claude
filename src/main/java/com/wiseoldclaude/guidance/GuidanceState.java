package com.wiseoldclaude.guidance;

import java.time.Duration;
import java.time.Instant;
import net.runelite.api.coords.WorldPoint;

/**
 * Decides when guidance starts, ends on arrival, or is withdrawn. Each distinct request is acted on at most
 * once: re-reading the same file after the player arrived must not draw the route again, while asking for
 * the same tile anew (a later request time) must. A request already older than the maximum age when first
 * seen is ignored, which keeps a file left over from an earlier session from guiding the player when the
 * client starts. Not thread-safe: use only from the client thread.
 */
public class GuidanceState
{
	private final GuidanceDisplay display;
	private final Duration maxAge;
	private final int arrivalRadius;

	private GuidanceRequest lastSeen;
	private GuidanceRequest active;

	/**
	 * @param display where guidance is drawn
	 * @param maxAge how old a request may be when first seen and still be followed
	 * @param arrivalRadius tiles from the target (same floor, diagonals count as one) that count as arrived
	 */
	public GuidanceState(GuidanceDisplay display, Duration maxAge, int arrivalRadius)
	{
		this.display = display;
		this.maxAge = maxAge;
		this.arrivalRadius = arrivalRadius;
	}

	/**
	 * Applies the request file's current content.
	 *
	 * @param latest the request in the file, or null when the file is gone or unreadable
	 * @param now the current time, for the staleness check
	 */
	public void onRequest(GuidanceRequest latest, Instant now)
	{
		if (latest != null && latest.equals(lastSeen))
		{
			return;
		}
		lastSeen = latest;
		if (latest == null || latest.getRequestedAt().isBefore(now.minus(maxAge)))
		{
			stop();
			return;
		}
		active = latest;
		display.show(latest);
	}

	/**
	 * @param position the player's current tile
	 */
	public void onPlayerAt(WorldPoint position)
	{
		if (active != null && position.distanceTo(active.getTarget()) <= arrivalRadius)
		{
			GuidanceRequest reached = active;
			active = null;
			display.arrived(reached);
		}
	}

	/**
	 * Withdraws any active guidance, e.g. when the plugin shuts down, so nothing it drew outlives it.
	 */
	public void stop()
	{
		if (active != null)
		{
			GuidanceRequest withdrawn = active;
			active = null;
			display.withdraw(withdrawn);
		}
	}
}
