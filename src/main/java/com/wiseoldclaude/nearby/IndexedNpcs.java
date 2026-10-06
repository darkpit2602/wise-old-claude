package com.wiseoldclaude.nearby;

import com.wiseoldclaude.model.NpcVariant;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * The indexable NPCs of a scene: name to its variants, plus whether the cap dropped any.
 */
@Value
public class IndexedNpcs
{
	Map<String, List<NpcVariant>> npcs;
	boolean truncated;
}
