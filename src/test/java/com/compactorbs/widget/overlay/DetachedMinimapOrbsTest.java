/* SPDX-License-Identifier: BSD-2-Clause */

package com.compactorbs.widget.overlay;

import com.compactorbs.CompactOrbsConstants.Widgets.MinimapOverlay;
import com.compactorbs.CompactOrbsManager;
import com.compactorbs.widget.WidgetManager;
import com.compactorbs.widget.elements.Orbs;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.runelite.api.Client;
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
		AtomicBoolean suspended = new AtomicBoolean();
		when(overlays.removeIf(any())).thenAnswer(invocation ->
		{
			Predicate<Overlay> predicate = invocation.getArgument(0);
			if (!suspended.get() && predicate.test(nativeOverlay))
			{
				suspended.set(true);
				return true;
			}
			return false;
		});
		when(overlays.add(nativeOverlay)).thenAnswer(invocation -> suspended.getAndSet(false));
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
		Rectangle parentBounds = parent != null ? parent.getBounds() : new Rectangle();
		when(widget.getRelativeX()).thenReturn(bounds.x - parentBounds.x);
		when(widget.getRelativeY()).thenReturn(bounds.y - parentBounds.y);
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
}
