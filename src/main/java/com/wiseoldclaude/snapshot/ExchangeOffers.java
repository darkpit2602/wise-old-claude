package com.wiseoldclaude.snapshot;

import com.wiseoldclaude.model.ExchangeOffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Decodes the client's Grand Exchange slots. The client receives every slot at login and on each change, so
 * reading them on every snapshot write needs no memory of its own.
 */
final class ExchangeOffers
{
	private ExchangeOffers()
	{
	}

	/**
	 * @param slots the client's offers, indexed by slot; null entries and empty slots are skipped
	 * @param itemName resolves an item id to its name
	 * @return the occupied slots in slot order; empty when there are none
	 */
	static List<ExchangeOffer> of(GrandExchangeOffer[] slots, IntFunction<String> itemName)
	{
		if (slots == null)
		{
			return Collections.emptyList();
		}
		List<ExchangeOffer> offers = new ArrayList<>();
		for (int i = 0; i < slots.length; i++)
		{
			GrandExchangeOffer offer = slots[i];
			if (offer == null || offer.getState() == null || offer.getState() == GrandExchangeOfferState.EMPTY)
			{
				continue;
			}
			offers.add(ExchangeOffer.builder()
				.slot(i + 1)
				.itemId(offer.getItemId())
				.itemName(itemName.apply(offer.getItemId()))
				.side(sideOf(offer.getState()))
				.status(statusOf(offer.getState()))
				.pricePerItem(offer.getPrice())
				.quantity(offer.getTotalQuantity())
				.quantityTraded(offer.getQuantitySold())
				.coinsTraded(offer.getSpent())
				.build());
		}
		return offers;
	}

	private static ExchangeOffer.Side sideOf(GrandExchangeOfferState state)
	{
		switch (state)
		{
			case BUYING:
			case BOUGHT:
			case CANCELLED_BUY:
				return ExchangeOffer.Side.BUY;
			default:
				return ExchangeOffer.Side.SELL;
		}
	}

	private static ExchangeOffer.Status statusOf(GrandExchangeOfferState state)
	{
		switch (state)
		{
			case BOUGHT:
			case SOLD:
				return ExchangeOffer.Status.COMPLETE;
			case CANCELLED_BUY:
			case CANCELLED_SELL:
				return ExchangeOffer.Status.CANCELLED;
			default:
				return ExchangeOffer.Status.ACTIVE;
		}
	}
}
