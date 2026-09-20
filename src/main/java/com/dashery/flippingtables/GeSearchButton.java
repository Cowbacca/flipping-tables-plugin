package com.dashery.flippingtables;

import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.SpriteID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class GeSearchButton {
    private final Client client;
    private boolean preferAdvice;
    private boolean newSearch = true;
    private Widget container;
    private Widget button;

    public void onInputTypeChanged() {
        newSearch = true;
        if (!isGeSearch()) {
            hideButton();
        }
    }

    public void init() {
        if (!isGeSearch()) {
            hideButton();
            return;
        }
        Widget parent = client.getWidget(WidgetInfo.CHATBOX_CONTAINER);
        Widget input = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
        if (parent == null || parent.isHidden() || input == null) {
            hideButton();
            return;
        }
        String query = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        if (newSearch && preferAdvice && (query == null || query.isEmpty())) {
            Object[] listener = input.getOnKeyListener();
            if (listener == null) {
                return;
            }
            newSearch = false;
            client.setVarcStrValue(VarClientStr.INPUT_TEXT, "ft");
            client.runScript(listener);
            query = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        }
        newSearch = false;
        preferAdvice = "ft".equalsIgnoreCase(query);
        if (parent != container || !containsButton(parent)) {
            hideButton();
            container = parent;
            button = createButton(parent);
        }
        button.setAction(0, preferAdvice ? "Show normal search" : "Show buy advice");
        button.setHidden(false);
    }

    public void reset() {
        preferAdvice = false;
        newSearch = true;
        hideButton();
    }

    private Widget createButton(Widget parent) {
        Widget widget = parent.createChild(-1, WidgetType.GRAPHIC);
        widget.setOriginalWidth(20);
        widget.setOriginalHeight(20);
        widget.setOriginalX(440);
        widget.setOriginalY(0);
        widget.setSpriteId(SpriteID.WELCOME_SCREEN_COINS);
        widget.setHasListener(true);
        widget.setOnOpListener((JavaScriptCallback) event -> toggleAdvice());
        widget.revalidate();
        return widget;
    }

    private void toggleAdvice() {
        if (!isGeSearch()) {
            return;
        }
        Widget input = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
        Object[] listener = input == null ? null : input.getOnKeyListener();
        if (listener == null) {
            return;
        }
        preferAdvice = !"ft".equalsIgnoreCase(client.getVarcStrValue(VarClientStr.INPUT_TEXT));
        newSearch = false;
        client.setVarcStrValue(VarClientStr.INPUT_TEXT, preferAdvice ? "ft" : "");
        client.runScript(listener);
        init();
    }

    private boolean isGeSearch() {
        Widget exchange = client.getWidget(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER);
        return client.getVarcIntValue(VarClientInt.INPUT_TYPE) == 14 && exchange != null && !exchange.isHidden();
    }

    private boolean containsButton(Widget parent) {
        Widget[] children = parent.getChildren();
        return button != null && children != null && Arrays.stream(children).anyMatch(child -> child == button);
    }

    private void hideButton() {
        if (button != null) {
            button.setHidden(true);
        }
    }
}
