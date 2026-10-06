package com.wiseoldclaude.nearby;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * One scene object as the client describes it after impostor resolution. Plain data, so deciding what is
 * worth indexing can be tested without a running client.
 */
@Value
public class ObjectSighting implements Sighting
{
	int id;
	String name;
	String[] actions;

	/**
	 * The tile RuneLite reports for the object: for objects larger than one tile, the centre tile rounded
	 * south-west, so every tile a large object covers reports the same one.
	 */
	WorldPoint tile;
}
