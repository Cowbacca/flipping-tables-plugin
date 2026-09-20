package com.dashery.flippingtables;

import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class WidgetCreator {
    private final Client client;
    private Widget previousParent;
    private Widget previousSuggestion;

    public void clearSuggestion() {
        if (previousSuggestion != null) {
            previousSuggestion.setHidden(true);
        }
    }

    public void showSuggestion(String text, String action, JavaScriptCallback onClick) {
        Widget parent = client.getWidget(WidgetInfo.CHATBOX_CONTAINER);
        if (parent == null || parent.isHidden()) {
            clearSuggestion();
            return;
        }
        if (parent != previousParent || previousSuggestion == null
                || parent.getChild(previousSuggestion.getIndex()) != previousSuggestion) {
            clearSuggestion();
            previousParent = parent;
            previousSuggestion = parent.createChild(-1, WidgetType.TEXT);
            previousSuggestion.setTextColor(0x800000);
            previousSuggestion.setFontId(FontID.BOLD_12);
            previousSuggestion.setHasListener(true);
            previousSuggestion.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
            previousSuggestion.setOriginalX(0);
            previousSuggestion.setYPositionMode(WidgetPositionMode.ABSOLUTE_BOTTOM);
            previousSuggestion.setOriginalY(8);
            previousSuggestion.setOriginalHeight(20);
            previousSuggestion.setXTextAlignment(WidgetTextAlignment.CENTER);
            previousSuggestion.setYTextAlignment(WidgetTextAlignment.CENTER);
            previousSuggestion.setWidthMode(WidgetSizeMode.MINUS);
            previousSuggestion.revalidate();
        }
        previousSuggestion.setText(text);
        previousSuggestion.setAction(0, action);
        previousSuggestion.setOnOpListener(onClick);
        previousSuggestion.setHidden(false);
    }
}
