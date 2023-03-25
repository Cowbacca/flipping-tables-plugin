package com.dashery.flippingtables;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;

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
    private final ClientThread clientThread;

    public void handleBuyPriceWidgetOpened() {
        clientThread.invokeLater(() -> {
                    int varbitValue = client.getVarpValue(CURRENT_GE_ITEM);
                    offerAdviceService.findPriceForItem(varbitValue).ifPresent(price ->
                            widgetCreator.createChildWidget(
                                    WidgetInfo.CHATBOX_CONTAINER,
                                    "Set price to " + price,
                                    ev -> {
                                        client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT).setText(price + "*");
                                        client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(price));
                                    }
                            ));
                }
        );

    }

    public void handleBuyQuantityWidgetOpened() {
        offerAdviceService.findQuantityForItem(client.getVarpValue(CURRENT_GE_ITEM)).ifPresent(quantity ->
                widgetCreator.createChildWidget(
                        WidgetInfo.CHATBOX_CONTAINER,
                        "Set quantity to " + quantity,
                        ev -> {
                            client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT).setText(quantity + "*");
                            client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(quantity));
                        }
                ));
    }

    public void handleSellPriceWidgetOpened() {
        clientThread.invokeLater(() -> {
            String quantityText = client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER)
                    .getChild(32)
                    .getText();

            SellAdvice sellAdvice = flippingTablesClient.getSellAdvice(new SellAdviceRequest(
                    Sets.newHashSet(
                            new ItemToSell(
                                    client.getVarpValue(CURRENT_GE_ITEM),
                                    Integer.parseInt(quantityText)
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
                        client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(price));
                    }
            );
        });
    }
}
