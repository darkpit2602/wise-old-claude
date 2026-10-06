package com.wiseoldclaude.model;

import lombok.Value;

/**
 * World tile the player stands on. The region id lets the assistant map coordinates to a named area.
 */
@Value
public class Position
{
	int x;
	int y;
	int plane;
	int regionId;
}
