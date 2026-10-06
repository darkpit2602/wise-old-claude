package com.wiseoldclaude.model;

/**
 * Account restriction mode, decoded from varbit 1777 ({@code VarbitID.IRONMAN}). Values per the OSRS Wiki
 * varbit page; RuneLite's own deprecated {@code AccountType} only covers 0 to 3, missing the group modes.
 */
public enum AccountType
{
	NORMAL,
	IRONMAN,
	ULTIMATE_IRONMAN,
	HARDCORE_IRONMAN,
	GROUP_IRONMAN,
	HARDCORE_GROUP_IRONMAN,
	UNRANKED_GROUP_IRONMAN,
	UNKNOWN;

	private static final AccountType[] BY_VARBIT = {
		NORMAL, IRONMAN, ULTIMATE_IRONMAN, HARDCORE_IRONMAN,
		GROUP_IRONMAN, HARDCORE_GROUP_IRONMAN, UNRANKED_GROUP_IRONMAN
	};

	/**
	 * Decodes the varbit value. Values outside the known range map to {@link #UNKNOWN} so a future
	 * game mode degrades to generic advice instead of being mislabelled.
	 *
	 * @param value raw value of varbit 1777
	 * @return the matching account type, or {@link #UNKNOWN}
	 */
	public static AccountType fromVarbit(int value)
	{
		if (value < 0 || value >= BY_VARBIT.length)
		{
			return UNKNOWN;
		}
		return BY_VARBIT[value];
	}

	/**
	 * Every ironman mode, group modes included, is locked out of the Grand Exchange. {@link #UNKNOWN} counts as
	 * able so a future mode that can trade is not hidden; an ironman simply has no offers to show.
	 *
	 * @return whether the account can place Grand Exchange offers
	 */
	public boolean canUseGrandExchange()
	{
		return this == NORMAL || this == UNKNOWN;
	}
}
