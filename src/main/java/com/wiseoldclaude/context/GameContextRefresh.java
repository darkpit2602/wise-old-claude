package com.wiseoldclaude.context;

import com.wiseoldclaude.common.LocalPlayer;
import com.wiseoldclaude.common.WriteThrottle;
import com.wiseoldclaude.io.BackgroundWrites;
import com.wiseoldclaude.model.GameContext;
import com.wiseoldclaude.model.OpenInterface;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;

/**
 * Keeps the game-context file current: the player's own recent game messages and the interface they have
 * open. Messages are pushed by {@link #onChatMessage}; the open interface has no single event and is polled
 * each tick, which costs a handful of widget lookups. Both calls arrive on the client thread, so no locking is
 * needed; only an immutable context crosses to the executor for the write.
 */
public class GameContextRefresh
{
	/**
	 * At most one write every 1.2 seconds: a burst of messages (a level-up, a drop, a full inventory) lands in
	 * one write, and the assistant reads the file seconds later anyway.
	 */
	static final int WRITE_INTERVAL_TICKS = 2;

	/**
	 * While an interface is open the file is rewritten every 30 seconds even when nothing changed. A closed
	 * client or a logout writes nothing more, so without this a shop open at that moment would read as open
	 * forever; the server treats an open interface in a file older than this as unknown.
	 */
	static final int KEEPALIVE_TICKS = 50;

	/**
	 * Enough to cover what just happened (the last minute or so of play) without the file growing past a few
	 * kilobytes.
	 */
	static final int MESSAGE_CAPACITY = 30;

	private final Supplier<String> player;
	private final Supplier<OpenInterface> interfaces;
	private final Consumer<GameContext> sink;
	private final GameMessageLog messages = new GameMessageLog(MESSAGE_CAPACITY);
	private final WriteThrottle throttle = new WriteThrottle(WRITE_INTERVAL_TICKS);
	private String rsn;
	private OpenInterface open;
	private int lastWriteTick = Integer.MIN_VALUE;

	/**
	 * Reads the client and writes into the plugin's output directory off the client thread.
	 *
	 * @param client the game client, for the character's name
	 * @param reader reads the open interface
	 * @param io the writer and the client's background executor, so disk writes never stall a frame
	 */
	@Inject
	public GameContextRefresh(Client client, OpenInterfaceReader reader, ContextIo io)
	{
		this(() -> LocalPlayer.nameOf(client), reader::read, BackgroundWrites.on(io.executor, io.writer::write, "game context"));
	}

	/**
	 * @param player the logged-in character's name, or null while none is loaded
	 * @param interfaces the open interface, or null when none is
	 * @param sink receives each context worth writing
	 */
	GameContextRefresh(Supplier<String> player, Supplier<OpenInterface> interfaces, Consumer<GameContext> sink)
	{
		this.player = player;
		this.interfaces = interfaces;
		this.sink = sink;
	}

	/**
	 * Records a chat message if it is part of the player's own game feed.
	 *
	 * @param event any chat message the client received
	 */
	public void onChatMessage(ChatMessage event)
	{
		record(event.getType(), event.getMessage(), Instant.now());
	}

	/**
	 * The testable core of {@link #onChatMessage}, with the arrival time supplied instead of read from the clock.
	 *
	 * @param type the chat channel
	 * @param message the raw message text
	 * @param at when it arrived
	 */
	void record(ChatMessageType type, String message, Instant at)
	{
		if (messages.record(type, message, at))
		{
			throttle.markDirty();
		}
	}

	/**
	 * Notices interface changes and character switches, and writes when something changed and the interval
	 * has elapsed. A brief unload (world hop, loading screen) keeps the messages; only a different character
	 * clears them, so one account's messages never land in another's file.
	 *
	 * @param tick current game tick count
	 */
	public void onTick(int tick)
	{
		String current = player.get();
		if (current == null)
		{
			return;
		}
		if (!current.equals(rsn))
		{
			if (rsn != null)
			{
				messages.clear();
			}
			rsn = current;
			throttle.markDirty();
		}
		OpenInterface latest = interfaces.get();
		if (!Objects.equals(latest, open))
		{
			open = latest;
			throttle.markDirty();
		}
		if (open != null && tick - lastWriteTick >= KEEPALIVE_TICKS)
		{
			throttle.markDirty();
		}
		if (!throttle.tryAcquire(tick))
		{
			return;
		}
		lastWriteTick = tick;
		sink.accept(GameContext.builder()
			.rsn(rsn)
			.capturedAt(Instant.now().toString())
			.messages(messages.messages())
			.openInterface(open)
			.build());
	}

	/**
	 * Where a context goes once built; grouped so the injected constructor stays within three collaborators.
	 */
	static class ContextIo
	{
		private final GameContextWriter writer;
		private final ScheduledExecutorService executor;

		@Inject
		ContextIo(GameContextWriter writer, ScheduledExecutorService executor)
		{
			this.writer = writer;
			this.executor = executor;
		}
	}
}
