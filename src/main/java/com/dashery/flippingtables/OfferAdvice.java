package com.dashery.flippingtables;

import com.google.common.primitives.Shorts;
import lombok.Data;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
public class OfferAdvice {
    private final List<Offers> offers;

    public int getNumberOfOffers() {
        return offers.size();
    }

    public short[] getItemIds() {
        return Shorts.toArray(
                offers.stream()
                        .map(Offers::getItemIdAsLong)
                        .collect(Collectors.toSet())
        );
    }

    public Optional<Long> priceForItem(int itemId) {
        return findOffersForItem(itemId).map(Offers::getBuyPrice);
    }

    private Optional<Offers> findOffersForItem(int itemId) {
        return offers.stream()
                .filter(offer -> offer.isForItem(itemId))
                .findFirst();
    }

    public Optional<Long> quantityForItem(int itemId) {
        return findOffersForItem(itemId).map(Offers::getBuyQuantity);
    }
}
