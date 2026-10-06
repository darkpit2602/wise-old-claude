package com.wiseoldclaude.context;

import com.wiseoldclaude.common.JagexMarkup;
import com.wiseoldclaude.model.GameMessage;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.ChatMessageType;

/**
 * The player's recent game feed: what the game told them ("You need an axe to chop down this tree."), what
 * NPCs said to them, and what they examined. An allow-list rather than a block-list, so a chat type Jagex adds
 * later stays out until someone decides it is the player's own; other players' chat (public, private,
 * friends, clan, trade, broadcasts) is never kept.
 */
class GameMessageLog
{
	/**
	 * The game's own feed: the types RuneLite's chat filter treats as game messages, filtered game spam (where
	 * most skilling lines land once the in-game filter is on), RuneLite's own notices, the player's dialogue
	 * with NPCs and message boxes, and the player's examines.
	 */
	private static final Set<ChatMessageType> OWN_TYPES = EnumSet.of(
		ChatMessageType.GAMEMESSAGE,
		ChatMessageType.ENGINE,
		ChatMessageType.SPAM,
		ChatMessageType.CONSOLE,
		ChatMessageType.DIALOG,
		ChatMessageType.MESBOX,
		ChatMessageType.ITEM_EXAMINE,
		ChatMessageType.NPC_EXAMINE,
		ChatMessageType.OBJECT_EXAMINE);

	/**
	 * Separates the speaker from the line in a dialogue message, as RuneLite's clue scroll plugin reads them.
	 */
	private static final String DIALOGUE_SEPARATOR = "|";

	private final int capacity;
	private final Deque<GameMessage> messages = new ArrayDeque<>();

	/**
	 * @param capacity how many distinct messages to remember before forgetting the oldest
	 */
	GameMessageLog(int capacity)
	{
		this.capacity = capacity;
	}

	/**
	 * Keeps the message if it belongs to the player's own feed.
	 *
	 * @param type the chat channel the message arrived on
	 * @param message the raw, tagged message text
	 * @param at when it arrived
	 * @return true when the log changed
	 */
	boolean record(ChatMessageType type, String message, Instant at)
	{
		if (!OWN_TYPES.contains(type))
		{
			return false;
		}
		GameMessage parsed = parse(type, message, at.toString());
		if (parsed.getText().isEmpty())
		{
			return false;
		}
		GameMessage previous = messages.peekLast();
		if (previous != null && sameLine(previous, parsed))
		{
			messages.removeLast();
			messages.addLast(previous.repeatedAt(parsed.getAt()));
			return true;
		}
		messages.addLast(parsed);
		if (messages.size() > capacity)
		{
			messages.removeFirst();
		}
		return true;
	}

	/**
	 * @return the remembered messages, oldest first, as a copy safe to hand to another thread
	 */
	List<GameMessage> messages()
	{
		return new ArrayList<>(messages);
	}

	/**
	 * Forgets every message, for when a different character logs in.
	 */
	void clear()
	{
		messages.clear();
	}

	private static GameMessage parse(ChatMessageType type, String message, String at)
	{
		int separator = message == null ? -1 : message.indexOf(DIALOGUE_SEPARATOR);
		if (type != ChatMessageType.DIALOG || separator < 0)
		{
			return new GameMessage(at, null, JagexMarkup.plain(message), 1);
		}
		String speaker = JagexMarkup.plain(message.substring(0, separator));
		String line = JagexMarkup.plain(message.substring(separator + 1));
		return new GameMessage(at, speaker.isEmpty() ? null : speaker, line, 1);
	}

	private static boolean sameLine(GameMessage a, GameMessage b)
	{
		return a.getText().equals(b.getText()) && Objects.equals(a.getSpeaker(), b.getSpeaker());
	}
}
