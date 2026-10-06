package com.wiseoldclaude.model;

import lombok.Value;

/**
 * When the cooldown-limited teleports can next be cast. Derived from the game's record of the last cast, so a
 * time already past simply means the teleport is ready.
 */
@Value
public class TeleportCooldowns
{
	/** Thirty minutes after the last Home Teleport; null when it was never cast. */
	String homeReadyAt;
	/** Twenty minutes after the last minigame teleport; null when it was never cast. */
	String minigameReadyAt;
}
