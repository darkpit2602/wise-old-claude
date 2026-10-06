package com.wiseoldclaude.nearby;

import com.wiseoldclaude.common.GameNames;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

/**
 * Reduces everything the scene holds to what a player could be guided to: objects with a real name and at
 * least one right-click action (trees, rocks, doors, ranges), grouped by name so the file repeats each name
 * once. Scenery that only offers Examine is the bulk of a scene and never something to walk to, which is
 * why the action filter matters as much as the name filter.
 */
public class NearbyObjectIndex
{
	private final int maxObjects;

	/**
	 * @param maxObjects most objects to keep; past it only the ones nearest the origin survive, so a dense
	 *   scene cannot grow the file without bound while the nearest matches, the ones asked for, remain
	 */
	public NearbyObjectIndex(int maxObjects)
	{
		this.maxObjects = maxObjects;
	}

	/**
	 * Builds the index from everything the scene adapter saw.
	 *
	 * @param origin the player's tile, which defines "nearest" for ordering and the cap
	 * @param sightings every object seen, possibly repeated: a large object is seen from each tile it covers
	 * @return object name, alphabetically, to its tiles nearest first
	 */
	public IndexedObjects index(WorldPoint origin, Collection<ObjectSighting> sightings)
	{
		List<ObjectSighting> ranked = sightings.stream()
			.filter(NearbyObjectIndex::isInteractable)
			.collect(Collectors.toMap(NearbyObjectIndex::identity, Function.identity(), (first, repeat) -> first,
				LinkedHashMap::new))
			.values().stream()
			.sorted(SceneRanking.nearestFirst(origin))
			.collect(Collectors.toList());

		Map<String, List<List<Integer>>> grouped = new TreeMap<>();
		for (ObjectSighting sighting : SceneRanking.keepNearest(ranked, maxObjects))
		{
			grouped.computeIfAbsent(sighting.getName(), name -> new ArrayList<>()).add(tileOf(sighting));
		}
		return new IndexedObjects(grouped, ranked.size() > maxObjects);
	}

	private static boolean isInteractable(ObjectSighting sighting)
	{
		return GameNames.isNamed(sighting.getName()) && sighting.getActions() != null && Arrays.stream(sighting.getActions()).anyMatch(Objects::nonNull);
	}

	private static List<Object> identity(ObjectSighting sighting)
	{
		return Arrays.asList(sighting.getId(), sighting.getName(), sighting.getTile());
	}

	private static List<Integer> tileOf(ObjectSighting sighting)
	{
		WorldPoint tile = sighting.getTile();
		return Arrays.asList(tile.getX(), tile.getY(), tile.getPlane(), sighting.getId());
	}
}
