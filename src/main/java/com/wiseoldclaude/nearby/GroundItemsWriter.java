package com.wiseoldclaude.nearby;

import com.google.gson.Gson;
import com.wiseoldclaude.io.CharacterFileWriter;
import com.wiseoldclaude.io.OutputDirectory;
import com.wiseoldclaude.model.GroundItems;
import java.io.IOException;
import javax.inject.Inject;
import net.runelite.client.util.Filepath;

/**
 * Persists the ground-item index as one file per character under {@code ground-items/}.
 */
public class GroundItemsWriter
{
	static final String GROUND_ITEMS_DIR = "ground-items";

	private final CharacterFileWriter files;

	/**
	 * Writes nulls, as the file format requires.
	 *
	 * @param gson the client's shared Gson
	 * @param directory where the plugin's files go
	 */
	@Inject
	public GroundItemsWriter(Gson gson, OutputDirectory directory)
	{
		this.files = CharacterFileWriter.writingNulls(gson, directory).in(GROUND_ITEMS_DIR);
	}

	/**
	 * Writes the file, replacing any previous one for the same character.
	 *
	 * @param index the value to persist
	 * @return the written file
	 * @throws IOException if the directory cannot be created or the file cannot be written
	 */
	public Filepath write(GroundItems index) throws IOException
	{
		return files.write(index.getRsn(), index);
	}
}
