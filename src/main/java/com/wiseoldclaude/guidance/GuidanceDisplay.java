package com.wiseoldclaude.guidance;

/**
 * Where guidance becomes visible in the client. Kept apart from {@link GuidanceState} so the decisions of
 * when to guide can be tested without a running client, and so the drawing side may only ever draw:
 * implementations must never send input or move the player.
 */
public interface GuidanceDisplay
{
	/**
	 * Starts guiding to the request's target, replacing whatever was shown before.
	 *
	 * @param request the destination to guide to
	 */
	void show(GuidanceRequest request);

	/**
	 * The player reached the target: remove what only helps on the way there.
	 *
	 * @param request the destination that was reached
	 */
	void arrived(GuidanceRequest request);

	/**
	 * Guidance was cancelled before arrival: remove everything shown for it.
	 *
	 * @param request the destination no longer wanted
	 */
	void withdraw(GuidanceRequest request);
}
