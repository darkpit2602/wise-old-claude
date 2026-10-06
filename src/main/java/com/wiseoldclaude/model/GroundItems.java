package com.wiseoldclaude.model;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Items lying on the ground in the loaded scene, serialized verbatim to the file the MCP server's
 * find_ground_items tool reads. Says whose drop a stack is only as self, group, other or none, never which
 * player. Field names and shape are shared with the MCP server; change both together
 * and bump {@link #SCHEMA_VERSION} on any breaking change.
 */
@Value
@Builder(toBuilder = true)
public class GroundItems
{
	public static final int SCHEMA_VERSION = 1;

	@Builder.Default
	int schemaVersion = SCHEMA_VERSION;
	String rsn;
	String capturedAt;
	int world;

	/**
	 * Inside an instance the tiles are instance coordinates, matching the snapshot's position but not the
	 * wiki's, which the assistant needs to know before comparing them.
	 */
	boolean instance;

	/**
	 * The player's tile when the index was built; the fallback origin when no snapshot is available.
	 */
	Position origin;
	boolean truncated;

	/**
	 * Item name to its variants, nearest variant first.
	 */
	Map<String, List<GroundItemVariant>> items;
}
