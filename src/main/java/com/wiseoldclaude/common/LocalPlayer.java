package com.wiseoldclaude.common;

import net.runelite.api.Client;
import net.runelite.api.Player;

/** Names the logged-in character, which every per-character file is keyed by. */
public final class LocalPlayer
{
	private LocalPlayer()
	{
	}

	/**
	 * @param client the game client
	 * @return the logged-in character's name, or null while no character is loaded
	 */
	public static String nameOf(Client client)
	{
		Player player = client.getLocalPlayer();
		return player == null ? null : player.getName();
	}
}
