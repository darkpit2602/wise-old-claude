package com.wiseoldclaude.quests;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.wiseoldclaude.io.AtomicFiles;
import com.wiseoldclaude.io.OutputDirectory;
import com.wiseoldclaude.model.QuestJournal;
import com.wiseoldclaude.model.QuestProgress;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

/**
 * Recovers the journals a character's previous quest-progress file recorded, so journals read in an earlier
 * session survive a client restart. An unreadable or incomplete file yields "nothing known" rather than an
 * error: losing an old journal is preferable to failing the export.
 */
public class PreviousQuestProgressReader
{
	private final Gson gson;
	private final OutputDirectory directory;

	/**
	 * @param gson deserializer matching the one the files were written with
	 * @param directory the plugin's data directory {@link QuestProgressWriter} writes under
	 */
	@Inject
	public PreviousQuestProgressReader(Gson gson, OutputDirectory directory)
	{
		this.gson = gson;
		this.directory = directory;
	}

	/**
	 * @param rsn character whose previous file to read
	 * @return the complete journals recorded there by quest name; empty when there is none or it cannot be read
	 */
	public Map<String, QuestJournal> lastJournals(String rsn)
	{
		try
		{
			String json = AtomicFiles.read(QuestProgressWriter.pathFor(rsn, directory.get()));
			QuestProgress previous = gson.fromJson(new JsonParser().parse(json), QuestProgress.class);
			return previous == null || previous.getJournals() == null ? Collections.emptyMap() : complete(previous.getJournals());
		}
		catch (IOException | JsonParseException | IllegalStateException e)
		{
			return Collections.emptyMap();
		}
	}

	private static Map<String, QuestJournal> complete(Map<String, QuestJournal> journals)
	{
		Map<String, QuestJournal> complete = new HashMap<>();
		journals.forEach((quest, journal) ->
		{
			if (journal != null && journal.getCapturedAt() != null && journal.getLines() != null)
			{
				complete.put(quest, journal);
			}
		});
		return complete;
	}
}
