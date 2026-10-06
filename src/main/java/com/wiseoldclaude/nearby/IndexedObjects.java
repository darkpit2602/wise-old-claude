package com.wiseoldclaude.nearby;

import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * The indexable part of a scene: object name to its {@code [x, y, plane, objectId]} tiles, nearest first,
 * plus whether the cap dropped any. Tiles are lists rather than objects so each costs a few bytes in the file.
 */
@Value
public class IndexedObjects
{
	Map<String, List<List<Integer>>> objects;
	boolean truncated;
}
