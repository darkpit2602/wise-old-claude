package com.wiseoldclaude.snapshot;

import com.wiseoldclaude.model.BirdhouseSpace;
import com.wiseoldclaude.model.Birdhouses;
import com.wiseoldclaude.model.Position;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Tracks each character's Fossil Island bird houses so "are my bird houses ready?" is answered away from the
 * island. Follows RuneLite's Time Tracking ({@code BirdHouseTracker}, client 1.13.1) in reading one varp per
 * space, and only on Fossil Island, but keeps its own record instead of reading that plugin's data. The game
 * reports what a space holds, not when seeds went in, so a ready time exists only for seeds the plugin saw go into
 * a built house during this session; a first reading after a restart keeps a remembered time only if nothing changed.
 * Not thread-safe: use only from the client thread.
 */
public class BirdhouseTracker
{
	/** Time from seeding to a full bird house; {@code BirdHouseTracker.BIRD_HOUSE_DURATION}. */
	static final Duration FILL_TIME = Duration.ofMinutes(50);

	/** {@code BirdHouseTracker.FOSSIL_ISLAND_REGIONS}: the varps only describe the spaces while here. */
	private static final Set<Integer> FOSSIL_ISLAND_REGIONS = Set.of(14650, 14651, 14652, 14906, 14907, 15162, 15163);

	/**
	 * More houses than this disappearing at once is the client still loading, not the player dismantling them;
	 * RuneLite ignores such readings the same way.
	 */
	private static final int MAX_VANISHED_AT_ONCE = 2;

	private static final String[] BIRDHOUSE_NAMES = {
		"Bird house", "Oak bird house", "Willow bird house", "Teak bird house", "Maple bird house",
		"Mahogany bird house", "Yew bird house", "Magic bird house", "Redwood bird house"};

	/**
	 * The spaces with RuneLite's names, in its order.
	 */
	private enum Space
	{
		MEADOW_NORTH("Mushroom Meadow (North)", VarPlayerID.BIRDHOUSE_TRANSMIT_A),
		MEADOW_SOUTH("Mushroom Meadow (South)", VarPlayerID.BIRDHOUSE_TRANSMIT_B),
		VALLEY_NORTH("Verdant Valley (Northeast)", VarPlayerID.BIRDHOUSE_TRANSMIT_C),
		VALLEY_SOUTH("Verdant Valley (Southwest)", VarPlayerID.BIRDHOUSE_TRANSMIT_D);

		private final String location;
		private final int varp;

		Space(String location, int varp)
		{
			this.location = location;
			this.varp = varp;
		}
	}

	private final Function<String, Birdhouses> previous;
	private final IntUnaryOperator varps;
	private final Supplier<Instant> clock;
	private final Map<String, Birdhouses> known = new HashMap<>();
	private final Set<String> watchedThisSession = new HashSet<>();

	/**
	 * @param previous loads a character's bird houses from its previous export, returning null when none
	 * @param varps reads a varp's current value
	 * @param clock the current time
	 */
	public BirdhouseTracker(Function<String, Birdhouses> previous, IntUnaryOperator varps, Supplier<Instant> clock)
	{
		this.previous = previous;
		this.varps = varps;
		this.clock = clock;
	}

	/**
	 * @param rsn the logged-in character
	 * @param position where the character stands
	 * @return the bird houses as last seen, or null when the character's have never been seen
	 */
	public Birdhouses observe(String rsn, Position position)
	{
		Birdhouses remembered = rememberedFor(rsn);
		if (!onFossilIsland(position))
		{
			return remembered;
		}
		Instant now = clock.get();
		Instant watchedAt = watchedThisSession.contains(rsn) ? now : null;
		List<BirdhouseSpace> spaces = new ArrayList<>();
		int vanished = 0;
		for (Space space : Space.values())
		{
			BirdhouseSpace prior = priorOf(remembered, space);
			BirdhouseSpace current = decode(space, varps.applyAsInt(space.varp));
			if (current.getState() == BirdhouseSpace.State.EMPTY && prior != null
				&& prior.getState() != BirdhouseSpace.State.EMPTY)
			{
				vanished++;
			}
			spaces.add(withReadyAt(current, prior, watchedAt));
		}
		if (vanished > MAX_VANISHED_AT_ONCE)
		{
			return remembered;
		}
		watchedThisSession.add(rsn);
		Birdhouses seen = new Birdhouses(now.toString(), spaces);
		known.put(rsn, seen);
		return seen;
	}

	/**
	 * Observing on every change of these, not only on snapshot writes, keeps a quick dismantle and reseed from
	 * looking like an untouched house.
	 *
	 * @param varpId the varp a {@code VarbitChanged} event reports
	 * @return whether it is one of the bird house spaces
	 */
	public static boolean watches(int varpId)
	{
		for (Space space : Space.values())
		{
			if (space.varp == varpId)
			{
				return true;
			}
		}
		return false;
	}

	private Birdhouses rememberedFor(String rsn)
	{
		if (!known.containsKey(rsn))
		{
			known.put(rsn, previous.apply(rsn));
		}
		return known.get(rsn);
	}

	private static boolean onFossilIsland(Position position)
	{
		return position != null && position.getPlane() == 0 && FOSSIL_ISLAND_REGIONS.contains(position.getRegionId());
	}

	private static BirdhouseSpace priorOf(Birdhouses remembered, Space space)
	{
		if (remembered == null || remembered.getSpaces() == null)
		{
			return null;
		}
		return remembered.getSpaces().stream()
			.filter(prior -> space.location.equals(prior.getLocation()))
			.findFirst()
			.orElse(null);
	}

	/**
	 * Decodes a space's varp as {@code BirdHouseState.fromVarpValue} and {@code BirdHouse.fromVarpValue} do:
	 * 0 is empty, then three values per bird house type in order, the third of which is seeded.
	 */
	private static BirdhouseSpace decode(Space space, int value)
	{
		if (value == 0)
		{
			return new BirdhouseSpace(space.location, BirdhouseSpace.State.EMPTY, null, null);
		}
		if (value < 0 || value > BIRDHOUSE_NAMES.length * 3)
		{
			return new BirdhouseSpace(space.location, BirdhouseSpace.State.UNKNOWN, null, null);
		}
		BirdhouseSpace.State state = value % 3 == 0 ? BirdhouseSpace.State.SEEDED : BirdhouseSpace.State.BUILT;
		return new BirdhouseSpace(space.location, state, BIRDHOUSE_NAMES[(value - 1) / 3], null);
	}

	/**
	 * Carries a seeded house's ready time: kept from the prior reading when nothing changed, or started from this
	 * reading when the seeding was watched happening.
	 *
	 * @param current the space as read now
	 * @param prior the space as last remembered, or null
	 * @param watchedAt when this reading was taken, or null when the plugin has not watched this character's spaces
	 *     yet this session, so a change cannot be timed
	 * @return the space with its ready time, or as read when it is not seeded
	 */
	private static BirdhouseSpace withReadyAt(BirdhouseSpace current, BirdhouseSpace prior, Instant watchedAt)
	{
		if (current.getState() != BirdhouseSpace.State.SEEDED)
		{
			return current;
		}
		String readyAt = null;
		if (unchanged(current, prior))
		{
			readyAt = prior.getReadyAt();
		}
		else if (watchedAt != null && seededJustNow(current, prior))
		{
			readyAt = watchedAt.plus(FILL_TIME).toString();
		}
		return new BirdhouseSpace(current.getLocation(), current.getState(), current.getBirdhouse(), readyAt);
	}

	/**
	 * Building a bird house and adding seeds are separate actions, so a seeding the plugin watched always arrives
	 * from the same house built empty. Any other arrival (from nothing, or from a reading taken while the client was
	 * still loading) is a seeding that happened unwatched.
	 */
	private static boolean seededJustNow(BirdhouseSpace current, BirdhouseSpace prior)
	{
		return prior != null && prior.getState() == BirdhouseSpace.State.BUILT
			&& Objects.equals(prior.getBirdhouse(), current.getBirdhouse());
	}

	private static boolean unchanged(BirdhouseSpace current, BirdhouseSpace prior)
	{
		return prior != null && prior.getState() == current.getState()
			&& Objects.equals(prior.getBirdhouse(), current.getBirdhouse());
	}
}
