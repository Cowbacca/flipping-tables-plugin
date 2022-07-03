/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.dashery.flippingtables;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GrandExchangeSearched;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@PluginDescriptor(
        name = "Flipping Tables",
        description = "Enable the Flipping Tables blah blah",
        tags = {"panel", "players"},
        loadWhenOutdated = true
)
@Slf4j
public class FlippingTablesPlugin extends Plugin {

    private static final int GE_OFFER_INIT_STATE_CHILD_ID = 18;
    private static final int GE_HISTORY_GROUP_ID = 383;
    private static final int SEARCHBOX_LOADED = 750;

    @Inject
    @Nullable
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private FlippingTablesConfig config;

    private NavigationButton navButton;
    private FlippingTablesPanel flippingTablesPanel;

    @Inject
    private FlippingTablesClient flippingTablesClient;
    @Inject
    private OfferAdviceRepository offerAdviceRepository;

    @Inject
    private GeOfferWidgetController geOfferWidgetController;

    @Inject
    private ClientThread clientThread;

    @Inject
    private GeLimitsTracker geLimitsTracker;

    @Inject
    private GeSearchButton geSearchButton;

    @Inject
    public FlippingTablesPlugin() {
    }

    @Provides
    FlippingTablesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FlippingTablesConfig.class);
    }

    @Override
    protected void startUp() {
        flippingTablesPanel = injector.getInstance(FlippingTablesPanel.class);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Flipping Tables")
                .icon(icon)
                .priority(5)
                .panel(flippingTablesPanel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        flippingTablesPanel.shutdown();
        clientToolbar.removeNavigation(navButton);
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event) {
        Widget geWidget = client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
        if (event.getIndex() == VarClientInt.INPUT_TYPE
                && geWidget != null
                && client.getVarcIntValue(VarClientInt.INPUT_TYPE) == 7
        ) {
            clientThread.invokeLater(() -> {
                String chatInputText = client.getWidget(WidgetInfo.CHATBOX_TITLE).getText();
                if (chatInputText.equals("How many do you wish to buy?")) {
                    geOfferWidgetController.handleBuyQuantityWidgetOpened();
                } else {
                    String offerText = geWidget
                            .getChild(GE_OFFER_INIT_STATE_CHILD_ID)
                            .getText();

                    switch (offerText) {
                        case "Buy offer":
                            geOfferWidgetController.handleBuyPriceWidgetOpened();
                            break;
                        case "Sell offer":
                            geOfferWidgetController.handleSellPriceWidgetOpened();
                            break;
                    }
                }
            });
        }
    }

    @Subscribe
    public void onGrandExchangeSearched(GrandExchangeSearched event) {
        final String input = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        if (input.equals("ft")) {
            Optional<OfferAdvice> offerAdviceOptional = offerAdviceRepository.find();
            offerAdviceOptional.ifPresent(offerAdvice -> {
                client.setGeSearchResultIndex(0);
                client.setGeSearchResultCount(offerAdvice.getNumberOfOffers());
                client.setGeSearchResultIds(offerAdvice.getItemIds());
                event.consume();
            });
        }
    }

    public void setupOffers(int slotsAvailable, int moneyAvailable, int buyWindow, int sellWindow) {
        OfferAdvice offerAdvice = flippingTablesClient.requestOfferAdvice(
                new OfferAdviceRequest(
                        slotsAvailable,
                        moneyAvailable,
                        new BuySellWindows(
                                new SerializableDuration(ChronoUnit.HOURS, buyWindow),
                                new SerializableDuration(ChronoUnit.HOURS, sellWindow)
                        ),
                        geLimitsTracker.getGeLimitsAlreadyUsed(),
                        config.members(),
                        60.0,
                        true
                )
        );

        log.info("Found offer advice {}", offerAdvice);

        offerAdviceRepository.save(offerAdvice);
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged offerChangedEvent) {
        geLimitsTracker.onGrandExchangeOfferChanged(offerChangedEvent);
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == SEARCHBOX_LOADED) {
            geSearchButton.init();
        }
    }
}
