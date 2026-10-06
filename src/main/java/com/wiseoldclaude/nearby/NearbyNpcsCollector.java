package com.wiseoldclaude.nearby;

import com.wiseoldclaude.common.Positions;
import com.wiseoldclaude.model.NearbyNpcs;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Adapter that reads the client's NPC list into a {@link NearbyNpcs} index. Kept free of logic beyond
 * mapping; what is worth indexing is decided by {@link NearbyNpcIndex}. Reads only {@link WorldView#npcs()},
 * which never contains other players. Must be called on the client thread: NPC definitions and the varbits
 * that select a multi-NPC's current form are only safe to read there.
 */
public class NearbyNpcsCollector
{
	/**
	 * A busy scene holds an estimated one to three hundred NPCs; this keeps all of them while bounding a
	 * pathological scene's file to a few tens of kilobytes.
	 */
	static final int MAX_NPCS = 1000;

	private final Client client;
	private final NearbyNpcIndex index = new NearbyNpcIndex(MAX_NPCS);

	@Inject
	public NearbyNpcsCollector(Client client)
	{
		this.client = client;
	}

	/**
	 * Indexes the NPCs around the logged-in character.
	 *
	 * @return the index, or null while no player or scene is loaded (login screen, hopping)
	 */
	public NearbyNpcs collect()
	{
		Player player = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		if (player == null || player.getName() == null || view == null)
		{
			return null;
		}

		WorldPoint origin = player.getWorldLocation();
		IndexedNpcs indexed = index.index(origin, sightingsIn(view));
		return NearbyNpcs.builder()
			.rsn(player.getName())
			.capturedAt(Instant.now().toString())
			.world(client.getWorld())
			.instance(view.isInstance())
			.origin(Positions.of(origin))
			.truncated(indexed.isTruncated())
			.npcs(indexed.getNpcs())
			.build();
	}

	/**
	 * Records each NPC as the player currently sees it. Multi-NPCs carry a placeholder definition whose real
	 * name and options come from the form the player's varbits select, which
	 * {@link NPC#getTransformedComposition()} resolves; null means the NPC is hidden for this player.
	 * Followers are skipped: a pet belongs to a player, and other players' data is out of scope.
	 */
	private static List<NpcSighting> sightingsIn(WorldView view)
	{
		List<NpcSighting> sightings = new ArrayList<>();
		for (NPC npc : view.npcs())
		{
			NPCComposition composition = npc.getTransformedComposition();
			if (composition == null || composition.isFollower())
			{
				continue;
			}
			sightings.add(new NpcSighting(composition.getId(), composition.getName(), composition.getCombatLevel(),
				composition.getActions(), composition.isInteractible(), npc.getWorldLocation()));
		}
		return sightings;
	}
}
