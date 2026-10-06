package com.wiseoldclaude.nearby;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * One NPC as the client describes it after multi-NPC transformation. Plain data, so deciding what is worth
 * indexing can be tested without a running client.
 */
@Value
public class NpcSighting implements Sighting
{
	int id;
	String name;

	/**
	 * As the client reports it, where -1 means the NPC has no combat level.
	 */
	int combatLevel;
	String[] actions;

	/**
	 * Whether the client offers any menu option on the NPC; scenery-like NPCs that only animate are not.
	 */
	boolean interactible;

	/**
	 * The NPC's server-side tile, which can be a tile ahead of where the client draws a walking NPC.
	 */
	WorldPoint tile;
}
