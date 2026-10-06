package com.wiseoldclaude.model;

import lombok.Value;

/**
 * One line of the player's own game feed, as plain text. Back-to-back repeats share one entry so a skilling
 * loop's "You get some logs." does not push everything else out of the short history.
 */
@Value
public class GameMessage
{
	/**
	 * ISO-8601 instant the message last arrived; a repeat moves it forward, so it reads as "last seen".
	 */
	String at;

	/**
	 * Who said a dialogue line (an NPC or the player); absent for every other message.
	 */
	String speaker;
	String text;

	/**
	 * How many times the message arrived back to back; 1 for a message seen once.
	 */
	int repeats;

	/**
	 * @param at when the repeat arrived
	 * @return this message seen once more
	 */
	public GameMessage repeatedAt(String at)
	{
		return new GameMessage(at, speaker, text, repeats + 1);
	}
}
