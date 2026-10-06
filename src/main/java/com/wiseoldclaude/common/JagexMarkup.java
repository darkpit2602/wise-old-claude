package com.wiseoldclaude.common;

import java.util.regex.Pattern;

/**
 * Turns the client's tagged text (colours, icons, line breaks, escaped brackets) into the plain sentence the
 * player reads. Written here rather than reusing {@code net.runelite.client.util.Text}, whose tag stripping
 * drops escaped brackets and glues lines together without a space.
 */
public final class JagexMarkup
{
	private static final Pattern LINE_BREAK = Pattern.compile("<br>|<n>");
	private static final Pattern TAG_OTHER_THAN_ESCAPE = Pattern.compile("<(?!lt>|gt>)[^>]*>");
	private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

	private JagexMarkup()
	{
	}

	/**
	 * @param text tagged client text, possibly null
	 * @return the readable text with single spaces, or an empty string when nothing readable remains
	 */
	public static String plain(String text)
	{
		if (text == null)
		{
			return "";
		}
		String unbroken = LINE_BREAK.matcher(text).replaceAll(" ");
		String untagged = TAG_OTHER_THAN_ESCAPE.matcher(unbroken).replaceAll("");
		String unescaped = untagged.replace("<lt>", "<").replace("<gt>", ">");
		return WHITESPACE.matcher(unescaped).replaceAll(" ").trim();
	}
}
