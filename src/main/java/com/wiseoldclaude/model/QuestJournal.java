package com.wiseoldclaude.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * The text of one quest's journal as last seen in the client. The game only shows it while the player has the
 * journal open, so it is remembered and stamped with the quest's stage, which later reveals whether the player
 * has progressed past what the saved text describes.
 */
@Value
@Builder(toBuilder = true)
public class QuestJournal
{
	/**
	 * ISO-8601 instant the text was read.
	 */
	String capturedAt;

	/**
	 * The quest's progress value when the text was read; null, and left out of the file, when the quest's
	 * progress variable is unknown.
	 */
	Integer stageAtCapture;

	/**
	 * Top to bottom.
	 */
	List<JournalLine> lines;
}
