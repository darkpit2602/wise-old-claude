package com.wiseoldclaude.common;

import com.wiseoldclaude.model.Position;
import net.runelite.api.coords.WorldPoint;

/** Converts the client's tiles into the exported position shape. */
public final class Positions
{
	private Positions()
	{
	}

	/**
	 * @param point a world tile
	 * @return the same tile with its region, as written to the files
	 */
	public static Position of(WorldPoint point)
	{
		return new Position(point.getX(), point.getY(), point.getPlane(), point.getRegionID());
	}
}
