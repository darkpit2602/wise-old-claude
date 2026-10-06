package com.wiseoldclaude.model;

import lombok.Value;

/**
 * An item and its quantity. The name is resolved in-client so the assistant needs no id lookup.
 */
@Value
public class ItemStack
{
	int id;
	String name;
	int qty;
}
