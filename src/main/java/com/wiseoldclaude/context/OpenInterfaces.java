package com.wiseoldclaude.context;

import com.wiseoldclaude.common.GameNames;
import com.wiseoldclaude.common.JagexMarkup;
import com.wiseoldclaude.model.ItemStack;
import com.wiseoldclaude.model.OpenInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds {@link OpenInterface} values from raw widget text and item slots, cleaning markup and dropping slots
 * that hold no real item. Kept apart from {@link OpenInterfaceReader} so the cleaning is testable without a
 * client.
 */
final class OpenInterfaces
{
	/**
	 * The name the client gives an item definition that does not describe a real item.
	 */

	private OpenInterfaces()
	{
	}

	/**
	 * @param kind {@link OpenInterface#BANK} or {@link OpenInterface#GRAND_EXCHANGE}, whose contents are
	 *   exported elsewhere or not at all
	 * @return an interface that carries only its kind
	 */
	static OpenInterface of(String kind)
	{
		return new OpenInterface(kind, null, null, null, null, null);
	}

	/**
	 * Keeps sold-out items: "they sell it but are out" answers "where can I buy one?" differently from "they
	 * never sell it".
	 *
	 * @param rawTitle the shop title widget's text
	 * @param slots every displayed slot with its resolved item name
	 * @return the shop with its stock in display order
	 */
	static OpenInterface shop(String rawTitle, List<ItemStack> slots)
	{
		List<ItemStack> stock = slots.stream()
			.filter(slot -> slot.getId() >= 0 && GameNames.isNamed(slot.getName()))
			.collect(Collectors.toList());
		return new OpenInterface(OpenInterface.SHOP, orNull(JagexMarkup.plain(rawTitle)), stock, null, null, null);
	}

	/**
	 * @param rawSpeaker the name widget's text, or null for a box without a speaker
	 * @param rawText the dialogue line
	 * @return the line on screen
	 */
	static OpenInterface dialogue(String rawSpeaker, String rawText)
	{
		return new OpenInterface(OpenInterface.DIALOGUE, null, null, orNull(JagexMarkup.plain(rawSpeaker)),
			JagexMarkup.plain(rawText), null);
	}

	/**
	 * The options widget lists its prompt ("Select an Option") first, then each choice; blank and missing
	 * children are padding.
	 *
	 * @param rawLines the options widget's child texts in order
	 * @return the prompt and choices on screen
	 */
	static OpenInterface choice(List<String> rawLines)
	{
		List<String> lines = new ArrayList<>();
		for (String raw : rawLines)
		{
			String line = JagexMarkup.plain(raw);
			if (!line.isEmpty())
			{
				lines.add(line);
			}
		}
		String prompt = lines.isEmpty() ? null : lines.get(0);
		List<String> options = lines.isEmpty() ? lines : lines.subList(1, lines.size());
		return new OpenInterface(OpenInterface.DIALOGUE, null, null, null, prompt, new ArrayList<>(options));
	}

	private static String orNull(String text)
	{
		return text.isEmpty() ? null : text;
	}
}
