package com.wiseoldclaude.nearby;

import com.wiseoldclaude.io.BackgroundWrites;
import com.wiseoldclaude.model.NearbyNpcs;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;

/**
 * Keeps the nearby-NPC file current. NPCs move every tick and the client fires no event for movement, so
 * spawn and despawn events alone would leave positions stale; instead the NPC list is polled on a fixed
 * cadence and written only when the index differs from the last one written. Polling also covers spawns,
 * despawns and multi-NPC transforms, so no NPC event subscriptions are needed. Call {@link #onTick} on the
 * client thread, where NPC definitions are safe to read; the write itself runs on the executor.
 */
public class NearbyNpcRefresh
{
	/**
	 * Three seconds between rebuilds. Iterating the NPC list is cheap (a few hundred entries, unlike the
	 * object index's full scene walk), and a walking NPC drifts at most five tiles in that time, which is
	 * within where the player would look for it. Fishing spots, the main reason this index exists, move at
	 * most every 250 ticks.
	 */
	static final int INTERVAL_TICKS = 5;

	private final Supplier<NearbyNpcs> source;
	private final Consumer<NearbyNpcs> sink;
	private int lastRebuildTick = Integer.MIN_VALUE;
	private NearbyNpcs lastPublished;

	/**
	 * Collects with the client and writes into the plugin's output directory off the client thread.
	 *
	 * @param collector reads the client's NPC list
	 * @param writer persists an index
	 * @param executor the client's background executor, so disk writes never stall a frame
	 */
	@Inject
	public NearbyNpcRefresh(NearbyNpcsCollector collector, NearbyNpcsWriter writer, ScheduledExecutorService executor)
	{
		this(collector::collect, BackgroundWrites.on(executor, writer::write, "nearby NPCs"));
	}

	/**
	 * @param source the current index, or null while no character is loaded
	 * @param sink receives each index worth writing
	 */
	NearbyNpcRefresh(Supplier<NearbyNpcs> source, Consumer<NearbyNpcs> sink)
	{
		this.source = source;
		this.sink = sink;
	}

	/**
	 * Rebuilds the index when the interval has elapsed and publishes it if anything but the capture time
	 * changed. The player's own tile is part of the comparison because it orders the tiles, so a walking
	 * player rewrites the small file each interval; a standing player in a quiet scene writes nothing.
	 *
	 * @param tick current game tick count
	 */
	public void onTick(int tick)
	{
		boolean due = lastRebuildTick == Integer.MIN_VALUE || tick - lastRebuildTick >= INTERVAL_TICKS;
		if (!due)
		{
			return;
		}
		NearbyNpcs index = source.get();
		if (index == null)
		{
			return;
		}
		lastRebuildTick = tick;
		NearbyNpcs comparable = index.toBuilder().capturedAt(null).build();
		if (comparable.equals(lastPublished))
		{
			return;
		}
		lastPublished = comparable;
		sink.accept(index);
	}
}
