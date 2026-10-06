package com.wiseoldclaude.nearby;

import net.runelite.api.coords.WorldPoint;

/** Something named seen on a tile of the loaded scene: an object, an NPC or a ground item. */
interface Sighting
{
	/**
	 * @return the definition id
	 */
	int getId();

	/**
	 * @return the definition's name, possibly a placeholder
	 */
	String getName();

	/**
	 * @return where it was seen
	 */
	WorldPoint getTile();
}
