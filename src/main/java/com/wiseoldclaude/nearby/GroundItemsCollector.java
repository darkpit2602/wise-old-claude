package com.wiseoldclaude.nearby;

import com.wiseoldclaude.common.Positions;
import com.wiseoldclaude.model.GroundItems;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

/**
 * Adapter that reads the items on every loaded scene tile into a {@link GroundItems} index. Kept free of
 * logic beyond mapping and price lookup; grouping and capping are decided by {@link GroundItemIndex}. Reads
 * each item's ownership code, never who dropped it. Must be called on the client thread: item definitions
 * are only safe to read there.
 */
public class GroundItemsCollector
{
	/**
	 * A busy scene (a popular boss, the Grand Exchange) holds an estimated few hundred stacks; this keeps all
	 * of them while bounding a pathological scene's file to a few tens of kilobytes.
	 */
	static final int MAX_STACKS = 500;

	private final Client client;
	private final ItemManager itemManager;
	private final GroundItemIndex index = new GroundItemIndex(MAX_STACKS);

	@Inject
	public GroundItemsCollector(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/**
	 * Indexes the ground items around the logged-in character.
	 *
	 * @return the index, or null while no player or scene is loaded (login screen, hopping)
	 */
	public GroundItems collect()
	{
		Player player = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		if (player == null || player.getName() == null || view == null)
		{
			return null;
		}

		WorldPoint origin = player.getWorldLocation();
		IndexedGroundItems indexed = index.index(origin, sightingsIn(view.getScene()));
		return GroundItems.builder()
			.rsn(player.getName())
			.capturedAt(Instant.now().toString())
			.world(client.getWorld())
			.instance(view.isInstance())
			.origin(Positions.of(origin))
			.truncated(indexed.isTruncated())
			.items(indexed.getItems())
			.build();
	}

	private List<GroundItemSighting> sightingsIn(Scene scene)
	{
		List<GroundItemSighting> sightings = new ArrayList<>();
		SceneTiles.forEach(scene, tile -> addItemsOn(tile, sightings));
		return sightings;
	}

	/**
	 * Treats a missing ground-item list like an empty one: the API does not promise a non-null list, and a
	 * scene has thousands of tiles without items.
	 */
	private void addItemsOn(Tile tile, List<GroundItemSighting> sightings)
	{
		if (tile == null || tile.getGroundItems() == null)
		{
			return;
		}
		for (TileItem item : tile.getGroundItems())
		{
			sightings.add(sightingOf(item, tile.getWorldLocation()));
		}
	}

	/**
	 * Prices the item the way RuneLite's Ground Items plugin does: the Grand Exchange guide price from
	 * {@link ItemManager#getItemPrice}, which already resolves noted items, coins and platinum tokens, and the
	 * high alchemy value from the item's own definition.
	 */
	private GroundItemSighting sightingOf(TileItem item, WorldPoint tile)
	{
		ItemComposition composition = itemManager.getItemComposition(item.getId());
		return new GroundItemSighting(item.getId(), composition.getName(), item.getQuantity(), item.getOwnership(),
			itemManager.getItemPrice(item.getId()), composition.getHaPrice(), tile);
	}
}
