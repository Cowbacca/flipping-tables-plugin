package com.dashery.flippingtables;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
public class GeLimitsTracker {


    private static Map<Integer, List<GeOffer>> offersByItemId = new HashMap<>();

    public Map<Long, GeLimitUsage> getGeLimitsAlreadyUsed() {
        removeOldOffers();

        Map<Long, GeLimitUsage> geLimitsAlreadyUsed = offersByItemId.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().longValue(),
                        entry -> {
                            Integer totalLimitUsed = entry.getValue().stream()
                                    .filter(GeOffer::isYoungerThan4Hours)
                                    .reduce(0,
                                            (limitUsed, nextOffer) -> limitUsed + nextOffer.getLimitUsed(),
                                            Integer::sum
                                    );
                            Instant limitRefreshTimestamp = entry.getValue().stream()
                                    .filter(GeOffer::isYoungerThan4Hours).map(GeOffer::getTime).min(Comparator.comparing(Instant::getEpochSecond)).get();
                            return new GeLimitUsage(totalLimitUsed, limitRefreshTimestamp.toEpochMilli());
                        }));
        return geLimitsAlreadyUsed;
    }

    private void removeOldOffers() {
        offersByItemId = offersByItemId.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream().filter(GeOffer::isYoungerThan4Hours).collect(Collectors.toList()))
        ).entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged offerChangedEvent) {
        GrandExchangeOffer offer = offerChangedEvent.getOffer();
        if (offer.getState() == GrandExchangeOfferState.BUYING || offer.getState() == GrandExchangeOfferState.BOUGHT) {
            addOrUpdateBuyOffer(offerChangedEvent, offer);
        }

        if (offer.getState() == GrandExchangeOfferState.CANCELLED_BUY) {
            cancelExistingBuyOffer(offerChangedEvent, offer);
        }
    }

    private void addOrUpdateBuyOffer(GrandExchangeOfferChanged offerChangedEvent, GrandExchangeOffer offer) {
        offersByItemId.compute(offer.getItemId(), (key, existingOffers) -> {
            int slot = getSlot(offerChangedEvent);
            GeOffer geOffer = new GeOffer(Instant.now(), slot, offer.getQuantitySold(), offer.getTotalQuantity());
            if (existingOffers == null) {
                return Lists.newArrayList(geOffer);
            } else {
                List<GeOffer> newList = existingOffers.stream()
                        .filter(existingGeOffer -> !existingGeOffer.isOfferForSlot(offerChangedEvent.getSlot()))
                        .filter(GeOffer::isYoungerThan4Hours)
                        .collect(Collectors.toList());
                newList.add(geOffer);
                return newList;
            }
        });
    }

    private void cancelExistingBuyOffer(GrandExchangeOfferChanged offerChangedEvent, GrandExchangeOffer offer) {
        List<GeOffer> existingOffers = offersByItemId.getOrDefault(offer.getItemId(), Lists.newArrayList());
        existingOffers.stream()
                .filter(existingOffer -> existingOffer.isUnfinishedOfferForSlot(offerChangedEvent.getSlot()))
                .forEach(GeOffer::markAsCancelled);
    }

    private int getSlot(GrandExchangeOfferChanged offerChangedEvent) {
        if (offerChangedEvent.getOffer().getState() == GrandExchangeOfferState.BOUGHT) {
            return -1;
        } else {
            return offerChangedEvent.getSlot();
        }
    }
}
