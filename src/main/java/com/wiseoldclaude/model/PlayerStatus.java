package com.wiseoldclaude.model;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Status that changes what the player should do next: whether they can run, how loaded they are, whether they
 * need a cure, and which protective or boosting effects are still running.
 */
@Value
@Builder
public class PlayerStatus
{
	int runEnergyPercent;

	/** Carried plus worn weight; negative with enough weight-reducing gear. */
	int weightKg;

	PoisonStatus poison;

	/** Damage of the next poison or venom hit; null when not poisoned. */
	Integer poisonDamage;

	boolean diseased;

	/** Effect name to seconds left as of the snapshot's capture; see {@code TimedEffects} for precision. */
	Map<String, Integer> effects;
}
