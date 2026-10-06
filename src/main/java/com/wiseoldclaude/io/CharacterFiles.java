package com.wiseoldclaude.io;

import java.util.Locale;
import java.util.regex.Pattern;
import net.runelite.client.util.Filepath;

/** Names the per-character files every writer and reader shares. */
public final class CharacterFiles
{
	private static final Pattern WINDOWS_DEVICE_NAME = Pattern.compile("con|prn|aux|nul|com[1-9]|lpt[1-9]");

	private CharacterFiles()
	{
	}

	/**
	 * Resolves where a character's file lives in a directory. Shared by every writer and reader so reading back
	 * can never disagree with writing about the file name. Names Windows reserves for devices (a character
	 * called "Con") get a trailing underscore, since {@link Filepath} refuses them on every platform.
	 *
	 * @param rsn character name
	 * @param directory the directory holding one file per character
	 * @return the character's file, which may not exist yet
	 */
	public static Filepath pathFor(String rsn, Filepath directory)
	{
		String stem = rsn.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
		if (WINDOWS_DEVICE_NAME.matcher(stem).matches())
		{
			stem += "_";
		}
		return directory.joinSegment(stem + ".json");
	}
}
