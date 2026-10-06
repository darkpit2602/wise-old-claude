package com.wiseoldclaude.quests;

import com.wiseoldclaude.model.QuestJournal;
import com.wiseoldclaude.snapshot.BankMemory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Remembers the last journal seen for each of a character's quests. The game shows a journal only while it is
 * open, so without memory the text would be lost the moment the player closes it, and on every client restart.
 * A character's first lookup is seeded from the previously exported file, like {@link BankMemory}.
 * Not thread-safe: use only from the client thread.
 */
public class QuestJournalMemory
{
	private final Function<String, Map<String, QuestJournal>> previousJournals;
	private final Map<String, Map<String, QuestJournal>> journals = new HashMap<>();

	/**
	 * @param previousJournals loads a character's journals from its previous export, empty when none
	 */
	public QuestJournalMemory(Function<String, Map<String, QuestJournal>> previousJournals)
	{
		this.previousJournals = previousJournals;
	}

	/**
	 * Records a journal as just read. A reopened journal with the same text and stage keeps its original
	 * capture time, so the assistant can tell how long the player has been at that step.
	 *
	 * @param rsn character the journal belongs to
	 * @param quest quest name
	 * @param journal the journal as just read
	 * @return true when the text or stage differs from what was remembered, meaning the export needs rewriting
	 */
	public boolean record(String rsn, String quest, QuestJournal journal)
	{
		Map<String, QuestJournal> known = journalsOf(rsn);
		QuestJournal previous = known.get(quest);
		if (previous != null && sameContent(previous, journal))
		{
			return false;
		}
		known.put(quest, journal);
		return true;
	}

	/**
	 * @param rsn character to look up
	 * @return the character's journals by quest name, alphabetically; empty when none was ever seen
	 */
	public Map<String, QuestJournal> journalsFor(String rsn)
	{
		return Collections.unmodifiableMap(new TreeMap<>(journalsOf(rsn)));
	}

	private Map<String, QuestJournal> journalsOf(String rsn)
	{
		return journals.computeIfAbsent(rsn, name -> new HashMap<>(previousJournals.apply(name)));
	}

	private static boolean sameContent(QuestJournal a, QuestJournal b)
	{
		return Objects.equals(a.getLines(), b.getLines()) && Objects.equals(a.getStageAtCapture(), b.getStageAtCapture());
	}
}
