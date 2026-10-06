package com.wiseoldclaude.nearby;

import com.wiseoldclaude.model.GroundItemVariant;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * The indexable ground items of a scene: name to its variants, plus whether the cap dropped any.
 */
@Value
public class IndexedGroundItems
{
	Map<String, List<GroundItemVariant>> items;
	boolean truncated;
}
