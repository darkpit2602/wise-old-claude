package com.wiseoldclaude.quests;

import java.util.Map;
import net.runelite.api.QuestState;

/** The client reads behind the quest list, quest points and fairy ring access. */
public interface QuestStateSource
{
	/**
	 * @return quest display name to progress, in RuneLite's quest order
	 */
	Map<String, QuestState> quests();

	/**
	 * @return total quest points
	 */
	int questPoints();

	/**
	 * @return true when the character has permission to use fairy rings
	 */
	boolean fairyRingsUnlocked();
}
