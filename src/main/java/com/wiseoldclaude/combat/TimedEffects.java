package com.wiseoldclaude.combat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decodes timed effects into seconds left, mirroring the units and rules of RuneLite's timers and buffs plugin
 * ({@code TimersAndBuffsPlugin#onVarbitChanged}). Effects counted down in multi-tick steps (antifire, super
 * antifire, overload, stamina, antipoison, antivenom) only know which step they are in, so their seconds are an
 * upper bound accurate to one step.
 */
public final class TimedEffects
{
	private static final int ANTIFIRE_STEP_TICKS = 30;
	private static final int SUPER_ANTIFIRE_STEP_TICKS = 20;
	private static final int STAMINA_STEP_TICKS = 10;
	private static final int OVERLOAD_STEP_TICKS = 25;
	private static final int POISON_STEP_TICKS = 30;

	/** Antivenom protection is encoded below this poison value, antipoison between it and zero. */
	private static final int VENOM_PROTECTION_CUTOFF = -38;

	private static final int TELEBLOCK_OFFSET = 100;

	private TimedEffects()
	{
	}

	/**
	 * Lists the effects in force. A divine potion whose effect is already covered by a combined potion lasting at
	 * least as long (for example divine super attack under divine super combat) is left out, as RuneLite's timers
	 * do, so one potion is not reported as four.
	 *
	 * @param vars the raw values
	 * @return effect name to whole seconds left, empty when nothing is active
	 */
	public static Map<String, Integer> decode(TimedEffectVars vars)
	{
		Map<String, Integer> effects = new LinkedHashMap<>();
		if (vars.getStaminaActive() == 1)
		{
			putTicks(effects, "Stamina", vars.getStaminaDuration() * STAMINA_STEP_TICKS);
		}
		putTicks(effects, "Antifire", vars.getAntifire() * ANTIFIRE_STEP_TICKS);
		putTicks(effects, "Super antifire", vars.getSuperAntifire() * SUPER_ANTIFIRE_STEP_TICKS);
		putPoisonProtection(effects, vars.getPoison());
		putTicks(effects, "Overload", Math.max(vars.getNmzOverload(), vars.getRaidsOverload()) * OVERLOAD_STEP_TICKS);
		putDivinePotions(effects, vars);
		putTicks(effects, "Teleblock", vars.getTeleblock() - TELEBLOCK_OFFSET);
		return effects;
	}

	private static void putPoisonProtection(Map<String, Integer> effects, int poison)
	{
		if (poison < VENOM_PROTECTION_CUTOFF)
		{
			putTicks(effects, "Antivenom", (VENOM_PROTECTION_CUTOFF - poison) * POISON_STEP_TICKS);
		}
		else if (poison < 0)
		{
			putTicks(effects, "Antipoison", -poison * POISON_STEP_TICKS);
		}
	}

	private static void putDivinePotions(Map<String, Integer> effects, TimedEffectVars vars)
	{
		int combat = vars.getDivineSuperCombat();
		int bastion = vars.getDivineBastion();
		int battlemage = vars.getDivineBattlemage();
		int moonlight = vars.getMoonlightPotion();
		int moonlightCover = moonlight > 0 ? moonlight + 1 : 0;
		int defenceCover = Math.max(Math.max(combat, bastion), Math.max(battlemage, moonlightCover));
		putTicks(effects, "Divine super attack", uncovered(vars.getDivineSuperAttack(), combat));
		putTicks(effects, "Divine super strength", uncovered(vars.getDivineSuperStrength(), combat));
		putTicks(effects, "Divine super defence", uncovered(vars.getDivineSuperDefence(), defenceCover));
		putTicks(effects, "Divine ranging", uncovered(vars.getDivineRanging(), bastion));
		putTicks(effects, "Divine magic", uncovered(vars.getDivineMagic(), battlemage));
		putTicks(effects, "Divine super combat", combat);
		putTicks(effects, "Divine bastion", bastion);
		putTicks(effects, "Divine battlemage", battlemage);
		putTicks(effects, "Moonlight potion", moonlight);
	}

	/**
	 * A single-stat potion's remaining ticks, unless a combined potion covers it. Moonlight potion's cover counts one
	 * tick extra because the game can leave divine super defence one tick ahead of it after a re-dose, which RuneLite
	 * also tolerates.
	 *
	 * @param ticks the single-stat potion's remaining ticks
	 * @param coveringTicks the remaining ticks of the combined potion that would cover it
	 * @return the single-stat potion's ticks, or 0 when the combined potion outlasts it, which leaves it unlisted
	 */
	private static int uncovered(int ticks, int coveringTicks)
	{
		return ticks > coveringTicks ? ticks : 0;
	}

	private static void putTicks(Map<String, Integer> effects, String name, int ticks)
	{
		if (ticks > 0)
		{
			effects.put(name, secondsFor(ticks));
		}
	}

	private static int secondsFor(int ticks)
	{
		return (ticks * 3 + 4) / 5;
	}
}
