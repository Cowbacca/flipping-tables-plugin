package com.dashery.flippingtables;

import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GeSearchButtonTest {
    private final Client client = mock(Client.class);
    private final ClientThread clientThread = mock(ClientThread.class);
    private final Queue<Runnable> deferredSearches = new ArrayDeque<>();
    private final Widget container = mock(Widget.class);
    private final Widget searchBox = mock(Widget.class);
    private final Widget exchange = mock(Widget.class);
    private final List<Widget> children = new ArrayList<>();
    private final AtomicReference<String> query = new AtomicReference<>("");
    private final Object[] listener = {42, "search"};
    private final AtomicBoolean scriptRunning = new AtomicBoolean();

    @Before
    public void setUp() {
        doAnswer(invocation -> {
            deferredSearches.add(invocation.getArgument(0, Runnable.class));
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(14);
        when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER)).thenReturn(exchange);
        when(client.getWidget(WidgetInfo.CHATBOX_CONTAINER)).thenReturn(container);
        when(client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT)).thenReturn(searchBox);
        when(client.getVarcStrValue(VarClientStr.INPUT_TEXT)).thenAnswer(invocation -> query.get());
        doAnswer(invocation -> { query.set(invocation.getArgument(1)); return null; })
                .when(client).setVarcStrValue(eq(VarClientStr.INPUT_TEXT), anyString());
        when(searchBox.getOnKeyListener()).thenReturn(listener);
        when(container.getChildren()).thenAnswer(invocation -> children.toArray(new Widget[0]));
        when(container.createChild(anyInt(), anyInt())).thenAnswer(invocation -> {
            Widget button = mock(Widget.class);
            children.add(button);
            return button;
        });
    }

    @Test
    public void secondBuySearchDoesNotRunNestedScriptsFromScriptPostFired() throws Exception {
        GeSearchButton button = new GeSearchButton(client, clientThread);
        FlippingTablesPlugin plugin = new FlippingTablesPlugin();
        java.lang.reflect.Field field = FlippingTablesPlugin.class.getDeclaredField("geSearchButton");
        field.setAccessible(true);
        field.set(plugin, button);
        doAnswer(invocation -> {
            assertFalse("scripts are not reentrant", scriptRunning.get());
            duringScript(() -> plugin.onScriptPostFired(new ScriptPostFired(ScriptID.GE_ITEM_SEARCH)));
            return null;
        }).when(client).runScript(listener);
        query.set("ft");
        button.init();
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(7);
        button.onInputTypeChanged();
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(14);
        query.set("");
        children.clear();
        button.onInputTypeChanged();

        duringScript(() -> plugin.onScriptPostFired(new ScriptPostFired(ScriptID.GE_ITEM_SEARCH)));

        verify(client, never()).runScript(listener);
        assertEquals("", query.get());
        button.init();
        assertEquals(1, deferredSearches.size());
        drainDeferredSearches();
        assertEquals("ft", query.get());
        verify(client).runScript(listener);
    }

    @Test
    public void searchButtonDoesNotRunNestedScriptsFromItsWidgetCallback() {
        GeSearchButton button = new GeSearchButton(client, clientThread);
        doAnswer(invocation -> {
            assertFalse("scripts are not reentrant", scriptRunning.get());
            duringScript(button::init);
            return null;
        }).when(client).runScript(listener);
        button.init();

        duringScript(() -> click(children.get(0)));

        verify(client, never()).runScript(listener);
        assertEquals("", query.get());
        drainDeferredSearches();
        assertEquals("ft", query.get());
        verify(client).runScript(listener);
    }

    @Test
    public void recreatesButtonWhenSearchScriptRebuildsChildrenUnderTheSameContainer() {
        GeSearchButton button = new GeSearchButton(client, clientThread);
        button.init();
        Widget first = children.get(0);
        children.clear();
        button.init();
        button.init();
        assertEquals(1, children.size());
        verify(container, times(2)).createChild(anyInt(), anyInt());
        verify(first).setHidden(true);
    }

    @Test
    public void remembersAdviceForTheNextOfferWithoutHidingTheSharedInput() {
        GeSearchButton button = new GeSearchButton(client, clientThread);
        button.init();
        click(children.get(0));
        drainDeferredSearches();
        assertEquals("ft", query.get());
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(7);
        button.onInputTypeChanged();
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(14);
        query.set("");
        children.clear();
        button.onInputTypeChanged();
        button.init();
        drainDeferredSearches();
        assertEquals("ft", query.get());
        verify(client, times(2)).runScript(listener);
        verify(searchBox, never()).setHidden(anyBoolean());
        verify(client, never()).setVarcIntValue(anyInt(), anyInt());
    }

    @Test
    public void manualSearchDisablesRememberedAdviceWithoutOverwritingTheQuery() {
        GeSearchButton button = new GeSearchButton(client, clientThread);
        button.init();
        click(children.get(0));
        drainDeferredSearches();
        query.set("lobster");
        button.init();
        assertEquals("lobster", query.get());
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(0);
        button.onInputTypeChanged();
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(14);
        query.set("");
        button.onInputTypeChanged();
        button.init();
        drainDeferredSearches();
        assertEquals("", query.get());
        verify(client, times(1)).runScript(listener);
    }

    @Test
    public void staleSearchControlCannotReplaceNumericDialogOrHiddenGeInput() {
        GeSearchButton button = new GeSearchButton(client, clientThread);
        button.init();
        Widget control = children.get(0);
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(7);
        button.onInputTypeChanged();
        click(control);
        button.init();
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(14);
        when(exchange.isHidden()).thenReturn(true);
        click(control);
        drainDeferredSearches();
        verify(client, never()).setVarcStrValue(anyInt(), anyString());
        verify(searchBox, never()).setHidden(anyBoolean());
    }

    @Test
    public void queuedSearchCannotOverwriteQuantityAfterDialogChanges() {
        GeSearchButton button = new GeSearchButton(client, clientThread);
        button.init();
        click(children.get(0));
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(7);
        button.onInputTypeChanged();
        query.set("150");

        drainDeferredSearches();

        assertEquals("150", query.get());
        verify(client, never()).runScript(listener);
        verify(client, never()).setVarcStrValue(anyInt(), anyString());
    }

    @Test
    public void queuedSearchCannotOverwriteAQueryTypedBeforeItRuns() {
        GeSearchButton button = new GeSearchButton(client, clientThread);
        button.init();
        click(children.get(0));
        query.set("lobster");

        drainDeferredSearches();

        assertEquals("lobster", query.get());
        verify(client, never()).runScript(listener);
        verify(client, never()).setVarcStrValue(anyInt(), anyString());
    }

    @Test
    public void queuedSearchCannotActOnAReplacedSearchWidget() {
        GeSearchButton button = new GeSearchButton(client, clientThread);
        button.init();
        click(children.get(0));
        when(client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT)).thenReturn(mock(Widget.class));

        drainDeferredSearches();

        verify(client, never()).runScript(listener);
        verify(client, never()).setVarcStrValue(anyInt(), anyString());
    }

    @Test
    public void resetCancelsTheQueuedSearch() {
        GeSearchButton button = new GeSearchButton(client, clientThread);
        button.init();
        click(children.get(0));
        button.reset();

        drainDeferredSearches();

        verify(client, never()).runScript(listener);
        verify(client, never()).setVarcStrValue(anyInt(), anyString());
    }

    @Test
    public void repeatedCallbacksQueueOnlyOneSearchAndCanToggleBackToNormalSearch() {
        GeSearchButton button = new GeSearchButton(client, clientThread);
        button.init();
        Widget control = children.get(0);
        duringScript(() -> {
            click(control);
            click(control);
            button.init();
        });
        assertEquals(1, deferredSearches.size());
        drainDeferredSearches();
        assertEquals("ft", query.get());

        duringScript(() -> click(control));
        drainDeferredSearches();

        assertEquals("", query.get());
        verify(client, times(2)).runScript(listener);
    }

    private void drainDeferredSearches() {
        assertFalse(scriptRunning.get());
        while (!deferredSearches.isEmpty()) {
            deferredSearches.remove().run();
        }
    }

    private void duringScript(Runnable callback) {
        scriptRunning.set(true);
        try {
            callback.run();
        } finally {
            scriptRunning.set(false);
        }
    }

    private void click(Widget button) {
        ArgumentCaptor<JavaScriptCallback> callback = ArgumentCaptor.forClass(JavaScriptCallback.class);
        verify(button).setOnOpListener(callback.capture());
        callback.getValue().run(null);
    }
}
