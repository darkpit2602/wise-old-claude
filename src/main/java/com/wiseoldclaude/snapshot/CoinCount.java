package com.wiseoldclaude.snapshot;

import com.wiseoldclaude.model.Bank;
import com.wiseoldclaude.model.ItemStack;
import java.util.List;
import net.runelite.api.gameval.ItemID;

/**
 * Sums the player's coins so the assistant can answer "can I afford X" without adding up containers itself.
 * Uses {@code gameval.ItemID.COINS} (995): the deprecated {@code net.runelite.api.ItemID.COINS} is a
 * different item (617).
 */
public final class CoinCount
{
	private CoinCount()
	{
	}

	/**
	 * Totals coins carried plus coins in the last-seen bank. Returned as a long because a full inventory
	 * stack and a full bank stack together exceed {@link Integer#MAX_VALUE}.
	 *
	 * @param inventory current inventory stacks
	 * @param bank last-seen bank, or null when never seen
	 * @return total coins
	 */
	public static long total(List<ItemStack> inventory, Bank bank)
	{
		long total = coinsIn(inventory);
		if (bank != null)
		{
			total += coinsIn(bank.getItems());
		}
		return total;
	}

	private static long coinsIn(List<ItemStack> stacks)
	{
		long coins = 0;
		for (ItemStack stack : stacks)
		{
			if (stack.getId() == ItemID.COINS)
			{
				coins += stack.getQty();
			}
		}
		return coins;
	}
}
