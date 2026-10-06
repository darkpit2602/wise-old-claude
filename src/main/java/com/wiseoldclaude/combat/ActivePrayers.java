package com.wiseoldclaude.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Prayer;

/**
 * Names the prayers that are on. Reads each prayer's varbit rather than the deprecated
 * {@code Client#isPrayerActive}, which RuneLite documents as mishandling the Deadeye and Mystic Vigour slots,
 * and resolves those slots with {@link PrayerUpgrades}.
 */
public final class ActivePrayers
{
	private static final String RUINOUS_POWERS_PREFIX = "RP_";
	private static final Set<String> CONNECTING_WORDS = Set.of("of", "from");

	private ActivePrayers()
	{
	}

	/**
	 * Names the active prayers.
	 *
	 * @param varbitsOn prayers whose varbit is non-zero
	 * @param upgrades which shared slots hold the upgraded prayer
	 * @return display names in prayer-book order
	 */
	public static List<String> names(Set<Prayer> varbitsOn, PrayerUpgrades upgrades)
	{
		List<String> names = new ArrayList<>();
		for (Prayer prayer : Prayer.values())
		{
			if (varbitsOn.contains(prayer) && isInEffect(prayer, upgrades))
			{
				names.add(displayName(prayer));
			}
		}
		return names;
	}

	private static boolean isInEffect(Prayer prayer, PrayerUpgrades upgrades)
	{
		switch (prayer)
		{
			case EAGLE_EYE:
				return !upgrades.isDeadeye();
			case DEADEYE:
				return upgrades.isDeadeye();
			case MYSTIC_MIGHT:
				return !upgrades.isMysticVigour();
			case MYSTIC_VIGOUR:
				return upgrades.isMysticVigour();
			default:
				return true;
		}
	}

	/**
	 * Derives the in-game name from RuneLite's constant ({@code PROTECT_FROM_MELEE} becomes "Protect from Melee");
	 * the Ruinous Powers prefix is dropped because the game shows those prayers without it.
	 *
	 * @param prayer the prayer
	 * @return the display name
	 */
	public static String displayName(Prayer prayer)
	{
		String constant = prayer.name();
		if (constant.startsWith(RUINOUS_POWERS_PREFIX))
		{
			constant = constant.substring(RUINOUS_POWERS_PREFIX.length());
		}
		List<String> words = Arrays.asList(constant.toLowerCase(Locale.ROOT).split("_"));
		return words.stream()
			.map(word -> CONNECTING_WORDS.contains(word) ? word : capitalise(word))
			.collect(Collectors.joining(" "));
	}

	private static String capitalise(String word)
	{
		return Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}
}
