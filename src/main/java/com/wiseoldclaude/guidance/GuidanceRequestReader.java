package com.wiseoldclaude.guidance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.wiseoldclaude.io.AtomicFiles;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Filepath;

/**
 * Parses the guidance request the MCP server writes. The file comes
 * from another process that may be older or newer than this plugin, so anything missing, corrupt, or of
 * another schema version reads as "no request" rather than an error: guiding nowhere is harmless, guiding
 * to a misread tile is not.
 */
public class GuidanceRequestReader
{
	private static final int SCHEMA_VERSION = 1;
	private static final int MAX_PLANE = 3;

	/**
	 * @param file the request file
	 * @return the request, or null when there is none or it cannot be trusted
	 */
	public GuidanceRequest read(Filepath file)
	{
		try
		{
			String json = AtomicFiles.read(file);
			return parse(new JsonParser().parse(json).getAsJsonObject());
		}
		catch (IOException | JsonParseException | IllegalStateException | DateTimeParseException e)
		{
			return null;
		}
	}

	private static GuidanceRequest parse(JsonObject root)
	{
		Integer version = wholeNumber(root.get("schemaVersion"));
		JsonElement target = root.get("target");
		JsonElement requestedAt = root.get("requestedAt");
		if (version == null || version != SCHEMA_VERSION || target == null || !target.isJsonObject()
			|| requestedAt == null || !requestedAt.isJsonPrimitive())
		{
			return null;
		}
		WorldPoint point = tile(target.getAsJsonObject());
		if (point == null)
		{
			return null;
		}
		return new GuidanceRequest(point, label(root.get("label")), Instant.parse(requestedAt.getAsString()));
	}

	private static WorldPoint tile(JsonObject target)
	{
		Integer x = wholeNumber(target.get("x"));
		Integer y = wholeNumber(target.get("y"));
		Integer plane = wholeNumber(target.get("plane"));
		if (x == null || y == null || plane == null || x < 0 || y < 0 || plane < 0 || plane > MAX_PLANE)
		{
			return null;
		}
		return new WorldPoint(x, y, plane);
	}

	/**
	 * Gson's {@code getAsInt} silently truncates 1.5 to 1, which would guide to the wrong tile, so whole
	 * numbers are checked explicitly.
	 */
	private static Integer wholeNumber(JsonElement element)
	{
		if (element == null || !element.isJsonPrimitive() || !((JsonPrimitive) element).isNumber())
		{
			return null;
		}
		double value = element.getAsDouble();
		return value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE ? (int) value : null;
	}

	private static String label(JsonElement element)
	{
		return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
	}
}
