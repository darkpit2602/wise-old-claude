package com.wiseoldclaude.nearby;

import java.util.function.Consumer;
import net.runelite.api.Scene;
import net.runelite.api.Tile;

/** Walks every tile of the loaded scene, on every plane. */
final class SceneTiles
{
	private SceneTiles()
	{
	}

	/**
	 * @param scene the loaded scene
	 * @param action receives each tile slot, including empty (null) ones the client leaves unset
	 */
	static void forEach(Scene scene, Consumer<Tile> action)
	{
		for (Tile[][] plane : scene.getTiles())
		{
			for (Tile[] column : plane)
			{
				for (Tile tile : column)
				{
					action.accept(tile);
				}
			}
		}
	}
}
