package com.wiseoldclaude.guidance;

import java.time.Instant;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * A destination the MCP server asked the plugin to guide the player to. Equality covers the request time,
 * so asking for the same tile again counts as a new request even after the player arrived at it once.
 */
@Value
public class GuidanceRequest
{
	WorldPoint target;
	/**
	 * Human-readable destination name, or null when the server gave none.
	 */
	String label;
	Instant requestedAt;
}
