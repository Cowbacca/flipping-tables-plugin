package com.dashery.flippingtables;

import java.time.Instant;
import java.util.UUID;

final class TradeObservation {
    final String eventId;
    final String offerId;
    final int sequence;
    final int slot;
    final int itemId;
    final String side;
    final String state;
    final int pricePerItem;
    final int totalQuantity;
    final int filledQuantity;
    final long cumulativeGp;
    final String observedAt;
    final String previousObservedAt;
    final boolean baseline;
    final boolean gap;
    final String recommendationId;

    TradeObservation(String offerId, int sequence, OfferSnapshot offer, String state, Instant observedAt,
            Instant previousObservedAt, boolean baseline, boolean gap, String recommendationId) {
        this.eventId = UUID.randomUUID().toString();
        this.offerId = offerId;
        this.sequence = sequence;
        this.slot = offer.slot;
        this.itemId = offer.itemId;
        this.side = offer.side;
        this.state = state;
        this.pricePerItem = offer.pricePerItem;
        this.totalQuantity = offer.totalQuantity;
        this.filledQuantity = offer.filledQuantity;
        this.cumulativeGp = offer.cumulativeGp;
        this.observedAt = observedAt.toString();
        this.previousObservedAt = previousObservedAt == null ? null : previousObservedAt.toString();
        this.baseline = baseline;
        this.gap = gap;
        this.recommendationId = recommendationId;
    }
}

final class OfferSnapshot {
    final int slot;
    final int itemId;
    final String side;
    final String state;
    final int pricePerItem;
    final int totalQuantity;
    final int filledQuantity;
    final long cumulativeGp;

    OfferSnapshot(int slot, int itemId, String side, String state, int pricePerItem, int totalQuantity,
            int filledQuantity, long cumulativeGp) {
        this.slot = slot;
        this.itemId = itemId;
        this.side = side;
        this.state = state;
        this.pricePerItem = pricePerItem;
        this.totalQuantity = totalQuantity;
        this.filledQuantity = filledQuantity;
        this.cumulativeGp = cumulativeGp;
    }

    boolean sameDescriptor(TrackedOffer offer) {
        return offer != null && itemId == offer.itemId && side.equals(offer.side)
                && pricePerItem == offer.pricePerItem && totalQuantity == offer.totalQuantity;
    }
}

final class TrackedOffer {
    String offerId;
    int sequence;
    int itemId;
    String side;
    int pricePerItem;
    int totalQuantity;
    int filledQuantity;
    long cumulativeGp;
    String observedAt;
    String state;
    boolean active;

    static TrackedOffer start(OfferSnapshot offer) {
        TrackedOffer tracked = new TrackedOffer();
        tracked.offerId = UUID.randomUUID().toString();
        tracked.sequence = 0;
        tracked.itemId = offer.itemId;
        tracked.side = offer.side;
        tracked.pricePerItem = offer.pricePerItem;
        tracked.totalQuantity = offer.totalQuantity;
        tracked.filledQuantity = offer.filledQuantity;
        tracked.cumulativeGp = offer.cumulativeGp;
        tracked.state = offer.state;
        tracked.active = true;
        return tracked;
    }

    void update(OfferSnapshot offer, Instant at, boolean active) {
        sequence++;
        filledQuantity = offer.filledQuantity;
        cumulativeGp = offer.cumulativeGp;
        state = offer.state;
        observedAt = at.toString();
        this.active = active;
    }
}
