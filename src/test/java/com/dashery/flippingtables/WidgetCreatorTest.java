package com.dashery.flippingtables;

import net.runelite.api.Client;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class WidgetCreatorTest {
    @Test
    public void refreshReusesItsOwnWidgetAndRecreatesItAfterAChatboxRebuild() {
        Client client = mock(Client.class);
        Widget parent = mock(Widget.class);
        List<Widget> children = new ArrayList<>();
        when(client.getWidget(WidgetInfo.CHATBOX_CONTAINER)).thenReturn(parent);
        when(parent.getChild(anyInt())).thenAnswer(call -> {
            int index = call.getArgument(0);
            return index < children.size() ? children.get(index) : null;
        });
        when(parent.createChild(anyInt(), anyInt())).thenAnswer(call -> {
            Widget child = mock(Widget.class);
            when(child.getIndex()).thenReturn(children.size());
            children.add(child);
            return child;
        });
        WidgetCreator creator = new WidgetCreator(client);
        JavaScriptCallback callback = event -> {};
        creator.showSuggestion("Use suggested quantity: 12", "Use quantity", callback);
        creator.showSuggestion("Use suggested quantity: 12", "Use quantity", callback);
        verify(parent).createChild(anyInt(), anyInt());
        Widget first = children.get(0);
        children.clear();
        creator.showSuggestion("Use suggested price: 100", "Use price", callback);
        verify(parent, times(2)).createChild(anyInt(), anyInt());
        verify(first).setHidden(true);
        creator.clearSuggestion();
        verify(children.get(0)).setHidden(true);
    }
}
