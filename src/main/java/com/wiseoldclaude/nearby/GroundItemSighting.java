package com.wiseoldclaude.nearby;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * One item on a tile as the client lists it, with its prices already looked up. Plain data, so grouping and
 * capping can be tested without a running client.
 */
@Value
@AllArgsConstructor
@Builder(toBuilder = true)
public class GroundItemSighting implements Sighting
{
	int id;
	String name;
	int quantity;

	/**
	 * The client's ownership code, one of the {@code TileItem.OWNERSHIP_*} constants.
	 */
	int ownership;
	long gePrice;
	int haPrice;
	WorldPoint tile;
}
