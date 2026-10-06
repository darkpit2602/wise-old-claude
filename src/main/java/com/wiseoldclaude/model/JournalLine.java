package com.wiseoldclaude.model;

import lombok.Value;

/**
 * One visual line of a quest journal as the player reads it. Kept per line rather than merged into sentences:
 * the game marks progress per drawn line, and guessing where a wrapped sentence ends would add errors the
 * assistant cannot see.
 */
@Value
public class JournalLine
{
	/**
	 * The line's text with markup stripped.
	 */
	String text;

	/**
	 * True when the game draws the line struck through, its way of marking a finished step.
	 */
	boolean done;
}
