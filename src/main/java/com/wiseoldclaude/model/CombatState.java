package com.wiseoldclaude.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * The player's combat setup and current fight, enough to answer "what am I fighting?" and to check advice
 * against the style, prayers and spellbook actually in use.
 */
@Value
@Builder(toBuilder = true)
public class CombatState
{
	/** Who the player is interacting with; null when not interacting, which is how a finished fight reads. */
	CombatTarget target;

	/** In-game style name ("Aggressive", "Defensive Casting"); null when the weapon's style is unknown. */
	String attackStyle;

	/** Null when the spellbook varbit holds a value outside the four known books. */
	Spellbook spellbook;

	List<String> activePrayers;
	int specialAttackPercent;
}
