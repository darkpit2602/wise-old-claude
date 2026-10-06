package com.wiseoldclaude.snapshot;

import com.wiseoldclaude.combat.ActivePrayers;
import com.wiseoldclaude.combat.AttackStyles;
import com.wiseoldclaude.combat.HealthPercent;
import com.wiseoldclaude.combat.PrayerUpgrades;
import com.wiseoldclaude.combat.TimedEffectVars;
import com.wiseoldclaude.combat.TimedEffects;
import com.wiseoldclaude.common.Positions;
import com.wiseoldclaude.model.AccountType;
import com.wiseoldclaude.model.Bank;
import com.wiseoldclaude.model.CombatAchievements;
import com.wiseoldclaude.model.CombatState;
import com.wiseoldclaude.model.CombatTarget;
import com.wiseoldclaude.model.ExchangeOffer;
import com.wiseoldclaude.model.ItemStack;
import com.wiseoldclaude.model.PlayerSnapshot;
import com.wiseoldclaude.model.PlayerStatus;
import com.wiseoldclaude.model.PoisonStatus;
import com.wiseoldclaude.model.Progress;
import com.wiseoldclaude.model.SkillLevel;
import com.wiseoldclaude.model.SlayerProgress;
import com.wiseoldclaude.model.SlayerTask;
import com.wiseoldclaude.model.Spellbook;
import com.wiseoldclaude.progress.AchievementDiaries;
import com.wiseoldclaude.progress.CombatAchievementTiers;
import com.wiseoldclaude.progress.SlayerStreaks;
import com.wiseoldclaude.progress.SlayerTasks;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

/**
 * Adapter that reads live client state into a {@link PlayerSnapshot}. Kept free of logic beyond mapping so
 * everything decision-bearing lives in classes testable without a running client.
 * Must be called on the client thread: item compositions and varbits are only safe to read there.
 */
public class SnapshotCollector
{
	/** {@code Client#getEnergy} counts hundredths of a percent. */
	private static final int RUN_ENERGY_UNITS_PER_PERCENT = 100;

	/** {@code VarPlayerID.SA_ENERGY} counts tenths of a percent, as RuneLite's status bars divide it. */
	private static final int SPECIAL_ATTACK_UNITS_PER_PERCENT = 10;

	private final Client client;
	private final ItemManager itemManager;

	@Inject
	public SnapshotCollector(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/**
	 * Captures the current state of the logged-in character.
	 *
	 * @return the snapshot, or null while no player is loaded (login screen, hopping)
	 */
	public PlayerSnapshot collect()
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getName() == null)
		{
			return null;
		}

		AccountType accountType = AccountType.fromVarbit(client.getVarbitValue(VarbitID.IRONMAN));
		return PlayerSnapshot.builder()
			.rsn(player.getName())
			.accountType(accountType)
			.capturedAt(Instant.now().toString())
			.world(client.getWorld())
			.combatLevel(player.getCombatLevel())
			.position(Positions.of(player.getWorldLocation()))
			.skills(skills())
			.inventory(inventory())
			.equipment(equipment())
			.combat(combat(player))
			.status(status())
			.gameRevision(client.getRevision())
			.progress(progress())
			.worldTypes(WorldTypes.names(client.getWorldType()))
			.nearbyPlayers(nearbyPlayers(player))
			.grandExchange(accountType.canUseGrandExchange() ? grandExchange() : null)
			.teleportCooldowns(TeleportCasts.cooldowns(
				client.getVarpValue(VarPlayerID.AIDE_TELE_TIMER),
				client.getVarpValue(VarPlayerID.SLUG2_REGIONUID)))
			.build();
	}

	/**
	 * Reads slayer, diary and combat achievement progress. Session XP is left null: it needs the baseline the
	 * plugin keeps across snapshots, so the plugin fills it in.
	 *
	 * @return progress without session XP
	 */
	private Progress progress()
	{
		return Progress.builder()
			.slayer(slayer())
			.diaries(AchievementDiaries.decode(client::getVarbitValue))
			.combatAchievements(new CombatAchievements(
				client.getVarbitValue(VarbitID.CA_POINTS),
				CombatAchievementTiers.completed(client::getVarbitValue)))
			.build();
	}

	private SlayerProgress slayer()
	{
		SlayerStreaks streaks = new SlayerStreaks(
			client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED),
			client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED),
			client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED));
		return new SlayerProgress(
			slayerTask(),
			client.getVarbitValue(VarbitID.SLAYER_POINTS),
			streaks.forMaster(client.getVarbitValue(VarbitID.SLAYER_MASTER)));
	}

	/**
	 * Resolves the task's name and area through the game cache's slayer tables, as RuneLite's slayer plugin does.
	 * A failed lookup leaves that part null instead of dropping the task, so the kill count still reaches Claude.
	 */
	private SlayerTask slayerTask()
	{
		int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		if (!SlayerTasks.isAssigned(remaining))
		{
			return null;
		}
		Integer taskRow = slayerTaskRow(client.getVarpValue(VarPlayerID.SLAYER_TARGET));
		String name = taskRow == null ? null : (String) firstField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE);
		String area = slayerArea(client.getVarpValue(VarPlayerID.SLAYER_AREA));
		return new SlayerTask(SlayerTasks.displayName(name), remaining, area);
	}

	private Integer slayerTaskRow(int targetId)
	{
		if (SlayerTasks.isBossTask(targetId))
		{
			List<Integer> bossRows = client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
				DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
			return bossRows.isEmpty() ? null : (Integer) firstField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK);
		}
		List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, targetId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	private String slayerArea(int areaId)
	{
		if (!SlayerTasks.hasArea(areaId))
		{
			return null;
		}
		List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
		return rows.isEmpty() ? null : (String) firstField(rows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER);
	}

	private Object firstField(int row, int column)
	{
		Object[] values = client.getDBTableField(row, column, 0);
		return values.length == 0 ? null : values[0];
	}

	/**
	 * Counts the other players in the loaded scene. Only the number is exported, never who they are or where.
	 *
	 * @param local the local player, excluded from the count
	 * @return other players nearby, or null while no scene is loaded
	 */
	private Integer nearbyPlayers(Player local)
	{
		WorldView view = client.getTopLevelWorldView();
		if (view == null)
		{
			return null;
		}
		return (int) view.players().stream().filter(other -> other != null && other != local).count();
	}

	/**
	 * Reads the combat setup and the player's current target.
	 *
	 * @param player the local player
	 * @return the combat state
	 */
	private CombatState combat(Player player)
	{
		return CombatState.builder()
			.target(targetOf(player.getInteracting()))
			.attackStyle(AttackStyles.decode(
				weaponStyleNames(client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY)),
				client.getVarpValue(VarPlayerID.COM_MODE),
				client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE)))
			.spellbook(Spellbook.fromVarbit(client.getVarbitValue(VarbitID.SPELLBOOK)))
			.activePrayers(ActivePrayers.names(prayersOn(), PrayerUpgrades.fromVarbits(
				client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED),
				client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED),
				client.getVarbitValue(VarbitID.BR_INGAME))))
			.specialAttackPercent(client.getVarpValue(VarPlayerID.SA_ENERGY) / SPECIAL_ATTACK_UNITS_PER_PERCENT)
			.build();
	}

	/**
	 * Reads run energy, weight, poison, disease and timed effects.
	 *
	 * @return the status
	 */
	private PlayerStatus status()
	{
		int poison = client.getVarpValue(VarPlayerID.POISON);
		return PlayerStatus.builder()
			.runEnergyPercent(client.getEnergy() / RUN_ENERGY_UNITS_PER_PERCENT)
			.weightKg(client.getWeight())
			.poison(PoisonStatus.fromVarp(poison))
			.poisonDamage(PoisonStatus.nextHit(poison))
			.diseased(client.getVarpValue(VarPlayerID.DISEASE) > 0)
			.effects(TimedEffects.decode(timedEffectVars(poison)))
			.build();
	}

	/**
	 * Only another player's combat level leaves the client; NPCs are named so Claude can look them up.
	 */
	private static CombatTarget targetOf(Actor actor)
	{
		if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			return CombatTarget.npc(npc.getName(), npc.getCombatLevel(), npc.getId())
				.withHpPercent(HealthPercent.of(npc.getHealthRatio(), npc.getHealthScale()));
		}
		if (actor instanceof Player)
		{
			return CombatTarget.player(actor.getCombatLevel());
		}
		return null;
	}

	/**
	 * The equipped weapon's style names per slot, read from the game cache as RuneLite's attack styles plugin
	 * does (weapon-styles enum, then each style struct's name param).
	 */
	private List<String> weaponStyleNames(int weaponCategory)
	{
		int stylesEnum = client.getEnum(EnumID.WEAPON_STYLES).getIntValue(weaponCategory);
		if (stylesEnum == -1)
		{
			return AttackStyles.fallbackStyles(weaponCategory);
		}
		List<String> names = new ArrayList<>();
		for (int structId : client.getEnum(stylesEnum).getIntVals())
		{
			names.add(client.getStructComposition(structId).getStringValue(ParamID.ATTACK_STYLE_NAME));
		}
		return names;
	}

	private Set<Prayer> prayersOn()
	{
		Set<Prayer> on = EnumSet.noneOf(Prayer.class);
		for (Prayer prayer : Prayer.values())
		{
			if (client.getVarbitValue(prayer.getVarbit()) != 0)
			{
				on.add(prayer);
			}
		}
		return on;
	}

	private TimedEffectVars timedEffectVars(int poison)
	{
		return TimedEffectVars.builder()
			.staminaActive(client.getVarbitValue(VarbitID.STAMINA_ACTIVE))
			.staminaDuration(client.getVarbitValue(VarbitID.STAMINA_DURATION))
			.antifire(client.getVarbitValue(VarbitID.ANTIFIRE_POTION))
			.superAntifire(client.getVarbitValue(VarbitID.SUPER_ANTIFIRE_POTION))
			.poison(poison)
			.nmzOverload(client.getVarbitValue(VarbitID.NZONE_OVERLOAD_POTION_EFFECTS))
			.raidsOverload(client.getVarbitValue(VarbitID.RAIDS_OVERLOAD_TIMER))
			.divineSuperAttack(client.getVarbitValue(VarbitID.DIVINEATTACK_POTION_TIME))
			.divineSuperStrength(client.getVarbitValue(VarbitID.DIVINESTRENGTH_POTION_TIME))
			.divineSuperDefence(client.getVarbitValue(VarbitID.DIVINEDEFENCE_POTION_TIME))
			.divineRanging(client.getVarbitValue(VarbitID.DIVINERANGE_POTION_TIME))
			.divineMagic(client.getVarbitValue(VarbitID.DIVINEMAGIC_POTION_TIME))
			.divineSuperCombat(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME))
			.divineBastion(client.getVarbitValue(VarbitID.DIVINEBASTION_POTION_TIME))
			.divineBattlemage(client.getVarbitValue(VarbitID.DIVINEBATTLEMAGE_POTION_TIME))
			.moonlightPotion(client.getVarbitValue(VarbitID.MOONLIGHT_POTION_TIME))
			.teleblock(client.getVarbitValue(VarbitID.TELEBLOCK_CYCLES))
			.build();
	}

	/**
	 * Captures the bank from its container. Only meaningful while the bank is open, which is why callers
	 * capture on the container's change event and remember the result.
	 *
	 * @param container the bank container ({@code InventoryID.BANK})
	 * @return the owned bank items, timestamped now
	 */
	public Bank bankOf(ItemContainer container)
	{
		List<ItemStack> items = new ArrayList<>();
		for (Item item : container.getItems())
		{
			if (BankContents.isStored(item))
			{
				items.add(stackOf(item));
			}
		}
		return new Bank(Instant.now().toString(), items);
	}

	private Map<String, SkillLevel> skills()
	{
		Map<String, SkillLevel> skills = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			skills.put(skill.name(), new SkillLevel(
				client.getRealSkillLevel(skill),
				client.getSkillExperience(skill),
				client.getBoostedSkillLevel(skill)));
		}
		return skills;
	}

	private List<ItemStack> inventory()
	{
		ItemContainer container = client.getItemContainer(InventoryID.INV);
		if (container == null)
		{
			return Collections.emptyList();
		}
		List<ItemStack> items = new ArrayList<>();
		for (Item item : container.getItems())
		{
			if (isPresent(item))
			{
				items.add(stackOf(item));
			}
		}
		return items;
	}

	private Map<String, ItemStack> equipment()
	{
		Map<String, ItemStack> equipped = new LinkedHashMap<>();
		ItemContainer container = client.getItemContainer(InventoryID.WORN);
		if (container == null)
		{
			return equipped;
		}
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			Item item = container.getItem(slot.getSlotIdx());
			if (isPresent(item))
			{
				equipped.put(slot.name(), stackOf(item));
			}
		}
		return equipped;
	}

	private List<ExchangeOffer> grandExchange()
	{
		return ExchangeOffers.of(client.getGrandExchangeOffers(), id -> itemManager.getItemComposition(id).getName());
	}

	private static boolean isPresent(Item item)
	{
		return item != null && item.getId() >= 0 && item.getQuantity() > 0;
	}

	private ItemStack stackOf(Item item)
	{
		String name = itemManager.getItemComposition(item.getId()).getName();
		return new ItemStack(item.getId(), name, item.getQuantity());
	}
}
