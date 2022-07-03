package com.dashery.flippingtables;

import lombok.AllArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.SpriteID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AllArgsConstructor(onConstructor = @__({@Inject}))
public class GeSearchButton {
    private static boolean showFlippingTablesResults = false;
    private final Client client;
    private final ClientThread clientThread;

    public void init() {
        Widget container = client.getWidget(WidgetInfo.CHATBOX_CONTAINER);
        Widget widget = container.createChild(-1, WidgetType.GRAPHIC);
        widget.setOriginalWidth(20);
        widget.setOriginalHeight(20);
        widget.setOriginalX(440);
        widget.setOriginalY(0);
        widget.setSpriteId(SpriteID.WELCOME_SCREEN_COINS);
        widget.setAction(1, "Show buy advice");
        widget.setHasListener(true);
        widget.setOnOpListener((JavaScriptCallback) ev -> toggleFlippingTablesResults());
        widget.revalidate();

        if (showFlippingTablesResults) {
            clientThread.invokeLater(this::updateSearchBox);
        }
    }

    private void updateSearchBox() {
        client.setVarcStrValue(VarClientStr.INPUT_TEXT, showFlippingTablesResults ? "ft" : "");
        client.setVarcIntValue(VarClientInt.INPUT_TYPE, 14);

        Widget geSearchBox = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
        if (geSearchBox == null) {
            return;
        }

        Object[] scriptArgs = geSearchBox.getOnKeyListener();
        if (scriptArgs == null) {
            return;
        }

        client.runScript(scriptArgs);
        geSearchBox.setHidden(showFlippingTablesResults);
    }

    private void toggleFlippingTablesResults() {
        showFlippingTablesResults = !showFlippingTablesResults;
        updateSearchBox();
    }
}
