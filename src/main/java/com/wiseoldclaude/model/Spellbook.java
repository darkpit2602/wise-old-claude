package com.wiseoldclaude.model;

/**
 * The active spellbook, decoded from varbit 4070 ({@code VarbitID.SPELLBOOK}). RuneLite itself only uses the
 * value as a cache-enum index, so the mapping follows the Plugin Hub plugins that decode it (quest-helper,
 * thrall-helper), which agree on 0 to 3.
 */
public enum Spellbook
{
	STANDARD,
	ANCIENT,
	LUNAR,
	ARCEUUS;

	/**
	 * Decodes the varbit value.
	 *
	 * @param value raw value of varbit 4070
	 * @return the spellbook, or null for a value outside the four known books so a new book is never mislabelled
	 */
	public static Spellbook fromVarbit(int value)
	{
		Spellbook[] books = values();
		return value >= 0 && value < books.length ? books[value] : null;
	}
}
