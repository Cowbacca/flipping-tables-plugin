package com.dashery.flippingtables;

import lombok.AllArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;

@Singleton
@AllArgsConstructor(onConstructor = @__({@Inject}))
public class GeOfferWidgetController {
    private final PortfolioAdviceRepository repository;
    private final Client client;
    private final WidgetCreator widgetCreator;
    private final ClientThread clientThread;

    public void handleBuyPriceWidgetOpened() {
        showSuggestion("BUY", false);
    }

    public void handleBuyQuantityWidgetOpened() {
        showSuggestion("BUY", true);
    }

    public void handleSellPriceWidgetOpened() {
        showSuggestion("SELL", false);
    }

    public void handleSellQuantityWidgetOpened() {
        showSuggestion("SELL", true);
    }

    public void clearSuggestion() {
        widgetCreator.clearSuggestion();
    }

    private void showSuggestion(String side, boolean quantity) {
        clientThread.invokeLater(() -> {
            int itemId = client.getVarpValue(CURRENT_GE_ITEM);
            repository.selectedFor(itemId, side).ifPresent(action -> {
                long value = quantity ? action.getQuantity() : action.getPricePerItem();
                widgetCreator.createChildWidget(WidgetInfo.CHATBOX_CONTAINER,
                        "Set " + (quantity ? "quantity" : "price") + " to " + value,
                        event -> {
                            if (client.getVarpValue(CURRENT_GE_ITEM) != itemId
                                    || !matchesDialog(side, quantity)
                                    || !repository.selectedFor(itemId, side).filter(selected -> selected == action).isPresent()) {
                                return;
                            }
                            Widget input = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
                            if (input != null) {
                                input.setText(value + "*");
                                client.setVarcStrValue(VarClientStr.INPUT_TEXT, Long.toString(value));
                            }
                        });
            });
        });
    }

    private boolean matchesDialog(String side, boolean quantity) {
        Widget offer = client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
        Widget title = client.getWidget(WidgetInfo.CHATBOX_TITLE);
        Widget heading = offer == null ? null : offer.getChild(18);
        if (client.getVarcIntValue(VarClientInt.INPUT_TYPE) != 7 || heading == null || title == null || title.getText() == null) {
            return false;
        }
        String expected = "BUY".equals(side) ? "Buy offer" : "Sell offer";
        boolean matchingInput = quantity ? title.getText().startsWith("How many")
                : title.getText().toLowerCase(java.util.Locale.ROOT).contains("price");
        return expected.equals(heading.getText()) && matchingInput;
    }
}
