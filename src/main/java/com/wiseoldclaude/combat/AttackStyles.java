package com.wiseoldclaude.combat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Decodes the selected attack style into its in-game name, following RuneLite's attack styles plugin
 * ({@code AttackStylesPlugin#updateAttackStyle} and {@code #getWeaponTypeStyles}, themselves from client
 * script 4525). The weapon's style names come from the game cache, so new weapons decode without a table here;
 * only the two categories the cache leaves without a table need {@link #fallbackStyles(int)}.
 */
public final class AttackStyles
{
	private static final String UNUSED_SLOT = "Other";
	private static final String DEFENSIVE = "Defensive";
	private static final String DEFENSIVE_CASTING = "Defensive Casting";

	/**
	 * Staves expose five style buttons but encode defensive autocast as button four plus the casting-mode varbit,
	 * which lands on slot five.
	 */
	private static final int AUTOCAST_SLOT = 4;
	private static final int DEFENSIVE_AUTOCAST_SLOT = 5;

	private static final int BLUE_MOON_SPEAR_CATEGORY = 22;
	private static final int PARTISAN_CATEGORY = 30;

	private AttackStyles()
	{
	}

	/**
	 * Names the selected style.
	 *
	 * @param styleNames the weapon's style names per slot as the cache spells them ("Accurate", "Other", ...)
	 * @param styleIndex the selected style button ({@code VarPlayerID.COM_MODE})
	 * @param castingMode the autocast mode ({@code VarbitID.AUTOCAST_DEFMODE}), 1 when autocasting defensively
	 * @return the style name, or null when the slot is unused or out of range, so an unknown style is never
	 *   reported as a real one
	 */
	public static String decode(List<String> styleNames, int styleIndex, int castingMode)
	{
		if (styleIndex < 0 || styleIndex >= styleNames.size())
		{
			return null;
		}
		int slot = styleIndex == AUTOCAST_SLOT ? styleIndex + castingMode : styleIndex;
		if (slot >= styleNames.size())
		{
			return null;
		}
		String name = styleNames.get(slot);
		if (UNUSED_SLOT.equals(name))
		{
			return null;
		}
		return slot == DEFENSIVE_AUTOCAST_SLOT && DEFENSIVE.equals(name) ? DEFENSIVE_CASTING : name;
	}

	/**
	 * Style names for the weapon categories the cache's weapon-styles enum has no entry for, copied from
	 * RuneLite's hardcoded tables.
	 *
	 * @param weaponCategory {@code VarbitID.COMBAT_WEAPON_CATEGORY}
	 * @return the style names per slot, empty when the category is unknown
	 */
	public static List<String> fallbackStyles(int weaponCategory)
	{
		if (weaponCategory == BLUE_MOON_SPEAR_CATEGORY)
		{
			return Arrays.asList("Accurate", "Aggressive", UNUSED_SLOT, DEFENSIVE, "Casting", DEFENSIVE);
		}
		if (weaponCategory == PARTISAN_CATEGORY)
		{
			return Arrays.asList("Accurate", "Aggressive", "Aggressive", DEFENSIVE);
		}
		return Collections.emptyList();
	}
}
