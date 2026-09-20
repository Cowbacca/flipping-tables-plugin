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
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Objects;

@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class GeSearchButton {
    private final Client client;
    private final ClientThread clientThread;
    private boolean preferAdvice;
    private boolean newSearch = true;
    private Widget container;
    private Widget button;
    private SearchRequest pendingSearch;

    public void onInputTypeChanged() {
        pendingSearch = null;
        newSearch = true;
        if (!isGeSearch()) {
            hideButton();
        }
    }

    public void init() {
        if (!isGeSearch()) {
            pendingSearch = null;
            hideButton();
            return;
        }
        Widget parent = client.getWidget(WidgetInfo.CHATBOX_CONTAINER);
        Widget input = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
        if (parent == null || parent.isHidden() || input == null) {
            pendingSearch = null;
            hideButton();
            return;
        }
        String query = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        if (newSearch && preferAdvice && (query == null || query.isEmpty())) {
            if (input.getOnKeyListener() == null) {
                return;
            }
            newSearch = false;
            requestSearch(parent, input, query, "ft");
        }
        newSearch = false;
        if (pendingSearch == null) {
            preferAdvice = "ft".equalsIgnoreCase(query);
        }
        if (parent != container || !containsButton(parent)) {
            hideButton();
            container = parent;
            button = createButton(parent);
        }
        button.setAction(0, preferAdvice ? "Show normal search" : "Show buy advice");
        button.setHidden(false);
    }

    public void reset() {
        pendingSearch = null;
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
        if (!isGeSearch() || pendingSearch != null) {
            return;
        }
        Widget parent = client.getWidget(WidgetInfo.CHATBOX_CONTAINER);
        Widget input = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
        if (parent == null || parent.isHidden() || input == null || input.getOnKeyListener() == null) {
            return;
        }
        String query = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        preferAdvice = !"ft".equalsIgnoreCase(query);
        newSearch = false;
        requestSearch(parent, input, query, preferAdvice ? "ft" : "");
    }

    private void requestSearch(Widget parent, Widget input, String originalQuery, String replacement) {
        if (pendingSearch != null) {
            return;
        }
        SearchRequest request = new SearchRequest(parent, input, originalQuery, replacement);
        pendingSearch = request;
        clientThread.invokeLater(() -> applySearch(request));
    }

    private void applySearch(SearchRequest request) {
        if (pendingSearch != request) {
            return;
        }
        pendingSearch = null;
        if (!isGeSearch() || client.getWidget(WidgetInfo.CHATBOX_CONTAINER) != request.parent
                || request.parent.isHidden() || client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT) != request.input
                || !Objects.equals(client.getVarcStrValue(VarClientStr.INPUT_TEXT), request.originalQuery)) {
            init();
            return;
        }
        Object[] listener = request.input.getOnKeyListener();
        if (listener == null) {
            return;
        }
        newSearch = false;
        preferAdvice = "ft".equalsIgnoreCase(request.replacement);
        client.setVarcStrValue(VarClientStr.INPUT_TEXT, request.replacement);
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

    @RequiredArgsConstructor
    private static class SearchRequest {
        private final Widget parent;
        private final Widget input;
        private final String originalQuery;
        private final String replacement;
    }
}
