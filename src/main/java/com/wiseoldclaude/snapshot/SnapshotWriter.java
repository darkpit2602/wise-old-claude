package com.wiseoldclaude.snapshot;

import com.google.gson.Gson;
import com.wiseoldclaude.io.CharacterFileWriter;
import com.wiseoldclaude.io.OutputDirectory;
import com.wiseoldclaude.model.PlayerSnapshot;
import java.io.IOException;
import javax.inject.Inject;
import net.runelite.client.util.Filepath;

/**
 * Persists snapshots as one JSON file per character.
 */
public class SnapshotWriter
{
	private final CharacterFileWriter files;

	/**
	 * Writes nulls: the file format requires optional-by-nature fields such as {@code bank} to be present as an explicit
	 * null.
	 *
	 * @param gson the client's shared Gson
	 * @param directory where the plugin's files go
	 */
	@Inject
	public SnapshotWriter(Gson gson, OutputDirectory directory)
	{
		this.files = CharacterFileWriter.writingNulls(gson, directory);
	}

	/**
	 * Writes the file, replacing any previous one for the same character.
	 *
	 * @param snapshot the value to persist
	 * @return the written file
	 * @throws IOException if the directory cannot be created or the file cannot be written
	 */
	public Filepath write(PlayerSnapshot snapshot) throws IOException
	{
		return files.write(snapshot.getRsn(), snapshot);
	}
}
