package com.wiseoldclaude.snapshot;

import com.wiseoldclaude.model.Bank;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Remembers each character's last-seen bank. The game exposes the bank only while it is open, so without
 * memory every snapshot written after closing it, or after a client restart, would lose the bank. A
 * character's first lookup is seeded from the previously exported snapshot; a bank captured live always
 * wins over the seed, even when captured before the first lookup.
 * Not thread-safe: use only from the client thread.
 */
public class BankMemory
{
	private final Function<String, Bank> previousBank;
	private final Map<String, Bank> banks = new HashMap<>();

	/**
	 * @param previousBank loads a character's bank from its previous export, returning null when none
	 */
	public BankMemory(Function<String, Bank> previousBank)
	{
		this.previousBank = previousBank;
	}

	/**
	 * Records the bank as just seen in the client.
	 *
	 * @param rsn character the bank belongs to
	 * @param bank current bank contents
	 */
	public void record(String rsn, Bank bank)
	{
		banks.put(rsn, bank);
	}

	/**
	 * @param rsn character to look up
	 * @return the most recent bank known for the character, or null when it has never been seen
	 */
	public Bank bankFor(String rsn)
	{
		if (!banks.containsKey(rsn))
		{
			banks.put(rsn, previousBank.apply(rsn));
		}
		return banks.get(rsn);
	}
}
