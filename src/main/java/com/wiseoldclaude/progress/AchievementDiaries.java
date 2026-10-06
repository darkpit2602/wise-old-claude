package com.wiseoldclaude.progress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarbitID;

/**
 * Decodes which achievement diary tiers are complete from the game's per-tier completion varbits. Every region's
 * {@code *_DIARY_*_COMPLETE} varbit reads 1 once the tier is complete, as RuneLite's daily tasks, clue scroll and
 * wiki DPS plugins test it. Karamja's easy, medium and hard tiers predate that scheme: their {@code ATJUN_*_DONE}
 * varbits read 1 once the tier is started and 2 once every task is done (OSRS Wiki, varbits 3578, 3599, 3611), so
 * a started tier must not be reported as complete. Only completion per tier is decoded: per-task progress is packed
 * into the {@code *_ACHIEVEMENT_DIARY} varps with no published mapping outside Karamja.
 */
public final class AchievementDiaries
{
	private static final int COMPLETE = 1;
	private static final int KARAMJA_LEGACY_COMPLETE = 2;

	private static final List<Region> REGIONS = List.of(
		new Region("Ardougne",
			complete(VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE),
			complete(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE),
			complete(VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE),
			complete(VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE)),
		new Region("Desert",
			complete(VarbitID.DESERT_DIARY_EASY_COMPLETE),
			complete(VarbitID.DESERT_DIARY_MEDIUM_COMPLETE),
			complete(VarbitID.DESERT_DIARY_HARD_COMPLETE),
			complete(VarbitID.DESERT_DIARY_ELITE_COMPLETE)),
		new Region("Falador",
			complete(VarbitID.FALADOR_DIARY_EASY_COMPLETE),
			complete(VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE),
			complete(VarbitID.FALADOR_DIARY_HARD_COMPLETE),
			complete(VarbitID.FALADOR_DIARY_ELITE_COMPLETE)),
		new Region("Fremennik",
			complete(VarbitID.FREMENNIK_DIARY_EASY_COMPLETE),
			complete(VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE),
			complete(VarbitID.FREMENNIK_DIARY_HARD_COMPLETE),
			complete(VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE)),
		new Region("Kandarin",
			complete(VarbitID.KANDARIN_DIARY_EASY_COMPLETE),
			complete(VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE),
			complete(VarbitID.KANDARIN_DIARY_HARD_COMPLETE),
			complete(VarbitID.KANDARIN_DIARY_ELITE_COMPLETE)),
		new Region("Karamja",
			new TierVar(VarbitID.ATJUN_EASY_DONE, KARAMJA_LEGACY_COMPLETE),
			new TierVar(VarbitID.ATJUN_MED_DONE, KARAMJA_LEGACY_COMPLETE),
			new TierVar(VarbitID.ATJUN_HARD_DONE, KARAMJA_LEGACY_COMPLETE),
			complete(VarbitID.KARAMJA_DIARY_ELITE_COMPLETE)),
		new Region("Kourend & Kebos",
			complete(VarbitID.KOUREND_DIARY_EASY_COMPLETE),
			complete(VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE),
			complete(VarbitID.KOUREND_DIARY_HARD_COMPLETE),
			complete(VarbitID.KOUREND_DIARY_ELITE_COMPLETE)),
		new Region("Lumbridge & Draynor",
			complete(VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE),
			complete(VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE),
			complete(VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE),
			complete(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)),
		new Region("Morytania",
			complete(VarbitID.MORYTANIA_DIARY_EASY_COMPLETE),
			complete(VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE),
			complete(VarbitID.MORYTANIA_DIARY_HARD_COMPLETE),
			complete(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE)),
		new Region("Varrock",
			complete(VarbitID.VARROCK_DIARY_EASY_COMPLETE),
			complete(VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE),
			complete(VarbitID.VARROCK_DIARY_HARD_COMPLETE),
			complete(VarbitID.VARROCK_DIARY_ELITE_COMPLETE)),
		new Region("Western Provinces",
			complete(VarbitID.WESTERN_DIARY_EASY_COMPLETE),
			complete(VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE),
			complete(VarbitID.WESTERN_DIARY_HARD_COMPLETE),
			complete(VarbitID.WESTERN_DIARY_ELITE_COMPLETE)),
		new Region("Wilderness",
			complete(VarbitID.WILDERNESS_DIARY_EASY_COMPLETE),
			complete(VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE),
			complete(VarbitID.WILDERNESS_DIARY_HARD_COMPLETE),
			complete(VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE)));

	private AchievementDiaries()
	{
	}

	private static TierVar complete(int varbit)
	{
		return new TierVar(varbit, COMPLETE);
	}

	/**
	 * Reads every region's completed tiers.
	 *
	 * @param varbits reads a varbit's current value, {@code Client#getVarbitValue} in the live client
	 * @return in-game region name to its completed tiers (EASY to ELITE), every region present in diary-tab order
	 */
	public static Map<String, List<String>> decode(IntUnaryOperator varbits)
	{
		Map<String, List<String>> diaries = new LinkedHashMap<>();
		for (Region region : REGIONS)
		{
			diaries.put(region.name, region.completedTiers(varbits));
		}
		return diaries;
	}

	/** One tier's completion varbit and the value it holds once the tier is complete. */
	private static final class TierVar
	{
		private final int varbit;
		private final int completeValue;

		TierVar(int varbit, int completeValue)
		{
			this.varbit = varbit;
			this.completeValue = completeValue;
		}

		boolean isComplete(IntUnaryOperator varbits)
		{
			return varbits.applyAsInt(varbit) == completeValue;
		}
	}

	/**
	 * A diary region and its tiers' completion varbits. Each tier is a named parameter rather than a position in a
	 * list, so a region can only be declared with the four tiers {@link TierNames} names.
	 */
	private static final class Region
	{
		private final String name;
		private final Map<String, TierVar> tiers = new LinkedHashMap<>();

		Region(String name, TierVar easy, TierVar medium, TierVar hard, TierVar elite)
		{
			this.name = name;
			tiers.put(TierNames.EASY, easy);
			tiers.put(TierNames.MEDIUM, medium);
			tiers.put(TierNames.HARD, hard);
			tiers.put(TierNames.ELITE, elite);
		}

		List<String> completedTiers(IntUnaryOperator varbits)
		{
			List<String> completed = new ArrayList<>();
			tiers.forEach((tier, completion) ->
			{
				if (completion.isComplete(varbits))
				{
					completed.add(tier);
				}
			});
			return completed;
		}
	}
}
