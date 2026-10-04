/* SPDX-License-Identifier: BSD-2-Clause */

package com.compactorbs.widget.overlay;

import com.compactorbs.CompactOrbsConfig;
import com.compactorbs.CompactOrbsConstants.Layout;
import com.compactorbs.CompactOrbsConstants.Layout.Original;
import com.compactorbs.CompactOrbsConstants.Widgets.MinimapOverlay;
import com.compactorbs.CompactOrbsManager;
import com.compactorbs.util.ValueKey;
import com.compactorbs.widget.WidgetManager;
import com.compactorbs.widget.elements.Orbs;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.client.callback.ClientThread;

/**
 * Displays utility widgets on the detached minimap while retaining the original
 * widgets as the source of state and client-side operation callbacks.
 */
@Singleton
public class DetachedMinimapOrbs
{
	private static final Orbs[] TARGETS = {
		Orbs.XP_DROPS_CONTAINER, Orbs.WORLD_MAP_CONTAINER,
		Orbs.STORE_ORB_CONTAINER, Orbs.ACTIVITY_ORB_CONTAINER,
		Orbs.WIKI_ICON_CONTAINER
	};

	private final Client client;
	private final CompactOrbsConfig config;
	private final CompactOrbsManager manager;
	private final WidgetManager widgetManager;
	private final ClientThread clientThread;
	private final Map<Orbs, Mirror> mirrors = new EnumMap<>(Orbs.class);
	private Widget parent;

	@Inject
	public DetachedMinimapOrbs(Client client, CompactOrbsConfig config,
		CompactOrbsManager manager, WidgetManager widgetManager, ClientThread clientThread)
	{
		this.client = client;
		this.config = config;
		this.manager = manager;
		this.widgetManager = widgetManager;
		this.clientThread = clientThread;
	}

	public void update()
	{
		Widget currentParent = client.getWidget(MinimapOverlay.UNIVERSE);
		if (!manager.hasUtilityOrbsWithMinimap() || currentParent == null)
		{
			clear();
			return;
		}

		if (currentParent != parent)
		{
			clear();
			parent = currentParent;
		}

		for (Orbs target : TARGETS)
		{
			Widget source = widgetManager.getTargetWidget(target);
			boolean selected = manager.keepOrbWithMinimap(target);
			Mirror mirror = mirrors.get(target);
			if (mirror != null && (mirror.source != source || !selected))
			{
				release(mirror);
				mirrors.remove(target);
				mirror = null;
			}
			if (!selected || source == null)
			{
				continue;
			}
			if (mirror == null)
			{
				// Apply vanilla sizes before copying; custom compact positions are irrelevant here.
				widgetManager.remapTargets(target);
				if (target == Orbs.WIKI_ICON_CONTAINER)
				{
					widgetManager.remapTargets(Orbs.WIKI_PLUGIN_ICON,
						Orbs.WIKI_VANILLA_CONTAINER, Orbs.WIKI_VANILLA_ICON);
				}
				mirror = new Mirror(source, parent.createChild(-1, source.getType()));
				mirrors.put(target, mirror);
			}

			mirror.update(true);
			position(target, mirror.copy);
			mirror.copy.setHidden(source.isSelfHidden() || isHidden(target));
			mirror.copy.revalidate();
			if (target == Orbs.WIKI_ICON_CONTAINER)
			{
				avoidMultiCombatIndicator(mirror.copy);
			}

			// Negative forced coordinates restore native positioning. Clip the source
			// at the right edge instead, retaining one pixel for the world-map hotkey.
			Widget sourceParent = source.getParent();
			int edge = sourceParent != null ? sourceParent.getWidth() : client.getCanvasWidth();
			source.setForcedPosition(edge -
				(target == Orbs.WORLD_MAP_CONTAINER ? 1 : 0), source.getOriginalY());
			source.revalidate();
		}
	}

	private void avoidMultiCombatIndicator(Widget wiki)
	{
		Widget indicator = client.getWidget(manager.isClassicResizable()
			? InterfaceID.ToplevelPreEoc.MULTIWAY_ICON
			: InterfaceID.ToplevelOsrsStretch.MULTIWAY_ICON);
		if (indicator == null || indicator.isHidden() || wiki.isHidden())
		{
			return;
		}
		Rectangle bounds = wiki.getBounds();
		Rectangle indicatorBounds = indicator.getBounds();
		if (bounds.intersects(indicatorBounds))
		{
			// Wiki is right-aligned. Leave a gap beside the native status indicator
			// without changing its position, visibility or combat state.
			wiki.setOriginalX(wiki.getOriginalX() + bounds.x + bounds.width - indicatorBounds.x + 4);
			wiki.revalidate();
		}
	}

	private boolean isHidden(Orbs target)
	{
		switch (target)
		{
			case XP_DROPS_CONTAINER:
				return config.hideXp();
			case WORLD_MAP_CONTAINER:
				return config.hideWorld();
			case WIKI_ICON_CONTAINER:
				return config.hideWiki();
			case ACTIVITY_ORB_CONTAINER:
				return config.hideActivity() || manager.isActivityOrbDisabled();
			case STORE_ORB_CONTAINER:
				return config.hideStore() || manager.isStoreOrbDisabled();
			default:
				return false;
		}
	}

	private void position(Orbs target, Widget copy)
	{
		// The vanilla orbs container is four pixels narrower and ten pixels below the map.
		boolean rightAligned = target == Orbs.WORLD_MAP_CONTAINER || target == Orbs.WIKI_ICON_CONTAINER;
		copy.setXPositionMode(rightAligned
			? WidgetPositionMode.ABSOLUTE_RIGHT
			: WidgetPositionMode.ABSOLUTE_LEFT);
		copy.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		copy.setOriginalX(target.getValueMap().get(ValueKey.X).getOriginal()
			+ (rightAligned
				? Original.MAP_CONTAINER_WIDTH - Original.ORBS_CONTAINER_WIDTH : 0));
		switch (target)
		{
			case XP_DROPS_CONTAINER:
				copy.setOriginalY(Layout.MinimapOverlay.XP_Y);
				break;
			case WIKI_ICON_CONTAINER:
				copy.setOriginalY(Layout.MinimapOverlay.WIKI_Y);
				break;
			case STORE_ORB_CONTAINER:
			case ACTIVITY_ORB_CONTAINER:
				copy.setOriginalY(Layout.MinimapOverlay.BOTTOM_UTILITY_Y);
				break;
			default:
				copy.setOriginalY(target.getValueMap().get(ValueKey.Y).getOriginal() + 10);
		}
	}

	public void clear()
	{
		for (Mirror mirror : mirrors.values())
		{
			release(mirror);
		}
		mirrors.clear();
		parent = null;
	}

	private void release(Mirror mirror)
	{
		mirror.source.setForcedPosition(-1, -1);
		mirror.source.revalidate();
		widgetManager.clearChild(mirror.copy);
	}

	private class Mirror
	{
		private final Widget source;
		private final Widget copy;
		private final List<Mirror> children = new ArrayList<>();

		private Mirror(Widget source, Widget copy)
		{
			this.source = source;
			this.copy = copy;
			// Listener-backed actions remain available with a zero click mask. Disable
			// native operation packets, dragging and target selection on the copy.
			copy.setClickMask(0);
			copy.setTargetVerb(null);
			copy.setOnOpListener((JavaScriptCallback) event ->
			{
				Object[] listener = source.getOnOpListener();
				String[] actions = source.getActions();
				int op = event.getOp();
				if (listener == null || listener.length == 0 || actions == null
					|| op <= 0 || op > actions.length || actions[op - 1] == null)
				{
					return;
				}
				// A user click runs the existing client-side callback, never a menu action.
				// Queue it because the script interpreter is not reentrant.
				Object[] arguments = listener.clone();
				clientThread.invokeLater(() -> client.createScriptEventBuilder(arguments)
					.setSource(source)
					.setOp(op)
					.build()
					.setCanSendPackets(false)
					.run());
			});
			copy.setHasListener(true);
		}

		private void update(boolean root)
		{
			copy.setType(source.getType());
			copy.setOriginalWidth(source.getOriginalWidth());
			copy.setOriginalHeight(source.getOriginalHeight());
			copy.setWidthMode(source.getWidthMode());
			copy.setHeightMode(source.getHeightMode());
			if (!root)
			{
				copy.setOriginalX(source.getOriginalX());
				copy.setOriginalY(source.getOriginalY());
				copy.setXPositionMode(source.getXPositionMode());
				copy.setYPositionMode(source.getYPositionMode());
			}
			copy.setSpriteId(source.getSpriteId());
			copy.setSpriteTiling(source.getSpriteTiling());
			copy.setBorderType(source.getBorderType());
			copy.setFlippedHorizontally(source.isFlippedHorizontally());
			copy.setFlippedVertically(source.isFlippedVertically());
			copy.setOpacity(source.getOpacity());
			copy.setText(source.getText());
			copy.setTextColor(source.getTextColor());
			copy.setTextShadowed(source.getTextShadowed());
			copy.setFontId(source.getFontId());
			copy.setLineHeight(source.getLineHeight());
			copy.setXTextAlignment(source.getXTextAlignment());
			copy.setYTextAlignment(source.getYTextAlignment());
			copy.setFilled(source.isFilled());
			copy.setName(source.getName());

			copy.setNoClickThrough(source.getNoClickThrough());
			copy.setHidden(source.isSelfHidden());
			copy.clearActions();
			String[] actions = source.getActions();
			if (actions != null)
			{
				for (int i = 0; i < actions.length; i++)
				{
					if (actions[i] != null)
					{
						copy.setAction(i, actions[i]);
					}
				}
			}
			copy.revalidate();

			List<Widget> sources = new ArrayList<>();
			addChildren(sources, source.getStaticChildren());
			addChildren(sources, source.getDynamicChildren());
			boolean changed = sources.size() != children.size();
			for (int i = 0; !changed && i < sources.size(); i++)
			{
				changed = sources.get(i) != children.get(i).source;
			}
			if (changed)
			{
				copy.deleteAllChildren();
				children.clear();
				for (Widget child : sources)
				{
					children.add(new Mirror(child, copy.createChild(-1, child.getType())));
				}
			}
			for (Mirror child : children)
			{
				child.update(false);
			}
		}
	}

	private static void addChildren(List<Widget> result, Widget[] widgets)
	{
		if (widgets != null)
		{
			for (Widget widget : widgets)
			{
				if (widget != null)
				{
					result.add(widget);
				}
			}
		}
	}
}
