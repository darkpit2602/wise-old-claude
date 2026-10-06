package com.wiseoldclaude.context;

import com.wiseoldclaude.common.JagexMarkup;
import com.wiseoldclaude.model.ItemStack;
import com.wiseoldclaude.model.OpenInterface;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Adapter that reads which tracked interface the player has open from the client's widgets. Kept free of
 * logic beyond reading; cleaning is {@link OpenInterfaces}'. A shop is read from its widget rather than its
 * item container because every shop has its own container id, while the shop interface is always the same.
 * Must be called on the client thread, where widgets and item definitions are safe to read.
 */
public class OpenInterfaceReader
{
	private final Client client;

	@Inject
	public OpenInterfaceReader(Client client)
	{
		this.client = client;
	}

	/**
	 * Checks the main-screen interfaces before the chatbox, since a dialogue can sit under a shop or bank that
	 * is the player's focus.
	 *
	 * @return the open interface, or null when none of the tracked ones is open
	 */
	public OpenInterface read()
	{
		if (isOpen(InterfaceID.Shopmain.UNIVERSE))
		{
			return shop();
		}
		if (isOpen(InterfaceID.Bankmain.UNIVERSE))
		{
			return OpenInterfaces.of(OpenInterface.BANK);
		}
		if (isOpen(InterfaceID.GeOffers.UNIVERSE))
		{
			return OpenInterfaces.of(OpenInterface.GRAND_EXCHANGE);
		}
		return dialogue();
	}

	/**
	 * Asks the interface's root layer, as RuneLite's own plugins do; the bank's component 0 is not its root.
	 */
	private boolean isOpen(int rootComponentId)
	{
		return visible(client.getWidget(rootComponentId));
	}

	private OpenInterface shop()
	{
		List<ItemStack> slots = new ArrayList<>();
		for (Widget slot : childrenOf(InterfaceID.Shopmain.ITEMS))
		{
			int itemId = slot.getItemId();
			if (itemId >= 0)
			{
				slots.add(new ItemStack(itemId, client.getItemDefinition(itemId).getName(), slot.getItemQuantity()));
			}
		}
		return OpenInterfaces.shop(shopTitle(), slots);
	}

	/**
	 * The title is drawn as one of the frame's generated children; the first child with text is it.
	 */
	private String shopTitle()
	{
		for (Widget child : childrenOf(InterfaceID.Shopmain.FRAME))
		{
			if (!JagexMarkup.plain(child.getText()).isEmpty())
			{
				return child.getText();
			}
		}
		return null;
	}

	private OpenInterface dialogue()
	{
		Widget npcLine = client.getWidget(InterfaceID.ChatLeft.TEXT);
		if (visible(npcLine))
		{
			return OpenInterfaces.dialogue(textOf(InterfaceID.ChatLeft.NAME), npcLine.getText());
		}
		Widget playerLine = client.getWidget(InterfaceID.ChatRight.TEXT);
		if (visible(playerLine))
		{
			return OpenInterfaces.dialogue(textOf(InterfaceID.ChatRight.NAME), playerLine.getText());
		}
		if (visible(client.getWidget(InterfaceID.Chatmenu.OPTIONS)))
		{
			List<String> lines = new ArrayList<>();
			childrenOf(InterfaceID.Chatmenu.OPTIONS).forEach(option -> lines.add(option.getText()));
			return OpenInterfaces.choice(lines);
		}
		return messageBox();
	}

	private OpenInterface messageBox()
	{
		for (int textId : new int[]{InterfaceID.Messagebox.TEXT, InterfaceID.Objectbox.TEXT})
		{
			Widget box = client.getWidget(textId);
			if (visible(box))
			{
				return OpenInterfaces.dialogue(null, box.getText());
			}
		}
		return null;
	}

	private String textOf(int componentId)
	{
		Widget widget = client.getWidget(componentId);
		return widget == null ? null : widget.getText();
	}

	private List<Widget> childrenOf(int componentId)
	{
		List<Widget> children = new ArrayList<>();
		Widget parent = client.getWidget(componentId);
		Widget[] generated = parent == null ? null : parent.getChildren();
		if (generated == null)
		{
			return children;
		}
		for (Widget child : generated)
		{
			if (child != null)
			{
				children.add(child);
			}
		}
		return children;
	}

	private static boolean visible(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}
}
