package com.wiseoldclaude.context;

import com.google.gson.Gson;
import com.wiseoldclaude.io.CharacterFileWriter;
import com.wiseoldclaude.io.OutputDirectory;
import com.wiseoldclaude.model.GameContext;
import java.io.IOException;
import javax.inject.Inject;
import net.runelite.client.util.Filepath;

/**
 * Persists the game context as one file per character under {@code context/}.
 */
public class GameContextWriter
{
	static final String CONTEXT_DIR = "context";

	private final CharacterFileWriter files;

	/**
	 * Leaves nulls out: most fields of a message or an open interface apply to only one kind, and writing them all as
	 * null would double the file for no information. The file format marks those fields optional to match.
	 *
	 * @param gson the client's shared Gson
	 * @param directory where the plugin's files go
	 */
	@Inject
	public GameContextWriter(Gson gson, OutputDirectory directory)
	{
		this.files = CharacterFileWriter.omittingNulls(gson, directory).in(CONTEXT_DIR);
	}

	/**
	 * Writes the file, replacing any previous one for the same character.
	 *
	 * @param context the value to persist
	 * @return the written file
	 * @throws IOException if the directory cannot be created or the file cannot be written
	 */
	public Filepath write(GameContext context) throws IOException
	{
		return files.write(context.getRsn(), context);
	}
}
