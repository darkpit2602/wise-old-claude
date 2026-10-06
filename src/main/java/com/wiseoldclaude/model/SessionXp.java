package com.wiseoldclaude.model;

import java.util.Map;
import lombok.Value;

/** XP gained since the session started, so Claude can say what a session achieved and at what rate. */
@Value
public class SessionXp
{
	/** When the session's baseline was taken, ISO-8601. */
	String since;

	/** Skill name to XP gained; skills without gains are left out. */
	Map<String, Integer> gained;
}
