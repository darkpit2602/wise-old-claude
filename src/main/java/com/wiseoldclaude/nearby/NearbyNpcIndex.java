package com.wiseoldclaude.nearby;

import com.wiseoldclaude.common.GameNames;
import com.wiseoldclaude.model.NpcVariant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

/**
 * Reduces the NPCs around the player to what a player could be guided to: named NPCs the client lets them
 * click with at least one option (Attack, Talk-to, Cage, Bank), grouped by name and then by id. Ambient
 * NPCs that offer nothing are dropped for the same reason Examine-only scenery is dropped from the object
 * index: nobody walks to them.
 */
public class NearbyNpcIndex
{
	private final int maxNpcs;

	/**
	 * @param maxNpcs most NPCs to keep; past it only the ones nearest the origin survive, so a crowded scene
	 *   cannot grow the file without bound while the nearest matches, the ones asked for, remain
	 */
	public NearbyNpcIndex(int maxNpcs)
	{
		this.maxNpcs = maxNpcs;
	}

	/**
	 * Builds the index from every NPC the client lists.
	 *
	 * @param origin the player's tile, which defines "nearest" for ordering and the cap
	 * @param sightings every NPC in the scene
	 * @return NPC name, alphabetically, to its variants; variants and their tiles nearest first
	 */
	public IndexedNpcs index(WorldPoint origin, Collection<NpcSighting> sightings)
	{
		List<NpcSighting> ranked = sightings.stream()
			.filter(NearbyNpcIndex::isGuidable)
			.sorted(SceneRanking.nearestFirst(origin))
			.collect(Collectors.toList());

		Map<String, Map<Integer, VariantTiles>> grouped = new TreeMap<>();
		for (NpcSighting sighting : SceneRanking.keepNearest(ranked, maxNpcs))
		{
			grouped.computeIfAbsent(sighting.getName(), name -> new LinkedHashMap<>())
				.computeIfAbsent(sighting.getId(), id -> new VariantTiles(sighting))
				.add(sighting.getTile());
		}

		Map<String, List<NpcVariant>> npcs = new TreeMap<>();
		grouped.forEach((name, variants) -> npcs.put(name,
			variants.values().stream().map(VariantTiles::toVariant).collect(Collectors.toList())));
		return new IndexedNpcs(npcs, ranked.size() > maxNpcs);
	}

	private static boolean isGuidable(NpcSighting sighting)
	{
		return GameNames.isNamed(sighting.getName()) && sighting.isInteractible() && !compact(sighting.getActions()).isEmpty();
	}

	/**
	 * The client pads NPC options to five slots with nulls and some definitions repeat an option; neither
	 * tells the player anything.
	 */
	private static List<String> compact(String[] actions)
	{
		if (actions == null)
		{
			return new ArrayList<>();
		}
		return new ArrayList<>(Arrays.stream(actions)
			.filter(Objects::nonNull)
			.filter(action -> !action.isEmpty())
			.collect(Collectors.toCollection(LinkedHashSet::new)));
	}

	/**
	 * Collects one variant's tiles while the nearest-first walk visits them, so the first tile is the nearest
	 * and a tile two NPCs share is listed once.
	 */
	private static final class VariantTiles
	{
		private final NpcSighting first;
		private final LinkedHashSet<List<Integer>> tiles = new LinkedHashSet<>();

		private VariantTiles(NpcSighting first)
		{
			this.first = first;
		}

		private void add(WorldPoint tile)
		{
			tiles.add(Arrays.asList(tile.getX(), tile.getY(), tile.getPlane()));
		}

		private NpcVariant toVariant()
		{
			int combatLevel = Math.max(0, first.getCombatLevel());
			return new NpcVariant(first.getId(), combatLevel, compact(first.getActions()), new ArrayList<>(tiles));
		}
	}
}
