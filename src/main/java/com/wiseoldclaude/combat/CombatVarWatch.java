package com.wiseoldclaude.combat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.Prayer;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Decides which variable changes make the snapshot's combat state or status stale, so a prayer flick or a
 * poisoning is written while the player stands still. Countdowns kept in ticks (divine potions, moonlight
 * potion, teleblock) are deliberately not watched: they change every tick and would force a write every
 * interval for the potion's whole duration; their seconds refresh with the next write and are read relative to
 * the snapshot's {@code capturedAt}.
 */
public final class CombatVarWatch
{
	private static final Set<Integer> VARPS = Set.of(
		VarPlayerID.COM_MODE,
		VarPlayerID.SA_ENERGY,
		VarPlayerID.POISON,
		VarPlayerID.DISEASE);

	private static final Set<Integer> VARBITS = Stream.concat(
		Stream.of(
			VarbitID.COMBAT_WEAPON_CATEGORY,
			VarbitID.AUTOCAST_DEFMODE,
			VarbitID.SPELLBOOK,
			VarbitID.STAMINA_ACTIVE,
			VarbitID.STAMINA_DURATION,
			VarbitID.ANTIFIRE_POTION,
			VarbitID.SUPER_ANTIFIRE_POTION,
			VarbitID.NZONE_OVERLOAD_POTION_EFFECTS,
			VarbitID.RAIDS_OVERLOAD_TIMER),
		Arrays.stream(Prayer.values()).map(Prayer::getVarbit))
		.collect(Collectors.toUnmodifiableSet());

	private CombatVarWatch()
	{
	}

	/**
	 * Whether a {@code VarbitChanged} event touches exported combat or status state.
	 *
	 * @param varpId the changed varp
	 * @param varbitId the changed varbit, -1 when the event is for a whole varp
	 * @return true when the snapshot should be rewritten
	 */
	public static boolean affectsSnapshot(int varpId, int varbitId)
	{
		return VARPS.contains(varpId) || VARBITS.contains(varbitId);
	}
}
