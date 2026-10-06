package com.wiseoldclaude.guidance;

import com.wiseoldclaude.common.PluginChat;
import java.util.Collections;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.util.Text;

/**
 * Draws guidance with what the client already offers instead of a pathfinder of our own: the Shortest Path
 * plugin's route (requested over the same PluginMessage Quest Helper uses) and the game's native hint
 * arrow. The message is always posted, because the event bus delivers it to no one when Shortest Path is
 * not installed, and the hint arrow is always set, so guidance degrades to the arrow alone rather than
 * depending on detecting another plugin. Must be called on the client thread.
 */
public class ClientGuidanceDisplay implements GuidanceDisplay
{
	private static final String SHORTEST_PATH = "shortestpath";
	private static final String PATH = "path";
	private static final String CLEAR = "clear";
	private static final String TARGET = "target";

	private final Client client;
	private final EventBus eventBus;

	/**
	 * @param client the game client, for the hint arrow and chat confirmation
	 * @param eventBus the client's event bus, shared with the Shortest Path plugin
	 */
	@Inject
	public ClientGuidanceDisplay(Client client, EventBus eventBus)
	{
		this.client = client;
		this.eventBus = eventBus;
	}

	/**
	 * Omits Shortest Path's optional {@code start}, so it routes from the player's tile and keeps
	 * recalculating when the player strays, as it does for paths set by hand.
	 */
	@Override
	public void show(GuidanceRequest request)
	{
		Map<String, Object> data = Collections.singletonMap(TARGET, request.getTarget());
		eventBus.post(new PluginMessage(SHORTEST_PATH, PATH, data));
		client.setHintArrow(request.getTarget());
		chat("Guiding you to " + nameOf(request) + ".");
	}

	/**
	 * Leaves Shortest Path's route alone: it ends the route itself near the target, and clearing it here
	 * could wipe a route another plugin set in the meantime.
	 */
	@Override
	public void arrived(GuidanceRequest request)
	{
		clearOwnHintArrow(request.getTarget());
		chat("You have arrived at " + nameOf(request) + ".");
	}

	@Override
	public void withdraw(GuidanceRequest request)
	{
		eventBus.post(new PluginMessage(SHORTEST_PATH, CLEAR));
		clearOwnHintArrow(request.getTarget());
	}

	/**
	 * The game sets hint arrows of its own (quests, tutorials), so only an arrow still pointing at our
	 * target is removed.
	 */
	private void clearOwnHintArrow(WorldPoint target)
	{
		if (client.hasHintArrow() && target.equals(client.getHintArrowPoint()))
		{
			client.clearHintArrow();
		}
	}

	private void chat(String message)
	{
		PluginChat.post(client, message);
	}

	private static String nameOf(GuidanceRequest request)
	{
		WorldPoint target = request.getTarget();
		return request.getLabel() != null
			? Text.escapeJagex(request.getLabel())
			: target.getX() + ", " + target.getY() + " (floor " + target.getPlane() + ")";
	}
}
