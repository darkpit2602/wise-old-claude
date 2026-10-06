package com.wiseoldclaude.snapshot;

import com.wiseoldclaude.model.TeleportCooldowns;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Turns the game's last-cast records for Home Teleport and the minigame teleport into ready times, as RuneLite's
 * timers plugin does ({@code TimersAndBuffsPlugin#checkTeleport}). Each varp holds the cast time in whole minutes
 * since the Unix epoch, so a ready time can be up to a minute early. The gameval names do not describe them:
 * {@code AIDE_TELE_TIMER} is the Home Teleport cast and {@code SLUG2_REGIONUID} the minigame teleport cast, the
 * varps the legacy API called {@code LAST_HOME_TELEPORT} and {@code LAST_MINIGAME_TELEPORT}.
 */
public final class TeleportCasts
{
	private static final Duration HOME_COOLDOWN = Duration.ofMinutes(30);
	private static final Duration MINIGAME_COOLDOWN = Duration.ofMinutes(20);
	private static final int NEVER_CAST = 0;

	private static final Set<Integer> VARPS = Set.of(VarPlayerID.AIDE_TELE_TIMER, VarPlayerID.SLUG2_REGIONUID);

	private TeleportCasts()
	{
	}

	/**
	 * @param lastHomeMinute {@code VarPlayerID.AIDE_TELE_TIMER}
	 * @param lastMinigameMinute {@code VarPlayerID.SLUG2_REGIONUID}
	 * @return when each teleport is next castable
	 */
	public static TeleportCooldowns cooldowns(int lastHomeMinute, int lastMinigameMinute)
	{
		return new TeleportCooldowns(
			readyAt(lastHomeMinute, HOME_COOLDOWN),
			readyAt(lastMinigameMinute, MINIGAME_COOLDOWN));
	}

	/**
	 * Whether a varp change is a teleport cast. Casts change these once each, so watching them costs one write per
	 * cast.
	 *
	 * @param varpId the changed varp
	 * @return true when the snapshot's cooldowns are stale
	 */
	public static boolean watches(int varpId)
	{
		return VARPS.contains(varpId);
	}

	private static String readyAt(int castMinute, Duration cooldown)
	{
		if (castMinute == NEVER_CAST)
		{
			return null;
		}
		return Instant.ofEpochSecond(castMinute * 60L).plus(cooldown).toString();
	}
}
