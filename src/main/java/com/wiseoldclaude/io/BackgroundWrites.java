package com.wiseoldclaude.io;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Moves file writes off the client thread. A failed write is logged and dropped: the next change writes the file
 * again, and an exception must never reach the client's executor.
 */
@Slf4j
public final class BackgroundWrites
{
	/**
	 * One file write.
	 *
	 * @param <T> the value written
	 */
	@FunctionalInterface
	public interface FileWrite<T>
	{
		/**
		 * @param value the value to persist
		 * @throws IOException if the file cannot be written
		 */
		void write(T value) throws IOException;
	}

	private BackgroundWrites()
	{
	}

	/**
	 * @param executor runs the writes, e.g. the client's background executor
	 * @param write persists one value
	 * @param what names the file in the warning a failed write logs
	 * @param <T> the value written
	 * @return a sink that writes each value it accepts on the executor
	 */
	public static <T> Consumer<T> on(Executor executor, FileWrite<T> write, String what)
	{
		return value -> executor.execute(() -> writeLogged(write, value, what));
	}

	private static <T> void writeLogged(FileWrite<T> write, T value, String what)
	{
		try
		{
			write.write(value);
		}
		catch (IOException e)
		{
			log.warn("Failed to write wise-old-claude {}", what, e);
		}
	}
}
