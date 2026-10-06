package com.wiseoldclaude.nearby;

import com.wiseoldclaude.common.WriteThrottle;
import com.wiseoldclaude.io.BackgroundWrites;
import com.wiseoldclaude.model.GroundItems;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;

/**
 * Keeps the ground-item file current. Ground items never move, so unlike NPCs they need no polling: the
 * client's item spawn, despawn and quantity events mark the index dirty, and a rebuild follows at most once
 * per interval. Starts dirty, so a plugin enabled mid-session, when no spawn event will arrive for the items
 * already lying around, still writes the scene once. Call {@link #onTick} on the client thread, where item
 * definitions are safe to read; the write itself runs on the executor.
 */
public class GroundItemRefresh
{
	/**
	 * Two ticks (1.2 seconds) at most between rebuilds. Item events are frequent (every drop, pickup and
	 * despawn in the scene, and one per item when a scene loads) and a rebuild walks every scene tile, so a
	 * burst must cost one walk; but "what dropped?" is asked right after a kill, so the drop must already be
	 * in the file by the time the player has typed the question.
	 */
	static final int INTERVAL_TICKS = 2;

	private final Supplier<GroundItems> source;
	private final Consumer<GroundItems> sink;
	private final WriteThrottle throttle = new WriteThrottle(INTERVAL_TICKS);
	private GroundItems lastPublished;

	/**
	 * Collects with the client and writes into the plugin's output directory off the client thread.
	 *
	 * @param collector reads the scene's ground items
	 * @param writer persists an index
	 * @param executor the client's background executor, so disk writes never stall a frame
	 */
	@Inject
	public GroundItemRefresh(GroundItemsCollector collector, GroundItemsWriter writer,
		ScheduledExecutorService executor)
	{
		this(collector::collect, BackgroundWrites.on(executor, writer::write, "ground items"));
	}

	/**
	 * @param source the current index, or null while no character is loaded
	 * @param sink receives each index worth writing
	 */
	GroundItemRefresh(Supplier<GroundItems> source, Consumer<GroundItems> sink)
	{
		this.source = source;
		this.sink = sink;
		throttle.markDirty();
	}

	/**
	 * Records that items appeared, disappeared or changed quantity, or that a new scene was loaded.
	 */
	public void markDirty()
	{
		throttle.markDirty();
	}

	/**
	 * Rebuilds the index when items changed and the interval has elapsed, and publishes it if anything but the
	 * capture time changed. A tick without a loaded character keeps the change pending for a later tick.
	 *
	 * @param tick current game tick count
	 */
	public void onTick(int tick)
	{
		if (!throttle.tryAcquire(tick))
		{
			return;
		}
		GroundItems index = source.get();
		if (index == null)
		{
			throttle.markDirty();
			return;
		}
		GroundItems comparable = index.toBuilder().capturedAt(null).build();
		if (comparable.equals(lastPublished))
		{
			return;
		}
		lastPublished = comparable;
		sink.accept(index);
	}
}
