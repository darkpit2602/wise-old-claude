package com.wiseoldclaude.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

/**
 * What the player is interacting with. Another player is reduced to its combat level: exporting other players'
 * names is out of scope and the Plugin Hub rejects plugins that collect them, so the
 * factories make a named player target impossible to build.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CombatTarget
{
	TargetKind kind;
	String name;
	int combatLevel;
	Integer npcId;

	/** Rounded-up percentage of the health bar; null until the target's bar has been shown. */
	@With
	Integer hpPercent;

	/**
	 * A non-player character, named so Claude can look it up. Its health is added with
	 * {@link #withHpPercent(Integer)} once known.
	 *
	 * @param name the NPC's name
	 * @param combatLevel its combat level as {@code NPC#getCombatLevel} reports it, -1 for non-combat NPCs; stored as
	 *   0 so the export never carries a negative level
	 * @param npcId its id, which tells apart same-named variants
	 * @return the target, health unknown
	 */
	public static CombatTarget npc(String name, int combatLevel, int npcId)
	{
		return new CombatTarget(TargetKind.NPC, name, Math.max(0, combatLevel), npcId, null);
	}

	/**
	 * Another player, anonymised to its combat level.
	 *
	 * @param combatLevel the player's combat level
	 * @return the target
	 */
	public static CombatTarget player(int combatLevel)
	{
		return new CombatTarget(TargetKind.PLAYER, null, combatLevel, null, null);
	}

	/** Whether the target is a non-player character or another player. */
	public enum TargetKind
	{
		NPC,
		PLAYER
	}
}
