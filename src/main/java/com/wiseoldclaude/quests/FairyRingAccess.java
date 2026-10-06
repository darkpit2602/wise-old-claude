package com.wiseoldclaude.quests;

import net.runelite.api.QuestState;

/**
 * Decides whether the character may use the fairy ring network, so the assistant stops hedging on fairy ring
 * routes. The OSRS Wiki states rings unlock part-way through Fairytale II - Cure a Queen, once the Fairy
 * Godfather grants permission, but does not document the stage value of the quest's progress varbit
 * ({@code VarbitID.FAIRY2_QUEENCURE_QUEST}, 2326) at which that happens.
 * <p>
 * The threshold is taken from Quest Helper (https://github.com/Zoinkwiz/quest-helper, BSD 2-Clause,
 * Copyright (c) 2020, Zoinkwiz), whose Fairytale II helper marks stage 40 as "Talked to godfather for fairy
 * rings" and whose achievement diary helpers gate fairy ring steps on that varbit being at least 40.
 * <p>
 * Only quest progress is judged: wielding a dramen or lunar staff (or the elite Lumbridge &amp; Draynor diary)
 * is still needed to travel, and that is not captured here.
 */
public final class FairyRingAccess
{
	/**
	 * Fairytale II stage at which the Fairy Godfather has granted permission to use fairy rings.
	 */
	static final int GODFATHER_PERMISSION_STAGE = 40;

	private FairyRingAccess()
	{
	}

	/**
	 * Judges fairy ring access from Fairytale II progress. A finished quest unlocks rings outright, so the stage
	 * varbit is only consulted mid-quest, where its completion value need not be relied on.
	 *
	 * @param fairytaleII the quest's state as the client reports it
	 * @param questStage the value of the quest's progress varbit (2326)
	 * @return true when the character has permission to use fairy rings
	 */
	public static boolean isUnlocked(QuestState fairytaleII, int questStage)
	{
		if (fairytaleII == QuestState.FINISHED)
		{
			return true;
		}
		return fairytaleII == QuestState.IN_PROGRESS && questStage >= GODFATHER_PERMISSION_STAGE;
	}
}
