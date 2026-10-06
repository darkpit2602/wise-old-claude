package com.wiseoldclaude.quests;

import java.util.Collections;
import java.util.Map;
import lombok.Value;
import net.runelite.api.QuestState;

/** The quest facts the snapshot carries, as last read from the client. */
@Value
public class QuestSummary
{
	/** Nothing read yet: no quests, and unknown points and fairy ring access. */
	public static final QuestSummary UNREAD = new QuestSummary(Collections.emptyMap(), null, null);

	/** Quest display name to progress, in RuneLite's quest order. */
	Map<String, QuestState> quests;

	/** Total quest points, or null before the first read. */
	Integer questPoints;

	/** Fairy ring permission, or null before the first read. */
	Boolean fairyRingsUnlocked;
}
