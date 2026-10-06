/*
 * The quest-to-variable table below is derived from the Quest Helper plugin
 * (https://github.com/Zoinkwiz/quest-helper, commit 75b623a6bc14237831fddc10b44765c0910a4eb0,
 * questinfo/QuestHelperQuest.java, QuestVarbits.java and QuestVarPlayer.java), used under its licence:
 *
 * Copyright (c) 2020, Zoinkwiz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wiseoldclaude.quests;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarbitID;

/**
 * Which client variable holds each quest's progress, and how to read it: a varbit through
 * {@code client.getVarbitValue}, a varp through {@code client.getVarpValue}, as Quest Helper's
 * {@code QuestHelperQuest.getVar} does. The values are mostly undocumented, so they are exported only as a
 * change signal: a stage that moved since a journal was captured means the saved journal is outdated. Quests
 * missing from Quest Helper's table have no stage.
 */
final class QuestStageTable
{
	private static final Map<String, ToIntFunction<QuestVars>> STAGES = table();

	private QuestStageTable()
	{
	}

	/**
	 * @param quest quest name as RuneLite's {@link Quest} enum spells it
	 * @param vars reads the client's variables
	 * @return the quest's current progress value, or null when the table does not cover the quest
	 */
	static Integer stageOf(String quest, QuestVars vars)
	{
		ToIntFunction<QuestVars> read = STAGES.get(quest);
		return read == null ? null : read.applyAsInt(vars);
	}

	/**
	 * Reads stages only for quests in progress: a quest not started or finished has no step to be at, and
	 * skipping them keeps the export small.
	 *
	 * @param states every quest's state by name, in the order the stages should be listed
	 * @param vars reads the client's variables
	 * @return quest name to progress value for in-progress quests the table covers
	 */
	static Map<String, Integer> inProgressStages(Map<String, QuestState> states, QuestVars vars)
	{
		Map<String, Integer> stages = new LinkedHashMap<>();
		states.forEach((quest, state) ->
		{
			Integer stage = state == QuestState.IN_PROGRESS ? stageOf(quest, vars) : null;
			if (stage != null)
			{
				stages.put(quest, stage);
			}
		});
		return stages;
	}

	private static void varbit(Map<String, ToIntFunction<QuestVars>> stages, Quest quest, int varbitId)
	{
		stages.put(quest.getName(), vars -> vars.varbit(varbitId));
	}

	private static void varp(Map<String, ToIntFunction<QuestVars>> stages, Quest quest, int varpId)
	{
		stages.put(quest.getName(), vars -> vars.varp(varpId));
	}

	private static Map<String, ToIntFunction<QuestVars>> table()
	{
		Map<String, ToIntFunction<QuestVars>> stages = new HashMap<>();
		varbit(stages, Quest.BELOW_ICE_MOUNTAIN, VarbitID.BIM);
		varp(stages, Quest.BLACK_KNIGHTS_FORTRESS, 130);
		varp(stages, Quest.COOKS_ASSISTANT, 29);
		varbit(stages, Quest.THE_CORSAIR_CURSE, VarbitID.CORSCURS_PROGRESS);
		varbit(stages, Quest.DEMON_SLAYER, VarbitID.DEMONSLAYER_MAIN);
		varp(stages, Quest.DORICS_QUEST, 31);
		varp(stages, Quest.DRAGON_SLAYER_I, 176);
		varp(stages, Quest.ERNEST_THE_CHICKEN, 32);
		varbit(stages, Quest.GOBLIN_DIPLOMACY, VarbitID.GOBDIP_MAIN);
		varp(stages, Quest.IMP_CATCHER, 160);
		varbit(stages, Quest.THE_IDES_OF_MILK, VarbitID.COWQUEST);
		varp(stages, Quest.THE_KNIGHTS_SWORD, 122);
		varbit(stages, Quest.MISTHALIN_MYSTERY, VarbitID.MISTMYST_PROGRESS);
		varp(stages, Quest.PIRATES_TREASURE, 71);
		varp(stages, Quest.PRINCE_ALI_RESCUE, 273);
		varp(stages, Quest.THE_RESTLESS_GHOST, 107);
		varp(stages, Quest.ROMEO__JULIET, 144);
		varp(stages, Quest.RUNE_MYSTERIES, 63);
		varp(stages, Quest.SHEEP_SHEARER, 179);
		varp(stages, Quest.VAMPYRE_SLAYER, 178);
		varp(stages, Quest.WITCHS_POTION, 67);
		varbit(stages, Quest.X_MARKS_THE_SPOT, VarbitID.CLUEQUEST);
		varbit(stages, Quest.ANOTHER_SLICE_OF_HAM, VarbitID.SLICE_QUEST);
		varbit(stages, Quest.BENEATH_CURSED_SANDS, VarbitID.BCS);
		varbit(stages, Quest.BETWEEN_A_ROCK, VarbitID.DWARFROCK_QUEST);
		varp(stages, Quest.BIG_CHOMPY_BIRD_HUNTING, 293);
		varp(stages, Quest.BIOHAZARD, 68);
		varp(stages, Quest.CABIN_FEVER, 655);
		varp(stages, Quest.CLOCK_TOWER, 10);
		varbit(stages, Quest.COLD_WAR, VarbitID.PENG_QUEST);
		varbit(stages, Quest.CONTACT, VarbitID.CONTACT);
		varp(stages, Quest.CREATURE_OF_FENKENSTRAIN, 399);
		varbit(stages, Quest.DARKNESS_OF_HALLOWVALE, VarbitID.MYQ3_MAIN_QUEST);
		varp(stages, Quest.DEATH_PLATEAU, 314);
		varbit(stages, Quest.DEATH_TO_THE_DORGESHUUN, VarbitID.DTTD_MAIN);
		varbit(stages, Quest.THE_DEPTHS_OF_DESPAIR, VarbitID.HOSIDIUSQUEST);
		varbit(stages, Quest.DESERT_TREASURE_I, VarbitID.DESERTTREASURE);
		varbit(stages, Quest.DESERT_TREASURE_II__THE_FALLEN_EMPIRE, VarbitID.DT2);
		varbit(stages, Quest.DEVIOUS_MINDS, VarbitID.DEVIOUS_MAIN);
		varp(stages, Quest.THE_DIG_SITE, 131);
		varbit(stages, Quest.DRAGON_SLAYER_II, VarbitID.DS2);
		varbit(stages, Quest.DREAM_MENTOR, VarbitID.DREAM_PROG);
		varp(stages, Quest.DRUIDIC_RITUAL, 80);
		varp(stages, Quest.DWARF_CANNON, 0);
		varp(stages, Quest.EADGARS_RUSE, 335);
		varbit(stages, Quest.EAGLES_PEAK, VarbitID.EAGLEPEAK_QUEST);
		varp(stages, Quest.ELEMENTAL_WORKSHOP_I, 299);
		varbit(stages, Quest.ELEMENTAL_WORKSHOP_II, VarbitID.ELEMENTAL_QUEST_2_MAIN);
		varbit(stages, Quest.ENAKHRAS_LAMENT, VarbitID.ENAKH_QUEST);
		varbit(stages, Quest.ENLIGHTENED_JOURNEY, VarbitID.ZEP_QUEST);
		varbit(stages, Quest.THE_EYES_OF_GLOUPHRIE, VarbitID.EYEGLO_QUEST);
		varbit(stages, Quest.THE_PATH_OF_GLOUPHRIE, VarbitID.POG);
		varbit(stages, Quest.FAIRYTALE_I__GROWING_PAINS, VarbitID.FAIRY_FARMERS_QUEST);
		varbit(stages, Quest.FAIRYTALE_II__CURE_A_QUEEN, VarbitID.FAIRY2_QUEENCURE_QUEST);
		varp(stages, Quest.FAMILY_CREST, 148);
		varbit(stages, Quest.THE_FEUD, VarbitID.FEUD_VAR);
		varp(stages, Quest.FIGHT_ARENA, 17);
		varp(stages, Quest.FISHING_CONTEST, 11);
		varbit(stages, Quest.FORGETTABLE_TALE, VarbitID.FORGET_QUEST);
		varbit(stages, Quest.BONE_VOYAGE, VarbitID.FOSSILQUEST_PROGRESS);
		varbit(stages, Quest.THE_FREMENNIK_ISLES, VarbitID.FRIS_QUEST);
		varp(stages, Quest.THE_FREMENNIK_TRIALS, 347);
		varbit(stages, Quest.GARDEN_OF_TRANQUILLITY, VarbitID.GARDEN_QUEST);
		varp(stages, Quest.GERTRUDES_CAT, 180);
		varbit(stages, Quest.GHOSTS_AHOY, VarbitID.AHOY_QUESTVAR);
		varbit(stages, Quest.THE_GIANT_DWARF, VarbitID.GIANTDWARF_QUEST);
		varbit(stages, Quest.THE_GOLEM, VarbitID.GOLEM_A);
		varp(stages, Quest.THE_GRAND_TREE, 150);
		varp(stages, Quest.THE_GREAT_BRAIN_ROBBERY, 980);
		varbit(stages, Quest.GRIM_TALES, VarbitID.GRIM_QUEST);
		varbit(stages, Quest.THE_HAND_IN_THE_SAND, VarbitID.HANDSAND_QUEST);
		varp(stages, Quest.HAUNTED_MINE, 382);
		varp(stages, Quest.HAZEEL_CULT, 223);
		varp(stages, Quest.HEROES_QUEST, 188);
		varp(stages, Quest.HOLY_GRAIL, 5);
		varbit(stages, Quest.HORROR_FROM_THE_DEEP, VarbitID.HORRORQUEST);
		varbit(stages, Quest.ICTHLARINS_LITTLE_HELPER, VarbitID.ICS_LITTLE_VAR);
		varbit(stages, Quest.IN_AID_OF_THE_MYREQUE, VarbitID.MYREQUE_2_QUEST);
		varp(stages, Quest.IN_SEARCH_OF_THE_MYREQUE, 387);
		varp(stages, Quest.JUNGLE_POTION, 175);
		varbit(stages, Quest.KINGS_RANSOM, VarbitID.KR_QUEST);
		varbit(stages, Quest.LAND_OF_THE_GOBLINS, VarbitID.LOTG);
		varp(stages, Quest.LEGENDS_QUEST, 139);
		varp(stages, Quest.LOST_CITY, 147);
		varbit(stages, Quest.THE_LOST_TRIBE, VarbitID.LOST_TRIBE_QUEST);
		varbit(stages, Quest.LUNAR_DIPLOMACY, VarbitID.LUNAR_QUEST_MAIN);
		varbit(stages, Quest.MAKING_FRIENDS_WITH_MY_ARM, VarbitID.MY2ARM_STATUS);
		varbit(stages, Quest.MAKING_HISTORY, VarbitID.MAKINGHISTORY_PROG);
		varp(stages, Quest.MERLINS_CRYSTAL, 14);
		varp(stages, Quest.MONKEY_MADNESS_I, 365);
		varbit(stages, Quest.MONKEY_MADNESS_II, VarbitID.MM2_PROGRESS);
		varp(stages, Quest.MONKS_FRIEND, 30);
		varbit(stages, Quest.MOUNTAIN_DAUGHTER, VarbitID.MDAUGHTER_QUEST_VAR);
		varp(stages, Quest.MOURNINGS_END_PART_I, 517);
		varbit(stages, Quest.MOURNINGS_END_PART_II, VarbitID.MOURNING_QUEST_MAIN);
		varp(stages, Quest.MURDER_MYSTERY, 192);
		varbit(stages, Quest.MY_ARMS_BIG_ADVENTURE, VarbitID.MYARM);
		varp(stages, Quest.NATURE_SPIRIT, 307);
		varp(stages, Quest.OBSERVATORY_QUEST, 112);
		varbit(stages, Quest.OLAFS_QUEST, VarbitID.OLAF_QUEST_VAR);
		varp(stages, Quest.ONE_SMALL_FAVOUR, 416);
		varp(stages, Quest.PLAGUE_CITY, 165);
		varp(stages, Quest.PRIEST_IN_PERIL, 302);
		varbit(stages, Quest.THE_QUEEN_OF_THIEVES, VarbitID.PISCQUEST);
		varp(stages, Quest.RAG_AND_BONE_MAN_I, 714);
		varp(stages, Quest.RAG_AND_BONE_MAN_II, 714);
		varbit(stages, Quest.RATCATCHERS, VarbitID.RATCATCH_VAR);
		varbit(stages, Quest.RECRUITMENT_DRIVE, VarbitID.RD_MAIN);
		varp(stages, Quest.REGICIDE, 328);
		varp(stages, Quest.ROVING_ELVES, 402);
		varbit(stages, Quest.ROYAL_TROUBLE, VarbitID.ROYAL_QUEST);
		varp(stages, Quest.RUM_DEAL, 600);
		varp(stages, Quest.SCORPION_CATCHER, 76);
		varp(stages, Quest.SEA_SLUG, 159);
		varp(stages, Quest.SHADES_OF_MORTTON, 339);
		varbit(stages, Quest.SHADOW_OF_THE_STORM, VarbitID.AGRITH_QUEST);
		varp(stages, Quest.SHEEP_HERDER, 60);
		varp(stages, Quest.SHILO_VILLAGE, 116);
		varbit(stages, Quest.SLEEPING_GIANTS, VarbitID.SLEEPING_GIANTS);
		varbit(stages, Quest.THE_SLUG_MENACE, VarbitID.SLUG2_MAIN);
		varbit(stages, Quest.A_SOULS_BANE, VarbitID.SOULBANE_PROG);
		varbit(stages, Quest.SPIRITS_OF_THE_ELID, VarbitID.ELIDQUEST);
		varbit(stages, Quest.SWAN_SONG, VarbitID.SWANSONG);
		varp(stages, Quest.TAI_BWO_WANNAI_TRIO, 320);
		varbit(stages, Quest.A_TAIL_OF_TWO_CATS, VarbitID.TWOCATS_QUEST);
		varbit(stages, Quest.TALE_OF_THE_RIGHTEOUS, VarbitID.SHAYZIENQUEST);
		varbit(stages, Quest.A_TASTE_OF_HOPE, VarbitID.MYQ4);
		varbit(stages, Quest.TEARS_OF_GUTHIX, VarbitID.TOG_JUNA_BOWL);
		varp(stages, Quest.TEMPLE_OF_IKOV, 26);
		varbit(stages, Quest.TEMPLE_OF_THE_EYE, VarbitID.TOTE);
		varp(stages, Quest.THRONE_OF_MISCELLANIA, 359);
		varp(stages, Quest.THE_TOURIST_TRAP, 197);
		varbit(stages, Quest.TOWER_OF_LIFE, VarbitID.TOL_PROG);
		varp(stages, Quest.TREE_GNOME_VILLAGE, 111);
		varp(stages, Quest.TRIBAL_TOTEM, 200);
		varp(stages, Quest.TROLL_ROMANCE, 385);
		varp(stages, Quest.TROLL_STRONGHOLD, 317);
		varp(stages, Quest.UNDERGROUND_PASS, 161);
		varbit(stages, Quest.CLIENT_OF_KOUREND, VarbitID.VEOS_PROGRESS);
		varbit(stages, Quest.WANTED, VarbitID.WANTED_MAIN);
		varp(stages, Quest.WATCHTOWER, 212);
		varp(stages, Quest.WATERFALL_QUEST, 65);
		varbit(stages, Quest.WHAT_LIES_BELOW, VarbitID.SUROK_QUEST);
		varp(stages, Quest.WITCHS_HOUSE, 226);
		varbit(stages, Quest.ZOGRE_FLESH_EATERS, VarbitID.ZOGRE);
		varbit(stages, Quest.THE_ASCENT_OF_ARCEUUS, VarbitID.ARCQUEST);
		varbit(stages, Quest.THE_FORSAKEN_TOWER, VarbitID.LOVAQUEST);
		varbit(stages, Quest.SONG_OF_THE_ELVES, VarbitID.SOTE);
		varbit(stages, Quest.THE_FREMENNIK_EXILES, VarbitID.VIKINGEXILE);
		varbit(stages, Quest.SINS_OF_THE_FATHER, VarbitID.MYQ5);
		varbit(stages, Quest.GETTING_AHEAD, VarbitID.GA);
		varbit(stages, Quest.A_PORCINE_OF_INTEREST, VarbitID.PORCINE);
		varbit(stages, Quest.A_KINGDOM_DIVIDED, VarbitID.AKD);
		varbit(stages, Quest.A_NIGHT_AT_THE_THEATRE, VarbitID.TOBQUEST);
		varbit(stages, Quest.THE_GARDEN_OF_DEATH, VarbitID.TGOD);
		varbit(stages, Quest.SECRETS_OF_THE_NORTH, VarbitID.SOTN);
		varbit(stages, Quest.CHILDREN_OF_THE_SUN, VarbitID.VMQ1);
		varbit(stages, Quest.DEFENDER_OF_VARROCK, VarbitID.DOV);
		varbit(stages, Quest.AT_FIRST_LIGHT, VarbitID.AFL);
		varbit(stages, Quest.PERILOUS_MOONS, VarbitID.PMOON_QUEST);
		varbit(stages, Quest.THE_RIBBITING_TALE_OF_A_LILY_PAD_LABOUR_DISPUTE, VarbitID.FROG_QUEST);
		varbit(stages, Quest.TWILIGHTS_PROMISE, VarbitID.VMQ2);
		varbit(stages, Quest.WHILE_GUTHIX_SLEEPS, VarbitID.WGS);
		varbit(stages, Quest.ETHICALLY_ACQUIRED_ANTIQUITIES, VarbitID.EAA);
		varbit(stages, Quest.DEATH_ON_THE_ISLE, VarbitID.DOTI);
		varbit(stages, Quest.MEAT_AND_GREET, VarbitID.MAG);
		varbit(stages, Quest.THE_HEART_OF_DARKNESS, VarbitID.VMQ3);
		varbit(stages, Quest.THE_CURSE_OF_ARRAV, VarbitID.COA);
		varbit(stages, Quest.THE_FINAL_DAWN, VarbitID.VMQ4);
		varbit(stages, Quest.SHADOWS_OF_CUSTODIA, VarbitID.SOC);
		varbit(stages, Quest.SCRAMBLED, VarbitID.SCRAMBLED);
		varbit(stages, Quest.PANDEMONIUM, VarbitID.SAILING_INTRO);
		varbit(stages, Quest.PRYING_TIMES, VarbitID.QUEST_PRY);
		varbit(stages, Quest.CURRENT_AFFAIRS, VarbitID.CURRENT_AFFAIRS);
		varbit(stages, Quest.TROUBLED_TORTUGANS, VarbitID.TT);
		varbit(stages, Quest.THE_RED_REEF, VarbitID.TRR);
		varbit(stages, Quest.THE_BLOOD_MOON_RISES, VarbitID.MYQ6);
		varbit(stages, Quest.FALLEN_FROM_GRACE, VarbitID.FFG);
		varp(stages, Quest.ENTER_THE_ABYSS, 492);
		varbit(stages, Quest.BEAR_YOUR_SOUL, VarbitID.ARCEUUS_SOULBEARER_STORY);
		varp(stages, Quest.ALFRED_GRIMHANDS_BARCRAWL, 77);
		varbit(stages, Quest.CURSE_OF_THE_EMPTY_LORD, VarbitID.SECRET_GHOST6_BACKSTORY);
		varbit(stages, Quest.THE_ENCHANTED_KEY, VarbitID.MAKINGHISTORY_LOCSTATUS);
		varbit(stages, Quest.THE_GENERALS_SHADOW, VarbitID.SHADOW_MAJ_MAIN);
		varbit(stages, Quest.SKIPPY_AND_THE_MOGRES, VarbitID.SKIPPY_STATE);
		varp(stages, Quest.MAGE_ARENA_I, 267);
		varbit(stages, Quest.LAIR_OF_TARN_RAZORLOR, VarbitID.LOTR_INSTANCE_ENTERED);
		varbit(stages, Quest.FAMILY_PEST, VarbitID.FAMILY_QUEST_PROGRESS);
		varbit(stages, Quest.MAGE_ARENA_II, VarbitID.MA2_PROGRESS);
		varbit(stages, Quest.IN_SEARCH_OF_KNOWLEDGE, VarbitID.HOSDUN_KNOWLEDGE_SEARCH);
		varbit(stages, Quest.DADDYS_HOME, VarbitID.DADDYSHOME_STATUS);
		varbit(stages, Quest.HOPESPEARS_WILL, VarbitID.HOPESPEAR);
		varbit(stages, Quest.HIS_FAITHFUL_SERVANTS, VarbitID.HFS);
		varbit(stages, Quest.BARBARIAN_TRAINING, VarbitID.BRUT_MINIQUEST);
		varbit(stages, Quest.VALE_TOTEMS, VarbitID.ENT_TOTEMS_INTRO);
		return Collections.unmodifiableMap(stages);
	}
}
