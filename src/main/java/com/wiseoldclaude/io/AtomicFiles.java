package com.wiseoldclaude.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import net.runelite.client.util.Filepath;

/**
 * File access for everything the plugin shares with the MCP server, through RuneLite's {@link Filepath} as the
 * Plugin Hub requires. Replacing goes through a sibling temp file followed by an atomic rename, so the server
 * never reads a half-written file while the client is mid-write.
 */
public final class AtomicFiles
{
	private AtomicFiles()
	{
	}

	/**
	 * Writes the content to the target, replacing any previous file, and creates missing parent directories.
	 *
	 * @param target the file to replace
	 * @param content UTF-8 text to write
	 * @throws IOException if the directory cannot be created or the file cannot be written
	 */
	public static void replace(Filepath target, String content) throws IOException
	{
		Filepath directory = target.getParent();
		directory.createDirectories();
		Filepath temp = directory.createTempFile(".write-", ".tmp");
		try
		{
			temp.write(content);
			temp.moveTo(target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		finally
		{
			temp.deleteIfExists();
		}
	}

	/**
	 * @param file the file to read
	 * @return its content as UTF-8 text
	 * @throws IOException if it does not exist or cannot be read
	 */
	public static String read(Filepath file) throws IOException
	{
		try (InputStream in = file.openInputStream())
		{
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
