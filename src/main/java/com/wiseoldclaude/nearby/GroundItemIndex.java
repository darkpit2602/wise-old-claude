package com.wiseoldclaude.nearby;

import com.wiseoldclaude.common.GameNames;
import com.wiseoldclaude.model.GroundItemVariant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.Value;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;

/**
 * Reduces the items on the ground around the player to named stacks grouped by name, then by id and
 * ownership. Several unstackable items of one kind on one tile become one stack with their summed quantity,
 * the way the client's own ground-item labels count them, so a kill tile of three bones costs one entry.
 */
public class GroundItemIndex
{
	private final int maxStacks;

	/**
	 * @param maxStacks most stacks to keep; past it only the ones nearest the origin survive, so a cluttered
	 *   scene (a Grand Exchange drop party) cannot grow the file without bound while the stacks the player can
	 *   reach soonest remain
	 */
	public GroundItemIndex(int maxStacks)
	{
		this.maxStacks = maxStacks;
	}

	/**
	 * Builds the index from every item the scene's tiles hold.
	 *
	 * @param origin the player's tile, which defines "nearest" for ordering and the cap
	 * @param sightings every item on the ground
	 * @return item name, alphabetically, to its variants; variants and their stacks nearest first
	 */
	public IndexedGroundItems index(WorldPoint origin, Collection<GroundItemSighting> sightings)
	{
		List<Stack> ranked = mergeByTile(sightings).stream()
			.sorted(nearestFirst(origin))
			.collect(Collectors.toList());

		Map<String, Map<VariantKey, GroundItemVariant>> grouped = new TreeMap<>();
		for (Stack stack : SceneRanking.keepNearest(ranked, maxStacks))
		{
			grouped.computeIfAbsent(stack.sighting.getName(), name -> new LinkedHashMap<>())
				.computeIfAbsent(stack.key(), key -> stack.toVariant())
				.getStacks().add(stack.toTuple());
		}

		Map<String, List<GroundItemVariant>> items = new TreeMap<>();
		grouped.forEach((name, variants) -> items.put(name, new ArrayList<>(variants.values())));
		return new IndexedGroundItems(items, ranked.size() > maxStacks);
	}

	/**
	 * Names the client's ownership code. A code this plugin does not know is reported as another player's, so
	 * an unfamiliar stack is never suggested to an ironman who could not take it.
	 *
	 * @param code one of the {@code TileItem.OWNERSHIP_*} constants
	 * @return the ownership name written to the file
	 */
	static String ownershipName(int code)
	{
		switch (code)
		{
			case TileItem.OWNERSHIP_NONE:
				return "none";
			case TileItem.OWNERSHIP_SELF:
				return "self";
			case TileItem.OWNERSHIP_GROUP:
				return "group";
			default:
				return "other";
		}
	}

	private static Collection<Stack> mergeByTile(Collection<GroundItemSighting> sightings)
	{
		Map<List<Object>, Stack> stacks = new LinkedHashMap<>();
		for (GroundItemSighting sighting : sightings)
		{
			if (GameNames.isNamed(sighting.getName()))
			{
				stacks.computeIfAbsent(Stack.identity(sighting), identity -> new Stack(sighting))
					.quantity += sighting.getQuantity();
			}
		}
		return stacks.values();
	}

	/**
	 * The shared nearest-first order, then ownership and quantity, which only stacks have.
	 */
	private static Comparator<Stack> nearestFirst(WorldPoint origin)
	{
		return Comparator.comparing((Stack stack) -> stack.sighting, SceneRanking.<GroundItemSighting>nearestFirst(origin))
			.thenComparing(stack -> ownershipName(stack.sighting.getOwnership()))
			.thenComparingInt(stack -> stack.quantity);
	}

	/**
	 * Identifies a variant within one name: the id fixes the item and its prices, the ownership who may take it.
	 */
	@Value
	private static class VariantKey
	{
		int id;
		String ownership;
	}

	/**
	 * Every item of one id and ownership on one tile, with their quantities summed.
	 */
	private static final class Stack
	{
		private final GroundItemSighting sighting;
		private int quantity;

		private Stack(GroundItemSighting sighting)
		{
			this.sighting = sighting;
		}

		private static List<Object> identity(GroundItemSighting sighting)
		{
			return Arrays.asList(sighting.getName(), sighting.getId(), ownershipName(sighting.getOwnership()),
				sighting.getTile());
		}

		private VariantKey key()
		{
			return new VariantKey(sighting.getId(), ownershipName(sighting.getOwnership()));
		}

		private GroundItemVariant toVariant()
		{
			return new GroundItemVariant(sighting.getId(), ownershipName(sighting.getOwnership()),
				Math.max(0, sighting.getGePrice()), Math.max(0, sighting.getHaPrice()), new ArrayList<>());
		}

		private List<Integer> toTuple()
		{
			WorldPoint tile = sighting.getTile();
			return Arrays.asList(tile.getX(), tile.getY(), tile.getPlane(), quantity);
		}
	}
}
