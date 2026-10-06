package com.wiseoldclaude.quests;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Reads quest state from the client. Call on the client thread. */
public class QuestStateReader implements QuestStateSource
{
	private final Client client;

	/**
	 * @param client the game client
	 */
	@Inject
	public QuestStateReader(Client client)
	{
		this.client = client;
	}

	/**
	 * Reads every quest's progress. Each read runs a client script, so this is called on a schedule rather
	 * than on every snapshot.
	 *
	 * @return quest display name to progress, in RuneLite's quest order
	 */
	@Override
	public Map<String, QuestState> quests()
	{
		Map<String, QuestState> quests = new LinkedHashMap<>();
		for (Quest quest : Quest.values())
		{
			quests.put(quest.getName(), quest.getState(client));
		}
		return quests;
	}

	/**
	 * Reads the game's own quest point total (the varp RuneLite's {@code !qp} chat command reports) instead
	 * of summing points per quest, which would need a hand-maintained points table.
	 *
	 * @return total quest points
	 */
	@Override
	public int questPoints()
	{
		return client.getVarpValue(VarPlayerID.QP);
	}

	/**
	 * Reads Fairytale II's state and stage varbit and judges fairy ring access from them via
	 * {@link FairyRingAccess}. Runs a quest-state script, so it is read on the quest schedule.
	 *
	 * @return true when the character has permission to use fairy rings
	 */
	@Override
	public boolean fairyRingsUnlocked()
	{
		return FairyRingAccess.isUnlocked(
			Quest.FAIRYTALE_II__CURE_A_QUEEN.getState(client),
			client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST));
	}
}
