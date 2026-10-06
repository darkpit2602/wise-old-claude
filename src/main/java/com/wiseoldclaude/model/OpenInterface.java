package com.wiseoldclaude.model;

import java.util.List;
import lombok.Value;

/**
 * The main interface the player has open, reduced to what changes advice: a shop's stock, the dialogue line
 * or choices on screen, or just which interface it is. Fields that do not apply to a kind stay null and are
 * left out of the file. Built by {@link com.wiseoldclaude.context.OpenInterfaces}.
 */
@Value
public class OpenInterface
{
	public static final String SHOP = "shop";
	public static final String BANK = "bank";
	public static final String GRAND_EXCHANGE = "grandExchange";
	public static final String DIALOGUE = "dialogue";

	String kind;

	/**
	 * The shop's title; null when the title could not be read.
	 */
	String name;

	/**
	 * The shop's items in display order with the quantity in stock, including sold-out items at 0. Prices are
	 * not included: the client does not expose the shop's price multipliers.
	 */
	List<ItemStack> stock;

	/**
	 * Who is talking in a dialogue box; null for options and message boxes.
	 */
	String speaker;

	/**
	 * The dialogue line, or the prompt above a list of options.
	 */
	String text;

	/**
	 * The choices a dialogue offers, top to bottom.
	 */
	List<String> options;
}
