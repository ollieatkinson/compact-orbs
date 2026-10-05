/* SPDX-License-Identifier: BSD-2-Clause */

package com.compactorbs.widget.overlay;

import com.compactorbs.CompactOrbsConstants.Widgets.MinimapOverlay;
import com.compactorbs.CompactOrbsManager;
import com.compactorbs.widget.WidgetManager;
import com.compactorbs.widget.elements.Orbs;
import java.awt.Rectangle;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DetachedMinimapOrbsTest
{
	private final OverlayManager overlays = mock(OverlayManager.class);
	private final Overlay nativeOverlay = mock(Overlay.class);
	private final Client client = mock(Client.class);
	private final CompactOrbsManager manager = mock(CompactOrbsManager.class);
	private final WidgetManager widgets = mock(WidgetManager.class);
	private Widget detached;
	private Widget container;
	private Widget host;
	private Widget world;
	private Widget hp;
	private DetachedMinimapOrbs orbs;

	@Before
	public void setUp()
	{
		Widget viewport = widget(null, new Rectangle(0, 0, 800, 600));
		host = widget(viewport, new Rectangle(500, 400, 200, 200));
		container = widget(host, new Rectangle(500, 400, 200, 200));
		when(host.getNestedChildren()).thenReturn(new Widget[] {container});
		world = widget(container, new Rectangle(660, 420, 30, 30));
		hp = widget(container, new Rectangle(510, 450, 30, 30));
		when(container.getStaticChildren()).thenReturn(new Widget[] {world, hp});
		detached = widget(viewport, new Rectangle(50, 50, 211, 213));
		when(client.getWidget(MinimapOverlay.UNIVERSE)).thenReturn(detached);
		when(widgets.getTargetWidget(Orbs.WORLD_MAP_CONTAINER)).thenReturn(world);
		when(widgets.getTargetWidget(Orbs.HP_ORB_CONTAINER)).thenReturn(hp);
		when(manager.hasUtilityOrbsWithMinimap()).thenReturn(true);
		when(manager.keepOrbWithMinimap(Orbs.WORLD_MAP_CONTAINER)).thenReturn(true);
		when(nativeOverlay.getName()).thenReturn("RESIZABLE_MINIMAP_WIDGET");
		when(overlays.removeIf(any())).thenAnswer(invocation ->
		{
			Predicate<Overlay> predicate = invocation.getArgument(0);
			return predicate.test(nativeOverlay);
		});
		orbs = new DetachedMinimapOrbs(client, overlays);
	}

	private Widget widget(Widget parent, Rectangle bounds)
	{
		Widget widget = mock(Widget.class);
		Rectangle original = new Rectangle(bounds);
		when(widget.getParent()).thenReturn(parent);
		when(widget.getBounds()).thenAnswer(invocation -> new Rectangle(bounds));
		when(widget.getWidth()).thenAnswer(invocation -> bounds.width);
		when(widget.getHeight()).thenAnswer(invocation -> bounds.height);
		when(widget.getRelativeX()).thenAnswer(invocation ->
			bounds.x - (parent != null ? parent.getBounds().x : 0));
		when(widget.getRelativeY()).thenAnswer(invocation ->
			bounds.y - (parent != null ? parent.getBounds().y : 0));
		doAnswer(invocation ->
		{
			int x = invocation.getArgument(0);
			int y = invocation.getArgument(1);
			Rectangle currentParent = parent != null ? parent.getBounds() : new Rectangle();
			bounds.x = x < 0 ? original.x : currentParent.x + x;
			bounds.y = y < 0 ? original.y : currentParent.y + y;
			return null;
		}).when(widget).setForcedPosition(anyInt(), anyInt());
		doAnswer(invocation -> { bounds.width = invocation.getArgument(0); return null; })
			.when(widget).setWidth(anyInt());
		doAnswer(invocation -> { bounds.height = invocation.getArgument(0); return null; })
			.when(widget).setHeight(anyInt());
		return widget;
	}

	@Test
	public void originalWorldMovesAndCompactHpStaysInPlace()
	{
		Rectangle hpBounds = hp.getBounds();
		orbs.update(manager, widgets);
		assertEquals(new Rectangle(227, 175, 30, 30), world.getBounds());
		assertEquals(hpBounds, hp.getBounds());
		assertEquals(new Rectangle(50, 50, 650, 550), container.getBounds());
		assertEquals(container.getBounds(), host.getBounds());
		verify(world, never()).setOnOpListener(any(Object[].class));
		verify(world, never()).setClickMask(anyInt());
		verify(world, never()).setHidden(anyBoolean());
		verify(detached, never()).createChild(anyInt(), anyInt());
	}

	@Test
	public void draggingAndDisablingRestoreNativeGeometry()
	{
		Rectangle originalWorld = world.getBounds();
		Rectangle originalContainer = container.getBounds();
		orbs.update(manager, widgets);
		detached.setForcedPosition(100, 100);
		orbs.update(manager, widgets);
		assertEquals(new Rectangle(277, 225, 30, 30), world.getBounds());
		detached.setForcedPosition(589, 0);
		orbs.update(manager, widgets);
		assertEquals(new Rectangle(766, 125, 30, 30), world.getBounds());
		when(manager.hasUtilityOrbsWithMinimap()).thenReturn(false);
		orbs.update(manager, widgets);
		assertEquals(originalContainer, container.getBounds());
		assertEquals(originalContainer, host.getBounds());
		assertEquals(originalWorld, world.getBounds());
		verify(overlays).add(nativeOverlay);
		verify(overlays).removeIf(any());
	}
	@Test
	public void wikiAvoidsIndicatorUsingItsNewPosition()
	{
		Widget wiki = widget(container, new Rectangle(660, 440, 33, 15));
		// Canvas bounds can still describe the previous frame after revalidation.
		when(wiki.getBounds()).thenReturn(new Rectangle(660, 440, 33, 15));
		when(widgets.getTargetWidget(Orbs.WIKI_ICON_CONTAINER)).thenReturn(wiki);
		when(manager.keepOrbWithMinimap(Orbs.WIKI_ICON_CONTAINER)).thenReturn(true);
		Widget indicator = widget(null, new Rectangle(225, 208, 25, 25));
		when(client.getWidget(InterfaceID.ToplevelPreEoc.MULTIWAY_ICON)).thenReturn(indicator);
		orbs.update(manager, widgets);
		verify(wiki).setForcedPosition(138, 158);
		orbs.update(manager, widgets);
		verify(wiki, times(2)).setForcedPosition(138, 158);
		when(indicator.isHidden()).thenReturn(true);
		orbs.update(manager, widgets);
		verify(wiki).setForcedPosition(174, 158);
	}

	@Test
	public void togglingMinimapKeepsCompactPositionWhenCanvasBoundsAreStale()
	{
		// The layout has been remapped, but getBounds still describes the last rendered position.
		hp.setForcedPosition(10, 100);
		when(hp.getBounds()).thenReturn(new Rectangle(510, 450, 30, 30));
		orbs.update(manager, widgets);
		verify(hp).setForcedPosition(460, 450);
		when(manager.hasUtilityOrbsWithMinimap()).thenReturn(false);
		orbs.update(manager, widgets);
		// Simulate the layout rebuild applying the saved custom position after restoring parents.
		hp.setForcedPosition(10, 100);
		when(manager.hasUtilityOrbsWithMinimap()).thenReturn(true);
		orbs.update(manager, widgets);
		verify(hp, times(2)).setForcedPosition(460, 450);
	}

}
