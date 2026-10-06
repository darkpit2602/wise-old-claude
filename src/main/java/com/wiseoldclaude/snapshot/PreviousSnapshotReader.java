package com.wiseoldclaude.snapshot;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.wiseoldclaude.io.AtomicFiles;
import com.wiseoldclaude.io.CharacterFiles;
import com.wiseoldclaude.io.OutputDirectory;
import com.wiseoldclaude.model.Bank;
import com.wiseoldclaude.model.Birdhouses;
import java.io.IOException;
import javax.inject.Inject;

/**
 * Recovers state from a character's previously exported snapshot, so data the game only exposes
 * intermittently (the bank, bird houses) survives a client restart. Any unreadable, outdated, or incomplete
 * file yields "nothing known" rather than an error: losing stale state is preferable to failing the export.
 */
public class PreviousSnapshotReader
{
	private final Gson gson;
	private final OutputDirectory directory;

	/**
	 * @param gson deserializer matching the one the snapshots were written with
	 * @param directory where {@link SnapshotWriter} puts snapshots
	 */
	@Inject
	public PreviousSnapshotReader(Gson gson, OutputDirectory directory)
	{
		this.gson = gson;
		this.directory = directory;
	}

	/**
	 * @param rsn character whose previous export to read
	 * @return the bank recorded in that export, or null when there is none or it cannot be trusted
	 */
	public Bank lastBank(String rsn)
	{
		Bank bank = lastField(rsn, "bank", Bank.class);
		return bank == null || bank.getCapturedAt() == null || bank.getItems() == null ? null : bank;
	}

	/**
	 * @param rsn character whose previous export to read
	 * @return the bird houses recorded in that export, or null when there are none or they cannot be trusted
	 */
	public Birdhouses lastBirdhouses(String rsn)
	{
		Birdhouses birdhouses = lastField(rsn, "birdhouses", Birdhouses.class);
		return birdhouses == null || birdhouses.getCheckedAt() == null || birdhouses.getSpaces() == null ? null : birdhouses;
	}

	private <T> T lastField(String rsn, String field, Class<T> type)
	{
		try
		{
			String json = AtomicFiles.read(CharacterFiles.pathFor(rsn, directory.get()));
			JsonElement value = new JsonParser().parse(json).getAsJsonObject().get(field);
			return value == null || value.isJsonNull() ? null : gson.fromJson(value, type);
		}
		catch (IOException | JsonParseException | IllegalStateException e)
		{
			return null;
		}
	}
}
