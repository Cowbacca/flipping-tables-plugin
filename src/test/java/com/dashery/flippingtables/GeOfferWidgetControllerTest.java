package com.dashery.flippingtables;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.VarPlayer;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class GeOfferWidgetControllerTest {
    private final Client client = mock(Client.class);
    private final WidgetCreator widgets = mock(WidgetCreator.class);
    private final Widget offer = mock(Widget.class);
    private final Widget title = mock(Widget.class);
    private final Widget heading = mock(Widget.class);
    private final Widget input = mock(Widget.class);
    private final PortfolioAdviceRepository repository = new PortfolioAdviceRepository();
    private final GeOfferWidgetController controller = new GeOfferWidgetController(repository, client, widgets);

    @Before
    public void setUp() {
        PortfolioModels.Action buy = new PortfolioModels.Action("CREATE_BUY", 4151, 12, 123456, null);
        PortfolioModels.Action sell = new PortfolioModels.Action("CREATE_SELL", 4151, 3, 130000, null);
        repository.save(new PortfolioModels.AdviceResponse(1, new PortfolioModels.Advice(Arrays.asList(buy, sell),
                0, 0, 0, 0, Collections.emptyList(), "EXACT"), null), null);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(7);
        when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(4151);
        when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER)).thenReturn(offer);
        when(client.getWidget(WidgetInfo.CHATBOX_TITLE)).thenReturn(title);
        when(client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT)).thenReturn(input);
        when(offer.getChildren()).thenReturn(new Widget[]{heading});
        when(heading.getText()).thenReturn("<col=ffffff>Buy offer</col>");
        when(title.getText()).thenReturn("How many do you wish to buy?");
    }

    @Test
    public void selectingSuggestedGeItemEnablesQuantityAndPriceOnTheirOwnClicks() {
        controller.refresh();
        verify(client, never()).setVarcStrValue(anyInt(), anyString());
        callback("Use suggested quantity: 12", "Use suggested quantity").run(null);
        verify(client).setVarcStrValue(VarClientStr.INPUT_TEXT, "12");
        verify(input).setText("12*");

        when(title.getText()).thenReturn("Set a price for each item:");
        controller.refresh();
        callback("Use suggested price: 123,456", "Use suggested price").run(null);
        verify(client).setVarcStrValue(VarClientStr.INPUT_TEXT, "123456");
        verify(client, never()).runScript(any(Object[].class));
    }

    @Test
    public void staleCallbackCannotFillADifferentItemOrDifferentDialog() {
        controller.refresh();
        JavaScriptCallback callback = callback("Use suggested quantity: 12", "Use suggested quantity");
        when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(1515);
        callback.run(null);
        when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(4151);
        when(title.getText()).thenReturn("Set a price for each item:");
        callback.run(null);
        verify(client, never()).setVarcStrValue(anyInt(), anyString());
    }

    @Test
    public void rechecksTheRenderedDialogAfterTheInputTypeEvent() {
        when(heading.getText()).thenReturn("");
        controller.refresh();
        verify(widgets).clearSuggestion();
        when(heading.getText()).thenReturn("Sell offer");
        when(title.getText()).thenReturn("How many do you wish to sell?");
        controller.refresh();
        callback("Use suggested quantity: 3", "Use suggested quantity").run(null);
        verify(client).setVarcStrValue(VarClientStr.INPUT_TEXT, "3");
    }

    @Test
    public void hiddenGeAndClearedAdviceCannotWriteIntoUnrelatedNumericInput() {
        controller.refresh();
        JavaScriptCallback callback = callback("Use suggested quantity: 12", "Use suggested quantity");
        when(offer.isHidden()).thenReturn(true);
        callback.run(null);
        when(offer.isHidden()).thenReturn(false);
        repository.clear();
        callback.run(null);
        controller.refresh();
        verify(client, never()).setVarcStrValue(anyInt(), anyString());
        verify(widgets).clearSuggestion();
    }

    private JavaScriptCallback callback(String label, String action) {
        ArgumentCaptor<JavaScriptCallback> callback = ArgumentCaptor.forClass(JavaScriptCallback.class);
        verify(widgets).showSuggestion(eq(label), eq(action), callback.capture());
        return callback.getValue();
    }
}
