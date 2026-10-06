package com.wiseoldclaude.quests;

import com.wiseoldclaude.common.LocalPlayer;
import com.wiseoldclaude.io.BackgroundWrites;
import com.wiseoldclaude.model.JournalLine;
import com.wiseoldclaude.model.QuestJournal;
import com.wiseoldclaude.model.QuestProgress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

/**
 * Keeps the quest-progress file current. The journal's text can be filled in after the interface loads and
 * changes without an event of its own, so it is polled each tick: one widget lookup while it is closed, and a
 * capture only once the text differs from what is remembered. Stages are fed in from the plugin's existing quest refresh, since knowing which quests
 * are in progress already costs a script run per quest there. Both calls arrive on the client thread; only an
 * immutable progress value crosses to the executor for the write.
 */
public class QuestProgressRefresh
{
	private static final List<String> QUEST_NAMES = questNames();

	private final Sources sources;
	private final QuestJournalMemory memory;
	private final Consumer<QuestProgress> sink;
	private String rsn;
	private Map<String, Integer> stages;
	private boolean dirty;

	/**
	 * Reads the client and writes into the plugin's output directory off the client thread.
	 *
	 * @param client the game client, for the character's name and quest variables
	 * @param reader reads the open quest journal
	 * @param io the writer, the previous file's reader and the client's background executor
	 */
	@Inject
	public QuestProgressRefresh(Client client, QuestJournalReader reader, ProgressIo io)
	{
		this(new Sources(() -> LocalPlayer.nameOf(client), reader::read, new ClientQuestVars(client), Instant::now),
			new QuestJournalMemory(io.previous::lastJournals),
			BackgroundWrites.on(io.executor, io.writer::write, "quest progress"));
	}

	/**
	 * @param sources what the refresh reads
	 * @param memory remembers journals per character across closes and restarts
	 * @param sink receives each progress worth writing
	 */
	QuestProgressRefresh(Sources sources, QuestJournalMemory memory, Consumer<QuestProgress> sink)
	{
		this.sources = sources;
		this.memory = memory;
		this.sink = sink;
	}

	/**
	 * Captures the open journal if its text or the quest's stage differs from what is remembered, and writes
	 * when anything changed.
	 */
	public void onTick()
	{
		String current = currentCharacter();
		if (current == null)
		{
			return;
		}
		captureOpenJournal(current);
		if (dirty)
		{
			dirty = false;
			sink.accept(QuestProgress.builder()
				.rsn(current)
				.capturedAt(sources.clock.get().toString())
				.stages(stages)
				.journals(memory.journalsFor(current))
				.build());
		}
	}

	/**
	 * Re-reads the stages of in-progress quests after the plugin has read quest states.
	 *
	 * @param states every quest's state by name, in RuneLite's quest order
	 */
	public void onQuestStates(Map<String, QuestState> states)
	{
		if (currentCharacter() == null)
		{
			return;
		}
		Map<String, Integer> latest = Collections.unmodifiableMap(QuestStageTable.inProgressStages(states, sources.vars));
		if (!latest.equals(stages))
		{
			stages = latest;
			dirty = true;
		}
	}

	/**
	 * Follows character switches. Stages belong to the character they were read for, so a new character has
	 * none until its quests are read.
	 */
	private String currentCharacter()
	{
		String current = sources.player.get();
		if (current != null && !current.equals(rsn))
		{
			rsn = current;
			stages = null;
		}
		return current;
	}

	private void captureOpenJournal(String character)
	{
		RawQuestJournal open = sources.journal.get();
		if (open == null)
		{
			return;
		}
		String quest = QuestJournalParser.questFor(open.getTitle(), QUEST_NAMES);
		List<JournalLine> lines = QuestJournalParser.lines(open.getLines());
		if (quest == null || lines.isEmpty())
		{
			return;
		}
		QuestJournal journal = QuestJournal.builder()
			.capturedAt(sources.clock.get().toString())
			.stageAtCapture(QuestStageTable.stageOf(quest, sources.vars))
			.lines(lines)
			.build();
		if (memory.record(character, quest, journal))
		{
			dirty = true;
		}
	}

	private static List<String> questNames()
	{
		List<String> names = new ArrayList<>();
		for (Quest quest : Quest.values())
		{
			names.add(quest.getName());
		}
		return Collections.unmodifiableList(names);
	}

	/**
	 * What the refresh reads, grouped so the constructor stays within three collaborators.
	 */
	static class Sources
	{
		private final Supplier<String> player;
		private final Supplier<RawQuestJournal> journal;
		private final QuestVars vars;
		private final Supplier<Instant> clock;

		/**
		 * @param player the logged-in character's name, or null while none is loaded
		 * @param journal the open quest journal, or null when it is closed
		 * @param vars reads quest progress variables
		 * @param clock the current time, for capture stamps
		 */
		Sources(Supplier<String> player, Supplier<RawQuestJournal> journal, QuestVars vars, Supplier<Instant> clock)
		{
			this.player = player;
			this.journal = journal;
			this.vars = vars;
			this.clock = clock;
		}
	}

	/**
	 * Where progress goes once built and where remembered journals come from; grouped so the injected
	 * constructor stays within three collaborators.
	 */
	static class ProgressIo
	{
		private final QuestProgressWriter writer;
		private final PreviousQuestProgressReader previous;
		private final ScheduledExecutorService executor;

		@Inject
		ProgressIo(QuestProgressWriter writer, PreviousQuestProgressReader previous, ScheduledExecutorService executor)
		{
			this.writer = writer;
			this.previous = previous;
			this.executor = executor;
		}
	}

	/**
	 * Reads quest variables straight from the client, as Quest Helper's {@code QuestHelperQuest.getVar} does.
	 */
	private static class ClientQuestVars implements QuestVars
	{
		private final Client client;

		ClientQuestVars(Client client)
		{
			this.client = client;
		}

		@Override
		public int varbit(int id)
		{
			return client.getVarbitValue(id);
		}

		@Override
		public int varp(int id)
		{
			return client.getVarpValue(id);
		}
	}
}
