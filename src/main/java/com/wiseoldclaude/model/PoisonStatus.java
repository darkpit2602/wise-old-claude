package com.wiseoldclaude.model;

/**
 * Poison state decoded from varp 102 ({@code VarPlayerID.POISON}), following RuneLite's poison plugin
 * ({@code PoisonPlugin#nextDamage} and its heart-icon choice): zero is clean, positive values below
 * {@value #VENOM_THRESHOLD} are poison, values from it upwards are venom, and negative values are antipoison or
 * antivenom protection (reported as timed effects, not here).
 */
public enum PoisonStatus
{
	NONE,
	POISONED,
	ENVENOMED;

	private static final int VENOM_THRESHOLD = 1000000;
	private static final int VENOM_MAXIMUM_DAMAGE = 20;
	private static final float POISON_DAMAGE_DIVISOR = 5.0f;

	/**
	 * Decodes the varp.
	 *
	 * @param value raw value of varp 102
	 * @return the poison state
	 */
	public static PoisonStatus fromVarp(int value)
	{
		if (value >= VENOM_THRESHOLD)
		{
			return ENVENOMED;
		}
		return value > 0 ? POISONED : NONE;
	}

	/**
	 * The damage of the next poison or venom hit, which tells the player how urgently to cure it: poison
	 * weakens over time, venom grows by two per hit up to {@value #VENOM_MAXIMUM_DAMAGE}.
	 *
	 * @param value raw value of varp 102
	 * @return the next hit's damage, or null when not poisoned
	 */
	public static Integer nextHit(int value)
	{
		if (value >= VENOM_THRESHOLD)
		{
			return Math.min(VENOM_MAXIMUM_DAMAGE, (value - (VENOM_THRESHOLD - 3)) * 2);
		}
		return value > 0 ? (int) Math.ceil(value / POISON_DAMAGE_DIVISOR) : null;
	}
}
