package com.dashery.flippingtables;

import lombok.AllArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;

@Singleton
@AllArgsConstructor(onConstructor = @__({@Inject}))
public class GeOfferWidgetController {
    private final PortfolioAdviceRepository repository;
    private final Client client;
    private final WidgetCreator widgetCreator;

    public void clearSuggestion() {
        widgetCreator.clearSuggestion();
    }

    public void refresh() {
        Dialog dialog = currentDialog();
        if (dialog == null) {
            clearSuggestion();
            return;
        }
        repository.selectedFor(dialog.itemId, dialog.side).ifPresentOrElse(action -> {
            long value = dialog.quantity ? action.getQuantity() : action.getPricePerItem();
            if (value <= 0 || value > Integer.MAX_VALUE) {
                clearSuggestion();
                return;
            }
            String field = dialog.quantity ? "quantity" : "price";
            widgetCreator.showSuggestion("Use suggested " + field + ": " + String.format(Locale.UK, "%,d", value),
                    "Use suggested " + field, event -> {
                        Dialog current = currentDialog();
                        if (current == null || !current.matches(dialog)
                                || repository.selectedFor(dialog.itemId, dialog.side).orElse(null) != action) {
                            return;
                        }
                        Widget input = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
                        if (input != null && !input.isHidden()) {
                            client.setVarcStrValue(VarClientStr.INPUT_TEXT, Long.toString(value));
                            input.setText(value + "*");
                        }
                    });
        }, this::clearSuggestion);
    }

    private Dialog currentDialog() {
        if (client.getGameState() != GameState.LOGGED_IN
                || client.getVarcIntValue(VarClientInt.INPUT_TYPE) != 7) {
            return null;
        }
        Widget offer = client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
        Widget title = client.getWidget(WidgetInfo.CHATBOX_TITLE);
        Widget input = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
        if (offer == null || offer.isHidden() || title == null || title.isHidden()
                || input == null || input.isHidden()) {
            return null;
        }
        String side = offerSide(offer);
        int itemId = client.getVarpValue(CURRENT_GE_ITEM);
        String prompt = plainText(title).toLowerCase(Locale.ROOT);
        if (side == null || itemId <= 0 || (!prompt.startsWith("how many") && !prompt.contains("price"))) {
            return null;
        }
        return new Dialog(itemId, side, prompt.startsWith("how many"), input);
    }

    private String offerSide(Widget offer) {
        Widget[] children = offer.getChildren();
        if (children != null) {
            for (Widget child : children) {
                if (child != null && !child.isHidden()) {
                    String text = plainText(child);
                    if ("Buy offer".equalsIgnoreCase(text)) {
                        return "BUY";
                    }
                    if ("Sell offer".equalsIgnoreCase(text)) {
                        return "SELL";
                    }
                }
            }
        }
        return null;
    }

    private String plainText(Widget widget) {
        return widget.getText() == null ? "" : Text.removeTags(widget.getText()).trim();
    }

    private static final class Dialog {
        private final int itemId;
        private final String side;
        private final boolean quantity;
        private final Widget input;

        private Dialog(int itemId, String side, boolean quantity, Widget input) {
            this.itemId = itemId;
            this.side = side;
            this.quantity = quantity;
            this.input = input;
        }

        private boolean matches(Dialog other) {
            return itemId == other.itemId && side.equals(other.side) && quantity == other.quantity && input == other.input;
        }
    }
}
