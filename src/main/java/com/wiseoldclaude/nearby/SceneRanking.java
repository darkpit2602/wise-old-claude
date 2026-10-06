package com.wiseoldclaude.nearby;

import java.util.Comparator;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/** The order and cap every nearby index shares: nearest the player first, and only as many as the file allows. */
final class SceneRanking
{
	private SceneRanking()
	{
	}

	/**
	 * Distance first, then every identifying field, so equal-distance sightings always come out in the same order
	 * and an unchanged scene produces an identical index, which is what lets an unchanged scene skip the write.
	 *
	 * @param origin the player's tile
	 * @param <T> the kind of sighting
	 * @return the nearest-first order
	 */
	static <T extends Sighting> Comparator<T> nearestFirst(WorldPoint origin)
	{
		return Comparator.<T>comparingInt(sighting -> origin.distanceTo2D(sighting.getTile()))
			.thenComparing(Sighting::getName)
			.thenComparingInt(sighting -> sighting.getTile().getX())
			.thenComparingInt(sighting -> sighting.getTile().getY())
			.thenComparingInt(sighting -> sighting.getTile().getPlane())
			.thenComparingInt(Sighting::getId);
	}

	/**
	 * @param ranked entries nearest first
	 * @param max most entries the file may hold
	 * @param <T> the kind of entry
	 * @return the nearest {@code max} entries, or all of them when there are fewer
	 */
	static <T> List<T> keepNearest(List<T> ranked, int max)
	{
		return ranked.subList(0, Math.min(max, ranked.size()));
	}
}
