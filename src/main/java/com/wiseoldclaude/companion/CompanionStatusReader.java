package com.wiseoldclaude.companion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.wiseoldclaude.io.AtomicFiles;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import net.runelite.client.util.Filepath;

/**
 * Reads the status the MCP server writes to say the Claude Code side exists. The file comes from another process
 * that may be older or newer than this plugin, so anything missing, corrupt or of another schema version reads as
 * "never seen": the plugin then keeps pointing the player at the setup page, which is harmless.
 */
public class CompanionStatusReader
{
	/** Subfolder of the plugin's data directory holding the status, and the file's name. */
	public static final String DIRECTORY = "companion";
	public static final String FILE = "status.json";

	private static final int SCHEMA_VERSION = 1;

	/**
	 * @param file the status file
	 * @return when the server last wrote it, or null when there is none or it cannot be trusted
	 */
	public Instant lastSeen(Filepath file)
	{
		try
		{
			JsonObject root = new JsonParser().parse(AtomicFiles.read(file)).getAsJsonObject();
			JsonElement version = root.get("schemaVersion");
			JsonElement lastSeen = root.get("lastSeen");
			if (version == null || !version.isJsonPrimitive() || version.getAsInt() != SCHEMA_VERSION
				|| lastSeen == null || !lastSeen.isJsonPrimitive())
			{
				return null;
			}
			return Instant.parse(lastSeen.getAsString());
		}
		catch (IOException | JsonParseException | IllegalStateException | NumberFormatException
			| DateTimeParseException e)
		{
			return null;
		}
	}
}
