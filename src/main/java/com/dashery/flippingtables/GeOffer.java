package com.dashery.flippingtables;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Data
@RequiredArgsConstructor
public class GeOffer {
    @NonNull
    private Instant time;
    @NonNull
    private int slot;
    @NonNull
    private int quantityBought;
    @NonNull
    private int totalToBuy;
    private boolean cancelled = false;


    public boolean isOfferForSlot(int slot) {
        return slot == this.slot;
    }

    public boolean isUnfinishedOfferForSlot(int slot) {
        return slot == this.slot && (totalToBuy - quantityBought > 0);
    }

    public void markAsCancelled() {
        cancelled = true;
        slot = -1;
    }

    public boolean isYoungerThan4Hours() {
        return time.isAfter(Instant.now().minus(Duration.ofHours(4)));
    }

    public int getLimitUsed() {
        if (cancelled) {
            return quantityBought;
        } else {
            return totalToBuy;
        }
    }
}
