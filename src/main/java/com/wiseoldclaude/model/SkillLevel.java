package com.wiseoldclaude.model;

import lombok.Value;

/**
 * One skill's state. {@code boosted} differs from {@code level} under potions or after damage, which matters
 * when advising on boostable requirements.
 */
@Value
public class SkillLevel
{
	int level;
	long xp;
	int boosted;
}
