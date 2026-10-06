package com.wiseoldclaude.quests;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;

/**
 * Adapter that reads the open quest journal (interface {@code questjournal}, group 119) from the client's
 * widgets. The title sits in {@link InterfaceID.Questjournal#TITLE}; each drawn line is one of the static
 * components {@code QJ1}..{@code QJ210} under {@link InterfaceID.Questjournal#TEXTLAYER}, which is where Quest
 * Helper's journal checks look for text too. Text is macro-expanded as Quest Helper does, so shorthand colour
 * and strike macros become the tags the parser understands. Must be called on the client thread.
 */
@Slf4j
public class QuestJournalReader
{
	private final Client client;
	private String lastLoggedTitle;

	@Inject
	public QuestJournalReader(Client client)
	{
		this.client = client;
	}

	/**
	 * @return the open journal's raw title and lines, or null when the quest journal is not open
	 */
	public RawQuestJournal read()
	{
		Widget title = client.getWidget(InterfaceID.Questjournal.TITLE);
		Widget textLayer = client.getWidget(InterfaceID.Questjournal.TEXTLAYER);
		if (title == null || title.isHidden() || textLayer == null)
		{
			return null;
		}
		String titleText = client.macroExpand(title.getText());
		logLayoutOnce(titleText, textLayer);
		return new RawQuestJournal(titleText, visibleLines(textLayer));
	}

	private List<String> visibleLines(Widget textLayer)
	{
		List<String> lines = new ArrayList<>();
		Widget[] children = textLayer.getStaticChildren();
		if (children == null)
		{
			return lines;
		}
		for (Widget line : children)
		{
			if (line != null && !line.isHidden())
			{
				lines.add(client.macroExpand(line.getText()));
			}
		}
		return lines;
	}

	/**
	 * Logs, once per opened journal, how its lines are laid out and which quest the game says was opened last,
	 * so a developer-mode client can confirm the widget layout this reader assumes.
	 */
	private void logLayoutOnce(String titleText, Widget textLayer)
	{
		if (!log.isDebugEnabled() || Objects.equals(titleText, lastLoggedTitle))
		{
			return;
		}
		lastLoggedTitle = titleText;
		log.debug("Quest journal '{}': {} static, {} dynamic line widgets; latest quest journal varp {}",
			titleText, countOf(textLayer.getStaticChildren()), countOf(textLayer.getDynamicChildren()),
			client.getVarpValue(VarPlayerID.LATEST_QUEST_JOURNAL));
	}

	private static int countOf(Widget[] widgets)
	{
		return widgets == null ? 0 : widgets.length;
	}
}
