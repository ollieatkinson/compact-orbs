/* SPDX-License-Identifier: BSD-2-Clause */

package com.compactorbs.widget.overlay;

import com.compactorbs.CompactOrbsConfig;
import com.compactorbs.CompactOrbsConstants.Widgets.MinimapOverlay;
import com.compactorbs.CompactOrbsManager;
import com.compactorbs.widget.WidgetManager;
import com.compactorbs.widget.elements.Orbs;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptEventBuilder;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DetachedMinimapOrbsTest
{
	private final Client client = mock(Client.class);
	private final CompactOrbsConfig config = mock(CompactOrbsConfig.class);
	private final CompactOrbsManager manager = mock(CompactOrbsManager.class);
	private final WidgetManager widgets = mock(WidgetManager.class);
	private final Widget parent = mock(Widget.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final List<Widget> copies = new ArrayList<>();
	private DetachedMinimapOrbs orbs;

	@Before
	public void setUp()
	{
		when(manager.hasUtilityOrbsWithMinimap()).thenReturn(true);
		when(manager.keepOrbWithMinimap(any())).thenReturn(true);
		when(client.getWidget(MinimapOverlay.UNIVERSE)).thenReturn(parent);
		when(parent.createChild(anyInt(), anyInt())).thenAnswer(invocation ->
		{
			Widget copy = mock(Widget.class, RETURNS_SELF);
			copies.add(copy);
			return copy;
		});
		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		orbs = new DetachedMinimapOrbs(client, config, manager, widgets, clientThread);
	}

	private Widget source(Orbs target)
	{
		Widget source = mock(Widget.class);
		Widget sourceParent = mock(Widget.class);
		when(source.getParent()).thenReturn(sourceParent);
		when(sourceParent.getWidth()).thenReturn(200);
		when(source.getType()).thenReturn(WidgetType.LAYER);
		when(source.getId()).thenReturn(target.getComponentId());
		when(source.getIndex()).thenReturn(-1);
		when(source.getWidth()).thenReturn(31);
		when(source.getOriginalWidth()).thenReturn(31);
		when(source.getOriginalHeight()).thenReturn(31);
		when(source.getItemId()).thenReturn(-1);
		when(source.getName()).thenReturn("Utility icon");
		when(widgets.getTargetWidget(target)).thenReturn(source);
		return source;
	}

	@Test
	public void visibilityChangesAndStateChangesReachExistingCopy()
	{
		Widget source = source(Orbs.ACTIVITY_ORB_CONTAINER);
		when(source.getSpriteId()).thenReturn(100);
		orbs.update();
		Widget copy = copies.get(0);
		verify(copy).setSpriteId(100);
		verify(copy).setOriginalY(174);
		when(source.getSpriteId()).thenReturn(101);
		when(config.hideActivity()).thenReturn(true);
		orbs.update();
		assertEquals(1, copies.size());
		verify(copy).setSpriteId(101);
		verify(copy).setHidden(true);
	}
	@Test
	public void widgetReloadRestoresOldSourceAndRebuildsCopy()
	{
		Widget oldSource = source(Orbs.WORLD_MAP_CONTAINER);
		orbs.update();
		Widget newSource = source(Orbs.WORLD_MAP_CONTAINER);
		orbs.update();
		assertEquals(2, copies.size());
		verify(oldSource).setForcedPosition(-1, -1);
		verify(widgets).clearChild(copies.get(0));
		verify(newSource).setForcedPosition(199, 0);
		when(manager.hasUtilityOrbsWithMinimap()).thenReturn(false);
		orbs.update();
		verify(newSource).setForcedPosition(-1, -1);
		verify(widgets).clearChild(copies.get(1));
	}
	@Test
	public void onlySelectedIconsMoveAndDisablingOneRestoresIt()
	{
		Widget world = source(Orbs.WORLD_MAP_CONTAINER);
		Widget xp = source(Orbs.XP_DROPS_CONTAINER);
		Widget hp = source(Orbs.HP_ORB_CONTAINER);
		when(manager.keepOrbWithMinimap(Orbs.XP_DROPS_CONTAINER)).thenReturn(false);
		orbs.update();
		assertEquals(1, copies.size());
		verify(world).setForcedPosition(199, 0);
		verify(xp, never()).setForcedPosition(anyInt(), anyInt());
		verify(hp, never()).setForcedPosition(anyInt(), anyInt());
		verify(world, never()).setHidden(anyBoolean());
		when(manager.keepOrbWithMinimap(Orbs.WORLD_MAP_CONTAINER)).thenReturn(false);
		orbs.update();
		verify(world).setForcedPosition(-1, -1);
		verify(widgets).clearChild(copies.get(0));
	}
	@Test
	public void wikiOperationsUseNativeCallbacksWithoutSendingPackets()
	{
		Widget wiki = source(Orbs.WIKI_ICON_CONTAINER);
		orbs.update();
		Widget wikiCopy = copies.get(0);
		Widget pluginChild = mock(Widget.class);
		Widget pluginCopy = mock(Widget.class, RETURNS_SELF);
		int wikiId = wiki.getId();
		when(pluginChild.getId()).thenReturn(wikiId);
		when(pluginChild.getIndex()).thenReturn(0);
		when(pluginChild.getItemId()).thenReturn(-1);
		when(pluginChild.getActions()).thenReturn(new String[] {"Search", null, null, null, null, null, "DPS"});
		when(pluginChild.getTargetVerb()).thenReturn("Lookup");
		when(pluginChild.getClickMask()).thenReturn(-1);
		when(pluginChild.getName()).thenReturn("Wiki");
		when(wiki.getDynamicChildren()).thenReturn(new Widget[] {pluginChild});
		when(wikiCopy.createChild(anyInt(), anyInt())).thenReturn(pluginCopy);
		Object[] nativeListener = {mock(JavaScriptCallback.class)};
		when(pluginChild.getOnOpListener()).thenReturn(nativeListener);
		ScriptEventBuilder builder = mock(ScriptEventBuilder.class, RETURNS_SELF);
		ScriptEvent callbackEvent = mock(ScriptEvent.class, RETURNS_SELF);
		when(client.createScriptEventBuilder(any(Object[].class))).thenReturn(builder);
		when(builder.build()).thenReturn(callbackEvent);
		orbs.update();
		ArgumentCaptor<Object> listener = ArgumentCaptor.forClass(Object.class);
		verify(pluginCopy).setOnOpListener(listener.capture());
		ScriptEvent event = mock(ScriptEvent.class);
		when(event.getOp()).thenReturn(1);
		((JavaScriptCallback) listener.getValue()).run(event);
		verify(client).createScriptEventBuilder(nativeListener);
		verify(builder).setSource(pluginChild);
		verify(builder).setOp(1);
		verify(callbackEvent).setCanSendPackets(false);
		verify(callbackEvent).run();
		when(event.getOp()).thenReturn(7);
		((JavaScriptCallback) listener.getValue()).run(event);
		verify(builder).setOp(7);
		verify(wikiCopy, atLeastOnce()).setOriginalY(158);
		verify(pluginCopy).setClickMask(0);
		verify(pluginCopy).setTargetVerb(null);
		verify(pluginCopy, never()).setOnTargetEnterListener(any(Object[].class));
	}
	@Test
	public void wikiMovesAsideOnlyForVisibleOverlappingMultiCombatIndicator()
	{
		source(Orbs.WIKI_ICON_CONTAINER);
		orbs.update();
		Widget wiki = copies.get(0);
		when(wiki.getBounds()).thenReturn(new Rectangle(167, 158, 40, 34));
		when(wiki.getOriginalX()).thenReturn(4);
		Widget indicator = mock(Widget.class);
		when(indicator.getBounds()).thenReturn(new Rectangle(185, 174, 20, 20));
		when(client.getWidget(InterfaceID.ToplevelOsrsStretch.MULTIWAY_ICON)).thenReturn(indicator);
		when(indicator.isHidden()).thenReturn(true);
		orbs.update();
		verify(wiki, never()).setOriginalX(30);
		when(indicator.isHidden()).thenReturn(false);
		when(indicator.getBounds()).thenReturn(new Rectangle(250, 174, 20, 20));
		orbs.update();
		verify(wiki, never()).setOriginalX(30);
		when(indicator.getBounds()).thenReturn(new Rectangle(185, 174, 20, 20));
		orbs.update();
		verify(wiki).setOriginalX(30);
		verify(indicator, never()).setForcedPosition(anyInt(), anyInt());
		verify(indicator, never()).setHidden(anyBoolean());
	}

}
