package com.wiseoldclaude.quests;

import com.wiseoldclaude.common.JagexMarkup;
import com.wiseoldclaude.model.JournalLine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Interprets the quest journal interface's tagged text. The game marks a finished step by drawing its line
 * struck through with a {@code <str>} tag, re-opened on every wrapped line of the step (RuneLite's
 * achievement diary plugin and Quest Helper both test lines for a {@code <str>} prefix), so a line is done
 * exactly when its own text carries the tag.
 */
final class QuestJournalParser
{
	private static final String STRIKE_TAG = "<str>";

	private QuestJournalParser()
	{
	}

	/**
	 * @param rawLines the line widgets' texts top to bottom, nulls and blanks included
	 * @return the readable lines with their finished state, blank lines dropped
	 */
	static List<JournalLine> lines(List<String> rawLines)
	{
		List<JournalLine> lines = new ArrayList<>();
		for (String raw : rawLines)
		{
			String text = JagexMarkup.plain(raw);
			if (!text.isEmpty())
			{
				lines.add(new JournalLine(text, raw.toLowerCase(Locale.ROOT).contains(STRIKE_TAG)));
			}
		}
		return lines;
	}

	/**
	 * Resolves the journal's title to a quest. The interface also shows non-quest scrolls, so a title naming no
	 * known quest is rejected rather than saved under a made-up name. An exact match wins; otherwise the
	 * longest quest name the title contains, so "Dragon Slayer II" is never filed under "Dragon Slayer I".
	 *
	 * @param rawTitle the title widget's tagged text, possibly null
	 * @param questNames every known quest name
	 * @return the matching quest name, or null when the title names no known quest
	 */
	static String questFor(String rawTitle, Collection<String> questNames)
	{
		String title = JagexMarkup.plain(rawTitle).toLowerCase(Locale.ROOT);
		if (title.isEmpty())
		{
			return null;
		}
		String longestContained = null;
		for (String quest : questNames)
		{
			String name = quest.toLowerCase(Locale.ROOT);
			if (name.equals(title))
			{
				return quest;
			}
			if (title.contains(name) && (longestContained == null || quest.length() > longestContained.length()))
			{
				longestContained = quest;
			}
		}
		return longestContained;
	}
}
