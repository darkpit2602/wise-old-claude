package com.wiseoldclaude.nearby;

import com.wiseoldclaude.common.WriteThrottle;
import com.wiseoldclaude.io.BackgroundWrites;
import com.wiseoldclaude.model.NearbyObjects;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the nearby-object file current. Object spawn and despawn events mark the index dirty, and a rebuild follows
 * at most once per interval. Starts dirty, so the scene is written once after the plugin starts even when no object
 * event arrives. Call {@link #onTick} on the client thread, where object definitions are safe to read; the write
 * itself runs on the executor.
 */
@Slf4j
public class NearbyObjectRefresh
{
	/**
	 * Six seconds between rebuilds. Spawns and despawns arrive in bursts (a scene load fires one per object, a busy
	 * woodcutting spot one per chop), and a rebuild walks the whole scene, so a burst costs one walk. Short enough
	 * that a tree chopped down is gone from the index before the player can ask again.
	 */
	static final int INTERVAL_TICKS = 10;

	private final Supplier<NearbyObjects> source;
	private final Consumer<NearbyObjects> sink;
	private final WriteThrottle throttle = new WriteThrottle(INTERVAL_TICKS);

	/**
	 * Collects with the client and writes into the plugin's output directory off the client thread.
	 *
	 * @param collector reads the scene's objects
	 * @param writer persists an index
	 * @param executor the client's background executor, so disk writes never stall a frame
	 */
	@Inject
	public NearbyObjectRefresh(NearbyObjectsCollector collector, NearbyObjectsWriter writer,
		ScheduledExecutorService executor)
	{
		this(collector::collect, BackgroundWrites.on(executor, writer::write, "nearby objects"));
	}

	/**
	 * @param source the current index, or null while no character is loaded
	 * @param sink receives each index worth writing
	 */
	NearbyObjectRefresh(Supplier<NearbyObjects> source, Consumer<NearbyObjects> sink)
	{
		this.source = source;
		this.sink = sink;
		throttle.markDirty();
	}

	/**
	 * Records that objects appeared or disappeared, that a new scene was loaded, or that the plugin restarted.
	 */
	public void markDirty()
	{
		throttle.markDirty();
	}

	/**
	 * Rebuilds the index when objects changed and the interval has elapsed. A tick without a loaded character keeps
	 * the change pending for a later tick. The rebuild's duration is logged at debug level so its cost can be
	 * checked in a developer-mode client.
	 *
	 * @param tick current game tick count
	 */
	public void onTick(int tick)
	{
		if (!throttle.tryAcquire(tick))
		{
			return;
		}
		long started = System.nanoTime();
		NearbyObjects index = source.get();
		if (index == null)
		{
			throttle.markDirty();
			return;
		}
		log.debug("Indexed {} nearby objects in {} ms", countTiles(index),
			Duration.ofNanos(System.nanoTime() - started).toMillis());
		sink.accept(index);
	}

	private static int countTiles(NearbyObjects index)
	{
		return index.getObjects().values().stream().mapToInt(List::size).sum();
	}
}
