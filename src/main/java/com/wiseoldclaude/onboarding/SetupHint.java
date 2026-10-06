package com.wiseoldclaude.onboarding;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Tells a player who installed the plugin from the Plugin Hub where the other half lives. On its own the plugin only
 * writes files nobody reads, and the hub shows no link, so until the Claude Code side has been seen the first login of
 * each client session says so in chat. The player can turn the reminder off in the plugin's settings.
 */
public final class SetupHint
{
	static final String MESSAGE = "Exporting your character for Claude Code, which has not connected yet. To set it up, "
		+ "see github.com/darkpit2602/wise-old-claude (or turn this reminder off in the plugin settings).";

	private final BooleanSupplier remind;
	private final BooleanSupplier companionSeen;
	private final Consumer<String> chat;
	private boolean shown;

	/**
	 * @param remind whether the player wants the reminder
	 * @param companionSeen whether the Claude Code side has ever announced itself
	 * @param chat posts a line to the game chat
	 */
	public SetupHint(BooleanSupplier remind, BooleanSupplier companionSeen, Consumer<String> chat)
	{
		this.remind = remind;
		this.companionSeen = companionSeen;
		this.chat = chat;
	}

	/** Posts the hint on the first login of this client session while the Claude Code side has never been seen. */
	public void onLogin()
	{
		if (shown || !remind.getAsBoolean() || companionSeen.getAsBoolean())
		{
			return;
		}
		shown = true;
		chat.accept(MESSAGE);
	}
}
