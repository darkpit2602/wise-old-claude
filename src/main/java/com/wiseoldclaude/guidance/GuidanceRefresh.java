package com.wiseoldclaude.guidance;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Filepath;

/**
 * Follows the guidance request the MCP server writes: reads the request file when it changes, draws the route, and
 * reports arrival once the player gets there. Call {@link #onTick} and {@link #stop} on the client thread, where the
 * display may draw.
 */
public class GuidanceRefresh
{
	/**
	 * Written by the MCP server's guide_to tool. A subdirectory, so the server's listing of {@code *.json} character
	 * snapshots never mistakes the request for a character.
	 */
	static final String DIRECTORY = "guidance";
	static final String FILE = "request.json";

	/**
	 * The server writes requests seconds before the plugin sees them, so anything older is a leftover from an earlier
	 * session that must not guide the player as soon as the client starts.
	 */
	static final Duration MAX_AGE = Duration.ofMinutes(5);

	/**
	 * Close enough to see the target on screen. Inside Shortest Path's default finish distance of 5, so the hint
	 * arrow still leads the last steps after the route has ended.
	 */
	private static final int ARRIVAL_RADIUS_TILES = 2;

	private final GuidanceInbox inbox;
	private final GuidanceRequestReader reader = new GuidanceRequestReader();
	private final GuidanceState state;
	private final Supplier<Instant> clock;

	/**
	 * @param inbox watches the request file
	 * @param display draws and withdraws the route
	 * @param clock the current time, for the staleness check
	 */
	GuidanceRefresh(GuidanceInbox inbox, GuidanceDisplay display, Supplier<Instant> clock)
	{
		this.inbox = inbox;
		this.state = new GuidanceState(display, MAX_AGE, ARRIVAL_RADIUS_TILES);
		this.clock = clock;
	}

	/**
	 * @param directory the plugin's data directory, which holds the request file
	 * @param display draws and withdraws the route in the client
	 * @return a refresh following the request file in that directory
	 */
	public static GuidanceRefresh in(Filepath directory, GuidanceDisplay display)
	{
		GuidanceInbox inbox = new GuidanceInbox(directory.joinSegment(DIRECTORY).joinSegment(FILE));
		return new GuidanceRefresh(inbox, display, Instant::now);
	}

	/**
	 * Applies a changed request, then checks whether the player has arrived.
	 *
	 * @param playerPosition the player's tile, or null while no player is logged in
	 */
	public void onTick(WorldPoint playerPosition)
	{
		if (inbox.hasChanged())
		{
			state.onRequest(reader.read(inbox.file()), clock.get());
		}
		if (playerPosition != null)
		{
			state.onPlayerAt(playerPosition);
		}
	}

	/**
	 * Withdraws any active guidance, so nothing the plugin drew outlives it.
	 */
	public void stop()
	{
		state.stop();
	}
}
