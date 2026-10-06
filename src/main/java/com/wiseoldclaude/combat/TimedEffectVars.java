package com.wiseoldclaude.combat;

import lombok.Builder;
import lombok.Value;

/**
 * Raw values of the varbits and varps behind timed effects, read on the client thread and decoded by
 * {@link TimedEffects}. Each field names its source; units differ per effect and are applied by the decoder.
 * Unset fields default to zero, which every source uses for "not active".
 */
@Value
@Builder
public class TimedEffectVars
{
	/** {@code VarbitID.STAMINA_ACTIVE}: 1 while a stamina effect runs. */
	int staminaActive;
	/** {@code VarbitID.STAMINA_DURATION}: remaining time in 10-tick units. */
	int staminaDuration;
	/** {@code VarbitID.ANTIFIRE_POTION}: remaining 30-tick steps. */
	int antifire;
	/** {@code VarbitID.SUPER_ANTIFIRE_POTION}: remaining 20-tick steps. */
	int superAntifire;
	/** {@code VarPlayerID.POISON}: negative while antipoison or antivenom protects. */
	int poison;
	/** {@code VarbitID.NZONE_OVERLOAD_POTION_EFFECTS}: remaining 25-tick steps. */
	int nmzOverload;
	/** {@code VarbitID.RAIDS_OVERLOAD_TIMER}: remaining 25-tick steps. */
	int raidsOverload;
	/** {@code VarbitID.DIVINEATTACK_POTION_TIME}, in ticks, as are the other divine and moonlight fields. */
	int divineSuperAttack;
	/** {@code VarbitID.DIVINESTRENGTH_POTION_TIME}. */
	int divineSuperStrength;
	/** {@code VarbitID.DIVINEDEFENCE_POTION_TIME}. */
	int divineSuperDefence;
	/** {@code VarbitID.DIVINERANGE_POTION_TIME}. */
	int divineRanging;
	/** {@code VarbitID.DIVINEMAGIC_POTION_TIME}. */
	int divineMagic;
	/** {@code VarbitID.DIVINECOMBAT_POTION_TIME}. */
	int divineSuperCombat;
	/** {@code VarbitID.DIVINEBASTION_POTION_TIME}. */
	int divineBastion;
	/** {@code VarbitID.DIVINEBATTLEMAGE_POTION_TIME}. */
	int divineBattlemage;
	/** {@code VarbitID.MOONLIGHT_POTION_TIME}. */
	int moonlightPotion;
	/** {@code VarbitID.TELEBLOCK_CYCLES}: remaining ticks plus 100. */
	int teleblock;
}
