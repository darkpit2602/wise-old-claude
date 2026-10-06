package com.wiseoldclaude.snapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.WorldType;

/**
 * Names the rulesets of the world the player is on ({@code Client#getWorldType}) in game terms. Mapped from an
 * explicit list rather than {@link WorldType#name()} so a flag RuneLite adds later can never leak a value the
 * file format does not know, and so {@code pvp} and {@code bounty_hunter} match the MCP server's {@code find_nearest}
 * world types exactly. {@code LEGACY_ONLY} and {@code EOC_ONLY} are client combat-display modes, not rulesets the
 * player's advice depends on, so they are left out.
 */
public final class WorldTypes
{
	private static final Map<WorldType, String> NAMES = new EnumMap<>(WorldType.class);

	static
	{
		NAMES.put(WorldType.MEMBERS, "members");
		NAMES.put(WorldType.PVP, "pvp");
		NAMES.put(WorldType.BOUNTY, "bounty_hunter");
		NAMES.put(WorldType.PVP_ARENA, "pvp_arena");
		NAMES.put(WorldType.SKILL_TOTAL, "skill_total");
		NAMES.put(WorldType.QUEST_SPEEDRUNNING, "quest_speedrunning");
		NAMES.put(WorldType.HIGH_RISK, "high_risk");
		NAMES.put(WorldType.LAST_MAN_STANDING, "last_man_standing");
		NAMES.put(WorldType.BETA_WORLD, "beta");
		NAMES.put(WorldType.NOSAVE_MODE, "no_save");
		NAMES.put(WorldType.TOURNAMENT_WORLD, "tournament");
		NAMES.put(WorldType.FRESH_START_WORLD, "fresh_start");
		NAMES.put(WorldType.DEADMAN, "deadman");
		NAMES.put(WorldType.SEASONAL, "seasonal");
	}

	private WorldTypes()
	{
	}

	/**
	 * Names the world's rulesets.
	 *
	 * @param types the world's type flags
	 * @return file-format names in {@link WorldType} declaration order; empty for a standard free-to-play world
	 */
	public static List<String> names(Set<WorldType> types)
	{
		List<String> names = new ArrayList<>();
		for (Map.Entry<WorldType, String> entry : NAMES.entrySet())
		{
			if (types.contains(entry.getKey()))
			{
				names.add(entry.getValue());
			}
		}
		return names;
	}
}
