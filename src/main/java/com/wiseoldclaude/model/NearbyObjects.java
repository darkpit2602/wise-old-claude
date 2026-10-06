package com.wiseoldclaude.model;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Named, interactable objects in the loaded scene, serialized verbatim to the file the MCP server's
 * find_nearby_objects tool reads. A separate file from {@link PlayerSnapshot} so the snapshot stays small.
 * Field names and shape are shared with the MCP server; change both together and
 * bump {@link #SCHEMA_VERSION} on any breaking change.
 */
@Value
@Builder(toBuilder = true)
public class NearbyObjects
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
	Map<String, List<List<Integer>>> objects;
}
