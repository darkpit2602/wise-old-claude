package com.wiseoldclaude.model;

import lombok.Value;

/**
 * One bird house space on Fossil Island.
 */
@Value
public class BirdhouseSpace
{
	/**
	 * What the space holds. {@code BUILT} is a bird house without seeds, which catches nothing.
	 */
	public enum State
	{
		EMPTY,
		BUILT,
		SEEDED,
		UNKNOWN
	}

	/** The space as RuneLite's Time Tracking names it, e.g. "Mushroom Meadow (North)". */
	String location;
	State state;
	/** The bird house's item name; null when the space is empty or unknown. */
	String birdhouse;
	/**
	 * When the seeded bird house is full; null unless seeded, and null when it was seeded while the plugin was
	 * not watching, since the game does not report when seeds went in.
	 */
	String readyAt;
}
