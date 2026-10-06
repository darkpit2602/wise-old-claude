package com.wiseoldclaude.model;

import java.util.List;
import lombok.Value;

/**
 * Every NPC of one id sharing a name. Same-named NPCs differ by id in what they offer: one "Fishing spot" id
 * offers Cage and Harpoon, another Net and Bait, and two goblins can differ in combat level. An id fixes all
 * of these, so grouping by it states each once however many of that NPC stand around.
 */
@Value
public class NpcVariant
{
	int id;

	/**
	 * 0 when the NPC has no combat level.
	 */
	int combatLevel;

	/**
	 * Right-click options in menu order, without blanks or repeats.
	 */
	List<String> actions;

	/**
	 * {@code [x, y, plane]} tiles, nearest the index origin first; lists rather than objects so each costs a
	 * few bytes in the file.
	 */
	List<List<Integer>> tiles;
}
