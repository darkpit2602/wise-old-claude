package com.wiseoldclaude.nearby;

import com.wiseoldclaude.common.Positions;
import com.wiseoldclaude.model.NearbyObjects;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Adapter that reads every object in the loaded scene into a {@link NearbyObjects} index. Kept free of logic
 * beyond mapping; what is worth indexing is decided by {@link NearbyObjectIndex}. Must be called on the client
 * thread: object definitions and the varbits that select a multiloc's current form are only safe to read there.
 * Walks all 4 x 104 x 104 scene tiles, so callers run it on scene load and after throttled spawn bursts, never
 * every tick.
 */
public class NearbyObjectsCollector
{
	/**
	 * A dense city scene holds an estimated 500 to 1,500 interactable objects; this keeps all of them while
	 * bounding a pathological scene's file to roughly 70 KB.
	 */
	static final int MAX_OBJECTS = 3000;

	private final Client client;
	private final NearbyObjectIndex index = new NearbyObjectIndex(MAX_OBJECTS);

	@Inject
	public NearbyObjectsCollector(Client client)
	{
		this.client = client;
	}

	/**
	 * Indexes the objects around the logged-in character.
	 *
	 * @return the index, or null while no player or scene is loaded (login screen, hopping)
	 */
	public NearbyObjects collect()
	{
		Player player = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		if (player == null || player.getName() == null || view == null)
		{
			return null;
		}

		WorldPoint origin = player.getWorldLocation();
		IndexedObjects indexed = index.index(origin, sightingsIn(view.getScene()));
		return NearbyObjects.builder()
			.rsn(player.getName())
			.capturedAt(Instant.now().toString())
			.world(client.getWorld())
			.instance(view.isInstance())
			.origin(Positions.of(origin))
			.truncated(indexed.isTruncated())
			.objects(indexed.getObjects())
			.build();
	}

	private List<ObjectSighting> sightingsIn(Scene scene)
	{
		List<ObjectSighting> sightings = new ArrayList<>();
		SceneTiles.forEach(scene, tile -> addObjectsOn(tile, sightings));
		return sightings;
	}

	private void addObjectsOn(Tile tile, List<ObjectSighting> sightings)
	{
		if (tile == null)
		{
			return;
		}
		for (GameObject object : tile.getGameObjects())
		{
			addSighting(object, sightings);
		}
		addSighting(tile.getWallObject(), sightings);
		addSighting(tile.getDecorativeObject(), sightings);
		addSighting(tile.getGroundObject(), sightings);
	}

	/**
	 * Records the object as the player currently sees it. Multilocs (farming patches, varbit-dependent doors
	 * and scenery) carry a placeholder definition whose real name and actions come from the impostor the
	 * player's varbits select, the same resolution RuneLite's Object Indicators uses; an impostor of null
	 * means the object is hidden for this player.
	 */
	private void addSighting(TileObject object, List<ObjectSighting> sightings)
	{
		if (object == null)
		{
			return;
		}
		ObjectComposition composition = client.getObjectDefinition(object.getId());
		if (composition != null && composition.getImpostorIds() != null)
		{
			composition = composition.getImpostor();
		}
		if (composition == null)
		{
			return;
		}
		sightings.add(new ObjectSighting(
			composition.getId(), composition.getName(), composition.getActions(), object.getWorldLocation()));
	}
}
