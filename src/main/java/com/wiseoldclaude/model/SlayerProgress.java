package com.wiseoldclaude.model;

import lombok.Value;

/** The current slayer task with the reward points and streak it builds towards. */
@Value
public class SlayerProgress
{
	/** Null when no task is assigned. */
	SlayerTask task;

	int points;

	/** Consecutive tasks completed for the current master's streak. */
	int streak;
}
