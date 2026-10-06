package com.wiseoldclaude.model;

import lombok.Value;

/** An assigned slayer task as the slayer gem would describe it. */
@Value
public class SlayerTask
{
	/** Task name from the game cache, e.g. "Cave crawlers"; null if the cache lookup failed. */
	String creature;

	/** Kills left. */
	int remaining;

	/** Area the task is restricted to (Konar's tasks); null when it can be done anywhere. */
	String location;
}
