package com.wiseoldclaude.snapshot;

import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;

/**
 * Decides which bank container slots represent items the player actually owns. The bank container also
 * holds placeholders (quantity 0, kept to reserve a slot) and bank fillers, which would otherwise be
 * reported to the assistant as owned items.
 */
public final class BankContents
{
	private BankContents()
	{
	}

	/**
	 * @param item one slot of the bank container
	 * @return true when the slot holds a real, owned stack
	 */
	public static boolean isStored(Item item)
	{
		return item.getId() >= 0 && item.getQuantity() > 0 && item.getId() != ItemID.BANK_FILLER;
	}
}
