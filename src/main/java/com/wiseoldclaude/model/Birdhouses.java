package com.wiseoldclaude.model;

import java.util.List;
import lombok.Value;

/**
 * The four Fossil Island bird house spaces as last seen. The game reports them only while the player is on the
 * island, so this carries its own check time and can be sessions older than the snapshot that holds it.
 */
@Value
public class Birdhouses
{
	/** When the plugin last read the spaces on Fossil Island. */
	String checkedAt;
	List<BirdhouseSpace> spaces;
}
