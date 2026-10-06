package com.wiseoldclaude.model;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Named NPCs a player could interact with in the loaded scene, serialized verbatim to the file the MCP
 * server's find_nearby_npcs tool reads. Never holds other players: only the client's NPC list is read.
 * Field names and shape are shared with the MCP server; change both together and bump
 * {@link #SCHEMA_VERSION} on any breaking change.
 */
@Value
@Builder(toBuilder = true)
public class NearbyNpcs
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
	 * NPC name to its variants, nearest variant first.
	 */
	Map<String, List<NpcVariant>> npcs;
}
