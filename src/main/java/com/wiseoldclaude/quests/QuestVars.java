package com.wiseoldclaude.quests;

/**
 * Reads the client variables that hold quest progress. An interface so the stage table and the refresh can be
 * tested without a client; the live implementation must run on the client thread.
 */
interface QuestVars
{
	/**
	 * @param id varbit id
	 * @return the varbit's current value
	 */
	int varbit(int id);

	/**
	 * @param id varp (VarPlayer) id
	 * @return the varp's current value
	 */
	int varp(int id);
}
