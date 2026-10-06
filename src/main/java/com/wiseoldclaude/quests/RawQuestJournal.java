package com.wiseoldclaude.quests;

import java.util.List;
import lombok.Value;

/**
 * The quest journal interface's text exactly as the client holds it, tags included, so all interpretation
 * stays in {@link QuestJournalParser} where it can be tested without a client.
 */
@Value
class RawQuestJournal
{
	String title;

	/**
	 * One entry per line widget, top to bottom, including blank and unused lines.
	 */
	List<String> lines;
}
