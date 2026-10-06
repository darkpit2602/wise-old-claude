package com.wiseoldclaude;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/**
 * User-tunable settings for the snapshot export.
 */
@ConfigGroup(WiseOldClaudeConfig.GROUP)
public interface WiseOldClaudeConfig extends Config
{
	String GROUP = "wiseoldclaude";

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "writeIntervalTicks",
		name = "Write interval (ticks)",
		description = "Minimum game ticks between snapshot writes. One tick is 0.6 seconds."
	)
	default int writeIntervalTicks()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "remindSetup",
		name = "Remind me to set up Claude Code",
		description = "On login, point to the Claude Code setup page until Claude Code has connected."
	)
	default boolean remindSetup()
	{
		return true;
	}
}
