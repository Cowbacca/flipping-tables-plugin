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
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GrandExchangeSearched;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientIntChanged;
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
    @Inject private OfferResultsRecorder offerResultsRecorder;
    @Inject private ConfigManager configManager;

    private final AtomicLong generation = new AtomicLong();
    private NavigationButton navigation;
    private FlippingTablesPanel panel;
    private ExecutorService worker;
    private volatile CapturedPortfolio lastCapture;
    private volatile boolean running;
    private boolean portfolioChanged;
    private volatile PortfolioPlanProgress planProgress;
    private volatile boolean expiryNotified;

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
        offerResultsRecorder.setStatusListener(message -> onPanel(generation.get(), () -> panel.setRecordingStatus(message)));
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
        clientThread.invokeLater(() -> {
            geSearchButton.reset();
            geOfferWidgetController.clearSuggestion();
        });
        planProgress = null;
        geLimitsTracker.resetSession();
        offerResultsRecorder.shutDown();
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
        long requestGeneration = invalidateRequest();
        panel.setBusy(true);
        clientThread.invokeLater(() -> {
            try {
                CapturedPortfolio captured = captureService.capture();
                if (isCurrent(requestGeneration)) {
                    boolean progressed = false;
                    if (lastCapture != null && !captured.getFingerprint().equals(lastCapture.getFingerprint())) {
                        if (planProgress != null && repository.isActionable()) {
                            java.util.Optional<PortfolioPlanProgress> advanced = planProgress.advance(captured);
                            if (advanced.isPresent()) {
                                planProgress = advanced.get();
                                repository.markCompleted(planProgress.getCompletedActions());
                                progressed = true;
                            } else {
                                planProgress = planProgress.rebase(captured);
                                repository.markStale();
                            }
                        } else if (repository.hasAdvice()) {
                            repository.markStale();
                        }
                    }
                    lastCapture = captured;
                    boolean planProgressed = progressed;
                    onPanel(requestGeneration, () -> {
                        panel.displayPortfolio(captured);
                        if (planProgressed) {
                            panel.showPlanProgress();
                        } else if (repository.hasAdvice() && repository.isStale()) {
                            panel.showAdviceStatus("Portfolio updated. Review the retained advice and request a fresh plan when ready.");
                        }
                    });
                }
            } catch (RuntimeException error) {
                onPanel(requestGeneration, () -> panel.showError(error.getMessage()));
            }
        });
    }

    public void requestAdvice(CapturedPortfolio displayed, Set<Long> selected, Map<Long, Long> costs,
            long cashBudget, Duration nextReturnInterval, String token) {
        long requestGeneration = invalidateRequest();
        panel.setBusy(true);
        clientThread.invokeLater(() -> {
            try {
                CapturedPortfolio captured = captureService.capture();
                if (!captured.getFingerprint().equals(displayed.getFingerprint())) {
                    throw new IllegalStateException("Your portfolio changed. Read it again before requesting advice.");
                }
                if (offerResultsRecorder.isRecordingEnabled() && offerResultsRecorder.accountId() == null) {
                    throw new IllegalStateException("Wait for offer recording to identify this account before requesting advice.");
                }
                PortfolioModels.AdviceRequest request = PortfolioRequestBuilder.create(
                        captured, selected, costs, cashBudget, nextReturnInterval,
                        config.volumeParticipationPercent(), offerResultsRecorder.accountId());
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
        markAdviceStale("Inputs changed. Review the retained advice and request a fresh plan when ready.");
    }

    public void configureOfferRecording(boolean enabled, String token) {
        if (config.recordOffers() != enabled) {
            configManager.setConfiguration("flippingtables", "recordOffers", enabled);
        }
        offerResultsRecorder.configure(enabled, config.apiBaseUrl(), token);
    }

    private void acceptAdvice(long requestGeneration, CapturedPortfolio captured,
            PortfolioModels.AdviceRequest request, PortfolioModels.AdviceResponse response) {
        if (!isCurrent(requestGeneration)) {
            return;
        }
        try {
            if (!captureService.capture().getFingerprint().equals(captured.getFingerprint())) {
                markAdviceStale("Your portfolio changed while advice was loading. Review the retained advice and read it again before requesting a fresh plan.");
                return;
            }
            Map<Long, String> names = new HashMap<>();
            for (PortfolioModels.Action action : response.getAdvice().getActions()) {
                names.put(action.getItemId(), itemManager.getItemComposition(Math.toIntExact(action.getItemId())).getName());
            }
            for (PortfolioModels.InventoryGuidance guidance : response.getAdvice().getInventoryGuidance()) {
                names.putIfAbsent(guidance.getItemId(), itemManager.getItemComposition(Math.toIntExact(guidance.getItemId())).getName());
            }
            repository.save(response, request.getSnapshot());
            expiryNotified = false;
            planProgress = PortfolioPlanProgress.start(captured, response.getAdvice().getActions());
            onPanel(requestGeneration, () -> panel.showAdvice(response, names));
        } catch (RuntimeException error) {
            markAdviceStale("Unable to verify this portfolio. Review the retained advice and read it again before requesting a fresh plan.");
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        geLimitsTracker.onGrandExchangeOfferChanged(event);
        if (offerResultsRecorder != null && client.getGameState() == GameState.LOGGED_IN && isNormalWorld()) {
            GrandExchangeOffer offer = event.getOffer();
            if (offer != null && offer.getState() == GrandExchangeOfferState.EMPTY) {
                offerResultsRecorder.clear(event.getSlot(), false);
            } else if (offer != null) {
                OfferSnapshot snapshot = offerSnapshot(event.getSlot(), offer);
                offerResultsRecorder.observe(snapshot, repository.exactRecommendationIdFor(snapshot.itemId, snapshot.side,
                        snapshot.totalQuantity, snapshot.pricePerItem), false);
            }
        }
        portfolioChanged = true;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
            portfolioChanged = true;
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
            if (offerResultsRecorder != null) {
                offerResultsRecorder.deactivate();
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (offerResultsRecorder != null && client.getGameState() == GameState.LOGGED_IN && isNormalWorld()) {
            activateOfferRecorder();
        }
        if (portfolioChanged) {
            portfolioChanged = false;
            invalidateIfPortfolioChanged();
        }
        if (repository.isExpired() && !expiryNotified) {
            expireAdvice("Advice is five minutes old. It is retained for reference; request a fresh plan before using suggestions.");
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        geSearchButton.init();
        geOfferWidgetController.refresh();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if ("flippingtables".equals(event.getGroup())) {
            if ("recordOffers".equals(event.getKey())) {
                SwingUtilities.invokeLater(() -> {
                    if (panel != null) {
                        panel.recordingConfigurationChanged(config.recordOffers());
                    }
                });
                return;
            }
            if ("apiBaseUrl".equals(event.getKey())) {
                invalidate("Configuration changed. Request a fresh plan.", true);
            } else {
                markAdviceStale("Configuration changed. Review the retained advice and request a fresh plan when ready.");
            }
            SwingUtilities.invokeLater(() -> {
                if (panel != null) {
                    panel.configurationChanged("apiBaseUrl".equals(event.getKey()));
                }
            });
            if (offerResultsRecorder != null && "apiBaseUrl".equals(event.getKey())) {
                offerResultsRecorder.configure(false, config.apiBaseUrl(), "");
            }
        }
    }

    @Subscribe
    public void onGrandExchangeSearched(GrandExchangeSearched event) {
        if ("ft".equalsIgnoreCase(client.getVarcStrValue(VarClientStr.INPUT_TEXT))) {
            short[] ids = repository.buyItemIds();
            client.setGeSearchResultIndex(0);
            client.setGeSearchResultCount(ids.length);
            client.setGeSearchResultIds(ids);
            event.consume();
        }
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() != VarClientInt.INPUT_TYPE) {
            return;
        }
        geOfferWidgetController.clearSuggestion();
        geSearchButton.onInputTypeChanged();
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == ScriptID.GE_ITEM_SEARCH) {
            geSearchButton.init();
        }
        if (event.getScriptId() == ScriptID.GE_OFFERS_SETUP_BUILD) {
            geOfferWidgetController.clearSuggestion();
        }
    }

    private void invalidateIfPortfolioChanged() {
        CapturedPortfolio previous = lastCapture;
        if (previous == null) {
            return;
        }
        try {
            CapturedPortfolio current = planProgress == null ? captureService.capture() : captureService.captureForProgress();
            if (current.getFingerprint().equals(previous.getFingerprint())) {
                return;
            }
            if (planProgress != null && !repository.isExpired()) {
                java.util.Optional<PortfolioPlanProgress> advanced = planProgress.advance(current);
                if (advanced.isPresent()) {
                    planProgress = advanced.get();
                    repository.markCompleted(planProgress.getCompletedActions());
                    lastCapture = current;
                    long changedGeneration = invalidateRequest();
                    onPanel(changedGeneration, () -> panel.showPlanProgress());
                    return;
                }
            }
            planProgress = planProgress == null ? null : planProgress.rebase(current);
            lastCapture = current;
            markAdviceStale("Portfolio changed. Review the retained advice and request a fresh plan when ready.");
        } catch (RuntimeException error) {
            markAdviceStale(error.getMessage());
        }
    }

    private long invalidateRequest() {
        long changedGeneration = generation.incrementAndGet();
        api.cancelPendingRequest();
        clientThread.invokeLater(geOfferWidgetController::clearSuggestion);
        return changedGeneration;
    }

    private void markAdviceStale(String message) {
        long changedGeneration = invalidateRequest();
        repository.markStale();
        Runnable update = () -> {
            if (running && generation.get() == changedGeneration && panel != null) {
                panel.showAdviceStatus(message);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private void expireAdvice(String message) {
        long changedGeneration = generation.get();
        expiryNotified = true;
        repository.markStale();
        Runnable update = () -> {
            if (running && generation.get() == changedGeneration && repository.isExpired() && panel != null) {
                panel.showPlanStale(message);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private void invalidate(String message, boolean clearPortfolio) {
        long changedGeneration = generation.incrementAndGet();
        api.cancelPendingRequest();
        repository.clear();
        expiryNotified = false;
        planProgress = null;
        clientThread.invokeLater(geOfferWidgetController::clearSuggestion);
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

    private void activateOfferRecorder() {
        String profileKey = configManager.getRSProfileKey();
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (profileKey == null || offers == null) {
            return;
        }
        java.util.List<OfferSnapshot> snapshots = new java.util.ArrayList<>();
        for (int slot = 0; slot < offers.length; slot++) {
            GrandExchangeOffer offer = offers[slot];
            if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY) {
                snapshots.add(offerSnapshot(slot, offer));
            }
        }
        offerResultsRecorder.activate(profileKey, snapshots, offers.length);
    }

    private OfferSnapshot offerSnapshot(int slot, GrandExchangeOffer offer) {
        GrandExchangeOfferState state = offer.getState();
        String side = state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT
                || state == GrandExchangeOfferState.CANCELLED_BUY ? "BUY" : "SELL";
        String resultState = state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD ? "FILLED"
                : state == GrandExchangeOfferState.CANCELLED_BUY || state == GrandExchangeOfferState.CANCELLED_SELL ? "CANCELLED" : "OPEN";
        return new OfferSnapshot(slot, offer.getItemId(), side, resultState, offer.getPrice(), offer.getTotalQuantity(),
                offer.getQuantitySold(), offer.getSpent());
    }

    private boolean isNormalWorld() {
        java.util.EnumSet<net.runelite.api.WorldType> worlds = client.getWorldType();
        return worlds != null && !worlds.contains(net.runelite.api.WorldType.DEADMAN)
                && !worlds.contains(net.runelite.api.WorldType.SEASONAL)
                && !worlds.contains(net.runelite.api.WorldType.TOURNAMENT_WORLD)
                && !worlds.contains(net.runelite.api.WorldType.FRESH_START_WORLD)
                && !worlds.contains(net.runelite.api.WorldType.BETA_WORLD);
    }
}
