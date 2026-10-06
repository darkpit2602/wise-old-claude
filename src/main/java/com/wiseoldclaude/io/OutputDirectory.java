package com.wiseoldclaude.io;

import java.io.IOException;
import net.runelite.client.util.Filepath;

/**
 * The plugin's data directory, {@code ~/.runelite/plugin-data/wise-old-claude/}: every file the MCP server reads
 * is written under it, and the guidance request it writes is read from it. Resolved on first use rather than at
 * injection, because RuneLite injects every installed plugin when the client starts, and resolving moves the
 * pre-Filepath {@code ~/.runelite/wise-old-claude/} folder into place, which must only happen once the plugin runs.
 */
public class OutputDirectory
{
	/**
	 * Produces the directory; in the client this is {@code Plugin.getPluginDirectory()}.
	 */
	public interface Source
	{
		/**
		 * @return the directory, which may not exist yet
		 * @throws IOException if the plugin data folder cannot be prepared
		 */
		Filepath resolve() throws IOException;
	}

	private final Source source;
	private Filepath resolved;

	/**
	 * @param source resolves the directory; called at most once successfully
	 */
	public OutputDirectory(Source source)
	{
		this.source = source;
	}

	/**
	 * Synchronized because writers call it from the executor while the client thread reads guidance.
	 *
	 * @return the directory, which may not exist yet
	 * @throws IOException if it cannot be resolved; the next call tries again
	 */
	public synchronized Filepath get() throws IOException
	{
		if (resolved == null)
		{
			resolved = source.resolve();
		}
		return resolved;
	}
}
