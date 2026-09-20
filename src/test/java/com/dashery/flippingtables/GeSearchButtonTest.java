package com.dashery.flippingtables;

import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GeSearchButtonTest {
    private final Client client = mock(Client.class);
    private final Widget container = mock(Widget.class);
    private final Widget searchBox = mock(Widget.class);
    private final Widget exchange = mock(Widget.class);
    private final List<Widget> children = new ArrayList<>();
    private final AtomicReference<String> query = new AtomicReference<>("");
    private final Object[] listener = {42, "search"};

    @Before
    public void setUp() {
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
    public void recreatesButtonWhenSearchScriptRebuildsChildrenUnderTheSameContainer() {
        GeSearchButton button = new GeSearchButton(client);
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
        GeSearchButton button = new GeSearchButton(client);
        button.init();
        click(children.get(0));
        assertEquals("ft", query.get());
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(7);
        button.onInputTypeChanged();
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(14);
        query.set("");
        children.clear();
        button.onInputTypeChanged();
        button.init();
        assertEquals("ft", query.get());
        verify(client, times(2)).runScript(listener);
        verify(searchBox, never()).setHidden(anyBoolean());
        verify(client, never()).setVarcIntValue(anyInt(), anyInt());
    }

    @Test
    public void manualSearchDisablesRememberedAdviceWithoutOverwritingTheQuery() {
        GeSearchButton button = new GeSearchButton(client);
        button.init();
        click(children.get(0));
        query.set("lobster");
        button.init();
        assertEquals("lobster", query.get());
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(0);
        button.onInputTypeChanged();
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(14);
        query.set("");
        button.onInputTypeChanged();
        button.init();
        assertEquals("", query.get());
        verify(client, times(1)).runScript(listener);
    }

    @Test
    public void staleSearchControlCannotReplaceNumericDialogOrHiddenGeInput() {
        GeSearchButton button = new GeSearchButton(client);
        button.init();
        Widget control = children.get(0);
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(7);
        button.onInputTypeChanged();
        click(control);
        button.init();
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(14);
        when(exchange.isHidden()).thenReturn(true);
        click(control);
        verify(client, never()).setVarcStrValue(anyInt(), anyString());
        verify(searchBox, never()).setHidden(anyBoolean());
    }

    private void click(Widget button) {
        ArgumentCaptor<JavaScriptCallback> callback = ArgumentCaptor.forClass(JavaScriptCallback.class);
        verify(button).setOnOpListener(callback.capture());
        callback.getValue().run(null);
    }
}
