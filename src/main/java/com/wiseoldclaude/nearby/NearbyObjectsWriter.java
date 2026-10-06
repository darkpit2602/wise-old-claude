package com.wiseoldclaude.nearby;

import com.google.gson.Gson;
import com.wiseoldclaude.io.CharacterFileWriter;
import com.wiseoldclaude.io.OutputDirectory;
import com.wiseoldclaude.model.NearbyObjects;
import java.io.IOException;
import javax.inject.Inject;
import net.runelite.client.util.Filepath;

/**
 * Persists the nearby-object index as one file per character under {@code objects/}.
 */
public class NearbyObjectsWriter
{
	static final String OBJECTS_DIR = "objects";

	private final CharacterFileWriter files;

	/**
	 * Writes nulls, as the file format requires.
	 *
	 * @param gson the client's shared Gson
	 * @param directory where the plugin's files go
	 */
	@Inject
	public NearbyObjectsWriter(Gson gson, OutputDirectory directory)
	{
		this.files = CharacterFileWriter.writingNulls(gson, directory).in(OBJECTS_DIR);
	}

	/**
	 * Writes the file, replacing any previous one for the same character.
	 *
	 * @param index the value to persist
	 * @return the written file
	 * @throws IOException if the directory cannot be created or the file cannot be written
	 */
	public Filepath write(NearbyObjects index) throws IOException
	{
		return files.write(index.getRsn(), index);
	}
}
