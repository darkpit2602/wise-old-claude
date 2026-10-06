package com.wiseoldclaude.io;

import com.google.gson.Gson;
import java.io.IOException;
import net.runelite.client.util.Filepath;

/**
 * Writes one JSON file per character into the plugin's directory or one of its subdirectories. Writes go to a
 * sibling temp file followed by an atomic rename, so the MCP server never reads a half-written file. Whether null
 * fields are written is each file format's choice, so it is named where the writer is made.
 */
public final class CharacterFileWriter
{
	private final Gson gson;
	private final OutputDirectory directory;
	private final String subdirectory;

	private CharacterFileWriter(Gson gson, OutputDirectory directory, String subdirectory)
	{
		this.gson = gson;
		this.directory = directory;
		this.subdirectory = subdirectory;
	}

	/**
	 * For formats that require optional fields to be present as an explicit null, which Gson omits by default.
	 *
	 * @param gson the client's shared Gson, from which a null-serializing copy is derived
	 * @param directory where the plugin's files go
	 * @return a writer into the plugin's directory itself
	 */
	public static CharacterFileWriter writingNulls(Gson gson, OutputDirectory directory)
	{
		return new CharacterFileWriter(gson.newBuilder().serializeNulls().create(), directory, null);
	}

	/**
	 * For formats whose optional fields are left out when unknown, so a file holding many kinds of entries does not
	 * carry a null for every field another kind uses.
	 *
	 * @param gson the client's shared Gson, used as is
	 * @param directory where the plugin's files go
	 * @return a writer into the plugin's directory itself
	 */
	public static CharacterFileWriter omittingNulls(Gson gson, OutputDirectory directory)
	{
		return new CharacterFileWriter(gson, directory, null);
	}

	/**
	 * A subdirectory keeps the MCP server's listing of {@code *.json} character snapshots from mistaking another
	 * file for a character.
	 *
	 * @param name the subdirectory's name
	 * @return the same writer, writing into that subdirectory
	 */
	public CharacterFileWriter in(String name)
	{
		return new CharacterFileWriter(gson, directory, name);
	}

	/**
	 * Writes a character's file, replacing any previous one.
	 *
	 * @param rsn the character the file belongs to
	 * @param content the value to serialize
	 * @return the written file
	 * @throws IOException if the directory cannot be created or the file cannot be written
	 */
	public Filepath write(String rsn, Object content) throws IOException
	{
		Filepath target = pathFor(rsn);
		AtomicFiles.replace(target, gson.toJson(content));
		return target;
	}

	/**
	 * @param rsn character name
	 * @return where that character's file lives, which may not exist yet
	 * @throws IOException if the plugin's directory cannot be prepared
	 */
	public Filepath pathFor(String rsn) throws IOException
	{
		Filepath root = directory.get();
		return CharacterFiles.pathFor(rsn, subdirectory == null ? root : root.joinSegment(subdirectory));
	}
}
