package com.dashery.flippingtables;

import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Singleton
public class PortfolioAdviceRepository {
    private PortfolioModels.AdviceResponse response;
    private PortfolioModels.Snapshot snapshot;
    private PortfolioModels.Action selected;
    private Instant savedAt;

    public synchronized void save(PortfolioModels.AdviceResponse response, PortfolioModels.Snapshot snapshot) {
        this.response = response;
        this.snapshot = snapshot;
        this.savedAt = Instant.now();
        this.selected = null;
    }

    public synchronized void clear() {
        response = null;
        snapshot = null;
        selected = null;
        savedAt = null;
    }

    public synchronized boolean isExpired() {
        return savedAt != null && !Instant.now().isBefore(savedAt.plus(Duration.ofMinutes(5)));
    }

    public synchronized void select(PortfolioModels.Action action) {
        if (response == null || isExpired() || !response.getAdvice().getActions().contains(action)) {
            throw new IllegalStateException("Request fresh advice before selecting a suggestion.");
        }
        selected = action;
    }

    public synchronized Optional<PortfolioModels.Action> selectedFor(int itemId, String side) {
        if (selected == null || isExpired() || selected.getItemId() != itemId) {
            return Optional.empty();
        }
        if (selected.getType().equals("CREATE_" + side)) {
            return Optional.of(selected);
        }
        return Optional.empty();
    }

    public synchronized short[] buyItemIds() {
        if (response == null || isExpired()) {
            return new short[0];
        }
        int[] ids = response.getAdvice().getActions().stream()
                .filter(action -> "CREATE_BUY".equals(action.getType()))
                .mapToInt(action -> Math.toIntExact(action.getItemId()))
                .filter(id -> id > 0 && id <= 65535)
                .distinct().toArray();
        short[] result = new short[ids.length];
        for (int index = 0; index < ids.length; index++) {
            result[index] = (short) ids[index];
        }
        return result;
    }

    public synchronized String sideOf(PortfolioModels.Action action) {
        if ("CREATE_BUY".equals(action.getType())) {
            return "BUY";
        }
        if ("CREATE_SELL".equals(action.getType())) {
            return "SELL";
        }
        if (snapshot == null) {
            return "";
        }
        return snapshot.getOpenOffers().stream()
                .filter(offer -> offer.getId().equals(action.getReplacesOfferId()))
                .map(PortfolioModels.OpenOffer::getSide)
                .findFirst().orElse("");
    }
}
