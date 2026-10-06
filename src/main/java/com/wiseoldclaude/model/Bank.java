package com.wiseoldclaude.model;

import java.util.List;
import lombok.Value;

/**
 * The bank as last seen. The game only populates the bank container while the bank interface is open, so
 * this carries its own capture time: it can be hours or sessions older than the snapshot that holds it.
 */
@Value
public class Bank
{
	String capturedAt;
	List<ItemStack> items;
}
