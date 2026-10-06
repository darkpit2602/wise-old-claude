package com.wiseoldclaude.model;

import java.util.List;
import lombok.Value;

/**
 * Every stack of one item id with one ownership. Ownership splits a variant because it decides who may take
 * the stack: an ironman can pick up their own bones but not another player's, so the two must not merge.
 */
@Value
public class GroundItemVariant
{
	int id;

	/**
	 * {@code self}, {@code group}, {@code other} or {@code none}; never who the other player is.
	 */
	String ownership;

	/**
	 * Grand Exchange guide price of one item, 0 when untradeable or unknown.
	 */
	long gePrice;

	/**
	 * High alchemy value of one item.
	 */
	int haPrice;

	/**
	 * {@code [x, y, plane, quantity]} stacks, nearest the index origin first; lists rather than objects so each
	 * costs a few bytes in the file.
	 */
	List<List<Integer>> stacks;
}
