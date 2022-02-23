package com.dashery.flippingtables;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.time.temporal.ChronoUnit;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;

@Singleton
@AllArgsConstructor(onConstructor = @__({@Inject}))
public class GeOfferWidgetController {
    private final OfferAdviceService offerAdviceService;
    private final FlippingTablesClient flippingTablesClient;
    private final Client client;
    private final WidgetCreator widgetCreator;
    private final FlippingTablesConfig config;

    public void handleBuyPriceWidgetOpened() {
        offerAdviceService.findPriceForItem(client.getVar(CURRENT_GE_ITEM)).ifPresent(price ->
                widgetCreator.createChildWidget(
                        WidgetInfo.CHATBOX_CONTAINER,
                        "Set price to " + price,
                        ev -> {
                            client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT).setText(price + "*");
                            client.setVar(VarClientStr.INPUT_TEXT, String.valueOf(price));
                        }
                ));
    }

    public void handleBuyQuantityWidgetOpened() {
        offerAdviceService.findQuantityForItem(client.getVar(CURRENT_GE_ITEM)).ifPresent(quantity ->
                widgetCreator.createChildWidget(
                        WidgetInfo.CHATBOX_CONTAINER,
                        "Set quantity to " + quantity,
                        ev -> {
                            client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT).setText(quantity + "*");
                            client.setVar(VarClientStr.INPUT_TEXT, String.valueOf(quantity));
                        }
                ));
    }

    public void handleSellPriceWidgetOpened() {
        new Thread(() -> {
            String quantityText = client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER)
                    .getChild(32)
                    .getText();

            SellAdvice sellAdvice = flippingTablesClient.getSellAdvice(new SellAdviceRequest(
                    Sets.newHashSet(
                            new ItemToSell(
                                    client.getVar(CURRENT_GE_ITEM),
                                    Integer.valueOf(quantityText)
                            )
                    ),
                    new SerializableDuration(ChronoUnit.HOURS, config.sellWindow())
            ));
            long price = sellAdvice.getItemSellAdvices().stream().findFirst().get().getSafePrice();

            widgetCreator.createChildWidget(
                    WidgetInfo.CHATBOX_CONTAINER,
                    "Set price to " + price,
                    ev -> {
                        client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT).setText(price + "*");
                        client.setVar(VarClientStr.INPUT_TEXT, String.valueOf(price));
                    }
            );
        }).start();
    }
}
