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
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GrandExchangeSearched;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@PluginDescriptor(
        name = "Flipping Tables",
        description = "Plan your next Grand Exchange visit using portfolio advice",
        tags = {"grand exchange", "flipping", "trading"}
)
public class FlippingTablesPlugin extends Plugin {
    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private FlippingTablesConfig config;
    @Inject private FlippingTablesClient api;
    @Inject private PortfolioCaptureService captureService;
    @Inject private PortfolioAdviceRepository repository;
    @Inject private GeOfferWidgetController geOfferWidgetController;
    @Inject private ClientThread clientThread;
    @Inject private ItemManager itemManager;
    @Inject private GeLimitsTracker geLimitsTracker;
    @Inject private GeSearchButton geSearchButton;

    private final AtomicLong generation = new AtomicLong();
    private NavigationButton navigation;
    private FlippingTablesPanel panel;
    private ExecutorService worker;
    private volatile CapturedPortfolio lastCapture;
    private volatile boolean running;

    @Provides
    FlippingTablesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FlippingTablesConfig.class);
    }

    @Override
    protected void startUp() {
        running = true;
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "flipping-tables-api");
            thread.setDaemon(true);
            return thread;
        });
        SwingUtilities.invokeLater(() -> {
            if (!running) {
                return;
            }
            panel = injector.getInstance(FlippingTablesPanel.class);
            navigation = NavigationButton.builder()
                    .tooltip("Flipping Tables")
                    .icon(ImageUtil.loadImageResource(getClass(), "/icon.png"))
                    .priority(5).panel(panel).build();
            clientToolbar.addNavigation(navigation);
        });
    }

    @Override
    protected void shutDown() {
        running = false;
        generation.incrementAndGet();
        api.cancelPendingRequest();
        repository.clear();
        lastCapture = null;
        geSearchButton.reset();
        geLimitsTracker.resetSession();
        if (worker != null) {
            worker.shutdownNow();
        }
        SwingUtilities.invokeLater(() -> {
            if (panel != null) {
                panel.shutdown();
                panel = null;
            }
            if (navigation != null) {
                clientToolbar.removeNavigation(navigation);
                navigation = null;
            }
        });
    }

    public void readPortfolio() {
        invalidate(null, true);
        long requestGeneration = generation.get();
        panel.setBusy(true);
        clientThread.invokeLater(() -> {
            try {
                CapturedPortfolio captured = captureService.capture();
                if (isCurrent(requestGeneration)) {
                    lastCapture = captured;
                    onPanel(requestGeneration, () -> panel.displayPortfolio(captured));
                }
            } catch (RuntimeException error) {
                onPanel(requestGeneration, () -> panel.showError(error.getMessage()));
            }
        });
    }

    public void requestAdvice(CapturedPortfolio displayed, Set<Long> selected, Map<Long, Long> costs,
            long cashBudget, Duration interval, String token) {
        invalidate(null, false);
        long requestGeneration = generation.get();
        panel.setBusy(true);
        clientThread.invokeLater(() -> {
            try {
                CapturedPortfolio captured = captureService.capture();
                if (!captured.getFingerprint().equals(displayed.getFingerprint())) {
                    throw new IllegalStateException("Your portfolio changed. Read it again before requesting advice.");
                }
                PortfolioModels.AdviceRequest request = PortfolioRequestBuilder.create(
                        captured, selected, costs, cashBudget, interval, config.volumeParticipationPercent());
                lastCapture = captured;
                worker.submit(() -> {
                    if (!isCurrent(requestGeneration)) {
                        return;
                    }
                    try {
                        PortfolioModels.AdviceResponse response = api.requestPortfolioAdvice(request, token);
                        clientThread.invokeLater(() -> acceptAdvice(requestGeneration, captured, request, response));
                    } catch (Exception error) {
                        onPanel(requestGeneration, () -> panel.showError(error.getMessage()));
                    }
                });
            } catch (RuntimeException error) {
                onPanel(requestGeneration, () -> panel.showError(error.getMessage()));
            }
        });
    }

    public void inputsChanged() {
        invalidate("Inputs changed. Request advice again when ready.", false);
    }

    private void acceptAdvice(long requestGeneration, CapturedPortfolio captured,
            PortfolioModels.AdviceRequest request, PortfolioModels.AdviceResponse response) {
        if (!isCurrent(requestGeneration)) {
            return;
        }
        try {
            if (!captureService.capture().getFingerprint().equals(captured.getFingerprint())) {
                invalidate("Your portfolio changed while advice was loading. Read it again.", true);
                return;
            }
            Map<Long, String> names = new HashMap<>();
            for (PortfolioModels.Action action : response.getAdvice().getActions()) {
                names.put(action.getItemId(), itemManager.getItemComposition(Math.toIntExact(action.getItemId())).getName());
            }
            repository.save(response, request.getSnapshot());
            onPanel(requestGeneration, () -> panel.showAdvice(response, names));
        } catch (RuntimeException error) {
            invalidate("Unable to verify this portfolio. Read it again before using advice.", true);
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        geLimitsTracker.onGrandExchangeOfferChanged(event);
        invalidateIfPortfolioChanged();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
            invalidateIfPortfolioChanged();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            geLimitsTracker.resetSession();
        }
        if (event.getGameState() != GameState.LOGGED_IN) {
            invalidate("Read your portfolio after logging in or returning to the game.", true);
            geSearchButton.reset();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (repository.isExpired()) {
            invalidate("Advice is five minutes old. Request a fresh plan.", false);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if ("flippingtables".equals(event.getGroup())) {
            invalidate("Configuration changed. Request a fresh plan.", false);
            SwingUtilities.invokeLater(() -> {
                if (panel != null) {
                    panel.configurationChanged("apiBaseUrl".equals(event.getKey()));
                }
            });
        }
    }

    @Subscribe
    public void onGrandExchangeSearched(GrandExchangeSearched event) {
        if ("ft".equals(client.getVarcStrValue(VarClientStr.INPUT_TEXT))) {
            short[] ids = repository.buyItemIds();
            if (ids.length > 0) {
                client.setGeSearchResultIndex(0);
                client.setGeSearchResultCount(ids.length);
                client.setGeSearchResultIds(ids);
                event.consume();
            }
        }
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() != VarClientInt.INPUT_TYPE) {
            return;
        }
        geOfferWidgetController.clearSuggestion();
        if (client.getVarcIntValue(VarClientInt.INPUT_TYPE) != 7) {
            return;
        }
        Widget offer = client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
        Widget title = client.getWidget(WidgetInfo.CHATBOX_TITLE);
        if (offer == null || title == null) {
            return;
        }
        Widget heading = offer.getChild(18);
        if (heading == null || title.getText() == null) {
            return;
        }
        if (title.getText().startsWith("How many")) {
            if ("Buy offer".equals(heading.getText())) {
                geOfferWidgetController.handleBuyQuantityWidgetOpened();
            } else if ("Sell offer".equals(heading.getText())) {
                geOfferWidgetController.handleSellQuantityWidgetOpened();
            }
        } else if (title.getText().toLowerCase(java.util.Locale.ROOT).contains("price")) {
            if ("Buy offer".equals(heading.getText())) {
                geOfferWidgetController.handleBuyPriceWidgetOpened();
            } else if ("Sell offer".equals(heading.getText())) {
                geOfferWidgetController.handleSellPriceWidgetOpened();
            }
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == 750) {
            geSearchButton.init();
        }
    }

    private void invalidateIfPortfolioChanged() {
        CapturedPortfolio previous = lastCapture;
        if (previous == null) {
            return;
        }
        try {
            if (!captureService.capture().getFingerprint().equals(previous.getFingerprint())) {
                invalidate("Inventory or offers changed. Read your portfolio again.", true);
            }
        } catch (RuntimeException error) {
            invalidate(error.getMessage(), true);
        }
    }

    private void invalidate(String message, boolean clearPortfolio) {
        long changedGeneration = generation.incrementAndGet();
        api.cancelPendingRequest();
        repository.clear();
        if (clearPortfolio) {
            lastCapture = null;
        }
        Runnable update = () -> {
            if (panel != null && generation.get() == changedGeneration) {
                panel.invalidateAdvice(message, clearPortfolio);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private boolean isCurrent(long requestGeneration) {
        return running && generation.get() == requestGeneration;
    }

    private void onPanel(long requestGeneration, Runnable operation) {
        SwingUtilities.invokeLater(() -> {
            if (isCurrent(requestGeneration) && panel != null) {
                operation.run();
            }
        });
    }
}
