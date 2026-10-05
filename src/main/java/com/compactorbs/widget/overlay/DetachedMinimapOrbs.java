/* SPDX-License-Identifier: BSD-2-Clause */

package com.compactorbs.widget.overlay;

import com.compactorbs.CompactOrbsConstants.Layout;
import com.compactorbs.CompactOrbsConstants.Widgets.MinimapOverlay;
import com.compactorbs.CompactOrbsManager;
import com.compactorbs.util.ValueKey;
import com.compactorbs.widget.WidgetManager;
import com.compactorbs.widget.elements.Orbs;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

/** Positions native utility widgets beside the detached minimap. */
@Singleton
public class DetachedMinimapOrbs
{
	private static final Orbs[] TARGETS = {
		Orbs.XP_DROPS_CONTAINER, Orbs.WORLD_MAP_CONTAINER,
		Orbs.STORE_ORB_CONTAINER, Orbs.ACTIVITY_ORB_CONTAINER,
		Orbs.WIKI_ICON_CONTAINER
	};

	private final Client client;
	private final OverlayManager overlayManager;
	private final List<Overlay> suspendedOverlays = new ArrayList<>();
	private final Map<Orbs, Widget> moved = new EnumMap<>(Orbs.class);
	private final Map<Widget, Container> containers = new LinkedHashMap<>();
	private Widget minimap;

	@Inject
	public DetachedMinimapOrbs(Client client, OverlayManager overlayManager)
	{
		this.client = client;
		this.overlayManager = overlayManager;
	}

	public void update(CompactOrbsManager manager, WidgetManager widgetManager)
	{
		Widget current = client.getWidget(MinimapOverlay.UNIVERSE);
		if (!manager.hasUtilityOrbsWithMinimap() || current == null || current.isHidden())
		{
			clear();
			return;
		}
		if (minimap != current || sourcesChanged(manager, widgetManager))
		{
			clear();
			minimap = current;
		}

		// The native minimap overlay otherwise snaps these same parents beneath our overlay.
		if (suspendedOverlays.isEmpty())
		{
			overlayManager.removeIf(overlay ->
			{
				if ("RESIZABLE_MINIMAP_WIDGET".equals(overlay.getName())
					|| "RESIZABLE_MINIMAP_STONES_WIDGET".equals(overlay.getName()))
				{
					suspendedOverlays.add(overlay);
					return true;
				}
				return false;
			});
		}

		Rectangle detached = minimap.getBounds();
		for (Orbs target : TARGETS)
		{
			Widget source = widgetManager.getTargetWidget(target);
			if (!manager.keepOrbWithMinimap(target) || source == null)
			{
				continue;
			}
			if (!moved.containsKey(target))
			{
				widgetManager.remapTargets(target);
				if (target == Orbs.WIKI_ICON_CONTAINER)
				{
					widgetManager.remapTargets(Orbs.WIKI_PLUGIN_ICON,
						Orbs.WIKI_VANILLA_CONTAINER, Orbs.WIKI_VANILLA_ICON);
				}
				moved.put(target, source);
			}
			// Snapshot the whole ancestor chain before changing any geometry.
			for (Widget parent = source.getParent(); parent != null; parent = parent.getParent())
			{
				Container saved = containers.get(parent);
				Rectangle bounds = saved != null ? saved.bounds : parent.getBounds();
				if (bounds.contains(detached))
				{
					break;
				}
				containers.computeIfAbsent(parent, Container::new);
			}
		}

		List<Container> ancestors = new ArrayList<>(containers.values());
		Collections.reverse(ancestors);
		for (Container container : ancestors)
		{
			container.expand(detached, containers);
		}
		for (Map.Entry<Orbs, Widget> entry : moved.entrySet())
		{
			Orbs target = entry.getKey();
			Widget source = entry.getValue();
			boolean right = target == Orbs.WORLD_MAP_CONTAINER || target == Orbs.WIKI_ICON_CONTAINER;
			int x = target.getValueMap().get(ValueKey.X).getOriginal();
			if (right)
			{
				x = detached.width - source.getWidth() - x
					- (Layout.Original.MAP_CONTAINER_WIDTH - Layout.Original.ORBS_CONTAINER_WIDTH);
			}
			int y;
			switch (target)
			{
				case XP_DROPS_CONTAINER:
					y = Layout.MinimapOverlay.XP_Y;
					break;
				case WIKI_ICON_CONTAINER:
					y = Layout.MinimapOverlay.WIKI_Y;
					break;
				case STORE_ORB_CONTAINER:
				case ACTIVITY_ORB_CONTAINER:
					y = Layout.MinimapOverlay.BOTTOM_UTILITY_Y;
					break;
				default:
					y = target.getValueMap().get(ValueKey.Y).getOriginal() + 10;
			}
			Container expandedParent = containers.get(source.getParent());
			Rectangle parent = expandedParent != null
				? expandedParent.bounds.union(detached) : source.getParent().getBounds();
			source.setForcedPosition(detached.x + x - parent.x, detached.y + y - parent.y);
			source.revalidate();
			if (target == Orbs.WIKI_ICON_CONTAINER)
			{
				avoidMultiCombatIndicator(source, parent, manager);
			}
		}
	}

	private boolean sourcesChanged(CompactOrbsManager manager, WidgetManager widgetManager)
	{
		for (Map.Entry<Orbs, Widget> entry : moved.entrySet())
		{
			if (!manager.keepOrbWithMinimap(entry.getKey())
				|| widgetManager.getTargetWidget(entry.getKey()) != entry.getValue())
			{
				return true;
			}
		}
		return false;
	}

	private void avoidMultiCombatIndicator(Widget wiki, Rectangle parent, CompactOrbsManager manager)
	{
		Widget indicator = client.getWidget(manager.isClassicResizable()
			? InterfaceID.ToplevelPreEoc.MULTIWAY_ICON
			: InterfaceID.ToplevelOsrsStretch.MULTIWAY_ICON);
		if (indicator != null && !indicator.isHidden() && !wiki.isHidden())
		{
			Rectangle bounds = wiki.getBounds();
			Rectangle occupied = indicator.getBounds();
			if (bounds.intersects(occupied))
			{
				wiki.setForcedPosition(occupied.x - bounds.width - 4 - parent.x, bounds.y - parent.y);
				wiki.revalidate();
			}
		}
	}

	public void clear()
	{
		for (Container container : containers.values())
		{
			container.restore();
		}
		for (Widget source : moved.values())
		{
			source.setForcedPosition(-1, -1);
			source.revalidate();
		}
		for (Overlay overlay : suspendedOverlays)
		{
			overlayManager.add(overlay);
		}
		suspendedOverlays.clear();
		containers.clear();
		moved.clear();
		minimap = null;
	}

	private static class Container
	{
		private final Widget widget;
		private final Rectangle bounds;
		private final int x;
		private final int y;
		private final boolean noClickThrough;
		private final Map<Widget, Rectangle> children = new LinkedHashMap<>();

		private Container(Widget widget)
		{
			this.widget = widget;
			bounds = new Rectangle(widget.getBounds());
			x = widget.getRelativeX();
			y = widget.getRelativeY();
			noClickThrough = widget.getNoClickThrough();
			remember(widget.getStaticChildren());
			remember(widget.getDynamicChildren());
			remember(widget.getNestedChildren());
		}

		private void remember(Widget[] widgets)
		{
			if (widgets != null)
			{
				for (Widget child : widgets)
				{
					if (child != null)
					{
						children.put(child, new Rectangle(child.getBounds()));
					}
				}
			}
		}

		private void expand(Rectangle detached, Map<Widget, Container> containers)
		{
			Rectangle expanded = bounds.union(detached);
			Widget parent = widget.getParent();
			Container expandedParent = containers.get(parent);
			Rectangle parentBounds = expandedParent != null ? expandedParent.bounds.union(detached)
				: parent != null ? parent.getBounds() : new Rectangle();
			widget.setForcedPosition(expanded.x - parentBounds.x, expanded.y - parentBounds.y);
			widget.revalidate();
			widget.setWidth(expanded.width);
			widget.setHeight(expanded.height);
			widget.setNoClickThrough(false);
			for (Map.Entry<Widget, Rectangle> entry : children.entrySet())
			{
				Rectangle original = entry.getValue();
				entry.getKey().setForcedPosition(original.x - expanded.x, original.y - expanded.y);
			}
		}

		private void restore()
		{
			widget.setForcedPosition(x, y);
			widget.revalidate();
			widget.setWidth(bounds.width);
			widget.setHeight(bounds.height);
			widget.setNoClickThrough(noClickThrough);
			for (Widget child : children.keySet())
			{
				child.setForcedPosition(-1, -1);
				child.revalidate();
			}
		}
	}
}
