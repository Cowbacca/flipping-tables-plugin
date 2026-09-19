package com.dashery.flippingtables;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeLimitsTrackerTest {
    @Test
    public void recordsOnlyNewFilledQuantityForDuplicateEvents() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        GeLimitsTracker tracker = new GeLimitsTracker(clock, itemId -> 100);

        tracker.onGrandExchangeOfferChanged(event(0, offer(4151, 20, GrandExchangeOfferState.BUYING)));
        tracker.onGrandExchangeOfferChanged(event(0, offer(4151, 20, GrandExchangeOfferState.BUYING)));
        tracker.onGrandExchangeOfferChanged(event(0, offer(4151, 27, GrandExchangeOfferState.BUYING)));

        assertEquals(27, tracker.snapshotLimits().get(4151L).getUsed());
    }

    @Test
    public void completedAndCancelledSlotsDoNotDoubleCountWhenReused() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        GeLimitsTracker tracker = new GeLimitsTracker(clock, itemId -> 100);

        tracker.onGrandExchangeOfferChanged(event(1, offer(4151, 10, GrandExchangeOfferState.BOUGHT)));
        tracker.onGrandExchangeOfferChanged(event(1, offer(4151, 10, GrandExchangeOfferState.BOUGHT)));
        tracker.onGrandExchangeOfferChanged(event(1, offer(4151, 3, GrandExchangeOfferState.BUYING)));
        tracker.onGrandExchangeOfferChanged(event(1, offer(4151, 3, GrandExchangeOfferState.CANCELLED_BUY)));
        tracker.onGrandExchangeOfferChanged(event(1, offer(4151, 3, GrandExchangeOfferState.CANCELLED_BUY)));

        assertEquals(13, tracker.snapshotLimits().get(4151L).getUsed());
    }

    @Test
    public void newFillsAfterTheWindowStartANewWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        GeLimitsTracker tracker = new GeLimitsTracker(clock, itemId -> 100);

        tracker.onGrandExchangeOfferChanged(event(2, offer(4151, 10, GrandExchangeOfferState.BUYING)));
        clock.set(Instant.parse("2026-09-19T14:00:01Z"));
        tracker.onGrandExchangeOfferChanged(event(2, offer(4151, 14, GrandExchangeOfferState.BUYING)));

        PortfolioModels.ItemLimit limit = tracker.snapshotLimits().get(4151L);
        assertEquals(4, limit.getUsed());
        assertEquals("2026-09-19T18:00:01Z", limit.getRefreshAt());
    }

    @Test
    public void resetSessionRemovesObservedAccountState() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        GeLimitsTracker tracker = new GeLimitsTracker(clock, itemId -> 100);

        tracker.onGrandExchangeOfferChanged(event(0, offer(4151, 10, GrandExchangeOfferState.BUYING)));
        tracker.resetSession();

        assertTrue(tracker.snapshotLimits().isEmpty());
    }

    @Test
    public void visiblePartialFillUsesItsFirstObservationAsConservativeUsage() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        GeLimitsTracker tracker = new GeLimitsTracker(clock, itemId -> 20);

        tracker.onGrandExchangeOfferChanged(event(0, offer(4151, 99, GrandExchangeOfferState.BUYING)));

        Map<Long, PortfolioModels.ItemLimit> limits = tracker.snapshotLimits();
        assertEquals(20, limits.get(4151L).getUsed());
        assertEquals("2026-09-19T14:00:00Z", limits.get(4151L).getRefreshAt());
    }

    @Test
    public void zeroFillWaitsForTheFirstActualBuyBeforeStartingTheWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        GeLimitsTracker tracker = new GeLimitsTracker(clock, itemId -> 100);

        tracker.onGrandExchangeOfferChanged(event(0, offer(4151, 0, GrandExchangeOfferState.BUYING)));
        clock.set(Instant.parse("2026-09-19T12:00:00Z"));
        tracker.onGrandExchangeOfferChanged(event(0, offer(4151, 1, GrandExchangeOfferState.BUYING)));

        assertEquals("2026-09-19T16:00:00Z", tracker.snapshotLimits().get(4151L).getRefreshAt());
    }

    @Test
    public void separateSlotsShareTheFirstBuyWindowForAnItem() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        GeLimitsTracker tracker = new GeLimitsTracker(clock, itemId -> 100);

        tracker.onGrandExchangeOfferChanged(event(0, offer(4151, 10, GrandExchangeOfferState.BUYING)));
        clock.set(Instant.parse("2026-09-19T11:00:00Z"));
        tracker.onGrandExchangeOfferChanged(event(1, offer(4151, 5, GrandExchangeOfferState.BUYING)));

        PortfolioModels.ItemLimit limit = tracker.snapshotLimits().get(4151L);
        assertEquals(15, limit.getUsed());
        assertEquals("2026-09-19T14:00:00Z", limit.getRefreshAt());
    }

    private static GrandExchangeOfferChanged event(int slot, GrandExchangeOffer offer) {
        GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
        event.setSlot(slot);
        event.setOffer(offer);
        return event;
    }

    private static GrandExchangeOffer offer(int itemId, int quantitySold, GrandExchangeOfferState state) {
        return new GrandExchangeOffer() {
            @Override
            public int getQuantitySold() {
                return quantitySold;
            }

            @Override
            public int getItemId() {
                return itemId;
            }

            @Override
            public int getTotalQuantity() {
                return 100;
            }

            @Override
            public int getPrice() {
                return 1;
            }

            @Override
            public int getSpent() {
                return quantitySold;
            }

            @Override
            public GrandExchangeOfferState getState() {
                return state;
            }
        };
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
