package com.dashery.flippingtables;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;

@Singleton
public final class GeLimitsTracker {
    public static final String UNKNOWN_HISTORY_WARNING = "Only buys observed since login are tracked; earlier/offline buys may reduce limits.";
    private static final Duration LIMIT_WINDOW = Duration.ofHours(4);

    private final Clock clock;
    private final IntFunction<Integer> limitLookup;
    private final Map<Integer, SlotObservation> slots = new HashMap<>();
    private final Map<Integer, UsageWindow> windowsByItem = new HashMap<>();

    @Inject
    public GeLimitsTracker(ItemManager itemManager) {
        this(Clock.systemUTC(), itemId -> {
            ItemStats itemStats = itemManager.getItemStats(itemId);
            return itemStats == null ? null : itemStats.getGeLimit();
        });
    }

    GeLimitsTracker(Clock clock, IntFunction<Integer> limitLookup) {
        this.clock = clock;
        this.limitLookup = limitLookup;
    }

    public synchronized void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        GrandExchangeOffer offer = event.getOffer();
        if (offer == null) {
            return;
        }
        GrandExchangeOfferState state = offer.getState();
        if (state != GrandExchangeOfferState.BUYING && state != GrandExchangeOfferState.BOUGHT
                && state != GrandExchangeOfferState.CANCELLED_BUY) {
            slots.remove(event.getSlot());
            return;
        }
        observeBuy(event.getSlot(), offer.getItemId(), offer.getQuantitySold(), clock.instant());
    }

    public synchronized Map<Long, PortfolioModels.ItemLimit> snapshotLimits() {
        Instant now = clock.instant();
        removeExpired(now);
        Map<Long, PortfolioModels.ItemLimit> limits = new LinkedHashMap<>();
        for (Map.Entry<Integer, UsageWindow> entry : windowsByItem.entrySet()) {
            Integer limit = limitLookup.apply(entry.getKey());
            if (limit == null || limit <= 0) {
                continue;
            }
            UsageWindow window = entry.getValue();
            limits.put(entry.getKey().longValue(), new PortfolioModels.ItemLimit(limit,
                    Math.min(window.getQuantity(), limit), window.getStartedAt().plus(LIMIT_WINDOW).toString()));
        }
        return limits;
    }

    public synchronized void resetSession() {
        slots.clear();
        windowsByItem.clear();
    }

    synchronized void observeBuy(int slot, int itemId, int filledQuantity, Instant now) {
        int safeFilledQuantity = Math.max(0, filledQuantity);
        SlotObservation previous = slots.get(slot);
        long delta = previous == null || previous.getItemId() != itemId || safeFilledQuantity < previous.getFilledQuantity()
                ? safeFilledQuantity : safeFilledQuantity - previous.getFilledQuantity();
        slots.put(slot, new SlotObservation(itemId, safeFilledQuantity));
        if (delta <= 0) {
            return;
        }
        UsageWindow currentWindow = windowsByItem.get(itemId);
        if (currentWindow == null || !currentWindow.getStartedAt().plus(LIMIT_WINDOW).isAfter(now)) {
            windowsByItem.put(itemId, new UsageWindow(now, delta));
            return;
        }
        currentWindow.add(delta);
    }

    private void removeExpired(Instant now) {
        Iterator<Map.Entry<Integer, UsageWindow>> entries = windowsByItem.entrySet().iterator();
        while (entries.hasNext()) {
            if (!entries.next().getValue().getStartedAt().plus(LIMIT_WINDOW).isAfter(now)) {
                entries.remove();
            }
        }
    }

    private static final class SlotObservation {
        private final int itemId;
        private final int filledQuantity;

        private SlotObservation(int itemId, int filledQuantity) {
            this.itemId = itemId;
            this.filledQuantity = filledQuantity;
        }

        private int getItemId() {
            return itemId;
        }

        private int getFilledQuantity() {
            return filledQuantity;
        }
    }

    private static final class UsageWindow {
        private final Instant startedAt;
        private long quantity;

        private UsageWindow(Instant startedAt, long quantity) {
            this.startedAt = startedAt;
            this.quantity = quantity;
        }

        private Instant getStartedAt() {
            return startedAt;
        }

        private long getQuantity() {
            return quantity;
        }

        private void add(long additionalQuantity) {
            quantity = Math.addExact(quantity, additionalQuantity);
        }
    }
}
