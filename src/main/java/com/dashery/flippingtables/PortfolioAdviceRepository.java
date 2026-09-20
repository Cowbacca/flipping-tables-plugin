package com.dashery.flippingtables;

import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class PortfolioAdviceRepository {
    private PortfolioModels.AdviceResponse response;
    private PortfolioModels.Snapshot snapshot;
    private PortfolioModels.Action selected;
    private Instant savedAt;
    private final Set<PortfolioModels.Action> completed = new HashSet<>();

    public synchronized void save(PortfolioModels.AdviceResponse response, PortfolioModels.Snapshot snapshot) {
        this.response = response;
        this.snapshot = snapshot;
        this.savedAt = Instant.now();
        this.selected = null;
        completed.clear();
    }

    public synchronized void clear() {
        response = null;
        snapshot = null;
        selected = null;
        savedAt = null;
        completed.clear();
    }

    public synchronized boolean isExpired() {
        return savedAt != null && !Instant.now().isBefore(savedAt.plus(Duration.ofMinutes(5)));
    }

    public synchronized void select(PortfolioModels.Action action) {
        if (response == null || isExpired() || completed.contains(action) || !response.getAdvice().getActions().contains(action)) {
            throw new IllegalStateException("Request fresh advice before selecting a suggestion.");
        }
        selected = action;
    }

    public synchronized Optional<PortfolioModels.Action> selectedFor(int itemId, String side) {
        if (response == null || isExpired()) {
            return Optional.empty();
        }
        if (selected != null && !completed.contains(selected) && selected.getItemId() == itemId
                && selected.getType().equals("CREATE_" + side)) {
            return Optional.of(selected);
        }
        List<PortfolioModels.Action> matching = availableActions().stream()
                .filter(action -> action.getItemId() == itemId && action.getType().equals("CREATE_" + side))
                .collect(Collectors.toList());
        return matching.size() == 1 ? Optional.of(matching.get(0)) : Optional.empty();
    }

    public synchronized void markCompleted(List<PortfolioModels.Action> actions) {
        if (response != null) {
            actions.stream().filter(response.getAdvice().getActions()::contains).forEach(completed::add);
            if (completed.contains(selected)) {
                selected = null;
            }
        }
    }

    public synchronized boolean isCompleted(PortfolioModels.Action action) {
        return completed.contains(action);
    }

    public synchronized List<PortfolioModels.Action> availableActions() {
        if (response == null || isExpired()) {
            return Collections.emptyList();
        }
        return response.getAdvice().getActions().stream().filter(action -> !completed.contains(action))
                .collect(Collectors.toList());
    }

    public synchronized short[] buyItemIds() {
        if (response == null || isExpired()) {
            return new short[0];
        }
        int[] ids = availableActions().stream()
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
