package com.wiseoldclaude.model;

import lombok.Builder;
import lombok.Value;

/**
 * One occupied Grand Exchange slot, decoded from the client's offer state into what the player would read off
 * the offer screen.
 */
@Value
@Builder
public class ExchangeOffer
{
	/**
	 * Whether the offer buys or sells.
	 */
	public enum Side
	{
		BUY,
		SELL
	}

	/**
	 * {@code COMPLETE} and {@code CANCELLED} offers still hold items or coins until the player collects them.
	 */
	public enum Status
	{
		ACTIVE,
		COMPLETE,
		CANCELLED
	}

	/** Slot number as the game shows it, 1 to 8. */
	int slot;
	int itemId;
	String itemName;
	Side side;
	Status status;
	/** Offered price per item, in coins. */
	long pricePerItem;
	/** Items the offer was placed for. */
	int quantity;
	/** Items bought or sold so far. */
	int quantityTraded;
	/** Coins spent on a buy offer, or received for a sell offer, so far. */
	long coinsTraded;
}
