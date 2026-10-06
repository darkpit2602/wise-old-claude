package com.wiseoldclaude;

import com.google.inject.Provides;
import com.wiseoldclaude.combat.CombatVarWatch;
import com.wiseoldclaude.common.PluginChat;
import com.wiseoldclaude.common.Positions;
import com.wiseoldclaude.companion.CompanionPanel;
import com.wiseoldclaude.companion.CompanionState;
import com.wiseoldclaude.companion.CompanionWatch;
import com.wiseoldclaude.context.GameContextRefresh;
import com.wiseoldclaude.guidance.ClientGuidanceDisplay;
import com.wiseoldclaude.guidance.GuidanceRefresh;
import com.wiseoldclaude.io.BackgroundWrites;
import com.wiseoldclaude.io.OutputDirectory;
import com.wiseoldclaude.model.PlayerSnapshot;
import com.wiseoldclaude.nearby.GroundItemRefresh;
import com.wiseoldclaude.nearby.NearbyNpcRefresh;
import com.wiseoldclaude.nearby.NearbyObjectRefresh;
import com.wiseoldclaude.onboarding.SetupHint;
import com.wiseoldclaude.progress.ProgressVarWatch;
import com.wiseoldclaude.quests.QuestProgressRefresh;
import com.wiseoldclaude.quests.QuestStateReader;
import com.wiseoldclaude.quests.QuestStateRefresh;
import com.wiseoldclaude.snapshot.BirdhouseTracker;
import com.wiseoldclaude.snapshot.PreviousSnapshotReader;
import com.wiseoldclaude.snapshot.SnapshotAssembler;
import com.wiseoldclaude.snapshot.SnapshotSchedule;
import com.wiseoldclaude.snapshot.SnapshotCollector;
import com.wiseoldclaude.snapshot.SnapshotWriter;
import com.wiseoldclaude.snapshot.TeleportCasts;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Exports a read-only snapshot of the logged-in character to {@code ~/.runelite/plugin-data/wise-old-claude/} for
 * the wise-old-claude MCP server, and draws the guidance the server requests there. It never sends input or moves
 * the player: guidance is only a drawn route and hint arrow the player chooses to follow.
 */
@PluginDescriptor(
	name = "Wise Old Claude",
	description = "Lets Claude Code see your character and draw routes; needs the Wise Old Claude plugin for Claude Code",
	tags = {"claude", "assistant", "export", "snapshot"},
	internalName = "wise-old-claude",
	legacyDataDirectory = "wise-old-claude"
)
public class WiseOldClaudePlugin extends Plugin
{
	private static final int PANEL_PRIORITY = 10;
	private static final int COMPANION_REFRESH_SECONDS = 15;

	@Inject
	private Client client;

	@Inject
	private WiseOldClaudeConfig config;

	@Inject
	private SnapshotCollector collector;

	@Inject
	private SnapshotWriter writer;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientGuidanceDisplay guidanceDisplay;

	@Inject
	private NearbyObjectRefresh objectRefresh;

	@Inject
	private NearbyNpcRefresh npcRefresh;

	@Inject
	private GroundItemRefresh groundItemRefresh;

	@Inject
	private GameContextRefresh gameContext;

	@Inject
	private QuestProgressRefresh questProgress;

	@Inject
	private QuestStateReader questStateReader;

	@Inject
	private OutputDirectory outputDirectory;

	@Inject
	private PreviousSnapshotReader previousSnapshots;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private CompanionWatch companionWatch;

	private SnapshotSchedule snapshotSchedule;
	private WorldPoint lastPosition;
	private QuestStateRefresh questStates;
	private SnapshotAssembler snapshots;
	private Consumer<PlayerSnapshot> snapshotSink;
	private GuidanceRefresh guidance;
	private SetupHint setupHint;
	private CompanionPanel companionPanel;
	private NavigationButton companionButton;
	private ScheduledFuture<?> companionRefresh;

	@Override
	protected void startUp() throws IOException
	{
		applyConfig();
		questStates = new QuestStateRefresh(questStateReader, questProgress::onQuestStates);
		snapshotSink = BackgroundWrites.on(executor, writer::write, "snapshot");
		snapshots = new SnapshotAssembler(previousSnapshots, client::getVarpValue);
		guidance = GuidanceRefresh.in(outputDirectory.get(), guidanceDisplay);
		setupHint = new SetupHint(config::remindSetup, companionWatch::seen, message -> PluginChat.post(client, message));
		startCompanionPanel();
		objectRefresh.markDirty();
	}

	@Override
	protected void shutDown()
	{
		GuidanceRefresh stopping = guidance;
		clientThread.invoke(stopping::stop);
		companionRefresh.cancel(false);
		clientToolbar.removeNavigation(companionButton);
	}

	/**
	 * Adds the side panel and keeps it current. The status file is read on the background executor, never the
	 * client thread, and the panel is updated on the Swing thread.
	 */
	private void startCompanionPanel()
	{
		companionPanel = new CompanionPanel();
		companionButton = NavigationButton.builder()
			.tooltip("Wise Old Claude")
			.icon(ImageUtil.resizeImage(ImageUtil.loadImageResource(getClass(), "panel_icon.png"), 16, 16, true))
			.priority(PANEL_PRIORITY)
			.panel(companionPanel)
			.build();
		clientToolbar.addNavigation(companionButton);
		companionRefresh = executor.scheduleWithFixedDelay(() ->
		{
			CompanionState state = companionWatch.refresh();
			SwingUtilities.invokeLater(() -> companionPanel.display(state));
		}, 0, COMPANION_REFRESH_SECONDS, TimeUnit.SECONDS);
	}

	private void applyConfig()
	{
		snapshotSchedule = new SnapshotSchedule(config.writeIntervalTicks());
		lastPosition = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (WiseOldClaudeConfig.GROUP.equals(event.getGroup()))
		{
			applyConfig();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			snapshotSchedule.markDirty();
			objectRefresh.markDirty();
			questStates.onLogin();
			setupHint.onLogin();
		}
		else if (state == GameState.LOGGING_IN)
		{
			snapshots.onLogin();
		}
		else if (state == GameState.HOPPING)
		{
			snapshots.onHop();
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		objectRefresh.markDirty();
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		objectRefresh.markDirty();
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		objectRefresh.markDirty();
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		objectRefresh.markDirty();
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		objectRefresh.markDirty();
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		objectRefresh.markDirty();
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		objectRefresh.markDirty();
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		objectRefresh.markDirty();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			rememberBank(event.getItemContainer());
		}
		if (id == InventoryID.INV || id == InventoryID.WORN || id == InventoryID.BANK)
		{
			snapshotSchedule.markDirty();
		}
	}

	private void rememberBank(ItemContainer container)
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getName() == null || container == null)
		{
			return;
		}
		snapshots.rememberBank(player.getName(), collector.bankOf(container));
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		snapshotSchedule.markDirty();
	}

	/**
	 * Writes soon after the player picks or drops a target, so "what am I fighting?" is answered from the fight
	 * in progress and a finished fight stops being reported.
	 */
	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getSource() == client.getLocalPlayer())
		{
			snapshotSchedule.markDirty();
		}
	}

	/**
	 * Keeps the target's health current. Only hits on the player or the player's target count, so other fights
	 * nearby do not cause writes.
	 */
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Player player = client.getLocalPlayer();
		Actor hit = event.getActor();
		if (player != null && (hit == player || hit == player.getInteracting()))
		{
			snapshotSchedule.markDirty();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (BirdhouseTracker.watches(event.getVarpId()))
		{
			observeBirdhouses();
		}
		if (CombatVarWatch.affectsSnapshot(event.getVarpId(), event.getVarbitId())
			|| ProgressVarWatch.affectsSnapshot(event.getVarpId(), event.getVarbitId())
			|| BirdhouseTracker.watches(event.getVarpId())
			|| TeleportCasts.watches(event.getVarpId()))
		{
			snapshotSchedule.markDirty();
		}
	}

	/**
	 * Offers arrive at login and change as they trade.
	 */
	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		snapshotSchedule.markDirty();
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		groundItemRefresh.markDirty();
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		groundItemRefresh.markDirty();
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged event)
	{
		groundItemRefresh.markDirty();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		gameContext.onChatMessage(event);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		snapshots.onTick();
		followGuidance();
		markDirtyIfMoved();
		refreshQuestsIfDue();
		objectRefresh.onTick(client.getTickCount());
		npcRefresh.onTick(client.getTickCount());
		groundItemRefresh.onTick(client.getTickCount());
		gameContext.onTick(client.getTickCount());
		questProgress.onTick();
		if (!snapshotSchedule.isDue(client.getTickCount()))
		{
			return;
		}
		PlayerSnapshot live = collector.collect();
		if (live == null)
		{
			return;
		}
		PlayerSnapshot snapshot = snapshots.complete(live, questStates.summary());
		snapshotSink.accept(snapshot);
	}

	private void observeBirdhouses()
	{
		Player player = client.getLocalPlayer();
		if (player != null && player.getName() != null)
		{
			snapshots.observeBirdhouses(player.getName(), Positions.of(player.getWorldLocation()));
		}
	}

	private void refreshQuestsIfDue()
	{
		if (questStates.onTick())
		{
			snapshotSchedule.markDirty();
		}
	}

	private void followGuidance()
	{
		Player player = client.getLocalPlayer();
		guidance.onTick(player == null ? null : player.getWorldLocation());
	}

	private void markDirtyIfMoved()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldPoint position = player.getWorldLocation();
		if (!position.equals(lastPosition))
		{
			lastPosition = position;
			snapshotSchedule.markDirty();
		}
	}

	/**
	 * Shares one directory between the plugin and every writer, resolved through {@link #getPluginDirectory()}
	 * on first use; see {@link OutputDirectory} for why not at injection.
	 */
	@Provides
	@Singleton
	OutputDirectory provideOutputDirectory()
	{
		return new OutputDirectory(this::getPluginDirectory);
	}

	@Provides
	WiseOldClaudeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WiseOldClaudeConfig.class);
	}
}
