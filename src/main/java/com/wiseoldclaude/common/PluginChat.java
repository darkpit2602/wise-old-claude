package com.wiseoldclaude.common;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;

/** Posts the plugin's own chat lines, all under one prefix so the player can tell them from the game's. */
public final class PluginChat
{
	private static final String PREFIX = "[Wise Old Claude] ";

	private PluginChat()
	{
	}

	/**
	 * Must run on the client thread.
	 *
	 * @param client the game client
	 * @param message the line, without the prefix
	 */
	public static void post(Client client, String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + message, null);
	}
}
