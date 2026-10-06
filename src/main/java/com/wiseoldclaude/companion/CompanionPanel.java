package com.wiseoldclaude.companion;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.time.Instant;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * Side panel saying whether the Claude Code side is set up and running, with a button to its setup page. Only shows
 * a {@link CompanionState}; deciding the state happens in {@link CompanionWatch}.
 */
public class CompanionPanel extends PluginPanel
{
	static final String SETUP_URL = "https://github.com/darkpit2602/wise-old-claude";
	private static final int TEXT_WIDTH = 190;
	private static final int GAP = 8;

	private final JLabel status = new JLabel();

	public CompanionPanel()
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("Wise Old Claude");
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		content.add(left(title));
		content.add(Box.createVerticalStrut(GAP));
		content.add(left(status));
		content.add(Box.createVerticalStrut(GAP));
		content.add(left(new JLabel(wrapped("This plugin exports your character so Claude Code can answer OSRS questions "
			+ "for your account and draw the routes it suggests. The Claude side is a separate plugin for Claude Code."))));
		content.add(Box.createVerticalStrut(GAP));

		JButton setup = new JButton("Open setup page");
		setup.addActionListener(e -> LinkBrowser.browse(SETUP_URL));
		content.add(left(setup));

		add(content, BorderLayout.NORTH);
		display(CompanionState.of(null, Instant.now()));
	}

	/**
	 * Must run on the Swing thread.
	 *
	 * @param state the state to show
	 */
	public void display(CompanionState state)
	{
		status.setText(wrapped(state.describe()));
	}

	private static String wrapped(String text)
	{
		return "<html><body style='width:" + TEXT_WIDTH + "px'>" + text + "</body></html>";
	}

	private static Component left(JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		return component;
	}
}
