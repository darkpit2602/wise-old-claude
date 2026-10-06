package com.wiseoldclaude.guidance;

import java.io.IOException;
import java.util.Objects;
import net.runelite.client.util.Filepath;

/**
 * Tells the plugin when the guidance request file changed, so it is parsed only then rather than on every
 * poll. Polled from the game tick: one metadata read per tick is negligible next to a WatchService, which
 * would need its own thread, lifecycle, and hand-off back to the client thread. Size is compared alongside
 * the modification time because some filesystems record times in whole seconds, and two requests written
 * within one second would otherwise look identical.
 */
public class GuidanceInbox
{
	private final Filepath file;
	private String lastSignature;

	/**
	 * @param file the request file, which need not exist yet
	 */
	public GuidanceInbox(Filepath file)
	{
		this.file = file;
	}

	/**
	 * @return true when the file appeared, disappeared, or was rewritten since the previous call
	 */
	public boolean hasChanged()
	{
		String signature = signature();
		if (Objects.equals(signature, lastSignature))
		{
			return false;
		}
		lastSignature = signature;
		return true;
	}

	/**
	 * @return the watched file
	 */
	public Filepath file()
	{
		return file;
	}

	private String signature()
	{
		try
		{
			return file.getLastModifiedTime().toMillis() + ":" + file.size();
		}
		catch (IOException e)
		{
			return null;
		}
	}
}
