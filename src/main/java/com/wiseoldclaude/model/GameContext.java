package com.wiseoldclaude.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * What just happened around the player and what they have open, serialized verbatim to the file the MCP
 * server's recent-message and open-interface tools read. Holds only the player's own game feed, never other
 * players' chat. Field names and shape are shared with the MCP server; change both
 * together and bump {@link #SCHEMA_VERSION} on any breaking change.
 */
@Value
@Builder(toBuilder = true)
public class GameContext
{
	public static final int SCHEMA_VERSION = 1;

	@Builder.Default
	int schemaVersion = SCHEMA_VERSION;
	String rsn;
	String capturedAt;

	/**
	 * Oldest first.
	 */
	List<GameMessage> messages;

	/**
	 * Null when no tracked interface is open, which leaves the field out of the file.
	 */
	OpenInterface openInterface;
}
