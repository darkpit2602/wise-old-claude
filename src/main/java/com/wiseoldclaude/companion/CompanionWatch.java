package com.wiseoldclaude.companion;

import com.wiseoldclaude.io.OutputDirectory;
import java.io.IOException;
import java.time.Instant;
import javax.inject.Inject;

/**
 * Keeps the latest view of the Claude Code side, re-read from its status file on each refresh. Refreshed off the
 * client thread; the state is read from the client thread (login hint) and the Swing thread (panel).
 */
public class CompanionWatch
{
	private final CompanionStatusReader reader;
	private final OutputDirectory directory;
	private volatile CompanionState state = CompanionState.of(null, Instant.now());

	/**
	 * @param reader parses the status file
	 * @param directory the plugin's data directory, which holds the status file
	 */
	@Inject
	public CompanionWatch(CompanionStatusReader reader, OutputDirectory directory)
	{
		this.reader = reader;
		this.directory = directory;
	}

	/**
	 * Re-reads the status file. A directory that cannot be resolved reads as "never seen".
	 *
	 * @return the new state
	 */
	public CompanionState refresh()
	{
		Instant lastSeen;
		try
		{
			lastSeen = reader.lastSeen(directory.get()
				.joinSegment(CompanionStatusReader.DIRECTORY)
				.joinSegment(CompanionStatusReader.FILE));
		}
		catch (IOException e)
		{
			lastSeen = null;
		}
		state = CompanionState.of(lastSeen, Instant.now());
		return state;
	}

	/**
	 * @return whether the Claude Code side has ever announced itself, as of the last refresh
	 */
	public boolean seen()
	{
		return state.getKind() != CompanionState.Kind.NOT_SET_UP;
	}
}
