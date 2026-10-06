package com.wiseoldclaude.quests;

import com.google.gson.Gson;
import com.wiseoldclaude.io.CharacterFileWriter;
import com.wiseoldclaude.io.CharacterFiles;
import com.wiseoldclaude.io.OutputDirectory;
import com.wiseoldclaude.model.QuestProgress;
import java.io.IOException;
import javax.inject.Inject;
import net.runelite.client.util.Filepath;

/**
 * Persists quest progress as one file per character under {@code quests/}.
 */
public class QuestProgressWriter
{
	static final String QUESTS_DIR = "quests";

	private final CharacterFileWriter files;

	/**
	 * Leaves nulls out: unknown values (stages not read yet, a quest without a stage) are absent rather than null,
	 * as the file format requires.
	 *
	 * @param gson the client's shared Gson
	 * @param directory where the plugin's files go
	 */
	@Inject
	public QuestProgressWriter(Gson gson, OutputDirectory directory)
	{
		this.files = CharacterFileWriter.omittingNulls(gson, directory).in(QUESTS_DIR);
	}

	/**
	 * Writes the file, replacing any previous one for the same character.
	 *
	 * @param progress the value to persist
	 * @return the written file
	 * @throws IOException if the directory cannot be created or the file cannot be written
	 */
	public Filepath write(QuestProgress progress) throws IOException
	{
		return files.write(progress.getRsn(), progress);
	}

	/**
	 * Shared with {@link PreviousQuestProgressReader} so reading back can never disagree with writing.
	 *
	 * @param rsn character name
	 * @param directory the plugin's data directory
	 * @return the character's quest-progress file, which may not exist yet
	 */
	static Filepath pathFor(String rsn, Filepath directory)
	{
		return CharacterFiles.pathFor(rsn, directory.joinSegment(QUESTS_DIR));
	}
}
