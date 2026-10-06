package com.wiseoldclaude.common;

/** Recognises the names the client gives things the player cannot see or name. */
public final class GameNames
{
	/** The client's name for an object, NPC or item definition that has none, e.g. a hidden scenery piece. */
	public static final String UNNAMED = "null";

	private GameNames()
	{
	}

	/**
	 * @param name a name from a client definition, possibly null
	 * @return true when the name is something the player can read and ask about
	 */
	public static boolean isNamed(String name)
	{
		return name != null && !name.isEmpty() && !UNNAMED.equals(name);
	}
}
