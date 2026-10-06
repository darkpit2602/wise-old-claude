package com.wiseoldclaude.snapshot;

import com.wiseoldclaude.model.Bank;
import com.wiseoldclaude.model.PlayerSnapshot;
import com.wiseoldclaude.model.Position;
import com.wiseoldclaude.model.SkillLevel;
import com.wiseoldclaude.quests.QuestSummary;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * Completes a live snapshot with what the client cannot show at that moment: the bank as last opened, bird houses
 * as last seen on Fossil Island, XP gained this session and the quests as last read. Holds that memory for one plugin
 * run. Not thread-safe: use only from the client thread.
 */
public class SnapshotAssembler
{
	private final BankMemory bankMemory;
	private final BirdhouseTracker birdhouses;
	private final SessionXpTracker sessionXp = new SessionXpTracker();
	private final Supplier<Instant> clock;

	/**
	 * @param previous the last export, which seeds the bank and bird houses until the player shows them again
	 * @param varps reads a varp's current value
	 */
	public SnapshotAssembler(PreviousSnapshotReader previous, IntUnaryOperator varps)
	{
		this(previous, varps, Instant::now);
	}

	/**
	 * @param previous the last export, which seeds the bank and bird houses until the player shows them again
	 * @param varps reads a varp's current value
	 * @param clock the current time
	 */
	SnapshotAssembler(PreviousSnapshotReader previous, IntUnaryOperator varps, Supplier<Instant> clock)
	{
		this.bankMemory = new BankMemory(previous::lastBank);
		this.birdhouses = new BirdhouseTracker(previous::lastBirdhouses, varps, clock);
		this.clock = clock;
	}

	/**
	 * @param rsn the character whose bank was opened
	 * @param bank the bank's contents
	 */
	public void rememberBank(String rsn, Bank bank)
	{
		bankMemory.record(rsn, bank);
	}

	/**
	 * Reads the bird house spaces on every change, not only on snapshot writes, so a quick dismantle and reseed is
	 * not mistaken for an untouched house.
	 *
	 * @param rsn the logged-in character
	 * @param position where the character stands
	 */
	public void observeBirdhouses(String rsn, Position position)
	{
		birdhouses.observe(rsn, position);
	}

	/** Starts a new XP session once the login stat sync has landed. */
	public void onLogin()
	{
		sessionXp.onLogin();
	}

	/** Keeps the XP session across a world hop while stats resync. */
	public void onHop()
	{
		sessionXp.onHop();
	}

	/** Counts down the stat sync after a login or hop. */
	public void onTick()
	{
		sessionXp.onTick();
	}

	/**
	 * @param live the snapshot as read from the client this tick
	 * @param quests the quests as last read
	 * @return the snapshot to write
	 */
	public PlayerSnapshot complete(PlayerSnapshot live, QuestSummary quests)
	{
		Bank bank = bankMemory.bankFor(live.getRsn());
		return live.toBuilder()
			.bank(bank)
			.coins(CoinCount.total(live.getInventory(), bank))
			.quests(quests.getQuests())
			.questPoints(quests.getQuestPoints())
			.fairyRingsUnlocked(quests.getFairyRingsUnlocked())
			.birdhouses(birdhouses.observe(live.getRsn(), live.getPosition()))
			.progress(live.getProgress().toBuilder()
				.sessionXp(sessionXp.observe(xpBySkill(live.getSkills()), clock.get()))
				.build())
			.build();
	}

	private static Map<String, Integer> xpBySkill(Map<String, SkillLevel> skills)
	{
		Map<String, Integer> xp = new LinkedHashMap<>();
		skills.forEach((name, skill) -> xp.put(name, (int) skill.getXp()));
		return xp;
	}
}
