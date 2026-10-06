package com.wiseoldclaude.combat;

import lombok.Value;

/**
 * Whether Deadeye and Mystic Vigour replace Eagle Eye and Mystic Might. The upgrades share their base prayer's
 * slot, so the prayer varbits alone cannot tell which one is on; RuneLite's prayer plugin
 * ({@code PrayerType.DEADEYE#isEnabled}) settles it from the unlock varbits and Last Man Standing, where the
 * upgrades are unavailable.
 */
@Value
public class PrayerUpgrades
{
	boolean deadeye;
	boolean mysticVigour;

	/**
	 * Reads the upgrade state from its varbits.
	 *
	 * @param deadeyeUnlocked {@code VarbitID.PRAYER_DEADEYE_UNLOCKED}
	 * @param mysticVigourUnlocked {@code VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED}
	 * @param inLastManStanding {@code VarbitID.BR_INGAME}
	 * @return which upgrades are in effect
	 */
	public static PrayerUpgrades fromVarbits(int deadeyeUnlocked, int mysticVigourUnlocked, int inLastManStanding)
	{
		boolean upgradesAllowed = inLastManStanding == 0;
		return new PrayerUpgrades(upgradesAllowed && deadeyeUnlocked != 0, upgradesAllowed && mysticVigourUnlocked != 0);
	}
}
