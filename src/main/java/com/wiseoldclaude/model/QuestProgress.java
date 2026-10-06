package com.wiseoldclaude.model;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Where the player is inside their quests, serialized verbatim to the file the MCP server's quest-progress
 * tool reads. Kept out of the snapshot because journals run to dozens of lines each. Field names and shape are
 * shared with the MCP server; change both together and bump {@link #SCHEMA_VERSION}
 * on any breaking change.
 */
@Value
@Builder(toBuilder = true)
public class QuestProgress
{
	public static final int SCHEMA_VERSION = 1;

	@Builder.Default
	int schemaVersion = SCHEMA_VERSION;
	String rsn;
	String capturedAt;

	/**
	 * Quest name to raw progress value for in-progress quests with a known progress variable. Null, and left
	 * out of the file, until quests have been read for this character in this session.
	 */
	Map<String, Integer> stages;

	/**
	 * Quest name to the journal last seen for it.
	 */
	Map<String, QuestJournal> journals;
}
